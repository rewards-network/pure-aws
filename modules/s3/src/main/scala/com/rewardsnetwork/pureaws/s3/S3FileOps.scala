package com.rewardsnetwork.pureaws.s3

import cats.MonadError
import cats.implicits._
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest

/** Defines miscelaneous operations for S3 objects */
trait S3FileOps[F[_]] {
  def copy(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit]
  def delete(bucket: String, key: String): F[Unit]
  def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit]
}

object S3FileOps {
  def apply[F[_]](client: PureS3Client[F])(implicit F: MonadError[F, Throwable]) = {
    new S3FileOps[F] {

      /** Copies a file from a source bucket and key to a new bucket and key. */
      def copy(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
        val req = CopyObjectRequest
          .builder()
          .copySource(s"$oldBucket/$oldKey")
          .destinationBucket(newBucket)
          .destinationKey(newKey)
          .build()
        client.copyObject(req).flatMap { res =>
          val statusCode = res.sdkHttpResponse.statusCode
          val statusText = res.sdkHttpResponse.statusText
          if (statusCode >= 300 || statusCode < 200) {
            F.raiseError[Unit](new Exception(s"Status code is $statusCode for copying file ($statusText)"))
          } else {
            F.unit
          }
        }
      }

      /** Deletes the file at the specified bucket and key */
      def delete(bucket: String, key: String): F[Unit] = {
        val req = DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        client.deleteObject(req).flatMap { res =>
          val statusCode = res.sdkHttpResponse.statusCode
          val statusText = res.sdkHttpResponse.statusText
          if (statusCode >= 300 || statusCode < 200) {
            F.raiseError[Unit](new Exception(s"Status code is $statusCode for deleting file ($statusText)"))
          } else {
            F.unit
          }
        }
      }

      /** Attempts a copy, followed by deleting the original file.
        * Operation is NOT ATOMIC and can result in inconsistent S3 state.
        */
      def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
        (copy(oldBucket, oldKey, newBucket, newKey) >> delete(oldBucket, oldKey))
      }

    }
  }
}
