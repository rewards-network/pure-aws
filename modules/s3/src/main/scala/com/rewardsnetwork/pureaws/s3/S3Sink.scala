package com.rewardsnetwork.pureaws.s3

import java.nio.ByteBuffer
import java.security.MessageDigest

import cats.ApplicativeError
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model._

/** A helper for uploading S3 objects using FS2. */
trait S3Sink[F[_]] {

  /** Write the stream of bytes to an object at the specified path.
    * Content type is assumed to be "text/plain".
    *
    * The bytes are uploaded all at once, and buffered in-memory.
    * For large objects on the order of several MiB, consider doing a multipart upload.
    *
    * @param bucket The bucket of the object you are uploading to.
    * @param key The key of the object you are uploading to.
    * @return An FS2 `Pipe` for writing bytes to this new object.
    */
  def writeText(bucket: String, key: String): Pipe[F, Byte, Unit]

  /** Write the stream of bytes to an object at the specified path in multiple parts.
    * Content type is assumed to be "text/plain"
    *
    * Unlike `writeText`, which uploads everything at once, this uses the somewhat more complex S3 multipart upload feature.
    * You may specify a "part size" which is the number of bytes you will upload at once in a streaming fashion.
    * By default this is 5MiB or 5120 bytes, the minimum supported number in the SDK.
    *
    * If some error occurs during upload, the multipart request will automatically be aborted and the exception raised will bubble up to you.
    *
    * @param bucket The bucket of the object you are uploading to.
    * @param key The key of the object you are uploading to.
    * @param partSizeBytes The number of bytes (default 5120 or 5MiB) to upload per-part.
    * @return An FS2 `Pipe` for writing bytes to this new object.
    */
  def writeTextMultipart(bucket: String, key: String, partSizeBytes: Int = 5120): Pipe[F, Byte, Unit]

  /** Write the stream of bytes to an object at the specified path.
    *
    * The bytes are uploaded all at once, and buffered in-memory.
    * For large objects on the order of several MiB, consider doing a multipart upload.
    *
    * @param bucket The bucket of the object you are uploading to.
    * @param key The key of the object you are uploading to.
    * @param contentType The desired content type of the object being uploaded.
    * @return An FS2 `Pipe` for writing bytes to this new object.
    */
  def writeBytes(bucket: String, key: String, contentType: String): Pipe[F, Byte, Unit]

  /** Write the stream of bytes to an object at the specified path in multiple parts.
    * Unlike `writeBytes`, which uploads everything at once, this uses the somewhat more complex S3 multipart upload feature.
    * You may specify a "part size" which is the number of bytes you will upload at once in a streaming fashion.
    * By default this is 5MiB or 5120 bytes, the minimum supported number in the SDK.
    *
    * If some error occurs during upload, the multipart request will automatically be aborted and the exception raised will bubble up to you.
    *
    * @param bucket The bucket of the object you are uploading to.
    * @param key The key of the object you are uploading to.
    * @param contentType The desired content type of the object being uploaded.
    * @param partSizeBytes The number of bytes (default 5120 or 5MiB) to upload per-part.
    * @return An FS2 `Pipe` for writing bytes to this new object.
    */
  def writeBytesMultipart(
      bucket: String,
      key: String,
      contentType: String,
      partSizeBytes: Int = 5120
  ): Pipe[F, Byte, Unit]
}

object S3Sink {

  /** Creates a new `S3Sink` given an existing `PureS3Client`.
    */
  def apply[F[_]](client: PureS3Client[F])(implicit F: ApplicativeError[F, Throwable]): S3Sink[F] = {
    def md5String(bytes: Array[Byte]) =
      scodec.bits.ByteVector(MessageDigest.getInstance("MD5").digest(bytes)).toBase64

    //Helper function to prevent code drifting too far to the right
    def putObj(bucket: String, key: String, bytes: ByteBuffer, contentType: String): F[PutObjectResponse] = {
      val req = PutObjectRequest.builder
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .contentMD5(md5String(bytes.array))
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

      def writeBytesMultipart(
          bucket: String,
          key: String,
          contentType: String,
          partSizeBytes: Int = 5120
      ): Pipe[F, Byte, Unit] = { s =>
        val partNumStream = Stream.iterate(1)(_ + 1)
        val createMultipartReq =
          CreateMultipartUploadRequest.builder.bucket(bucket).key(key).contentType(contentType).build

        def uploadPartReq(bytes: ByteBuffer, partNumber: Int, uploadId: String) =
          UploadPartRequest
            .builder()
            .bucket(bucket)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .contentMD5(md5String(bytes.array))
            .build()

        def completedPart(etag: String, partNumber: Int) = CompletedPart.builder.eTag(etag).partNumber(partNumber).build

        Stream.eval(client.createMultipartUpload(createMultipartReq)).flatMap { res =>
          val uploadId = res.uploadId
          def completeUploadReq(parts: List[CompletedPart]) =
            CompleteMultipartUploadRequest.builder
              .bucket(bucket)
              .key(key)
              .uploadId(uploadId)
              .multipartUpload(CompletedMultipartUpload.builder.parts(parts: _*).build)
              .build

          s.chunkN(partSizeBytes).map(_.toByteBuffer).zip(partNumStream).flatMap { case (bytes, partNum) =>
            Stream
              .eval(client.uploadPart(uploadPartReq(bytes, partNum, uploadId), bytes))
              .map(partRes => completedPart(partRes.eTag, partNum))
              .fold(List.empty[CompletedPart])(_ :+ _)
              .map(completeUploadReq)
              .evalMap(client.completeMultipartUpload)
              .handleErrorWith { e =>
                val abortReq = AbortMultipartUploadRequest.builder.bucket(bucket).key(key).uploadId(uploadId).build
                Stream
                  .eval(client.abortMultipartUpload(abortReq) *> ApplicativeError[F, Throwable].raiseError[String](e))
              }
              .as(())
          }
        }
      }

      def writeText(bucket: String, key: String): fs2.Pipe[F, Byte, Unit] = writeBytes(bucket, key, "text/plain")

      def writeTextMultipart(bucket: String, key: String, partSizeBytes: Int = 5120): fs2.Pipe[F, Byte, Unit] =
        writeBytesMultipart(bucket, key, "text/plain", partSizeBytes)
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
  def async[F[_]: ConcurrentEffect: ContextShift](blocker: Blocker, awsRegion: Region) =
    PureS3Client.async[F](blocker, awsRegion).map(apply[F])
}
