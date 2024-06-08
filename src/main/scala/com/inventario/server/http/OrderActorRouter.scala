package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.HttpMethods.{DELETE, GET, OPTIONS, POST, PUT}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Methods`
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.inventario.server.actors.OrderActor._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import java.text.SimpleDateFormat
import java.util.{Date, UUID}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

case class OrderProductRequest(product_id: String, quantity: Int)

case class OrderCreationRequest(clientID: String, creationDate: String, totalPayment: Float, products: Seq[OrderProductRequest], details: OrderDetailsRequest) {
  private def parseDate(dateString: String, pattern: String = "yyyy-MM-dd"): Date = {
    val dateFormat = new SimpleDateFormat(pattern)
    val utilDate = dateFormat.parse(dateString)
    new Date(utilDate.getTime)
  }

  private def convertProductIDs(products: Seq[OrderProductRequest]): Seq[FormatedOrderProductRequest] = {
    products.map(product => FormatedOrderProductRequest(UUID.fromString(product.product_id), product.quantity))
  }

  def toCommand(replyTo: ActorRef[OrderResponse]): OrderCommand = {
    val formatedClientId = UUID.fromString(clientID)
    val formatedDate = parseDate(creationDate)
    val formattedProducts = convertProductIDs(products)
    CreateOrder(formatedClientId, formatedDate, totalPayment, formattedProducts, details, replyTo)
  }
}

class OrderActorRouter(order: ActorRef[OrderCommand])(implicit system: ActorSystem[_]) extends CORSHandler {
  implicit val timeout: Timeout = 3.seconds

  private def createOrder(request: OrderCreationRequest): Future[OrderResponse] = {
    order.ask(replyTo => request.toCommand(replyTo))
  }

  private def getAllOrders: Future[OrderResponse] = {
    order.ask(replyTo => GetAllOrders(replyTo))
  }

  val routes = corsHandler {
    options {
      complete(HttpResponse(StatusCodes.OK)
        .withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, GET, PUT, DELETE)))
    } ~
      pathPrefix("order") {
        path("create") {
          post {
            entity(as[OrderCreationRequest]) { request =>
              onSuccess(createOrder(request)) {
                case CreateOrderResponse(id) =>
                  complete(StatusCodes.Created, id)
                case CreateOrderFailedResponse(reason) =>
                  complete(StatusCodes.InternalServerError, reason)
              }
            }
          }
        } ~
          path("all") {
            get {
              onSuccess(getAllOrders) {
                case GetAllOrdersResponse(orders) =>
                  complete(StatusCodes.OK, orders)
                case GetAllOrdersFailedResponse(reason) =>
                  complete(StatusCodes.InternalServerError, reason)
              }
            }
          }
      }
  }
}
