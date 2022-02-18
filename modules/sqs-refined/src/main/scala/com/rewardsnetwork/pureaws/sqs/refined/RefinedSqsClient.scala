package com.rewardsnetwork.pureaws.sqs.refined

import cats.effect._
import com.rewardsnetwork.pureaws.sqs._
import fs2.Stream
import software.amazon.awssdk.regions.Region

import scala.jdk.CollectionConverters._
import eu.timepit.refined.api.Refined
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue

/** A version of `SimpleSqsClient` that has refined versions of method parameters. */
trait RefinedSqsClient[F[_]] {

  /** Get a stream of messages from a queue URL. Each message is emitted as an individual `String`.
    *
    * Types are checked using `refined`.
    */
  def streamMessages(
      queueUrl: String,
      settings: RefinedStreamMessageSettings = RefinedStreamMessageSettings.default
  ): Stream[F, RefinedMessage[F]]

  /** Like `streamMessages`, but also pairs each message with its attributes. */
  def streamMessagesWithAttributes(
      queueUrl: String,
      settings: RefinedStreamMessageSettings = RefinedStreamMessageSettings.default
  ): Stream[F, RefinedMessageWithAttributes[F]]

  /** Change a message's visibility timeout to the specified value. */
  def changeMessageVisibility(
      visibilityTimeoutSeconds: VisibilityTimeout,
      receiptHandle: RefinedReceiptHandle[F]
  ): F[Unit]

  /** Delete a message given you have its receipt handle. */
  def deleteMessage(receiptHandle: RefinedReceiptHandle[F]): F[Unit]

  /** Send a message to an SQS queue. Message delay is determined by the queue settings.
    * @return
    *   The message ID string of the sent message.
    */
  def sendMessage(
      queueUrl: String,
      messageBody: String
  ): F[String]

  /** Send a message with attributes to an SQS queue. Message delay is determined by the queue settings.
    * @return
    *   The message ID string of the sent message.
    */
  def sendMessage(
      queueUrl: String,
      messageBody: String,
      messageAttributes: Map[String, MessageAttributeValue]
  ): F[String]

  /** Sends a message to an SQS queue. Allows specifying the seconds to delay the message.
    * @return
    *   The message ID string of the sent message.
    */
  def sendMessage(
      queueUrl: String,
      messageBody: String,
      delaySeconds: Int Refined DelaySeconds
  ): F[String]

  /** Sends a message with attributes to an SQS queue. Allows specifying the seconds to delay the message.
    * @return
    *   The message ID string of the sent message.
    */
  def sendMessage(
      queueUrl: String,
      messageBody: String,
      delaySeconds: Int Refined DelaySeconds,
      messageAttributes: Map[String, MessageAttributeValue]
  ): F[String]
}

object RefinedSqsClient {
  def apply[F[_]: Sync](pureClient: PureSqsClient[F]): RefinedSqsClient[F] =
    new RefinedSqsClient[F] {
      private val simpleClient = SimpleSqsClient(pureClient)
      def streamMessages(
          queueUrl: String,
          settings: RefinedStreamMessageSettings
      ): Stream[F, RefinedMessage[F]] =
        SimpleSqsClient
          .streamMessagesInternal(pureClient)(
            queueUrl,
            settings.maxMessages.value,
            settings.visibilityTimeoutSeconds.value,
            settings.waitTimeSeconds.value
          )
          .map(message => RefinedMessage(message.body, RefinedReceiptHandle(message.receiptHandle, queueUrl, this)))

      def streamMessagesWithAttributes(
          queueUrl: String,
          settings: RefinedStreamMessageSettings
      ): Stream[F, RefinedMessageWithAttributes[F]] =
        SimpleSqsClient
          .streamMessagesInternal(pureClient)(
            queueUrl,
            settings.maxMessages.value,
            settings.visibilityTimeoutSeconds.value,
            settings.waitTimeSeconds.value
          )
          .map { m =>
            val attributes = MessageAttributes.fromMap(
              m.attributes().asScala.toMap,
              m.messageAttributes().asScala.toMap
            )
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

      def sendMessage(queueUrl: String, messageBody: String, messageAttributes: Map[String, MessageAttributeValue]): F[String] =
        simpleClient.sendMessage(queueUrl, messageBody, messageAttributes)

      def sendMessage(queueUrl: String, messageBody: String, delaySeconds: Int Refined DelaySeconds): F[String] =
        simpleClient.sendMessage(queueUrl, messageBody, delaySeconds.value)

      def sendMessage(queueUrl: String, messageBody: String, delaySeconds: Int Refined DelaySeconds, messageAttributes: Map[String, MessageAttributeValue]): F[String] =
        simpleClient.sendMessage(queueUrl, messageBody, delaySeconds.value, messageAttributes)
    }

  /** Constructs a `RefinedSqsClient` using an underlying synchronous client backend.
    *
    * @param region
    *   The AWS region you are operating in.
    * @return
    *   A `RefinedSqsClient` instance using a synchronous backend.
    */
  def sync[F[_]: Sync](region: Region): Resource[F, RefinedSqsClient[F]] =
    PureSqsClient.sync[F](region).map(apply[F])

  /** Constructs a `RefinedSqsClient` using an underlying asynchronous client backend.
    *
    * @param region
    *   The AWS region you are operating in.
    * @return
    *   A `RefinedSqsClient` instance using an asynchronous backend.
    */
  def async[F[_]: Async](region: Region): Resource[F, RefinedSqsClient[F]] =
    PureSqsClient.async[F](region).map(apply[F])
}
