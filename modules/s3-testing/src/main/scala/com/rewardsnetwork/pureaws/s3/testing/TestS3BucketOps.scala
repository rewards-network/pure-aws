package com.rewardsnetwork.pureaws.s3.testing

import cats._
import cats.syntax.all._
import com.rewardsnetwork.pureaws.s3.{S3BucketPermission, S3BucketOps}
import software.amazon.awssdk.services.s3.model._
import com.rewardsnetwork.pureaws.s3.S3BucketInfo

/** A test utility for integrating with the `S3BucketOps` algebra.
  *
  * @param backend
  *   Your `S3TestingBackend`.
  * @param failWith
  *   An optional `Throwable` that you would like all requests to fail with, to test error recovery.
  */
class TestS3BucketOps[F[_]](backend: S3TestingBackend[F], failWith: Option[Throwable] = none)(implicit
    F: MonadError[F, Throwable]
) extends S3BucketOps[F] {

  private def doOrFail[A](fa: F[A]): F[A] = failWith match {
    case Some(t) => F.raiseError(t)
    case None    => fa
  }

  /** All parameters except for `name` are ignored. */
  def createBucket(
      name: String,
      location: BucketLocationConstraint,
      acl: BucketCannedACL,
      permissions: List[(String, S3BucketPermission)] = Nil
  ): F[Unit] = doOrFail(backend.newBucket(name))

  /** `expectedBucketOwner` is ignored. */
  def deleteBucket(name: String, expectedBucketOwner: Option[String] = none): F[Unit] = doOrFail {
    backend.deleteBucket(name)
  }

  def listBuckets: F[List[S3BucketInfo]] = doOrFail {
    backend.getAll.map(_.toList.map { case (name, (createdAt, _)) =>
      S3BucketInfo(name, createdAt)
    })
  }

}
