package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.inventario.server.actors.UserActor._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._

import scala.concurrent.Future
import scala.concurrent.duration._

case class AccountCreationRequest(name: String, email: String, password: String, role: String) {
  def toCommand(replyTo: ActorRef[UserResponse]): UserCommand = CreateUserAccount(name, email, password, role, replyTo)
}

case class RegisterVisitRequest(url: String, date: String) {
  def toCommand(replyTo: ActorRef[UserResponse]): UserCommand = {
    import com.inventario.server.utils.DateUtils._

    val formatedDate = parseDate(date)

    RegisterVisit(url, formatedDate, replyTo)
  }
}

class UserActorRouter(userAccount: ActorRef[UserCommand])(implicit system: ActorSystem[_]) extends CORSHandler {

  implicit val timeout: Timeout = 3.seconds

  private def createUserAccount(request: AccountCreationRequest): Future[UserResponse] = {
    userAccount.ask(replyTo => request.toCommand(replyTo))
  }

  private def registerVisit(request: RegisterVisitRequest): Future[UserResponse] = {
    userAccount.ask(replyTo => request.toCommand(replyTo))
  }

  private def getPageVisits: Future[UserResponse] = {
    userAccount.ask(replyTo => GetRegisteredVisits(replyTo))
  }

  private def searchUserAccountBySearchTerm(searchTerm: String): Future[UserResponse] = {
    userAccount.ask(replyTo => GetUserAccountBySearchTerm(searchTerm, replyTo))
  }

  private def searchUserAccountById(id: String): Future[UserResponse] = {
    userAccount.ask(replyTo => GetUserAccountById(id, replyTo))
  }

  private def loginUserAccount(identifier: String, password: String): Future[UserResponse] = {
    userAccount.ask(replyTo => UserAccountLogin(identifier, password, replyTo))
  }

  val routes: Route = corsHandler {
    pathPrefix("user") {
      path("visit") {
        post {
          entity(as[RegisterVisitRequest]) { request =>
            onSuccess(registerVisit(request)) {
              case RegisterVisitResponse(confirmation) =>
                system.log.info(confirmation)
                complete(StatusCodes.OK, confirmation)
              case RegisterVisitFailedResponse(reason) =>
                system.log.error(reason)
                complete(StatusCodes.InternalServerError, reason)
            }
          }
        } ~
          get {
            onSuccess(getPageVisits) {
              case GetRegisteredVisitsResponse(numberVisits) =>
                system.log.info(s"Registered visits requested number: ${numberVisits.length}")
                complete(StatusCodes.OK, numberVisits)
              case GetRegisteredVisitsFailedResponse(reason) =>
                system.log.error(reason)
                complete(StatusCodes.InternalServerError, s"Failed to get all products: $reason")
            }
          }
      } ~
      path("create") {
        post {
          entity(as[AccountCreationRequest]) { request =>
            onSuccess(createUserAccount(request)) {
              case CreateUserAccountResponse(id) =>
                complete(StatusCodes.Created, id.toString)
              case UserAccountCreationFailedResponse(reason) =>
                complete(StatusCodes.InternalServerError, s"Failed to create user: $reason")
            }
          }
        }
      } ~
        path("search") {
          get {
            parameter("q") { searchTerm =>
              onSuccess(searchUserAccountBySearchTerm(searchTerm)) {
                case GetUserAccountBySearchTermResponse(user) =>
                  complete(StatusCodes.OK, user)
                case GetUserAccountBySearchTermFailedResponse(reason) =>
                  complete(StatusCodes.NotFound, s"No user found: $reason")
              }
            }
          }
        } ~
        path("searchId") {
          get {
            parameter("id") { id =>
              onSuccess(searchUserAccountById(id)) {
                case GetUserAccountByIdResponse(user) =>
                  complete(StatusCodes.OK, user)
                case GetUserAccountByIdFailedResponse(reason) =>
                  complete(StatusCodes.NotFound, s"No user found: $reason")
              }
            }
          }
        } ~
        path("login") {
          post {
            parameters("q", "p") { (identifier, password) =>
              onSuccess(loginUserAccount(identifier, password)) {
                case UserAccountLoginResponse(responseBody) =>
                  complete(StatusCodes.OK, responseBody)
                case UserAccountLoginFailedResponse(error) =>
                  if (error.errorType == "server") {
                    complete(StatusCodes.InternalServerError, error)
                  } else {
                    complete(StatusCodes.NotFound, error)
                  }
              }
            }
          }
        } ~
        options {
          complete(HttpResponse(StatusCodes.OK)
            .withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, GET, PUT)))
        }
    }
  }
}
