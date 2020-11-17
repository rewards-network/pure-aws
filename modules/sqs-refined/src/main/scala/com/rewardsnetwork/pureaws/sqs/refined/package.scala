package com.rewardsnetwork.pureaws.sqs

import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._

package object refined {

  /** A valid range of values for specifying the maximum messages to retrieve at a time from SQS, from 1 to 10. */
  type MaxMessages = Int Refined Interval.Closed[W.`1`.T, W.`10`.T]

  /** Any non-negative integer 0+ */
  type IntNonNegative = Int Refined NonNegative

  /** A valid visibility timeout interval in seconds, from 0 to 43200 (12 hours) */
  type VisibilityTimeout = Int Refined Interval.Closed[W.`0`.T, W.`43200`.T]

  /** A `ReceiptHandle` that takes in only refined parameters as function arguments. */
  type RefinedReceiptHandle[F[_]] = BaseReceiptHandle[F, VisibilityTimeout]

  /** A valid value of seconds between 0 and 900 (15 minutes) for delaying a sent message. */
  type DelaySeconds = Int Refined Interval.Closed[W.`0`.T, W.`900`.T]
}
