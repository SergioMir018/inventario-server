package com.inventario.server.http

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.HttpMethods.{DELETE, GET, OPTIONS, POST, PUT}
import akka.http.scaladsl.model.headers.`Access-Control-Allow-Methods`
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.inventario.server.actors.ProductActor._
import com.inventario.server.utils.ImageUtils.saveImage
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

case class ProductInsertionRequest(name: String, category: String, short_desc: String, desc: String, price: Float, photo: String, imageExt: String) {
  def toCommand(replyTo: ActorRef[ProductResponse]): ProductCommand = InsertNewProduct(name, category, short_desc, desc, price, imageExt, replyTo)
}

case class ProductUpdateRequest(name: Option[String], category: Option[String], short_desc: Option[String], desc: Option[String], price: Option[Float], photo: Option[String], imageExt: Option[String])

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

  private def getAllCategories: Future[ProductResponse] = {
    product.ask(replyTo => GetAllCategories(replyTo))
  }

  private def deleteProductById(id: String): Future[ProductResponse] = {
    product.ask(replyTo => DeleteProductById(id, replyTo))
  }

  private def updateProduct(id: String, request: ProductUpdateRequest): Future[ProductResponse] = {
    val productId = UUID.fromString(id)
    product.ask(replyTo => UpdateProduct(productId, request, replyTo))
  }

  val routes: Route = corsHandler {
    options {
      complete(HttpResponse(StatusCodes.OK)
        .withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, GET, PUT, DELETE)))
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
                    saveImage(id.toString, request.photo, request.imageExt, system)
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
          } ~
          path("delete") {
            delete {
              parameter("id") { id =>
                onSuccess(deleteProductById(id)) {
                  case DeleteProductByIdResponse(success) =>
                    complete(StatusCodes.OK, success)
                  case DeleteProductByIdFailedResponse(reason) =>
                    complete(StatusCodes.NotFound, s"Product not found: $reason")
                }
              }
            }
          } ~
          path("update") {
            put {
              parameter("id") { id =>
                entity(as[ProductUpdateRequest]) { request =>
                  onSuccess(updateProduct(id, request)) {
                    case UpdateProductResponse(success) if success =>
                      complete(StatusCodes.OK, "Product updated successfully")
                    case UpdateProductResponse(_) =>
                      complete(StatusCodes.NotFound, "Product not found")
                    case UpdateProductFailedResponse(reason) =>
                      complete(StatusCodes.InternalServerError, reason)
                  }
                }
              }
            }
          } ~
          path("categories") {
            get {
              onSuccess(getAllCategories) {
                case GetAllCategoriesResponse(allCategories) =>
                  complete(StatusCodes.OK, allCategories)
                case GetAllCategoriesFailedResponse(reason) =>
                  complete(StatusCodes.InternalServerError, s"Failed to get all categories: $reason")
              }
            }
          }
      }
  }
}
