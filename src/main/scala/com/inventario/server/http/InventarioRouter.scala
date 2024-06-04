package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}
import akka.util.Timeout
import com.inventario.server.actors.UserAccount
import com.inventario.server.actors.UserAccount._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._

import scala.concurrent.Future
import scala.concurrent.duration._

case class AccountCreationRequest(name: String, email: String, password: String, role: String) {
  def toCommand(replyTo: ActorRef[UserResponse]): UserCommand = CreateUserAccount(name, email, password, role, replyTo)
}

trait CORSHandler{

  private val corsResponseHeaders = List(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Credentials`(true),
    `Access-Control-Allow-Headers`("Authorization",
      "Content-Type", "X-Requested-With")
  )

  //this directive adds access control headers to normal responses
  private def addAccessControlHeaders: Directive0 = {
    respondWithHeaders(corsResponseHeaders)
  }

  //this handles preflight OPTIONS requests.
  private def preflightRequestHandler: Route = options {
    complete(HttpResponse(StatusCodes.OK).
      withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))
  }

  // Wrap the Route with this method to enable adding of CORS headers
  def corsHandler(r: Route): Route = addAccessControlHeaders {
    preflightRequestHandler ~ r
  }

  // Helper method to add CORS headers to HttpResponse
  // preventing duplication of CORS headers across code
  def addCORSHeaders(response: HttpResponse):HttpResponse =
    response.withHeaders(corsResponseHeaders)

}

class InventarioRouter(userAccount: ActorRef[UserAccount.UserCommand])(implicit system: ActorSystem[_]) extends CORSHandler {

  implicit val timeout: Timeout = 3.seconds

  private def createUserAccount(request: AccountCreationRequest): Future[UserResponse] = {
    userAccount.ask(replyTo => request.toCommand(replyTo))
  }

  private def searchUserAccount(searchTerm: String): Future[UserResponse] = {
    userAccount.ask(replyTo => GetUserAccount(searchTerm, replyTo))
  }

  private def loginUserAccount(identifier: String, password: String): Future[UserResponse] = {
    userAccount.ask(replyTo => UserAccountLogin(identifier, password, replyTo))
  }

  private val cors = new CORSHandler {}

  val routes: Route = corsHandler {
    pathPrefix("user") {
      path("create") {
        post {
          entity(as[AccountCreationRequest]) { request =>
            onSuccess(createUserAccount(request)) {
              case CreateUserAccountResponse(id) =>
                complete(StatusCodes.Created, s"User created with ID: ${id.toString}")
              case UserAccountCreationFailed(reason) =>
                complete(StatusCodes.InternalServerError, s"Failed to create user: $reason")
            }
          }
        }
      } ~
        path("search") {
          get {
            parameter("q") { searchTerm =>
              onSuccess(searchUserAccount(searchTerm)) {
                case GetUserAccountResponse(user) =>
                  complete(StatusCodes.OK, user)
                case UserAccountSearchFailed(reason) =>
                  complete(StatusCodes.NotFound, s"No user found: $reason")
              }
            }
          }
        } ~
        path("login") {
          post {
            parameters("q", "p") { (identifier, password) =>
              onSuccess(loginUserAccount(identifier, password)) {
                case UserAccountLoginResponse(role) =>
                  complete(StatusCodes.OK, role)
                case UserAccountLoginResponse(reason) =>
                  complete(StatusCodes.Unauthorized, reason)
                case UserAccountSearchFailed(reason) =>
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
