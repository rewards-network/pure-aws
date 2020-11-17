package com.rewardsnetwork.pureaws.sqs

import cats.effect._
import software.amazon.awssdk.services.sqs._
import software.amazon.awssdk.regions.Region

/** Builders for a raw SQS client, a `PureClient`, and a `SimpleClient` with sync and async modes. */
object SQSBuilders {

  /** Builds a raw AWS SQS sync client.
    * Prefer to use `PureSqsClient` instead, where possible.
    */
  def clientResource[F[_]: Sync: ContextShift](blocker: Blocker, region: Region) =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      SqsClient.builder.region(region).build
    })

  /** Builds a raw AWS SQS async client.
    * Prefer to use `PureSqsClient` instead, where possible.
    */
  def clientResourceAsync[F[_]: Sync: ContextShift](blocker: Blocker, region: Region) =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      SqsAsyncClient.builder.region(region).build
    })

  /** Builds a `PureSqsClient` based on the sync AWS SQS client backend. */
  def pureClientResource[F[_]: Sync: ContextShift](blocker: Blocker, region: Region) =
    clientResource[F](blocker, region).map(c => PureSqsClient[F](blocker, c))

  /** Builds a `PureSqsClient` based on the async AWS SQS client backend. */
  def pureClientResourceAsync[F[_]: Async: ContextShift](blocker: Blocker, region: Region) =
    clientResourceAsync[F](blocker, region).map(c => PureSqsClient(c))

  /** Builds a `SimpleSqsClient` based on an underlying sync AWS SQS client. */
  def simpleClientResource[F[_]: Sync: ContextShift](blocker: Blocker, region: Region) =
    pureClientResource[F](blocker, region).map(SimpleSqsClient[F])

  /** Builds a `SimpleSqsClient` based on an underlying async AWS SQS client. */
  def simpleClientResourceAsync[F[_]: Async: ContextShift](blocker: Blocker, region: Region) =
    pureClientResourceAsync[F](blocker, region).map(SimpleSqsClient[F])
}
