package com.rewardsnetwork.pureaws.s3

import cats.Applicative
import cats.implicits._
import fs2.Stream
import software.amazon.awssdk.services.s3.model.GetObjectRequest

import scala.jdk.CollectionConverters._

/** A helper for downloading files from an S3 object using FS2. */
trait S3FileSource[F[_]] {

  /** Read a stream of bytes from the file at the specified bucket/key in S3. */
  def readFile(bucket: String, key: String): Stream[F, Byte]

  def readFileWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Stream[F, Byte])]
}

object S3FileSource {

  /** Creates a new S3FileSource given an existing `S3Client`.
    * Note that you will have to close the client yourself when you are finished.
    */
  def apply[F[_]](client: PureS3Client[F]): S3FileSource[F] = {

    new S3FileSource[F] {
      def readFile(bucket: String, key: String): Stream[F, Byte] = {
        val req = GetObjectRequest.builder().bucket(bucket).key(key).build()
        Stream.eval(client.getObjectBytes(req)).flatMap { bytes =>
          Stream.emits(bytes.asByteArray)
        }
      }

      def readFileWithMetadata(bucket: String, key: String)(implicit
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
}
