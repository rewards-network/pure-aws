package com.rewardsnetwork.pureaws.sqs

import cats.effect._
import software.amazon.awssdk.services.sqs._
import software.amazon.awssdk.regions.Region

/** Builders for a raw SQS client, a `PureClient`, and a `SimpleClient` with sync and async modes. */
object SqsClientBackend {

  /** Builds a raw AWS `SqsClient` (synchronous).
    * Prefer to use `PureSqsClient` instead, where possible.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, region: Region)(
      configure: SqsClientBuilder => SqsClientBuilder = identity
  ) =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      configure(SqsClient.builder.region(region)).build
    })

  /** Builds a raw AWS `SqsAsyncClient`.
    * Prefer to use `PureSqsClient` instead, where possible.
    */
  def async[F[_]: Sync: ContextShift](blocker: Blocker, region: Region)(
      configure: SqsAsyncClientBuilder => SqsAsyncClientBuilder = identity
  ) =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      configure(SqsAsyncClient.builder.region(region)).build
    })
}
