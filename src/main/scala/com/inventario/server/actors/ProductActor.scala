package com.inventario.server.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.inventario.server.database.{Product, ProductSearchResponse, ProductTable}
import com.inventario.server.http.ProductUpdateRequest
import com.inventario.server.utils.ImageUtils.saveImage

import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ProductActor {

  sealed trait ProductCommand
  final case class InsertNewProduct(name: String, short_desc: String, desc: String, price: Float, photoExt: String, replyTo: ActorRef[ProductResponse]) extends ProductCommand
  final case class GetProductById(id: String, replyTo: ActorRef[ProductResponse]) extends ProductCommand
  final case class GetAllProducts(replyTo: ActorRef[ProductResponse]) extends ProductCommand
  final case class DeleteProductById(id: String, replyTo: ActorRef[ProductResponse]) extends ProductCommand
  final case class UpdateProduct(id: UUID, updateData: ProductUpdateRequest, replyTo: ActorRef[ProductResponse]) extends ProductCommand

  sealed trait ProductResponse
  final case class InsertNewProductResponse(id: UUID) extends ProductResponse
  final case class InsertNewProductFailedResponse(reason: String) extends ProductResponse
  final case class GetProductByIdResponse(product: ProductSearchResponse) extends ProductResponse
  final case class GetProductByIdFailedResponse(reason: String) extends ProductResponse
  final case class GetAllProductsResponse(allProducts: Seq[ProductSearchResponse]) extends ProductResponse
  final case class GetAllProductsFailedResponse(reason: String) extends ProductResponse
  final case class DeleteProductByIdResponse(success: Boolean) extends ProductResponse
  final case class DeleteProductByIdFailedResponse(reason: String) extends ProductResponse
  final case class UpdateProductResponse(success: Boolean) extends ProductResponse
  final case class UpdateProductFailedResponse(reason: String) extends ProductResponse

  def apply(): Behavior[ProductCommand] = Behaviors.receive { (context, message) =>
    implicit val ec: ExecutionContext = context.executionContext

    message match {
      case InsertNewProduct(name, short_desc, desc, price, photoExt, replyTo) =>
        val id = UUID.randomUUID()
        val photoPath = s"public/photos/$id.$photoExt"
        // cambiar por id de la categoria que llegue
        val product = Product(id, name, short_desc, desc, price, photoPath, id)

        ProductTable.insertProduct(product).onComplete {
          case Success(_) =>
            replyTo ! InsertNewProductResponse(id)
          case Failure(ex) =>
            replyTo ! InsertNewProductFailedResponse(ex.getMessage)
        }

        Behaviors.same

      case GetProductById(id, replyTo) =>
        val searchId = UUID.fromString(id)

        ProductTable.searchProductById(searchId).onComplete {
          case Success(Some(product)) =>
            replyTo ! GetProductByIdResponse(product)
          case Success(None) =>
            replyTo ! GetProductByIdFailedResponse("Product not found")
          case Failure(ex) =>
            replyTo ! GetProductByIdFailedResponse(ex.getMessage)
        }

        Behaviors.same

      case GetAllProducts(replyTo) =>
        ProductTable.getAllProducts.onComplete {
          case Success(products) =>
            replyTo ! GetAllProductsResponse(products)
          case Failure(ex) =>
            replyTo ! GetAllProductsFailedResponse(ex.getMessage)
        }

        Behaviors.same

      case DeleteProductById(id, replyTo) =>
        val deleteId = UUID.fromString(id)
        var deletePath = "src/main/resources/"

        ProductTable.searchProductById(deleteId).onComplete {
          case Success(Some(product)) =>
            deletePath = deletePath.concat(product.photo)
        }

        ProductTable.deleteProductById(deleteId).onComplete {
          case Success(_) =>
            Files.deleteIfExists(Paths.get(deletePath))
            replyTo ! DeleteProductByIdResponse(success = true)
          case Failure(ex) => replyTo ! DeleteProductByIdFailedResponse(ex.getMessage)
        }

        Behaviors.same

      case UpdateProduct(id, updateData, replyTo) =>

        var deletePath = "src/main/resources/"

        ProductTable.searchProductById(id).onComplete {
          case Success(Some(product)) =>

            deletePath = deletePath.concat(product.photo)

            val updatedProduct = Product(
              id = id,
              name = updateData.name.getOrElse(product.name),
              short_desc = updateData.short_desc.getOrElse(product.short_desc),
              desc = updateData.desc.getOrElse(product.desc),
              price = updateData.price.getOrElse(product.price),
              photo = updateData.photo.map { base64Photo =>
                val photoExt = updateData.imageExt.getOrElse(product.photo.split('.').last)
                val photoPath = s"public/photos/$id.$photoExt"
                context.system.log.info(s"Saving new photo for product $id at $photoPath")
                Files.deleteIfExists(Paths.get(deletePath))
                saveImage(id.toString, base64Photo, photoExt, context.system)
                photoPath
              }.getOrElse(product.photo),
              // cambiar por id de la categoria que llegue
              category_id = id,
            )

            ProductTable.updateProduct(updatedProduct).onComplete {
              case Success(_) =>
                replyTo ! UpdateProductResponse(success = true)
              case Failure(ex) =>
                replyTo ! UpdateProductFailedResponse(ex.getMessage)
            }

          case Success(None) =>
            replyTo ! UpdateProductFailedResponse("Product not found")

          case Failure(ex) =>
            replyTo ! UpdateProductFailedResponse(ex.getMessage)
        }

        Behaviors.same
    }
  }
}
