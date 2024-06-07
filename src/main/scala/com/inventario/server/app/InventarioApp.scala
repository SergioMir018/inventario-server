package com.inventario.server.app

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.inventario.server.actors.{OrderActor, ProductActor, UserActor}
import com.inventario.server.http.{OrderActorRouter, ProductActorRouter, StaticFileRouter, UserActorRouter}
import slick.jdbc.PostgresProfile.api._
import akka.http.scaladsl.server.Directives._
import com.inventario.server.database.DatabaseConnection

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object InventarioApp {
  def main(args: Array[String]): Unit = {
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val executionContext: ExecutionContext = system.executionContext
      implicit val db: Database = DatabaseConnection.db

      val userActor = context.spawn(UserActor(), "UserActor")
      val productActor = context.spawn(ProductActor(), "ProductActor")
      val orderActor = context.spawn(OrderActor(), "OrderActor")

      val staticFileRouter = new StaticFileRouter

      val userRouter = new UserActorRouter(userActor)
      val productRouter = new ProductActorRouter(productActor)
      val orderRouter = new OrderActorRouter(orderActor)

      val routes = userRouter.routes ~ productRouter.routes ~ orderRouter.routes ~ staticFileRouter.staticFilesRoute
      val httpBinding = Http().newServerAt("localhost", 8080).bind(routes)

      httpBinding.onComplete {
        case Success(binding) =>
          val address = binding.localAddress
          context.system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
        case Failure(exception) =>
          context.system.log.error(s"Failed to bind HTTP endpoint, terminating system", exception)
          context.system.terminate()
      }

      Behaviors.empty
    }

    val system = ActorSystem[Nothing](rootBehavior, "InventarioSystem")
  }
}
