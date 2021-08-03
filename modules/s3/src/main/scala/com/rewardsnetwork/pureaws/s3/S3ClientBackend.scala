package com.rewardsnetwork.pureaws.s3

import cats.effect.kernel._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3Client, S3ClientBuilder, S3AsyncClient, S3AsyncClientBuilder}

/** Contains useful builders for an Amazon S3 Client */
object S3ClientBackend {

  /** Builds an `S3Client` from the AWS v2 SDK, which operates synchronously.
    * Prefer to use `PureS3Client` directly where possible.
    *
    * You can configure your client before it is created using the `configure` function parameter
    * if you want to set anything other than your region.
    *
    * @param awsRegion The AWS region you will be operating in.
    * @param configure A function to configure your client before it is built and returned to you.
    * @return A configured `S3Client` as a `Resource` that will close itself after use.
    */
  def sync[F[_]: Sync](
      awsRegion: Region
  )(configure: S3ClientBuilder => S3ClientBuilder = identity): Resource[F, S3Client] =
    Resource.fromAutoCloseable(Sync[F].delay {
      configure(S3Client.builder().region(awsRegion)).build()
    })

  /** Builds an `S3AsyncClient` from the AWS v2 SDK, which operates asynchronously.
    * Prefer to use `PureS3Client` directly where possible.
    *
    * You can configure your client before it is created using the `configure` function parameter
    * if you want to set anything other than your region.
    *
    * @param awsRegion The AWS region you will be operating in.
    * @param configure A function to configure your client before it is built and returned to you.
    * @return A configured `S3AsyncClient` as a `Resource` that will close itself after use.
    */
  def async[F[_]: Sync](
      awsRegion: Region
  )(configure: S3AsyncClientBuilder => S3AsyncClientBuilder = identity): Resource[F, S3AsyncClient] =
    Resource.fromAutoCloseable(Sync[F].delay {
      configure(S3AsyncClient.builder().region(awsRegion)).build()
    })
}
