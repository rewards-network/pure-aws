package com.rewardsnetwork.pureaws.sqs.refined

import munit.FunSuite
import com.rewardsnetwork.pureaws.sqs.StreamMessageSettings

class RefinedStreamMessageSettingsSpec extends FunSuite {
  test("Is equal to non-refined stream message settings") {
    val nonRefined = StreamMessageSettings.default
    val refined = RefinedStreamMessageSettings.default
    assertEquals(nonRefined.maxMessages, refined.maxMessages.value)
    assertEquals(nonRefined.visibilityTimeoutSeconds, refined.visibilityTimeoutSeconds.value)
    assertEquals(nonRefined.waitTimeSeconds, refined.waitTimeSeconds.value)
  }
}
