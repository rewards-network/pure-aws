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

  /** Read a stream of bytes from the object at the specified bucket/key in S3. */
  def readObject(bucket: String, key: String): Stream[F, Byte]

  def readObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Stream[F, Byte])]
}

object S3Source {

  /** Creates a new `S3Source` given an existing `S3Client`.
    */
  def apply[F[_]](client: PureS3Client[F]): S3Source[F] = {

    new S3Source[F] {
      def readObject(bucket: String, key: String): Stream[F, Byte] = {
        val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
        Stream.eval(client.getObjectBytes(req)).flatMap { bytes =>
          Stream.emits(bytes.asByteArray)
        }
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
    }
  }

  /** Constructs an `S3Source` using an underlying synchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Source` instance using a synchronous backend.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.sync[F](blocker, awsRegion).map(apply[F])

  /** Constructs an `S3Source` using an underlying asynchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Source` instance using an asynchronous backend.
    */
  def async[F[_]: Async: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.async[F](blocker, awsRegion).map(apply[F])
}
