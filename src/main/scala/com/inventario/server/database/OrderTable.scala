package com.inventario.server.database

import slick.jdbc.PostgresProfile.api._

import java.sql.Date
import java.util.UUID
import scala.concurrent.Future

// Case classes representing the database entities
case class Order(id: UUID, status: String,name: String, client_id: UUID, creation_date: Date, total_payment: Float)
case class OrderProduct(order_id: UUID, product_id: UUID, quantity: Int)
case class OrderDetails(order_id: UUID, shipping_address: String, billing_address: String, phone_number: String)

// Order Table
class OrderTable(tag: Tag) extends Table[Order](tag, Some("orders"), "Order") {
  def id = column[UUID]("order_id", O.PrimaryKey)
  def status = column[String]("status")
  def name = column[String]("name")
  def client_id = column[UUID]("client_id")
  def creation_date = column[Date]("creation_date")
  def total_payment = column[Float]("total_payment")

  def clientFk = foreignKey("client_fk", client_id, UserTable.userTable)(_.id)

  override def * = (id, status, name, client_id, creation_date, total_payment).mapTo[Order]
}

// Order Product Table
class OrderProductTable(tag: Tag) extends Table[OrderProduct](tag, Some("orders"), "OrderProduct") {
  def order_id = column[UUID]("order_id")
  def product_id = column[UUID]("product_id")
  def quantity = column[Int]("quantity")

  def pk = primaryKey("pk_orderproduct", (order_id, product_id))
  def orderFk = foreignKey("order_fk", order_id, OrderTable.orderTable)(_.id)
  def productFk = foreignKey("product_fk", product_id, ProductTable.productTable)(_.product_id)

  override def * = (order_id, product_id, quantity).mapTo[OrderProduct]
}

// Order Details Table
class OrderDetailsTable(tag: Tag) extends Table[OrderDetails](tag, Some("orders"), "OrderDetails") {
  def order_id = column[UUID]("order_id", O.PrimaryKey)
  def shipping_address = column[String]("shipping_address")
  def billing_address = column[String]("billing_address")
  def phone_number = column[String]("phone_number")

  def orderFk = foreignKey("order_fk", order_id, OrderTable.orderTable)(_.id)

  override def * = (order_id, shipping_address, billing_address, phone_number).mapTo[OrderDetails]
}

// Companion objects for the tables to provide utility methods
object OrderTable {
  val orderTable = TableQuery[OrderTable]

  def countOrders(): Future[Int] = {
    DatabaseConnection.db.run(orderTable.length.result)
  }

  def insertOrder(order: Order): Future[Int] = {
    DatabaseConnection.db.run(orderTable += order)
  }
}

object OrderProductTable {
  val orderProductTable = TableQuery[OrderProductTable]

  def insertOrderProducts(orderProducts: Seq[OrderProduct]): Future[Option[Int]] = {
    DatabaseConnection.db.run(orderProductTable ++= orderProducts)
  }
}

object OrderDetailsTable {
  val orderDetailsTable = TableQuery[OrderDetailsTable]

  def insertOrderDetails(orderDetails: OrderDetails): Future[Int] = {
    DatabaseConnection.db.run(orderDetailsTable += orderDetails)
  }
}
