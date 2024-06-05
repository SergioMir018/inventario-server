package com.inventario.server.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.inventario.server.database.{DBProductTable, Product}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProductActor {

  sealed trait ProductCommand
  final case class InsertNewProduct(name: String, short_desc: String, desc: String, photo: String, replyTo: ActorRef[ProductResponse]) extends ProductCommand

  sealed trait ProductResponse
  final case class InsertNewProductResponse(id: UUID) extends ProductResponse
  final case class InsertNewProductFailedResponse(reason: String) extends ProductResponse

  def apply(): Behavior[ProductCommand] = Behaviors.receive{ (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext

    message match {
      case InsertNewProduct(name, short_desc, desc, photo, replyTo) =>
        val id = UUID.randomUUID()
        val product = Product(id, name, short_desc, desc, photo)

        DBProductTable.insertProduct(product).onComplete {
          case Success(_) =>
            replyTo ! InsertNewProductResponse(id)
          case Failure(ex) =>
            replyTo ! InsertNewProductFailedResponse(ex.getMessage)
        }

        Behaviors.same
    }
  }
}
