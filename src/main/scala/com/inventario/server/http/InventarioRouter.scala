package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.inventario.server.actors.UserAccount
import com.inventario.server.actors.UserAccount.{CreateUserAccount, CreateUserAccountResponse, GetUserAccount, UserAccountCreationFailed, UserCommand, UserResponse}
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future
import scala.concurrent.duration._

case class AccountCreationRequest(name: String, email: String, password: String, role: String) {
  def toCommand(replyTo: ActorRef[UserResponse]): UserCommand = CreateUserAccount(name, email, password, role, replyTo)
}

class InventarioRouter(userAccount: ActorRef[UserAccount.UserCommand])(implicit system: ActorSystem[_]) {

  implicit val timeout: Timeout = 3.seconds

  private def createUserAccount(request: AccountCreationRequest): Future[UserResponse] = {
    userAccount.ask(replyTo => request.toCommand(replyTo))
  }

  private def searchUserAccount(searchTerm: String): Future[UserResponse] = {
    userAccount.ask(replyTo => GetUserAccount(searchTerm, replyTo))
  }

  val routes: Route = {
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
                case UserAccount.GetUserAccountResponse(user) =>
                  complete(StatusCodes.OK, user)
                case UserAccount.UserAccountSearchFailed(reason) =>
                  complete(StatusCodes.NotFound, s"No user found: $reason")
              }
            }
          }
        }
    }
  }
}
