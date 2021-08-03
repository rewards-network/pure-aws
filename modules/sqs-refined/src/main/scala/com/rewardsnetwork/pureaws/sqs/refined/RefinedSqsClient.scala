package com.rewardsnetwork.pureaws.sqs.refined

import cats.effect._
import com.rewardsnetwork.pureaws.sqs._
import fs2.Stream
import eu.timepit.refined.auto._
import software.amazon.awssdk.regions.Region

import scala.jdk.CollectionConverters._

/** A version of `SimpleSqsClient` that has refined versions of method parameters. */
trait RefinedSqsClient[F[_]] {

  /** Get a stream of messages from a queue URL.
    * Each message is emitted as an individual `String`.
    *
    * Types are checked using `refined`.
    */
  def streamMessages(
      queueUrl: String,
      maxMessages: MaxMessages = 10,
      visibilityTimeoutSeconds: VisibilityTimeout = 30,
      waitTimeSeconds: IntNonNegative = 0
  ): Stream[F, RefinedMessage[F]]

  /** Like `streamMessages`, but also pairs each message with its attributes. */
  def streamMessagesWithAttributes(
      queueUrl: String,
      maxMessages: MaxMessages = 10,
      visibilityTimeoutSeconds: VisibilityTimeout = 30,
      waitTimeSeconds: IntNonNegative = 0
  ): Stream[F, RefinedMessageWithAttributes[F]]

  /** Change a message's visibility timeout to the specified value. */
  def changeMessageVisibility(
      visibilityTimeoutSeconds: VisibilityTimeout,
      receiptHandle: RefinedReceiptHandle[F]
  ): F[Unit]

  /** Delete a message given you have its receipt handle. */
  def deleteMessage(receiptHandle: RefinedReceiptHandle[F]): F[Unit]

  /** Send a message to an SQS queue. Message delay is determined by the queue settings.
    * @return The message ID string of the sent message.
    */
  def sendMessage(queueUrl: String, messageBody: String): F[String]

  /** Sends a message to an SQS queue. Allows specifying the seconds to delay the message.
    * @return The message ID string of the sent message.
    */
  def sendMessage(queueUrl: String, messageBody: String, delaySeconds: DelaySeconds): F[String]

}

object RefinedSqsClient {
  def apply[F[_]: Sync](pureClient: PureSqsClient[F]) =
    new RefinedSqsClient[F] {
      private val simpleClient = SimpleSqsClient(pureClient)
      def streamMessages(
          queueUrl: String,
          maxMessages: MaxMessages,
          visibilityTimeoutSeconds: VisibilityTimeout,
          waitTimeSeconds: IntNonNegative
      ): Stream[F, RefinedMessage[F]] =
        SimpleSqsClient
          .streamMessagesInternal(pureClient)(
            queueUrl,
            maxMessages.value,
            visibilityTimeoutSeconds.value,
            waitTimeSeconds.value
          )
          .map(message => RefinedMessage(message.body, RefinedReceiptHandle(message.receiptHandle, queueUrl, this)))

      def streamMessagesWithAttributes(
          queueUrl: String,
          maxMessages: MaxMessages,
          visibilityTimeoutSeconds: VisibilityTimeout,
          waitTimeSeconds: IntNonNegative
      ): Stream[F, RefinedMessageWithAttributes[F]] =
        SimpleSqsClient
          .streamMessagesInternal(pureClient)(
            queueUrl,
            maxMessages.value,
            visibilityTimeoutSeconds.value,
            waitTimeSeconds.value
          )
          .map { m =>
            val attributes = MessageAttributes.fromMap(m.attributes().asScala.toMap)
            RefinedMessageWithAttributes(
              m.body,
              RefinedReceiptHandle(m.receiptHandle, queueUrl, this),
              attributes
            )
          }

      def changeMessageVisibility(
          visibilityTimeoutSeconds: VisibilityTimeout,
          receiptHandle: RefinedReceiptHandle[F]
      ): F[Unit] =
        simpleClient
          .changeMessageVisibility(
            visibilityTimeoutSeconds.value,
            receiptHandle.rawReceiptHandle,
            receiptHandle.queueUrl
          )

      def deleteMessage(receiptHandle: RefinedReceiptHandle[F]): F[Unit] =
        simpleClient.deleteMessage(receiptHandle.rawReceiptHandle, receiptHandle.queueUrl)

      def sendMessage(queueUrl: String, messageBody: String): F[String] =
        simpleClient.sendMessage(queueUrl, messageBody)

      def sendMessage(queueUrl: String, messageBody: String, delaySeconds: DelaySeconds): F[String] =
        simpleClient.sendMessage(queueUrl, messageBody, delaySeconds.value)

    }

  /** Constructs a `RefinedSqsClient` using an underlying synchronous client backend.
    *
    * @param awsRegion The AWS region you are operating in.
    * @return A `RefinedSqsClient` instance using a synchronous backend.
    */
  def sync[F[_]: Sync](region: Region) =
    PureSqsClient.sync[F](region).map(apply[F])

  /** Constructs a `RefinedSqsClient` using an underlying asynchronous client backend.
    *
    * @param awsRegion The AWS region you are operating in.
    * @return A `RefinedSqsClient` instance using an asynchronous backend.
    */
  def async[F[_]: Async](region: Region) =
    PureSqsClient.async[F](region).map(apply[F])
}
