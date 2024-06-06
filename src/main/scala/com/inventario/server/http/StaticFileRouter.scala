package com.inventario.server.http

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class StaticFileRouter {
  val staticFilesRoute: Route =
    pathPrefix("public") {
      getFromDirectory("src/main/resources/public")
    }
}

