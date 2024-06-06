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

import java.nio.file.{Files, Paths}
import java.util.Base64
import scala.concurrent.Future
import scala.concurrent.duration._

case class ProductInsertionRequest(name: String, short_desc: String, desc: String, price: Float, photo: String, imageExt: String) {
  def toCommand(replyTo: ActorRef[ProductResponse]): ProductCommand = InsertNewProduct(name, short_desc, desc, price, imageExt, replyTo)
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

  private def saveImage(id: String, base64Image: String, photoExt: String): Unit = {
    try {
      if (isValidBase64(base64Image)) {
        val imageBytes = Base64.getDecoder.decode(base64Image)
        val fileRoute = Paths.get("src/main/resources/public/photos", s"$id.$photoExt")

        Files.write(fileRoute, imageBytes)
      } else {
        system.log.error("The base64 string contains invalid characters")
      }
    } catch {
      case e: IllegalArgumentException =>
        system.log.error(s"Error while decoding base64 string: ${e.getMessage}")
    }
  }

  private def isValidBase64(base64String: String): Boolean = {
    val base64Pattern = "^[a-zA-Z0-9+/]*={0,2}$".r
    base64Pattern.pattern.matcher(base64String).matches()
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
                  saveImage(id.toString, request.photo, request.imageExt)
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
