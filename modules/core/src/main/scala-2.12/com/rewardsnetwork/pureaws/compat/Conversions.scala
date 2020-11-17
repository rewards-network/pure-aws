package com.rewardsnetwork.pureaws.compat

import scala.util.Try

private[pureaws] object Conversions {
  def toIntOption(s: String) = Try(s.toInt).toOption
  def toLongOption(s: String) = Try(s.toLong).toOption
}
