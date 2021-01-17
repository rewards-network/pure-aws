package com.rewardsnetwork.pureaws.s3.testing

import cats.effect._

/** An amalgamation of all available S3 algebras in one client. */
sealed trait TestSimpleS3Client[F[_]] {
  def ops: TestS3ObjectOps[F]
  def sink: TestS3Sink[F]
  def source: TestS3Source[F]
}

object TestSimpleS3Client {

  /** Constructs a `SimpleS3Client` using an existing `PureS3Client` for some `F[_]`.
    * Gives you access to all available algebras for the S3 client in one place.
    */
  def apply[F[_]: Sync](backend: S3TestingBackend[F]) = new TestSimpleS3Client[F] {
    val ops = new TestS3ObjectOps(backend)
    val sink = new TestS3Sink(backend)
    val source = new TestS3Source(backend)
  }
}
