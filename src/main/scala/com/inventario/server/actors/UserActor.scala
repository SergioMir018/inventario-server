package com.inventario.server.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import com.inventario.server.database.{UserTable, User}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object UserActor {

  sealed trait UserCommand
  final case class CreateUserAccount(name: String, email: String, password: String, role: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class GetUserAccountBySearchTerm(searchValue: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class UserAccountLogin(identifier: String, password: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class GetUserAccountById(id: String, replyTo: ActorRef[UserResponse]) extends UserCommand

  sealed trait UserResponse
  final case class CreateUserAccountResponse(id: UUID) extends UserResponse
  final case class UserAccountCreationFailedResponse(reason: String) extends UserResponse
  final case class GetUserAccountBySearchTermResponse(user: User) extends UserResponse
  final case class GetUserAccountBySearchTermFailedResponse(reason: String) extends UserResponse
  final case class UserAccountLoginResponse(responseBody: LoginResponseBody) extends UserResponse
  final case class UserAccountLoginFailedResponse(reason: String) extends UserResponse
  final case class GetUserAccountByIdResponse(userDetails: AccountDetails) extends UserResponse
  final case class GetUserAccountByIdFailedResponse(reason: String) extends UserResponse

  final case class LoginResponseBody(role: String, id: UUID)
  final case class AccountDetails(name: String, email: String)

  def apply(): Behavior[UserCommand] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext

    message match {
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
          case Success(Some(user)) if password.equals(user.password) =>
            val loginResponse = LoginResponseBody(user.role, user.id)
            replyTo ! UserAccountLoginResponse(loginResponse)
          case Success(Some(_)) =>
            replyTo ! UserAccountLoginFailedResponse("Invalid password")
          case Success(None) =>
            replyTo ! UserAccountLoginFailedResponse("User not found")
          case Failure(ex) =>
            replyTo ! UserAccountLoginFailedResponse(ex.getMessage)
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
            replyTo ! UserAccountLoginFailedResponse(ex.getMessage)
        }

        Behaviors.same
    }
  }
}