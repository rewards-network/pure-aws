package com.rewardsnetwork.pureaws.sqs

/** The handle received from a message. Allows you to delete the message and change its visibility timeout. */
trait BaseReceiptHandle[F[_], T] {

  /** The underlying raw receipt handle string value. */
  val rawReceiptHandle: String

  /** The raw Queue URL string */
  val queueUrl: String

  /** Delete the message this `ReceiptHandle` is attached to from SQS.
    * Used to signal that the message was consumed/processed successfully.
    */
  val delete: F[Unit]

  /** Change the visibility timeout for the message this `ReceiptHandle` is attached to.
    * Type `T` is dependent on the underlying client's visibility timeout parameter type.
    */
  def changeVisibility(visibilityTimeout: T): F[Unit]
}

trait ReceiptHandle[F[_]] extends BaseReceiptHandle[F, Int]

object ReceiptHandle {
  def apply[F[_]](messageReceiptHandle: String, messageQueueUrl: String, simpleClient: SimpleSqsClient[F]) =
    new ReceiptHandle[F] {
      val rawReceiptHandle: String = messageReceiptHandle

      val queueUrl: String = messageQueueUrl

      val delete: F[Unit] = simpleClient.deleteMessage(rawReceiptHandle, queueUrl)

      def changeVisibility(visibilityTimeout: Int): F[Unit] =
        simpleClient.changeMessageVisibility(visibilityTimeout, rawReceiptHandle, queueUrl)
    }
}
