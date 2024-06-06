package com.inventario.server.database

import com.inventario.server.database.DBUserTable.userTable
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID
import scala.concurrent.Future

case class User(id: UUID, name: String, email: String, password: String, role: String)
case class Product(id: UUID, name: String, short_desc: String, desc: String, price: Float, photo: String)

class UserTable(tag: Tag) extends Table[User](tag, Some("users"), "User") {
  def id = column[UUID]("user_id", O.PrimaryKey)
  def name = column[String]("name")
  def email = column[String]("email")
  def password = column[String]("password")
  def role = column[String]("role")

  def * = (id, name, email, password, role).mapTo[User]
}

class ProductTable(tag: Tag) extends Table[Product](tag, Some("products"), "Product") {
  def product_id = column[UUID]("product_id", O.PrimaryKey)
  def name = column[String]("name")
  def short_desc = column[String]("short_desc")
  def desc = column[String]("desc")
  def price = column[Float]("price")
  def photo = column[String]("photo")

  def * = (product_id, name, short_desc, desc, price, photo).mapTo[Product]
}

object DBUserTable {

  val userTable = TableQuery[UserTable]

  def createUser(user: User): Future[Int] = {
    DatabaseConnection.db.run(userTable += user)
  }


  def searchUserByTerm(searchTerm: String): Future[Option[User]] = {
    val searchQuery = userTable.filter(user => user.name === searchTerm || user.email === searchTerm).result.headOption

    DatabaseConnection.db.run(searchQuery)
  }

  def searchUserById(id: UUID): Future[Option[User]] = {
    val searchQuery = userTable.filter(user => user.id === id).result.headOption

    DatabaseConnection.db.run(searchQuery)
  }
}

object DBProductTable {
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
}
