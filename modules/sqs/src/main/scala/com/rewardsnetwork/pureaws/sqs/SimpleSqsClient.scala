package com.rewardsnetwork.pureaws.sqs

import cats.syntax.all._
import cats.effect._
import fs2.Stream
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.model._

import scala.jdk.CollectionConverters._

trait SimpleSqsClient[F[_]] {

  /** Get a stream of messages from a queue URL. Each message is emitted as an individual `String`.
    *
    * Settings is a set of common settings for streaming messages, as follows: * Valid `maxMessages` values are 1 -> 10.
    * * Valid `visibilityTimeoutSeconds` values are 0 -> 43200 (12 hours) * Valid `waitTimeSeconds` values are all
    * positive integers.
    *
    * For compile-time and run-time type-checking of these parameters, please use the `pure-aws-sqs-refined` library
    * instead.
    */
  def streamMessages(
      queueUrl: String,
      settings: StreamMessageSettings = StreamMessageSettings.default
  ): Stream[F, SqsMessage[F]]

  /** Like `streamMessages`, but also pairs each message with its attributes. */
  def streamMessagesWithAttributes(
      queueUrl: String,
      settings: StreamMessageSettings = StreamMessageSettings.default
  ): Stream[F, SqsMessageWithAttributes[F]]

  /** Change a message's visibility timeout to the specified value. Valid `visibilityTimeoutSeconds` values are 0 ->
    * 43200 (12 hours)
    *
    * For compile-time and run-time type-checking of these parameters, please use the `pure-aws-sqs-refined` library
    * instead.
    */
  def changeMessageVisibility(visibilityTimeoutSeconds: Int, rawReceiptHandle: String, queueUrl: String): F[Unit]

  /** Delete a message from AWS SQS. */
  def deleteMessage(rawReceiptHandle: String, queueUrl: String): F[Unit]

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

  /** Send a message to an SQS queue. Allows specifying the seconds to delay the message (valid values
    * between 0 and 900).
    * @return
    *   The message ID string of the sent message.
    */
  def sendMessage(
      queueUrl: String,
      messageBody: String,
      delaySeconds: Int
  ): F[String]

 /** Send a message with attributes to an SQS queue. Allows specifying the seconds to delay the message (valid values
    * between 0 and 900).
    * @return
    *   The message ID string of the sent message.
    */
  def sendMessage(
      queueUrl: String,
      messageBody: String,
      delaySeconds: Int,
      messageAttributes: Map[String, MessageAttributeValue]
  ): F[String]
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
      .flatMap[F, Message](res =>
        Stream.fromIterator[F](res.messages.iterator.asScala, 1024)
      ) // TODO: make the chunk size configurable
  }

  def apply[F[_]: Sync](client: PureSqsClient[F]): SimpleSqsClient[F] =
    new SimpleSqsClient[F] {

      def streamMessages(
          queueUrl: String,
          settings: StreamMessageSettings
      ): Stream[F, SqsMessage[F]] =
        streamMessagesInternal(client)(
          queueUrl,
          settings.maxMessages,
          settings.visibilityTimeoutSeconds,
          settings.waitTimeSeconds
        ).map(m => SqsMessage(m.body, ReceiptHandle(m.receiptHandle, queueUrl, this)))

      def streamMessagesWithAttributes(
          queueUrl: String,
          settings: StreamMessageSettings
      ): Stream[F, SqsMessageWithAttributes[F]] = {
        streamMessagesInternal(client)(
          queueUrl,
          settings.maxMessages,
          settings.visibilityTimeoutSeconds,
          settings.waitTimeSeconds,
          receiveAttrs = true
        ).map { m =>
          val attributes = MessageAttributes.fromMap(
            m.attributes().asScala.toMap,
            m.messageAttributes().asScala.toMap
          )
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

      def sendMessage(
          queueUrl: String,
          messageBody: String
      ): F[String] = sendMessage(queueUrl, messageBody, Map.empty[String, MessageAttributeValue])

      def sendMessage(
          queueUrl: String,
          messageBody: String,
          messageAttributes: Map[String, MessageAttributeValue]
      ): F[String] = {
        val req =
          SendMessageRequest.builder
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .messageAttributes(messageAttributes.asJava)
            .build()
        client.sendMessage(req).map(_.messageId)
      }

      def sendMessage(
          queueUrl: String,
          messageBody: String,
          delaySeconds: Int,
      ): F[String] = {
        sendMessage(queueUrl, messageBody, delaySeconds, Map.empty[String, MessageAttributeValue])
      }

      def sendMessage(
          queueUrl: String,
          messageBody: String,
          delaySeconds: Int,
          messageAttributes: Map[String, MessageAttributeValue]
      ): F[String] = {
        val req =
          SendMessageRequest.builder
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .messageAttributes(messageAttributes.asJava)
            .delaySeconds(delaySeconds)
            .build()
        client.sendMessage(req).map(_.messageId)
      }
    }

  /** Constructs a `SimpleSqsClient` using an underlying synchronous client backend.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   An `SimpleSqsClient` instance using a synchronous backend.
    */
  def sync[F[_]: Sync](awsRegion: Region): Resource[F, SimpleSqsClient[F]] =
    PureSqsClient.sync[F](awsRegion).map(apply[F])

  /** Constructs a `SimpleSqsClient` using an underlying synchronous client backend. This variant allows for creating
    * the client with a different effect type than the `Resource` it is provided in.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   An `SimpleSqsClient` instance using a synchronous backend.
    */
  def syncIn[F[_]: Sync, G[_]: Sync](awsRegion: Region): Resource[F, SimpleSqsClient[G]] =
    PureSqsClient.syncIn[F, G](awsRegion).map(apply[G])

  /** Constructs a `SimpleSqsClient` using an underlying asynchronous client backend.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   A `SimpleSqsClient` instance using an asynchronous backend.
    */
  def async[F[_]: Async](awsRegion: Region): Resource[F, SimpleSqsClient[F]] =
    PureSqsClient.async[F](awsRegion).map(apply[F])

  /** Constructs a `SimpleSqsClient` using an underlying asynchronous client backend. This variant allows for creating
    * the client with a different effect type than the `Resource` it is provided in.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   A `SimpleSqsClient` instance using an asynchronous backend.
    */
  def asyncIn[F[_]: Sync, G[_]: Async](awsRegion: Region): Resource[F, SimpleSqsClient[G]] =
    PureSqsClient.asyncIn[F, G](awsRegion).map(apply[G])
}
