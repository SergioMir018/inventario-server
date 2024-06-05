package com.inventario.server.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.inventario.server.actors.UserActor.{GetUserAccountByIdFailedResponse, UserAccountLoginFailedResponse}
import com.inventario.server.database.{DBProductTable, Product}

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProductActor {

  sealed trait ProductCommand
  final case class InsertNewProduct(name: String, short_desc: String, desc: String, price: Float, photo: String, replyTo: ActorRef[ProductResponse]) extends ProductCommand
  final case class GetProductById(id: String, replyTo: ActorRef[ProductResponse]) extends ProductCommand
  final case class GetAllProducts(replyTo: ActorRef[ProductResponse]) extends ProductCommand

  sealed trait ProductResponse
  final case class InsertNewProductResponse(id: UUID) extends ProductResponse
  final case class InsertNewProductFailedResponse(reason: String) extends ProductResponse
  final case class GetProductByIdResponse(product: Product) extends ProductResponse
  final case class GetProductByIdFailedResponse(reason: String) extends ProductResponse
  final case class GetAllProductsResponse(allProducts: Seq[Product]) extends ProductResponse
  final case class GetAllProductsFailedResponse(reason: String) extends ProductResponse

  def apply(): Behavior[ProductCommand] = Behaviors.receive{ (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext

    message match {
      case InsertNewProduct(name, short_desc, desc, price, photo, replyTo) =>
        val id = UUID.randomUUID()
        val product = Product(id, name, short_desc, desc, price, photo)

        DBProductTable.insertProduct(product).onComplete {
          case Success(_) =>
            replyTo ! InsertNewProductResponse(id)
          case Failure(ex) =>
            replyTo ! InsertNewProductFailedResponse(ex.getMessage)
        }

        Behaviors.same
      case GetProductById(id, replyTo) =>
        val searchId = UUID.fromString(id)

        DBProductTable.searchProductById(searchId).onComplete {
          case Success(Some(product)) =>
            replyTo ! GetProductByIdResponse(product)
          case Success(None) =>
            replyTo ! GetProductByIdFailedResponse("Product not found")
          case Failure(ex) =>
            replyTo ! GetProductByIdFailedResponse(ex.getMessage)
        }

        Behaviors.same
      case GetAllProducts(replyTo) =>
        DBProductTable.getAllProducts.onComplete {
          case Success(products) =>
            replyTo ! GetAllProductsResponse(products)
          case Failure(ex) =>
            replyTo ! GetAllProductsFailedResponse(ex.getMessage)
        }

        Behaviors.same
    }
  }
}
