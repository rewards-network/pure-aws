package com.rewardsnetwork.pureaws.sqs

package object refined {

  /** A valid range of values for specifying the maximum messages to retrieve at a time from SQS, from 1 to 10. */
  type MaxMessages = defs.MaxMessages

  /** A valid visibility timeout interval in seconds, from 0 to 43200 (12 hours) */
  type VisibilityTimeout = defs.VisibilityTimeout

  /** A `ReceiptHandle` that takes in only refined parameters as function arguments. */
  type RefinedReceiptHandle[F[_]] = BaseReceiptHandle[F, VisibilityTimeout]

  /** A valid value of seconds between 0 and 900 (15 minutes) for delaying a sent message. */
  type DelaySeconds = defs.DelaySeconds
}
