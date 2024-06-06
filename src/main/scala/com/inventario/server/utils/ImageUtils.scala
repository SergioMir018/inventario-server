package com.inventario.server.utils

import akka.actor.typed.ActorSystem

import java.nio.file.{Files, Paths}
import java.util.Base64

object ImageUtils {

  def saveImage(id: String, base64Image: String, photoExt: String, system: ActorSystem[_]): Unit = {
    try {
      if (isValidBase64(base64Image)) {
        val imageBytes = Base64.getDecoder.decode(base64Image)
        val fileRoute = Paths.get("src/main/resources/public/photos", s"$id.$photoExt")

        Files.write(fileRoute, imageBytes)
      } else {
        system.log.error("The base64 string contains invalid characters")
      }
    } catch {
      case e: IllegalArgumentException =>
        system.log.error(s"Error while decoding base64 string: ${e.getMessage}")
    }
  }

  private def isValidBase64(base64String: String): Boolean = {
    val base64Pattern = "^[a-zA-Z0-9+/]*={0,2}$".r
    base64Pattern.pattern.matcher(base64String).matches()
  }
}
