package com.inventario.server.app

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object BankApp {

  def startHttpServer(bank: ActorRef[Command])(implicit system: ActorSystem[_]): Unit = {
    implicit val ec: ExecutionContext = system.executionContext
    val router = new InvetarioRouter(bank)
    val routes = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    httpBindingFuture.onComplete {
      case Success(value) =>
        val address = value.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")
      case Failure(exception) =>
        system.log.error(s"Failed to bind HTTP server, because $exception")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    LogbackConfig.configureLogback()

    trait RootCommand
    case class RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]]) extends RootCommand

    val rootBehavior: Behavior[RootCommand] = Behaviors.setup{ context =>
      val bankActor = context.spawn(Bank(), "bank")

      Behaviors.receiveMessage {
        case RetrieveBankActor(replyTo) =>
          replyTo ! bankActor
          Behaviors.same
      }
    }

    implicit val system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "BankSystem")
    implicit val timeout: Timeout = Timeout(5.seconds)
    implicit val ec: ExecutionContext = system.executionContext

    val bankActorFuture: Future[ActorRef[Command]] = system.ask(replyTo => RetrieveBankActor(replyTo))

    bankActorFuture.foreach(startHttpServer)
  }
}

object LogbackConfig {
  import ch.qos.logback.classic.{Level, LoggerContext}
  import ch.qos.logback.classic.encoder.PatternLayoutEncoder
  import ch.qos.logback.classic.filter.LevelFilter
  import ch.qos.logback.classic.spi.ILoggingEvent
  import ch.qos.logback.core.ConsoleAppender
  import ch.qos.logback.core.spi.FilterReply
  import org.slf4j.LoggerFactory

  def configureLogback(): Unit = {
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

    val consoleAppender = new ConsoleAppender[ILoggingEvent]()
    val patternLayoutEncoder = new PatternLayoutEncoder()
    patternLayoutEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n")
    patternLayoutEncoder.setContext(loggerContext)
    patternLayoutEncoder.start()
    consoleAppender.setEncoder(patternLayoutEncoder)
    consoleAppender.setContext(loggerContext)
    consoleAppender.setName("CONSOLE")
    consoleAppender.start()

    val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    rootLogger.addAppender(consoleAppender)
    rootLogger.setLevel(Level.INFO)

    val levelFilter = new LevelFilter()
    levelFilter.setLevel(Level.INFO)
    levelFilter.setOnMatch(FilterReply.ACCEPT)
    levelFilter.setOnMismatch(FilterReply.DENY)
    levelFilter.start()
    consoleAppender.addFilter(levelFilter)

    rootLogger.setAdditive(false)
  }
}


