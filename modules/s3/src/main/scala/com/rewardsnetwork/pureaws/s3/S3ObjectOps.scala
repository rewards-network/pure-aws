package com.rewardsnetwork.pureaws.s3

import cats.Monad
import cats.effect.kernel._
import cats.syntax.all._
import fs2.Stream
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model._

import scala.jdk.CollectionConverters._

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
  def copyObject(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit]

  /** Deletes the object at the specified bucket and key
    *
    * @param bucket Bucket of the object to be deleted.
    * @param key Key of the object to be deleted.
    * @return `Unit` if successful, will throw if failed.
    */
  def deleteObject(bucket: String, key: String): F[Unit]

  /** A `copyObject`, followed by a `deleteObject` of the original object.
    * Be warned that this operation is, by design, not atomic and if the copy or delete step fails you might need to clean up your S3 bucket.
    *
    * @param oldBucket Bucket of the object to be moved.
    * @param oldKey Key of the object to be moved.
    * @param newBucket Bucket to move the object to.
    * @param newKey Key to move the object to.
    * @return `Unit` if successful, will throw if failed.
    */
  def moveObject(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit]

  /** Lists objects in a given bucket, with some optional config parameters.
    * For a paginated listing, see `listObjectsPaginated`.
    *
    * @param bucket The bucket you would like to list objects in.
    * @param delimiter (Optional) a "path delimiter" if you are treating your S3 object keys as being in "folders". A common example delimiter would be "/".
    * @param prefix (Optional) a prefix that you want to filter your search by, i.e. a "folder". When used along with `delimiter` it can affect your common prefix results.
    * @param expectedBucketOwner (Optional) the owner of this bucket, if it is not you.
    * @param requestPayer (Optional) An acknowledgement that you are paying for accessing this bucket, if applicable.
    * @return An `S3ObjectListing` containing a complete list of all objects in the listed bucket, and all common prefixes between them.
    */
  def listObjects(
      bucket: String,
      delimiter: Option[String] = none,
      prefix: Option[String] = none,
      expectedBucketOwner: Option[String] = none,
      requestPayer: Option[RequestPayer]
  )(implicit sync: Sync[F]): F[S3ObjectListing]

  /** Lists objects in a given bucket, paginated as a stream of results-per-request.
    * For a complete listing, see `listObjects`.
    *
    * @param bucket The bucket you would like to list objects in.
    * @param maxKeysPerRequest The max number of results you would like to return per-request. Default is 1000 results.
    * @param delimiter (Optional) a "path delimiter" if you are treating your S3 object keys as being in "folders". A common example delimiter would be "/".
    * @param prefix (Optional) a prefix that you want to filter your search by, i.e. a "folder". When used along with `delimiter` it can affect your common prefix results.
    * @param expectedBucketOwner (Optional) the owner of this bucket, if it is not you.
    * @param requestPayer (Optional) An acknowledgement that you are paying for accessing this bucket, if applicable.
    * @return A stream of `S3ObjectListing` objects containing a list of all of the objects and prefixes per-request.
    */
  def listObjectsPaginated(
      bucket: String,
      maxKeysPerRequest: Int = 1000,
      delimiter: Option[String] = none,
      prefix: Option[String] = none,
      expectedBucketOwner: Option[String] = none,
      requestPayer: Option[RequestPayer]
  ): Stream[F, S3ObjectListing]
}

