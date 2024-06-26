package com.inventario.server.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import com.inventario.server.database.{User, UserTable, Visit, VisitResponse, VisitsTable}

import java.sql.Timestamp
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserActor {

  sealed trait UserCommand
  final case class RegisterVisit(url: String, date: Date, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class GetRegisteredVisits(replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class CreateUserAccount(name: String, email: String, password: String, role: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class GetUserAccountBySearchTerm(searchValue: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class UserAccountLogin(identifier: String, password: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class GetUserAccountById(id: String, replyTo: ActorRef[UserResponse]) extends UserCommand

  sealed trait UserResponse
  final case class RegisterVisitResponse(confirmation: String) extends UserResponse
  final case class RegisterVisitFailedResponse(reason: String) extends UserResponse
  final case class GetRegisteredVisitsResponse(visits: Seq[VisitResponse]) extends UserResponse
  final case class GetRegisteredVisitsFailedResponse(reason: String) extends UserResponse
  final case class CreateUserAccountResponse(id: UUID) extends UserResponse
  final case class UserAccountCreationFailedResponse(reason: String) extends UserResponse
  final case class GetUserAccountBySearchTermResponse(user: User) extends UserResponse
  final case class GetUserAccountBySearchTermFailedResponse(reason: String) extends UserResponse
  final case class UserAccountLoginResponse(responseBody: LoginResponseBody) extends UserResponse
  final case class UserAccountLoginFailedResponse(error: LoginFailedResponseBody) extends UserResponse
  final case class GetUserAccountByIdResponse(userDetails: AccountDetails) extends UserResponse
  final case class GetUserAccountByIdFailedResponse(reason: String) extends UserResponse

  final case class LoginResponseBody(role: String, id: UUID)
  final case class LoginFailedResponseBody(errorType: String, reason: String)
  final case class AccountDetails(name: String, email: String)

  def apply(): Behavior[UserCommand] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext

    message match {
      case RegisterVisit(url, date, replyTo) =>
        val timestamp = new Timestamp(date.getTime)
        val visit = Visit(None, url, timestamp)

        VisitsTable.insertVisit(visit).onComplete {
          case Success(_) =>
            replyTo ! RegisterVisitResponse("New page visit registered")
          case Failure(exception) =>
            replyTo ! RegisterVisitFailedResponse(exception.getMessage)
        }

        Behaviors.same
      case GetRegisteredVisits(replyTo) =>
        VisitsTable.getAllVisits.onComplete {
          case Success(visits) =>
            replyTo ! GetRegisteredVisitsResponse(visits)
          case Failure(exception) =>
            replyTo ! GetRegisteredVisitsFailedResponse(exception.getMessage)
        }

        Behaviors.same
      case CreateUserAccount(name, email, password, role, replyTo) =>
        val id = UUID.randomUUID()
        val user = User(id, name, email, password, role)

        UserTable.createUser(user).onComplete {
          case Success(_) =>
            replyTo ! CreateUserAccountResponse(id)
          case Failure(ex) =>
            replyTo ! UserAccountCreationFailedResponse(ex.getMessage)
        }

        Behaviors.same
      case GetUserAccountBySearchTerm(searchValue, replyTo) =>
        UserTable.searchUserByTerm(searchValue).onComplete {
          case Success(Some(user)) =>
            replyTo ! GetUserAccountBySearchTermResponse(user)
          case Success(None) =>
            replyTo ! GetUserAccountBySearchTermFailedResponse("This user doesn't exists")
          case Failure(ex) =>
            replyTo ! GetUserAccountBySearchTermFailedResponse(ex.getMessage)
        }

        Behaviors.same
      case UserAccountLogin(identifier, password, replyTo) =>
        UserTable.searchUserByTerm(identifier).onComplete {
          case Success(Some(user)) =>
            if (password == user.password) {
              val loginResponse = LoginResponseBody(user.role, user.id)
              replyTo ! UserAccountLoginResponse(loginResponse)
            } else {
              val loginErrorResponse = LoginFailedResponseBody("password", "Contraseña inválida")
              replyTo ! UserAccountLoginFailedResponse(loginErrorResponse)
            }
          case Success(None) =>
            val loginErrorResponse = LoginFailedResponseBody("user", "Usuario inválido")
            replyTo ! UserAccountLoginFailedResponse(loginErrorResponse)
          case Failure(ex) =>
            val loginErrorResponse = LoginFailedResponseBody("server", s"Database error: ${ex.getMessage}")
            replyTo ! UserAccountLoginFailedResponse(loginErrorResponse)
        }

        Behaviors.same
      case GetUserAccountById(id, replyTo) =>
        val searchId = UUID.fromString(id)
        UserTable.searchUserById(searchId).onComplete {
          case Success(Some(user)) =>
            val userDetails = AccountDetails(user.name, user.email)
            replyTo ! GetUserAccountByIdResponse(userDetails)
          case Success(None) =>
            replyTo ! GetUserAccountByIdFailedResponse("User not found")
          case Failure(ex) =>
            val loginErrorResponse = LoginFailedResponseBody("server", s"Database error: ${ex.getMessage}")
            replyTo ! UserAccountLoginFailedResponse(loginErrorResponse)
        }

        Behaviors.same
    }
  }
}