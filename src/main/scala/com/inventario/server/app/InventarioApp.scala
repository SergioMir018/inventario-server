package com.inventario.server.app

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.inventario.server.actors.UserAccount
import com.inventario.server.actors.UserAccount.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.Http
import akka.util.Timeout
import com.inventario.server.http.InventarioRouter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Success, Failure}


object InventarioApp {

  def startHttpServer(userAccount: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router =  new InventarioRouter(userAccount)
    val routes = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    httpBindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(ex) =>
        system.log.error(s"Failed to bind HTTP server due, $ex")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    trait RootCommand
    case class RetrieveUserAccountActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup {context =>
      val userAccountActor = context.spawn(UserAccount(), "user")

      Behaviors.receiveMessage {
        case RetrieveUserAccountActor(replyTo) =>
          replyTo ! userAccountActor
          Behaviors.same
      }
    }

    implicit val system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "InventarioSystem")
    implicit val timeout: Timeout = Timeout(5.seconds)
    implicit val ec: ExecutionContext = system.executionContext

    val userActorFuture = system.ask(replyTo =>  RetrieveUserAccountActor(replyTo))
    userActorFuture.foreach(startHttpServer)
  }
}
