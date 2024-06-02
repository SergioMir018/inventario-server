package com.inventario.server.http

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Directives.pathPrefix

case class AccountCreationRequest(name: String, email: String, password: String)

case class LoginRequest(nameOrEmail: String, password: String)

class InventarioRouter(inventario: ActorRef[_])(implicit system: ActorSystem[_]) {

  val routes = {
    pathPrefix("login") {

    }
  }
}