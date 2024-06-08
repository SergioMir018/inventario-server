package com.inventario.server.database

import com.inventario.server.http.ProductUpdateRequest
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID
import scala.concurrent.Future

case class Product(id: UUID, name: String, short_desc: String, desc: String, price: Float, photo: String)

class ProductTable(tag: Tag) extends Table[Product](tag, Some("products"), "Product") {
  def product_id = column[UUID]("product_id", O.PrimaryKey)
  def name = column[String]("name")
  def short_desc = column[String]("short_desc")
  def desc = column[String]("desc")
  def price = column[Float]("price")
  def photo = column[String]("photo")

  override def * : ProvenShape[Product] = (product_id, name, short_desc, desc, price, photo).mapTo[Product]
}

object ProductTable {
  val productTable = TableQuery[ProductTable]

  def getAllProducts: Future[Seq[Product]] = {
    DatabaseConnection.db.run(productTable.result)
  }

  def insertProduct(product: Product): Future[Int] = {
    DatabaseConnection.db.run(productTable += product)
  }

  def searchProductById(id: UUID): Future[Option[Product]] = {
    val searchQuery = productTable.filter(product => product.product_id === id).result.headOption

    DatabaseConnection.db.run(searchQuery)
  }

  def deleteProductById(id: UUID): Future[Int] = {
    val deleteQuery = productTable.filter(product => product.product_id === id)

    DatabaseConnection.db.run(deleteQuery.delete)
  }

  def updateProduct(product: Product): Future[Int] = {
    val updateQuery = productTable.filter(_.product_id === product.id)
      .map(p => (p.name, p.short_desc, p.desc, p.price, p.photo))
      .update((product.name, product.short_desc, product.desc, product.price, product.photo))

    DatabaseConnection.db.run(updateQuery)
  }
}
