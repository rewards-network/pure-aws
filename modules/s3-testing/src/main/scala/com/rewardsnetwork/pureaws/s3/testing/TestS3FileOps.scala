package com.rewardsnetwork.pureaws.s3.testing

import cats._
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3FileOps

class TestS3FileOps[F[_]](backend: S3Backend[F])(implicit F: MonadError[F, Throwable]) extends S3FileOps[F] {
  def copy(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
    backend.get(oldBucket, oldKey).flatMap {
      case None               => F.raiseError(new Exception("File not found"))
      case Some((meta, file)) => backend.put(newBucket, newKey, file, meta)
    }
  }

  def delete(bucket: String, key: String): F[Unit] =
    backend.delete(bucket, key)

  def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] =
    copy(oldBucket, oldKey, newBucket, newKey) >> delete(oldBucket, oldKey)

}
