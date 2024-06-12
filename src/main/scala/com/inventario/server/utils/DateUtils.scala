package com.inventario.server.utils

import java.text.SimpleDateFormat
import java.util.Date

object DateUtils {
  def parseDate(dateString: String, pattern: String = "yyyy-MM-dd"): Date = {
    val dateFormat = new SimpleDateFormat(pattern)
    val utilDate = dateFormat.parse(dateString)
    new Date(utilDate.getTime)
  }
}
