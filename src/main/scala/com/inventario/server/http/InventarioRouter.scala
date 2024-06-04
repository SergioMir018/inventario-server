package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.inventario.server.actors.UserAccount
import com.inventario.server.actors.UserAccount.{Command, CreateUserAccount, Response, UserAccountCreated, UserAccountCreationFailed}
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future
import scala.concurrent.duration._

case class AccountCreationRequest(name: String, email: String, password: String, role: String) {
  def toCommand(replyTo: ActorRef[Response]): Command = CreateUserAccount(name, email, password, role, replyTo)
}

class InventarioRouter(userAccount: ActorRef[UserAccount.Command])(implicit system: ActorSystem[_]) {

  implicit val timeout: Timeout = 3.seconds

  def createUserAccount(request: AccountCreationRequest): Future[UserAccount.Response] = {
    userAccount.ask(replyTo => request.toCommand(replyTo))
  }

  val routes: Route = {
    pathPrefix("user") {
      path("create") {
        post {
          entity(as[AccountCreationRequest]) { request =>
            onSuccess(createUserAccount(request)) {
              case UserAccountCreated(id) =>
                complete(StatusCodes.Created, s"User created with ID: ${id.toString}")
              case UserAccountCreationFailed(reason) =>
                complete(StatusCodes.InternalServerError, s"Failed to create user: $reason")
            }
          }
        }
      }
    }
  }
}
