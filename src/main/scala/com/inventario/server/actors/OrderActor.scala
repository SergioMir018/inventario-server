package com.inventario.server.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.inventario.server.database.{Order, OrderDetails, OrderProduct, OrderTable, OrderProductTable, OrderDetailsTable}
import slick.jdbc.PostgresProfile.api._

import java.util.{Date, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object OrderActor {

  sealed trait OrderCommand
  case class CreateOrder(
                          clientID: UUID,
                          creationDate: Date,
                          totalPayment: Float,
                          products: Seq[FormatedOrderProductRequest],
                          details: OrderDetailsRequest,
                          replyTo: ActorRef[OrderResponse]
                        ) extends OrderCommand

  case class OrderDetailsRequest(shippingAddress: String, billingAddress: String, phoneNumber: String)
  case class FormatedOrderProductRequest(product_id: UUID, quantity: Int)

  sealed trait OrderResponse
  case class CreateOrderResponse(id: UUID) extends OrderResponse
  case class CreateOrderFailedResponse(reason: String) extends OrderResponse

  def apply()(implicit db: Database): Behavior[OrderCommand] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext

    def generateOrderName(): Future[String] = {
      OrderTable.countOrders().map { count =>
        s"Orden#${count + 1}"
      }
    }

    message match {
      case CreateOrder(clientID, creationDate, totalPayment, products, details, replyTo) =>
        generateOrderName().onComplete {
          case Success(orderName) =>
            val orderId = UUID.randomUUID()
            val formatedCreationDate = new java.sql.Date(creationDate.getTime)
            val newOrder = Order(orderId, "pending", orderName, clientID, formatedCreationDate, totalPayment)
            val orderProductsWithId = products.map(product => OrderProduct(orderId, product.product_id, product.quantity))
            val orderDetailsWithGeneratedId = OrderDetails(orderId, details.shippingAddress, details.billingAddress, details.phoneNumber)

            val orderInsertAction = OrderTable.insertOrder(newOrder)
            val productInsertAction = OrderProductTable.insertOrderProducts(orderProductsWithId)
            val detailsInsertAction = OrderDetailsTable.insertOrderDetails(orderDetailsWithGeneratedId)

            val combinedAction = for {
              orderResult <- orderInsertAction
              _ <- productInsertAction
              _ <- detailsInsertAction
            } yield orderResult

            combinedAction.onComplete {
              case Success(orderResult) =>
                replyTo ! CreateOrderResponse(orderId)
              case Failure(ex) =>
                replyTo ! CreateOrderFailedResponse(ex.getMessage)
            }

          case Failure(exception) =>
            replyTo ! CreateOrderFailedResponse(exception.getMessage)
        }

        Behaviors.same
    }
  }
}
