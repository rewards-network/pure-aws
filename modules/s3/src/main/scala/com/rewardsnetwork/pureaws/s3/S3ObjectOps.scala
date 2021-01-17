package com.rewardsnetwork.pureaws.s3

import cats.MonadError
import cats.effect._
import cats.implicits._
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, DeleteObjectRequest}
import software.amazon.awssdk.regions.Region

/** Defines miscelaneous operations for S3 objects */
trait S3ObjectOps[F[_]] {

  /** Copies an object from a source bucket and key to a new bucket and key.
    *
    * @param oldBucket Bucket of the object to be copied.
    * @param oldKey Key of the object to be copied.
    * @param newBucket Bucket to copy the object to.
    * @param newKey Key to copy the object to.
    * @return `Unit` if successful, will throw if failed.
    */
  def copy(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit]

  /** Deletes the object at the specified bucket and key
    *
    * @param bucket Bucket of the object to be deleted.
    * @param key Key of the object to be deleted.
    * @return `Unit` if successful, will throw if failed.
    */
  def delete(bucket: String, key: String): F[Unit]

  /** A `copy`, followed by a `delete` of the original object.
    * Be warned that this operation is, by design, not atomic and if the copy or delete step fails you might need to clean up your S3 bucket.
    *
    * @param oldBucket Bucket of the object to be moved.
    * @param oldKey Key of the object to be moved.
    * @param newBucket Bucket to move the object to.
    * @param newKey Key to move the object to.
    * @return `Unit` if successful, will throw if failed.
    */
  def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit]
}

object S3ObjectOps {
  def apply[F[_]](client: PureS3Client[F])(implicit F: MonadError[F, Throwable]) = {
    new S3ObjectOps[F] {

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
            F.raiseError[Unit](new Exception(s"Status code is $statusCode for copying object ($statusText)"))
          } else {
            F.unit
          }
        }
      }

      def delete(bucket: String, key: String): F[Unit] = {
        val req = DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        client.deleteObject(req).flatMap { res =>
          val statusCode = res.sdkHttpResponse.statusCode
          val statusText = res.sdkHttpResponse.statusText
          if (statusCode >= 300 || statusCode < 200) {
            F.raiseError[Unit](new Exception(s"Status code is $statusCode for deleting object ($statusText)"))
          } else {
            F.unit
          }
        }
      }

      def move(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
        (copy(oldBucket, oldKey, newBucket, newKey) >> delete(oldBucket, oldKey))
      }

    }
  }

  /** Constructs an `S3ObjectOps` using an underlying synchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3ObjectOps` instance using a synchronous backend.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.sync[F](blocker, awsRegion).map(apply[F])

  /** Constructs an `S3ObjectOps` using an underlying asynchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3ObjectOps` instance using an asynchronous backend.
    */
  def async[F[_]: ConcurrentEffect: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.async[F](blocker, awsRegion).map(apply[F])
}
