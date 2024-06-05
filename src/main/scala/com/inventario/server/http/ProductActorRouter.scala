package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.model.HttpMethods.{GET, OPTIONS, POST, PUT}
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Methods`
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.inventario.server.actors.ProductActor._
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future
import scala.concurrent.duration._

case class ProductInsertionRequest(name: String, short_desc: String, desc: String, photo: String) {
  def toCommand(replyTo: ActorRef[ProductResponse]): ProductCommand = InsertNewProduct(name, short_desc, desc, photo, replyTo)
}

class ProductActorRouter(product: ActorRef[ProductCommand])(implicit system: ActorSystem[_]) extends CORSHandler {
  implicit val timeout: Timeout = 3.seconds

  private def insertProduct(request: ProductInsertionRequest): Future[ProductResponse] = {
    product.ask(replyTo => request.toCommand(replyTo))
  }

  private def searchProductById(id: String): Future[ProductResponse] = {
    product.ask(replyTo => GetProductById(id, replyTo))
  }

  private def getAllProducts: Future[ProductResponse] = {
    product.ask(replyTo => GetAllProducts(replyTo))
  }

  val routes: Route = corsHandler {
    options {
      complete(HttpResponse(StatusCodes.OK)
        .withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, GET, PUT)))
    } ~
      pathPrefix("product") {
        path("all") {
          get {
            onSuccess(getAllProducts) {
              case GetAllProductsResponse(allProducts) =>
                complete(StatusCodes.OK, allProducts)
              case GetAllProductsFailedResponse(reason) =>
                complete(StatusCodes.InternalServerError, s"Failed to get all products: $reason")
            }
          }
        } ~
        path("create") {
          post {
            entity(as[ProductInsertionRequest]) { request =>
              onSuccess(insertProduct(request)) {
                case InsertNewProductResponse(id) =>
                  complete(StatusCodes.Created, id.toString)
                case InsertNewProductFailedResponse(reason) =>
                  complete(StatusCodes.InternalServerError, s"Failed to create product: $reason")
              }
            }
          }
        } ~
          path("searchId") {
            get {
              parameter("id") { id =>
                onSuccess(searchProductById(id)) {
                  case GetProductByIdResponse(product) =>
                    complete(StatusCodes.OK, product)
                  case GetProductByIdFailedResponse(reason) =>
                    complete(StatusCodes.NotFound, s"Product not found: $reason")
                }
              }
            }
          }
      }
  }
}
