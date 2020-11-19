package com.rewardsnetwork.pureaws.s3

import cats.effect._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3Client, S3ClientBuilder, S3AsyncClient, S3AsyncClientBuilder}

/** Contains useful builders for an Amazon S3 Client */
object S3ClientBackend {

  /** Builds an `S3Client` from the AWS v2 SDK, which operates synchronously.
    *
    * You can configure your client before it is created using the `configure` function parameter
    * if you want to set anything other than your region.
    *
    * @param blocker A Cats Effect `Blocker`, used to create
    * @param awsRegion
    * @param configure
    * @return
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region)(
      configure: S3ClientBuilder => S3ClientBuilder = identity
  ): Resource[F, S3Client] =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      configure(S3Client.builder().region(awsRegion)).build()
    })

  /** Builds an AWS S3 Client (Async) with the specified region */
  def async[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region)(
      configure: S3AsyncClientBuilder => S3AsyncClientBuilder = identity
  ): Resource[F, S3AsyncClient] =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      configure(S3AsyncClient.builder().region(awsRegion)).build()
    })
}
