package com.inventario.server.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.inventario.server.database._

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
  case class GetAllOrders(replyTo: ActorRef[OrderResponse]) extends OrderCommand
  case class GetOrderById(orderId: String, replyTo: ActorRef[OrderResponse]) extends OrderCommand
  case class UpdateOrderStatus(id: String, newStatus: String, replyTo: ActorRef[OrderResponse]) extends OrderCommand

  case class OrderDetailsRequest(shippingAddress: String, billingAddress: String, phoneNumber: String)
  case class FormatedOrderProductRequest(product_id: UUID, quantity: Int)

  sealed trait OrderResponse
  case class CreateOrderResponse(id: UUID) extends OrderResponse
  case class CreateOrderFailedResponse(reason: String) extends OrderResponse
  case class GetAllOrdersResponse(orders: Seq[OrderResponseWithDetails]) extends OrderResponse
  case class GetAllOrdersFailedResponse(reason: String) extends OrderResponse
  case class GetOrderByIdResponse(order: OrderResponseWithDetails) extends OrderResponse
  case class GetOrderByIdFailedResponse(reason: String) extends OrderResponse
  case class UpdateOrderStatusResponse(updatedStatus: String) extends OrderResponse
  case class UpdateOrderStatusFailedResponse(reason: String) extends OrderResponse

  def apply(): Behavior[OrderCommand] = Behaviors.receive { (context, message) =>
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
            val newOrder = Order(orderId, "Pendiente", orderName, clientID, formatedCreationDate, totalPayment)
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

      case GetAllOrders(replyTo) =>

        FullOrderTable
          .getAllOrdersWithDetails
          .onComplete {
          case Success(orders) =>
            replyTo ! GetAllOrdersResponse(orders)
          case Failure(exception) =>
            replyTo ! GetAllOrdersFailedResponse(exception.getMessage)
        }

        Behaviors.same
      case GetOrderById(orderId, replyTo) =>
        val formatedOrderId = UUID.fromString(orderId)

        FullOrderTable
          .getAllOrdersWithDetails
          .onComplete {
            case Success(orders) =>
              val requestedOrder = orders.filter(order => order.orderId == formatedOrderId).head

              replyTo ! GetOrderByIdResponse(requestedOrder)
            case Failure(exception) =>
              replyTo ! GetOrderByIdFailedResponse(exception.getMessage)
          }

        Behaviors.same
      case UpdateOrderStatus(id, newStatus, replyTo) =>
        val formatedId = UUID.fromString(id)

        OrderTable.updateOrderStatus(formatedId, newStatus).onComplete {
          case Success(_) =>
            replyTo ! UpdateOrderStatusResponse(newStatus)
          case Failure(exception) =>
            replyTo ! UpdateOrderStatusFailedResponse(exception.getMessage)
        }

        Behaviors.same
    }
  }
}
