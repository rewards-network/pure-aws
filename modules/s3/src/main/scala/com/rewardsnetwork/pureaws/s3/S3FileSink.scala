package com.rewardsnetwork.pureaws.s3

import java.nio.ByteBuffer
import java.security.MessageDigest

import cats.ApplicativeError
import fs2.{Pipe, Stream}
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/** A helper for uploading files to an S3 object using FS2. */
trait S3FileSink[F[_]] {

  /** Write the stream of bytes to a file at the specified path. File is assumed to be text. */
  def writeText(bucket: String, key: String): Pipe[F, Byte, Unit]
}

object S3FileSink {

  /** Creates a new S3FileSink given an existing `S3Client`.
    * Note that you will have to close the client yourself when you are finished.
    */
  def apply[F[_]](client: PureS3Client[F])(implicit F: ApplicativeError[F, Throwable]): S3FileSink[F] = {
    //Helper function to prevent code drifting too far to the right
    def putFile(bucket: String, key: String, bytes: ByteBuffer): F[PutObjectResponse] = {
      val md5 = MessageDigest.getInstance("MD5").digest(bytes.array)
      val md5String = scodec.bits.ByteVector(md5).toBase64
      val req = PutObjectRequest.builder
        .bucket(bucket)
        .key(key)
        .contentType("text/plain")
        .contentMD5(md5String)
        .build

      client.putObject(
        req,
        bytes
      )
    }

    new S3FileSink[F] {
      def writeText(bucket: String, key: String): Pipe[F, Byte, Unit] = { s =>
        s.chunkAll
          .map(_.toByteBuffer)
          .evalMap { allBytes =>
            putFile(bucket, key, allBytes)
          }
          .flatMap { res =>
            val statusCode = res.sdkHttpResponse().statusCode()
            val statusText = res.sdkHttpResponse().statusText().orElse("")
            if (statusCode >= 300 || statusCode < 200) {
              Stream.raiseError[F](new Exception(s"Status code is $statusCode for submitting file ($statusText)"))
            } else {
              Stream.empty
            }
          }
      }
    }
  }
}
