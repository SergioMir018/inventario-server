ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "inventario-back",
  )

lazy val akkaHttpVersion = "10.2.10"
lazy val akkaVersion     = "2.6.20"
lazy val circeVersion    = "0.14.3"

libraryDependencies ++= Seq(
  // Slick and PostgreSQL
  "com.typesafe.slick" %% "slick" % "3.5.0",
  "org.postgresql" % "postgresql" % "42.7.3",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
  "com.github.tminglei" %% "slick-pg" % "0.22.2",
  "com.github.tminglei" %% "slick-pg_play-json" % "0.22.2",

  // Circe for JSON serialization
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",

  // Akka HTTP and Akka modules
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.2.11",

  // Testing dependencies
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,
  "org.mockito" %% "mockito-scala-scalatest" % "1.17.12" % Test,

  // Configurations
  "com.typesafe" % "config" % "1.4.2",

  // Authentication and Authorization (optional)
  "com.softwaremill.akka-http-session" %% "core" % "0.6.1", // Actualizado a una versión compatible con Scala 3

  // Metrics (optional)
  "io.kamon" %% "kamon-bundle" % "2.5.4", // Actualizado a una versión compatible con Scala 3
  "io.kamon" %% "kamon-prometheus" % "2.5.4" // Actualizado a una versión compatible con Scala 3
)
