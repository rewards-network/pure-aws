package com.rewardsnetwork.pureaws.sqs

import cats.effect._
import software.amazon.awssdk.services.sqs._
import software.amazon.awssdk.regions.Region

/** Builders for a raw SQS client, a `PureClient`, and a `SimpleClient` with sync and async modes. */
object SqsClientBackend {

  /** Builds a raw AWS `SqsClient` (synchronous).
    * Prefer to use `PureSqsClient` instead, where possible.
    */
  def sync[F[_]: Sync](region: Region)(
      configure: SqsClientBuilder => SqsClientBuilder = identity
  ) =
    Resource.fromAutoCloseable(Sync[F].blocking {
      configure(SqsClient.builder.region(region)).build
    })

  /** Builds a raw AWS `SqsAsyncClient`.
    * Prefer to use `PureSqsClient` instead, where possible.
    */
  def async[F[_]: Sync](region: Region)(
      configure: SqsAsyncClientBuilder => SqsAsyncClientBuilder = identity
  ) =
    Resource.fromAutoCloseable(Sync[F].blocking {
      configure(SqsAsyncClient.builder.region(region)).build
    })
}
