package com.rewardsnetwork.pureaws.sqs.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval

object defs {

  /** A valid range of values for specifying the maximum messages to retrieve at a time from SQS, from 1 to 10. */
  type MaxMessages = Int Refined Interval.Closed[1, 10]

  /** A valid visibility timeout interval in seconds, from 0 to 43200 (12 hours) */
  type VisibilityTimeout = Int Refined Interval.Closed[0, 43200]

  /** A valid value of seconds between 0 and 900 (15 minutes) for delaying a sent message. */
  type DelaySeconds = Int Refined Interval.Closed[0, 900]
}
