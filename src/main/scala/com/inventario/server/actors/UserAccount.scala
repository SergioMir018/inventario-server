package com.inventario.server.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import com.inventario.server.database.{SlickTables, User}

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object UserAccount {

  sealed trait UserCommand
  final case class CreateUserAccount(name: String, email: String, password: String, role: String, replyTo: ActorRef[UserResponse]) extends UserCommand
  final case class GetUserAccount(searchValue: String, replyTo: ActorRef[UserResponse]) extends UserCommand

  sealed trait UserResponse
  final case class CreateUserAccountResponse(id: UUID) extends UserResponse
  final case class UserAccountCreationFailed(reason: String) extends UserResponse
  final case class GetUserAccountResponse(user: User) extends UserResponse
  final case class UserAccountSearchFailed(reason: String) extends UserResponse

  def apply(): Behavior[UserCommand] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContextExecutor = context.executionContext

    message match {
      case CreateUserAccount(name, email, password, role, replyTo) =>
        val id = UUID.randomUUID()
        val user = User(id, name, email, password, role)

        SlickTables.createUser(user).onComplete {
          case Success(_) =>
            replyTo ! CreateUserAccountResponse(id)
          case Failure(ex) =>
            replyTo ! UserAccountCreationFailed(ex.getMessage)
        }

        Behaviors.same
      case GetUserAccount(searchValue, replyTo) =>

        SlickTables.searchUser(searchValue).onComplete {
          case Success(Some(user)) =>
            replyTo ! GetUserAccountResponse(user)
          case Success(None) =>
            replyTo ! UserAccountSearchFailed("This user doesn't exists")
          case Failure(ex) =>
            replyTo ! UserAccountSearchFailed(ex.getMessage)
        }
        Behaviors.same
    }
  }
}
