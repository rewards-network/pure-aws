package com.rewardsnetwork.pureaws.sqs

import cats.implicits._
import cats.effect.Sync
import fs2.Stream
import software.amazon.awssdk.services.sqs.model._

import scala.jdk.CollectionConverters._

trait SimpleSqsClient[F[_]] {

  /** Get a stream of messages from a queue URL.
    * Each message is emitted as an individual `String`.
    *
    * Valid `maxMessages` values are 1 -> 10.
    * Valid `visibilityTimeoutSeconds` values are 0 -> 43200 (12 hours)
    * Valid `waitTimeSeconds` values are all positive integers.
    *
    * For compile-time and run-time type-checking of these parameters, please use the `fs2-sqs-refined` library instead.
    */
  def streamMessages(
      queueUrl: String,
      maxMessages: Int = 10,
      visibilityTimeoutSeconds: Int = 30,
      waitTimeSeconds: Int = 0
  ): Stream[F, SqsMessage[F]]

  /** Like `streamMessages`, but also pairs each message with its attributes. */
  def streamMessagesWithAttributes(
      queueUrl: String,
      maxMessages: Int = 10,
      visibilityTimeoutSeconds: Int = 30,
      waitTimeSeconds: Int = 0
  ): Stream[F, SqsMessageWithAttributes[F]]

  /** Change a message's visibility timeout to the specified value.
    * Valid `visibilityTimeoutSeconds` values are 0 -> 43200 (12 hours)
    *
    * For compile-time and run-time type-checking of these parameters, please use the `fs2-sqs-refined` library instead.
    */
  def changeMessageVisibility(visibilityTimeoutSeconds: Int, rawReceiptHandle: String, queueUrl: String): F[Unit]

  /** Delete a message from AWS SQS. */
  def deleteMessage(rawReceiptHandle: String, queueUrl: String): F[Unit]

  /** Send a message to an SQS queue. Message delay is determined by the queue settings.
    * @return The message ID string of the sent message.
    */
  def sendMessage(queueUrl: String, messageBody: String): F[String]

  /** Sends a message to an SQS queue. Allows specifying the seconds to delay the message (valid values between 0 and 900).
    * @return The message ID string of the sent message.
    */
  def sendMessage(queueUrl: String, messageBody: String, delaySeconds: Int): F[String]

}

object SimpleSqsClient {
  private[sqs] def streamMessagesInternal[F[_]: Sync](client: PureSqsClient[F])(
      queueUrl: String,
      maxMessages: Int,
      visibilityTimeoutSeconds: Int,
      waitTimeSeconds: Int,
      receiveAttrs: Boolean = false
  ) = {
    val req = ReceiveMessageRequest.builder
      .queueUrl(queueUrl)
      .maxNumberOfMessages(maxMessages)
      .visibilityTimeout(visibilityTimeoutSeconds)
      .waitTimeSeconds(waitTimeSeconds)

    val reqWithMaybeAttrs = (if (receiveAttrs) req.attributeNames(QueueAttributeName.ALL) else req).build

    client
      .receiveMessageStream(reqWithMaybeAttrs)
      .flatMap[F, Message](res => Stream.fromIterator[F](res.messages.iterator.asScala))
  }

  def apply[F[_]: Sync](client: PureSqsClient[F]) =
    new SimpleSqsClient[F] {

      def streamMessages(
          queueUrl: String,
          maxMessages: Int,
          visibilityTimeoutSeconds: Int,
          waitTimeSeconds: Int
      ): Stream[F, SqsMessage[F]] =
        streamMessagesInternal(client)(queueUrl, maxMessages, visibilityTimeoutSeconds, waitTimeSeconds).map(m =>
          SqsMessage(m.body, ReceiptHandle(m.receiptHandle, queueUrl, this))
        )

      def streamMessagesWithAttributes(
          queueUrl: String,
          maxMessages: Int,
          visibilityTimeoutSeconds: Int,
          waitTimeSeconds: Int
      ): Stream[F, SqsMessageWithAttributes[F]] = {
        streamMessagesInternal(client)(
          queueUrl,
          maxMessages,
          visibilityTimeoutSeconds,
          waitTimeSeconds,
          receiveAttrs = true
        ).map { m =>
          val attributes = MessageAttributes.fromMap(m.attributes().asScala.toMap)
          SqsMessageWithAttributes(m.body, ReceiptHandle(m.receiptHandle, queueUrl, this), attributes)
        }
      }

      def changeMessageVisibility(
          visibilityTimeoutSeconds: Int,
          rawReceiptHandle: String,
          queueUrl: String
      ): F[Unit] = {
        val req = ChangeMessageVisibilityRequest.builder
          .queueUrl(queueUrl)
          .receiptHandle(rawReceiptHandle)
          .visibilityTimeout(visibilityTimeoutSeconds)
          .build
        client.changeMessageVisibility(req).void
      }

      def deleteMessage(rawReceiptHandle: String, queueUrl: String): F[Unit] = {
        val req = DeleteMessageRequest.builder.queueUrl(queueUrl).receiptHandle(rawReceiptHandle).build
        client.deleteMessage(req).void
      }

      def sendMessage(queueUrl: String, messageBody: String): F[String] = {
        val req =
          SendMessageRequest.builder.queueUrl(queueUrl).messageBody(messageBody).build()
        client.sendMessage(req).map(_.messageId)
      }

      def sendMessage(queueUrl: String, messageBody: String, delaySeconds: Int): F[String] = {
        val req =
          SendMessageRequest.builder.queueUrl(queueUrl).messageBody(messageBody).delaySeconds(delaySeconds).build()
        client.sendMessage(req).map(_.messageId)
      }

    }
}
