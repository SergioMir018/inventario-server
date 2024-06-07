package com.inventario.server.database

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID
import scala.concurrent.Future

case class User(id: UUID, name: String, email: String, password: String, role: String)

class UserTable(tag: Tag) extends Table[User](tag, Some("users"), "User") {
  def id = column[UUID]("user_id", O.PrimaryKey)
  def name = column[String]("name")
  def email = column[String]("email")
  def password = column[String]("password")
  def role = column[String]("role")

  override def * : ProvenShape[User] = (id, name, email, password, role).mapTo[User]
}

object UserTable {

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