object S3ObjectOps {
  def apply[F[_]: Monad](client: PureS3Client[F]) = {
    new S3ObjectOps[F] {

      def copyObject(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
        val req = CopyObjectRequest
          .builder()
          .sourceBucket(oldBucket)
          .sourceKey(oldKey)
          .destinationBucket(newBucket)
          .destinationKey(newKey)
          .build()
        client.copyObject(req).void
      }

      def deleteObject(bucket: String, key: String): F[Unit] = {
        val req = DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        client.deleteObject(req).void
      }

      def moveObject(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = {
        (copyObject(oldBucket, oldKey, newBucket, newKey) *> deleteObject(oldBucket, oldKey))
      }

      def listObjects(
          bucket: String,
          delimiter: Option[String],
          prefix: Option[String],
          expectedBucketOwner: Option[String],
          requestPayer: Option[RequestPayer]
      )(implicit sync: Sync[F]): F[S3ObjectListing] =
        listObjectsPaginated(
          bucket = bucket,
          delimiter = delimiter,
          prefix = prefix,
          expectedBucketOwner = expectedBucketOwner,
          requestPayer = requestPayer
        ).foldMonoid.compile.lastOrError

      def listObjectsPaginated(
          bucket: String,
          maxKeysPerRequest: Int = 1000,
          delimiter: Option[String] = none,
          prefix: Option[String] = none,
          expectedBucketOwner: Option[String] = none,
          requestPayer: Option[RequestPayer]
      ): Stream[F, S3ObjectListing] = {
        val initialReq = ListObjectsV2Request.builder.bucket(bucket).maxKeys(maxKeysPerRequest)
        val reqWithDelimiter = delimiter.fold(initialReq)(s => initialReq.delimiter(s))
        val reqWithPrefix = prefix.fold(reqWithDelimiter)(s => reqWithDelimiter.prefix(s))
        val reqWithBucketOwner = expectedBucketOwner.fold(reqWithPrefix)(s => reqWithPrefix.expectedBucketOwner(s))
        val reqWithRequestPayer = requestPayer.fold(reqWithBucketOwner)(rp => reqWithBucketOwner.requestPayer(rp))

        val finalReq = reqWithRequestPayer.build()

        Stream.unfoldLoopEval[F, ListObjectsV2Request, S3ObjectListing](finalReq) { nextReq =>
          client.listObjects(nextReq).map[(S3ObjectListing, Option[ListObjectsV2Request])] { res =>
            val prefixes = res.commonPrefixes.asScala.map(_.prefix).toSet
            val objects = res.contents.asScala.toList.map(S3ObjectInfo.fromS3Object(_, bucket))
            val out = S3ObjectListing(objects, prefixes)
            if (res.isTruncated) {
              val ct = res.nextContinuationToken
              val nextReq = reqWithRequestPayer.continuationToken(ct).build()
              out -> nextReq.some
            } else {
              out -> none[ListObjectsV2Request]
            }
          }
        }
      }

    }
  }

  /** Constructs an `S3ObjectOps` using an underlying synchronous client backend.
    *
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3ObjectOps` instance using a synchronous backend.
    */
  def sync[F[_]: Sync](awsRegion: Region): Resource[F, S3ObjectOps[F]] =
    PureS3Client.sync[F](awsRegion).map(apply[F])

  /** Constructs an `S3ObjectOps` using an underlying synchronous client backend.
    * This variant allows for creating the client with a different effect type than the `Resource` it is provided in.
    *
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3ObjectOps` instance using a synchronous backend.
    */
  def syncIn[F[_]: Sync, G[_]: Sync](awsRegion: Region): Resource[F, S3ObjectOps[G]] =
    PureS3Client.syncIn[F, G](awsRegion).map(apply[G])

  /** Constructs an `S3ObjectOps` using an underlying asynchronous client backend.
    *
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3ObjectOps` instance using an asynchronous backend.
    */
  def async[F[_]: Async](awsRegion: Region): Resource[F, S3ObjectOps[F]] =
    PureS3Client.async[F](awsRegion).map(apply[F])

  /** Constructs an `S3ObjectOps` using an underlying asynchronous client backend.
    * This variant allows for creating the client with a different effect type than the `Resource` it is provided in.
    *
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3ObjectOps` instance using an asynchronous backend.
    */
  def asyncIn[F[_]: Sync, G[_]: Async](awsRegion: Region): Resource[F, S3ObjectOps[G]] =
    PureS3Client.asyncIn[F, G](awsRegion).map(apply[G])
}
