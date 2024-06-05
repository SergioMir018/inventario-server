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

class UserActorRouter(userAccount: ActorRef[UserCommand])(implicit system: ActorSystem[_]) extends CORSHandler {

  implicit val timeout: Timeout = 3.seconds

  private def createUserAccount(request: AccountCreationRequest): Future[UserResponse] = {
    userAccount.ask(replyTo => request.toCommand(replyTo))
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
                case UserAccountLoginFailedResponse(reason) =>
                  complete(StatusCodes.Unauthorized, reason)
                case UserAccountLoginFailedResponse(reason) =>
                  complete(StatusCodes.NotFound, reason)
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
