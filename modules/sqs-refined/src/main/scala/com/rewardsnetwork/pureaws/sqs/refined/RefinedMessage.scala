package com.rewardsnetwork.pureaws.sqs.refined

import com.rewardsnetwork.pureaws.sqs._
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue

/** An `SqsMessage` that also contains a `RefinedReceiptHandle` */
final case class RefinedMessage[F[_]](
    body: String,
    receiptHandle: RefinedReceiptHandle[F]
) extends BaseSqsMessage[F, VisibilityTimeout]

/** An `SqsMessageWithAttributes` that also contains a `RefinedReceiptHandle` */
final case class RefinedMessageWithAttributes[F[_]](
    body: String,
    receiptHandle: RefinedReceiptHandle[F],
    attributes: MessageAttributes
) extends BaseSqsMessage[F, VisibilityTimeout]
    with WithAttributes

/** An `SqsMessageWithCustomAttributes` that also contains a `RefinedReceiptHandle` */
final case class RefinedMessageWithCustomAttributes[F[_]](
    body: String,
    receiptHandle: RefinedReceiptHandle[F],
    attributes: Map[String, MessageAttributeValue]
) extends BaseSqsMessage[F, VisibilityTimeout]
