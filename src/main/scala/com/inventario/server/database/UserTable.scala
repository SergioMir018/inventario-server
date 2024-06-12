package com.inventario.server.database

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

case class User(id: UUID, name: String, email: String, password: String, role: String)
case class Visit(id: Option[Int] = None, url: String, timestamp: Timestamp)
case class VisitResponse(date: String)

class UserTable(tag: Tag) extends Table[User](tag, Some("users"), "User") {
  def id = column[UUID]("user_id", O.PrimaryKey)
  def name = column[String]("name")
  def email = column[String]("email")
  def password = column[String]("password")
  def role = column[String]("role")

  override def * : ProvenShape[User] = (id, name, email, password, role).mapTo[User]
}

class VisitsTable(tag: Tag) extends Table[Visit](tag, Some("public"), "visits") {
  def id: Rep[Int] = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def url: Rep[String] = column[String]("url")
  def timestamp: Rep[Timestamp] = column[Timestamp]("timestamp")

  override def * : ProvenShape[Visit] = (id.?, url, timestamp).mapTo[Visit]
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

object VisitsTable {
  val visitsTable = TableQuery[VisitsTable]

  def insertVisit(visit: Visit): Future[Int] = {
    DatabaseConnection.db.run(visitsTable returning visitsTable.map(_.id) += visit)
  }

  def getAllVisits(implicit ec: ExecutionContext): Future[Seq[VisitResponse]] = {
    val query = visitsTable.result
    DatabaseConnection.db.run(query).map { visits =>
      visits.map { visit =>
        VisitResponse(visit.timestamp.toString.split(' ')(0))
      }
    }
  }
}
