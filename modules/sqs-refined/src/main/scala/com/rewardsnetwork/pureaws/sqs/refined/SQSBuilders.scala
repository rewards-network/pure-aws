package com.rewardsnetwork.pureaws.sqs.refined

import com.rewardsnetwork.pureaws.sqs.{SQSBuilders => BaseBuilders}
import cats.effect._
import software.amazon.awssdk.regions.Region

/** Builders for `RefinedSqsClient` based on an underlying client implementation. */
object SQSBuilders {

  /** Builds a `RefinedSqsClient` using the sync AWS SQS client. */
  def refinedClientResource[F[_]: Sync: ContextShift](blocker: Blocker, region: Region) =
    BaseBuilders.pureClientResource[F](blocker, region).map(RefinedSqsClient[F])

  /** Builds a `RefinedSqsClient` using the async AWS SQS client. */
  def refinedClientResourceAsync[F[_]: Async: ContextShift](blocker: Blocker, region: Region) =
    BaseBuilders.pureClientResourceAsync[F](blocker, region).map(RefinedSqsClient[F])
}
