package com.rewardsnetwork.pureaws.s3.testing

import cats.effect._
import cats.implicits._

/** All available test helpers for S3 at once. */
sealed trait TestSimpleS3Client[F[_]] {
  def ops: TestS3ObjectOps[F]
  def sink: TestS3Sink[F]
  def source: TestS3Source[F]
}

object TestSimpleS3Client {

  /** Constructs a `SimpleS3Client` using an existing `PureS3Client` for some `F[_]`.
    * Gives you access to all available algebras for the S3 client in one place.
    *
    * @param backend Your `S3TestingBackend`.
    * @param failWith An optional `Throwable` that you would like all requests to fail with, to test error recovery.
    */
  def apply[F[_]: Sync](backend: S3TestingBackend[F], failWith: Option[Throwable] = none) = new TestSimpleS3Client[F] {
    val ops = new TestS3ObjectOps(backend, failWith)
    val sink = new TestS3Sink(backend, failWith)
    val source = new TestS3Source(backend, failWith)
  }
}
