package com.rewardsnetwork.pureaws.s3.testing

import cats._
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3ObjectOps

/** A test utility for integrating with the `S3ObjectOps` algebra.
  *
  * @param backend Your `S3TestingBackend`.
  * @param failWith An optional `Throwable` that you would like all requests to fail with, to test error recovery.
  */
class TestS3ObjectOps[F[_]](backend: S3TestingBackend[F], failWith: Option[Throwable] = none)(implicit
    F: MonadError[F, Throwable]
) extends S3ObjectOps[F] {

  private def doOrFail[A](fa: F[A]): F[A] = failWith match {
    case Some(t) => F.raiseError(t)
    case None    => fa
  }

  def copy(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = doOrFail {
    backend.get(oldBucket, oldKey).flatMap {
      case None              => F.raiseError(new Exception("Object not found"))
      case Some((meta, obj)) => backend.put(newBucket, newKey, obj, meta)
    }
  }

  def delete(bucket: String, key: String): F[Unit] = doOrFail {
    backend.delete(bucket, key)
  }

  def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = doOrFail {
    copy(oldBucket, oldKey, newBucket, newKey) >> delete(oldBucket, oldKey)
  }

}
