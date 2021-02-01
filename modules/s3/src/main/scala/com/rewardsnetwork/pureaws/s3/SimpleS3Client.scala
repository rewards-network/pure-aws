package com.rewardsnetwork.pureaws.s3

import cats.effect._
import cats.MonadError
import software.amazon.awssdk.regions.Region

/** An amalgamation of all available S3 algebras in one client. */
sealed trait SimpleS3Client[F[_]] {
  def bucketOps: S3BucketOps[F]
  def objectOps: S3ObjectOps[F]
  def sink: S3Sink[F]
  def source: S3Source[F]
}

object SimpleS3Client {

  /** Constructs a `SimpleS3Client` using an existing `PureS3Client` for some `F[_]`.
    * Gives you access to all available algebras for the S3 client in one place.
    */
  def apply[F[_]](client: PureS3Client[F])(implicit F: MonadError[F, Throwable]) = new SimpleS3Client[F] {
    val bucketOps = S3BucketOps(client)
    val objectOps = S3ObjectOps(client)
    val sink = S3Sink(client)
    val source = S3Source(client)
  }

  /** Constructs a `SimpleS3Client` using an underlying synchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return A `SimpleS3Client` instance using a synchronous backend.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region): Resource[F, SimpleS3Client[F]] =
    PureS3Client.sync[F](blocker, awsRegion).map(apply[F])

  /** Constructs a `SimpleS3Client` using an underlying synchronous client backend.
    * This variant allows for creating the client with a different effect type than the `Resource` it is provided in.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return A `SimpleS3Client` instance using a synchronous backend.
    */
  def syncIn[F[_]: Sync: ContextShift, G[_]: Sync: ContextShift](
      blocker: Blocker,
      awsRegion: Region
  ): Resource[F, SimpleS3Client[G]] =
    PureS3Client.syncIn[F, G](blocker, awsRegion).map(apply[G])

  /** Constructs a `SimpleS3Client` using an underlying asynchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return A `SimpleS3Client` instance using an asynchronous backend.
    */
  def async[F[_]: ConcurrentEffect: ContextShift](blocker: Blocker, awsRegion: Region): Resource[F, SimpleS3Client[F]] =
    PureS3Client.async[F](blocker, awsRegion).map(apply[F])

  /** Constructs a `SimpleS3Client` using an underlying asynchronous client backend.
    * This variant allows for creating the client with a different effect type than the `Resource` it is provided in.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return A `SimpleS3Client` instance using an asynchronous backend.
    */
  def asyncIn[F[_]: Sync: ContextShift, G[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      awsRegion: Region
  ): Resource[F, SimpleS3Client[G]] =
    PureS3Client.asyncIn[F, G](blocker, awsRegion).map(apply[G])
}
