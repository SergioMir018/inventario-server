package com.inventario.server.database

import slick.lifted.ProvenShape
import com.inventario.server.definitions.UserRole

case class User(id: Long, name: String, email: String, password: String, role: UserRole)

object SlickTables {
  import com.inventario.server.definitions.UserRole._
  import slick.jdbc.PostgresProfile.api._

  class UserTable(tag: Tag) extends Table[User](tag, Some("users"), "User") {
    private def id = column[Long]("user_id", O.PrimaryKey, O.AutoInc)
    private def name = column[String]("name")
    private def email = column[String]("email")
    private def password = column[String]("password")
    private def role = column[UserRole]("role")

    override def * : ProvenShape[User] = (id, name, email, password, role) <> ((User.apply _).tupled, User.unapply)
  }

  lazy val userTable = TableQuery[UserTable]
}
