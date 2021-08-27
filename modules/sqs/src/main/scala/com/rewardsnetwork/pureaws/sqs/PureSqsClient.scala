package com.rewardsnetwork.pureaws.sqs

import java.util.concurrent.CompletableFuture

import cats.effect._
import fs2.Stream
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.{SqsClient, SqsAsyncClient}
import software.amazon.awssdk.services.sqs.model._

/** A pure client for dealing with Amazon SQS. Uses bare AWS request/response types. Consider using `SimpleSqsClient`
  * instead which is based on this.
  */
trait PureSqsClient[F[_]] {

  /** Add permissions to a queue using the provided request. */
  def addPermission(r: AddPermissionRequest): F[AddPermissionResponse]

  /** Deletes a message using the provided request. */
  def deleteMessage(r: DeleteMessageRequest): F[DeleteMessageResponse]

  /** Changes a message's visibility timeout using the provided request. */
  def changeMessageVisibility(r: ChangeMessageVisibilityRequest): F[ChangeMessageVisibilityResponse]

  /** Creates a queue using the provided request. */
  def createQueue(r: CreateQueueRequest): F[CreateQueueResponse]

  /** Deletes a queue using the provided request. */
  def deleteQueue(r: DeleteQueueRequest): F[DeleteQueueResponse]

  /** Deletes all messages from a queue using the provided request. */
  def purgeQueue(r: PurgeQueueRequest): F[PurgeQueueResponse]

  /** Receive some messages from SQS, given the provided request. */
  def receiveMessages(r: ReceiveMessageRequest): F[ReceiveMessageResponse]

  /** Receive as many messages as possible from SQS using the provided request, indefinitely, as a stream. */
  def receiveMessageStream(r: ReceiveMessageRequest): Stream[F, ReceiveMessageResponse]

  /** Sends a message using the provided request. */
  def sendMessage(r: SendMessageRequest): F[SendMessageResponse]
}

object PureSqsClient {

  /** Creates a new PureSqsClient given an existing `SqsClient`. Note that you will have to close the client yourself
    * when you are finished.
    */
  def apply[F[_]: Sync](client: SqsClient) = new PureSqsClient[F] {
    private def block[A](f: => A): F[A] = Sync[F].blocking(f)

    def addPermission(r: AddPermissionRequest): F[AddPermissionResponse] = block {
      client.addPermission(r)
    }

    def changeMessageVisibility(r: ChangeMessageVisibilityRequest): F[ChangeMessageVisibilityResponse] = block {
      client.changeMessageVisibility(r)
    }

    def createQueue(r: CreateQueueRequest): F[CreateQueueResponse] = block {
      client.createQueue(r)
    }

    def deleteMessage(r: DeleteMessageRequest): F[DeleteMessageResponse] = block {
      client.deleteMessage(r)
    }

    def deleteQueue(r: DeleteQueueRequest): F[DeleteQueueResponse] = block {
      client.deleteQueue(r)
    }

    def purgeQueue(r: PurgeQueueRequest): F[PurgeQueueResponse] = block {
      client.purgeQueue(r)
    }

    def receiveMessages(r: ReceiveMessageRequest): F[ReceiveMessageResponse] =
      block {
        client.receiveMessage(r)
      }

    def receiveMessageStream(r: ReceiveMessageRequest): Stream[F, ReceiveMessageResponse] = {
      Stream.repeatEval(receiveMessages(r))
    }

    def sendMessage(r: SendMessageRequest): F[SendMessageResponse] = block {
      client.sendMessage(r)
    }

    def sendMessageBatch(r: SendMessageBatchRequest): F[SendMessageBatchResponse] = block {
      client.sendMessageBatch(r)
    }

  }

  /** Creates a new PureSqsClient using an existing SqsAsyncClient. Note that you will have to close the client yourself
    * when you are finished.
    */
  def apply[F[_]: Async](client: SqsAsyncClient) = new PureSqsClient[F] {
    private def lift[A](f: => CompletableFuture[A]): F[A] = Async[F].fromCompletableFuture(Sync[F].delay(f))
    def addPermission(r: AddPermissionRequest): F[AddPermissionResponse] = lift {
      client.addPermission(r)
    }

    def changeMessageVisibility(r: ChangeMessageVisibilityRequest): F[ChangeMessageVisibilityResponse] = lift {
      client.changeMessageVisibility(r)
    }

    def createQueue(r: CreateQueueRequest): F[CreateQueueResponse] = lift {
      client.createQueue(r)
    }

    def deleteMessage(r: DeleteMessageRequest): F[DeleteMessageResponse] = lift {
      client.deleteMessage(r)
    }

    def deleteQueue(r: DeleteQueueRequest): F[DeleteQueueResponse] = lift {
      client.deleteQueue(r)
    }

    def purgeQueue(r: PurgeQueueRequest): F[PurgeQueueResponse] = lift {
      client.purgeQueue(r)
    }

    def receiveMessages(r: ReceiveMessageRequest): F[ReceiveMessageResponse] = lift {
      client.receiveMessage(r)
    }

    def receiveMessageStream(r: ReceiveMessageRequest): Stream[F, ReceiveMessageResponse] =
      Stream.repeatEval(receiveMessages(r))

    def sendMessage(r: SendMessageRequest): F[SendMessageResponse] = lift {
      client.sendMessage(r)
    }

  }

  /** Creates a `PureSqsClient` using a synchronous backend with default settings.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   A `Resource` containing a `PureSqsClient` using a synchronous backend.
    */
  def sync[F[_]: Sync](awsRegion: Region): Resource[F, PureSqsClient[F]] =
    SqsClientBackend.sync[F](awsRegion)().map(apply[F](_))

  /** Creates a `PureSqsClient` using a synchronous backend with default settings. This variant allows for creating the
    * client with a different effect type than the `Resource` it is provided in.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   A `Resource` containing a `PureSqsClient` using a synchronous backend.
    */
  def syncIn[F[_]: Sync, G[_]: Sync](awsRegion: Region): Resource[F, PureSqsClient[G]] =
    SqsClientBackend.sync[F](awsRegion)().map(apply[G](_))

  /** Creates a `PureSqsClient` using an asynchronous backend with default settings.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   A `Resource` containing a `PureSqsClient` using an asynchronous backend.
    */
  def async[F[_]: Async](awsRegion: Region): Resource[F, PureSqsClient[F]] =
    SqsClientBackend.async[F](awsRegion)().map(apply[F])

  /** Creates a `PureSqsClient` using an asynchronous backend with default settings. This variant allows for creating
    * the client with a different effect type than the `Resource` it is provided in.
    *
    * @param awsRegion
    *   The AWS region you are operating in.
    * @return
    *   A `Resource` containing a `PureSqsClient` using an asynchronous backend.
    */
  def asyncIn[F[_]: Sync, G[_]: Async](awsRegion: Region): Resource[F, PureSqsClient[G]] =
    SqsClientBackend.async[F](awsRegion)().map(apply[G])
}
