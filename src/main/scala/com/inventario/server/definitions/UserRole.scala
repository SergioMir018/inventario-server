package com.inventario.server.definitions

import slick.jdbc.H2Profile.MappedColumnType

// Definición del trait y sus subclases
sealed trait UserRole
object UserRole {
  case object Client extends UserRole
  case object Employee extends UserRole
  case object Admin extends UserRole

  // Método para convertir a y desde String para almacenar en la base de datos
  def fromString(s: String): UserRole = s match {
    case "Client" => Client
    case "Employee" => Employee
    case "Admin" => Admin
  }

  def toString(role: UserRole): String = role match {
    case Client => "Client"
    case Employee => "Employee"
    case Admin => "Admin"
  }

  // Implicitos para mapear a la base de datos
  implicit val userRoleColumnType = MappedColumnType.base[UserRole, String](
    toString, fromString
  )
}
