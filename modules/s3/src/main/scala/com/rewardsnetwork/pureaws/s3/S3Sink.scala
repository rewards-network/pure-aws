package com.rewardsnetwork.pureaws.s3

import java.nio.ByteBuffer
import java.security.MessageDigest

import cats.ApplicativeError
import cats.effect._
import fs2.{Pipe, Stream}
import software.amazon.awssdk.services.s3.model.{PutObjectResponse, PutObjectRequest}
import software.amazon.awssdk.regions.Region

/** A helper for uploading S3 objects using FS2. */
trait S3Sink[F[_]] {

  /** Write the stream of bytes to an object at the specified path.
    * Content type is assumed to be "text/plain".
    *
    * The bytes are uploaded all at once, and buffered in-memory.
    * For larger objects, consider doing a multipart upload.
    *
    * @param bucket The bucket of the object you are uploading to.
    * @param key The key of the object you are uploading to.
    * @return An FS2 `Pipe` for writing bytes to this new object.
    */
  def writeText(bucket: String, key: String): Pipe[F, Byte, Unit]

  /** Write the stream of bytes to an object at the specified path.
    * The bytes are uploaded all at once, and buffered in-memory.
    * For larger objects, consider doing a multipart upload.
    *
    * @param bucket The bucket of the object you are uploading to.
    * @param key The key of the object you are uploading to.
    * @param contentType The desired content type of the object being uploaded.
    * @return An FS2 `Pipe` for writing bytes to this new object.
    */
  def writeBytes(bucket: String, key: String, contentType: String): Pipe[F, Byte, Unit]
}

object S3Sink {

  /** Creates a new `S3Sink` given an existing `PureS3Client`.
    */
  def apply[F[_]](client: PureS3Client[F])(implicit F: ApplicativeError[F, Throwable]): S3Sink[F] = {
    //Helper function to prevent code drifting too far to the right
    def putObj(bucket: String, key: String, bytes: ByteBuffer, contentType: String): F[PutObjectResponse] = {
      val md5 = MessageDigest.getInstance("MD5").digest(bytes.array)
      val md5String = scodec.bits.ByteVector(md5).toBase64
      val req = PutObjectRequest.builder
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .contentMD5(md5String)
        .build

      client.putObject(
        req,
        bytes
      )
    }

    new S3Sink[F] {
      def writeBytes(bucket: String, key: String, contentType: String): Pipe[F, Byte, Unit] = { s =>
        s.chunkAll
          .map(_.toByteBuffer)
          .evalMap { allBytes =>
            putObj(bucket, key, allBytes, contentType)
          }
          .flatMap { res =>
            val statusCode = res.sdkHttpResponse().statusCode()
            val statusText = res.sdkHttpResponse().statusText().orElse("")
            if (statusCode >= 300 || statusCode < 200) {
              Stream.raiseError[F](new Exception(s"Status code is $statusCode for uploading object ($statusText)"))
            } else {
              Stream.empty
            }
          }
      }

      def writeText(bucket: String, key: String): fs2.Pipe[F, Byte, Unit] = writeBytes(bucket, key, "text/plain")
    }
  }

  /** Constructs an `S3Sink` using an underlying synchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Sink` instance using a synchronous backend.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.sync[F](blocker, awsRegion).map(apply[F])

  /** Constructs an `S3Sink` using an underlying asynchronous client backend.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return An `S3Sink` instance using an asynchronous backend.
    */
  def async[F[_]: Async: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.async[F](blocker, awsRegion).map(apply[F])
}
