package com.rewardsnetwork.pureaws.sqs

final case class StreamMessageSettings(
    maxMessages: Int,
    visibilityTimeoutSeconds: Int,
    waitTimeSeconds: Int
)

object StreamMessageSettings {
  val default = StreamMessageSettings(10, 30, 0)
}
