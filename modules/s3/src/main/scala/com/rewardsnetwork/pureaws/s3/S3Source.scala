package com.rewardsnetwork.pureaws.s3

import cats.Applicative
import cats.implicits._
import cats.effect._
import fs2.Stream
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.GetObjectRequest

import scala.jdk.CollectionConverters._

/** A helper for downloading object bytes from an S3 object using FS2. */
trait S3Source[F[_]] {

  /** Read a stream of bytes from the object at the specified bucket/key in S3.
    * For reading a whole object into memory at once and not using streaming, it might be more efficient to use `readWholeObject` instead.
    */
  def readObject(bucket: String, key: String): Stream[F, Byte]

  /** Reads a whole object into memory at once from a specified bucket/key in S3. */
  def readWholeObject(bucket: String, key: String)(implicit F: Applicative[F]): F[Array[Byte]]

  /** Same as `readObject` but also returns the map of the object's metadata. */
  def readObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Stream[F, Byte])]

  /** Same as `readWholeObject` but also returns the map of the object's metadata. */
  def readWholeObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Array[Byte])]
}

object S3Source {

  /** Creates a new `S3Source` given an existing `S3Client`.
    */
  def apply[F[_]](client: PureS3Client[F]): S3Source[F] = {

    new S3Source[F] {
      def readObject(bucket: String, key: String): Stream[F, Byte] = {
        val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
        client.getObjectStream(req)
      }

      def readWholeObject(bucket: String, key: String)(implicit F: Applicative[F]): F[Array[Byte]] = {
        val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
        client.getObjectBytes(req).map(_.asByteArray)
      }

      def readObjectWithMetadata(bucket: String, key: String)(implicit
          F: Applicative[F]
      ): F[(Map[String, String], Stream[F, Byte])] = {
        val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
        client.getObjectBytes(req).map { r =>
          r.response.metadata.asScala.toMap -> Stream.eval(client.getObjectBytes(req)).flatMap { bytes =>
            Stream.emits(bytes.asByteArray)
          }
        }
      }

      def readWholeObjectWithMetadata(bucket: String, key: String)(implicit
          F: Applicative[F]
      ): F[(Map[String, String], Array[Byte])] = {
        val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
        client.getObjectBytes(req).map { r =>
          r.response.metadata.asScala.toMap -> r.asByteArray
        }
      }
    }
  }

  /** Constructs an `S3Source` using an underlying synchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Source` instance using a synchronous backend.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region): Resource[F, S3Source[F]] =
    PureS3Client.sync[F](blocker, awsRegion).map(apply[F])

  /** Constructs an `S3Source` using an underlying synchronous client backend.
    * This variant allows for creating the client with a different effect type than the `Resource` it is provided in.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Source` instance using a synchronous backend.
    */
  def syncIn[F[_]: Sync: ContextShift, G[_]: Sync: ContextShift](
      blocker: Blocker,
      awsRegion: Region
  ): Resource[F, S3Source[G]] =
    PureS3Client.syncIn[F, G](blocker, awsRegion).map(apply[G])

  /** Constructs an `S3Source` using an underlying asynchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Source` instance using an asynchronous backend.
    */
  def async[F[_]: ConcurrentEffect: ContextShift](blocker: Blocker, awsRegion: Region): Resource[F, S3Source[F]] =
    PureS3Client.async[F](blocker, awsRegion).map(apply[F])

  /** Constructs an `S3Source` using an underlying asynchronous client backend.
    * This variant allows for creating the client with a different effect type than the `Resource` it is provided in.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Source` instance using an asynchronous backend.
    */
  def asyncIn[F[_]: Sync: ContextShift, G[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      awsRegion: Region
  ): Resource[F, S3Source[G]] =
    PureS3Client.asyncIn[F, G](blocker, awsRegion).map(apply[G])
}
