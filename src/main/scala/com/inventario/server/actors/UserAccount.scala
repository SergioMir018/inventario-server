package com.inventario.server.actors

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import com.inventario.server.database.{SlickTables, User}

import java.util.UUID
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object UserAccount {

  sealed trait Command
  final case class CreateUserAccount(name: String, email: String, password: String, role: String, replyTo: ActorRef[Response]) extends Command

  sealed trait Response
  final case class UserAccountCreated(id: UUID) extends Response
  final case class UserAccountCreationFailed(reason: String) extends Response

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContextExecutor = context.executionContext

    message match {
      case CreateUserAccount(name, email, password, role, replyTo) =>
        val id = UUID.randomUUID()
        val user = User(id, name, email, password, role)

        SlickTables.createUser(user).onComplete {
          case Success(_) =>
            replyTo ! UserAccountCreated(id)
          case Failure(ex) =>
            replyTo ! UserAccountCreationFailed(ex.getMessage)
        }

        Behaviors.same
    }
  }
}
