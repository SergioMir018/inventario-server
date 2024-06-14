package com.inventario.server.database

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

case class Product(id: UUID, name: String, short_desc: String, desc: String, price: Float, photo: String, category_id: UUID)
case class ProductSearchResponse(id: UUID, name: String, short_desc: String, desc: String, price: Float, photo: String, category: String)

class ProductTable(tag: Tag) extends Table[Product](tag, Some("products"), "Product") {
  def product_id = column[UUID]("product_id", O.PrimaryKey)
  def name = column[String]("name")
  def short_desc = column[String]("short_desc")
  def desc = column[String]("desc")
  def price = column[Float]("price")
  def photo = column[String]("photo")
  def category_id = column[UUID]("category_id")

  def categoryFk = foreignKey("category_fk", category_id, CategoryTable.categoryTable)(_.category_id)

  override def * : ProvenShape[Product] = (product_id, name, short_desc, desc, price, photo, category_id).mapTo[Product]
}


case class Category(id: UUID, name: String)

class CategoryTable(tag: Tag) extends Table[Category](tag, Some("products"), "Category") {
  def category_id = column[UUID]("category_id", O.PrimaryKey)
  def category_name = column[String]("category_name")

  override def * : ProvenShape[Category] = (category_id, category_name).mapTo[Category]
}

object CategoryTable {
  val categoryTable = TableQuery[CategoryTable]

  def getAllCategories: Future[Seq[Category]] = {
    val categoriesQuery = categoryTable.result

    DatabaseConnection.db.run(categoriesQuery)
  }
}

object ProductTable {
  val productTable = TableQuery[ProductTable]
  val categoryTable = TableQuery[CategoryTable]

  def getAllProducts(implicit ec: ExecutionContext): Future[Seq[ProductSearchResponse]] = {
    val query = for {
      (product, category) <- productTable join categoryTable on (_.category_id === _.category_id)
    } yield (product.product_id, product.name, product.short_desc, product.desc, product.price, product.photo, category.category_name)

    DatabaseConnection.db.run(query.result).map { result =>
      result.map {
        case (id, name, short_desc, desc, price, photo, category_name) =>
          ProductSearchResponse(id, name, short_desc, desc, price, photo, category_name)
      }
    }
  }

  def insertProduct(product: Product): Future[Int] = {
    DatabaseConnection.db.run(productTable += product)
  }

  def searchProductById(id: UUID)(implicit ec: ExecutionContext): Future[Option[ProductSearchResponse]] = {
    val query = for {
      (product, category) <- productTable.filter(_.product_id === id) join categoryTable on (_.category_id === _.category_id)
    } yield (product.product_id, product.name, product.short_desc, product.desc, product.price, product.photo, category.category_name)

    DatabaseConnection.db.run(query.result.headOption).map {
      _.map {
        case (id, name, short_desc, desc, price, photo, category_name) =>
          ProductSearchResponse(id, name, short_desc, desc, price, photo, category_name)
      }
    }
  }

  def deleteProductById(id: UUID)(implicit ec: ExecutionContext): Future[Int] = {
    val orderTable = TableQuery[OrderTable]
    val orderProductTable = TableQuery[OrderProductTable]
    val productTable = TableQuery[ProductTable]

    val checkIfCompletedQuery = for {
      (orderProduct, order) <- orderProductTable join orderTable on (_.order_id === _.id)
      if orderProduct.product_id === id && order.status === "Completada"
    } yield orderProduct

    val deleteOrderProductsAction = orderProductTable.filter(_.product_id === id).delete
    val deleteProductAction = productTable.filter(_.product_id === id).delete

    val combinedAction = for {
      isReferencedInCompletedOrder <- checkIfCompletedQuery.exists.result
      result <- if (isReferencedInCompletedOrder) {
        for {
          _ <- deleteOrderProductsAction
          rowsDeleted <- deleteProductAction
        } yield rowsDeleted
      } else {
        DBIO.failed(new Exception("Product is not referenced in any completed orders"))
      }
    } yield result

    DatabaseConnection.db.run(combinedAction.transactionally)
  }

  def updateProduct(product: Product): Future[Int] = {
    val updateQuery = productTable.filter(_.product_id === product.id)
      .map(p => (p.name, p.short_desc, p.desc, p.price, p.photo, p.category_id))
      .update((product.name, product.short_desc, product.desc, product.price, product.photo, product.category_id))

    DatabaseConnection.db.run(updateQuery)
  }
}
