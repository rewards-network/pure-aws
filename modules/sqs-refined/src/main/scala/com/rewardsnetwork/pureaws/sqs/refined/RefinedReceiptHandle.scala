package com.rewardsnetwork.pureaws.sqs.refined

object RefinedReceiptHandle {
  def apply[F[_]](messageReceiptHandle: String, messageQueueUrl: String, refinedClient: RefinedSqsClient[F]) =
    new RefinedReceiptHandle[F] {
      val rawReceiptHandle: String = messageReceiptHandle

      val queueUrl: String = messageQueueUrl

      val delete: F[Unit] = refinedClient.deleteMessage(this)

      def changeVisibility(visibilityTimeout: VisibilityTimeout): F[Unit] =
        refinedClient.changeMessageVisibility(visibilityTimeout, this)
    }
}
