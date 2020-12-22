package com.rewardsnetwork.pureaws.s3.testing

import cats._
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3ObjectOps

class TestS3ObjectOps[F[_]](backend: S3TestingBackend[F])(implicit F: MonadError[F, Throwable]) extends S3ObjectOps[F] {
  def copy(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
    backend.get(oldBucket, oldKey).flatMap {
      case None              => F.raiseError(new Exception("Object not found"))
      case Some((meta, obj)) => backend.put(newBucket, newKey, obj, meta)
    }
  }

  def delete(bucket: String, key: String): F[Unit] =
    backend.delete(bucket, key)

  def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] =
    copy(oldBucket, oldKey, newBucket, newKey) >> delete(oldBucket, oldKey)

}
