package com.inventario.server.database

import slick.jdbc.PostgresProfile.api._


object DatabaseConnection {
  val db = Database.forConfig("postgres")
}
