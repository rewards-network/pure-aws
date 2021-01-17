package com.rewardsnetwork.pureaws.s3.testing

import cats.effect.Sync
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3Sink
import fs2.{Pipe, Stream}

class TestS3Sink[F[_]: Sync](backend: S3TestingBackend[F], failUploadWith: Option[Throwable] = none) extends S3Sink[F] {

  private def doOrFail[A](pipe: Pipe[F, Byte, Unit]): Pipe[F, Byte, Unit] = failUploadWith match {
    case Some(t) => _ >> Stream.raiseError[F](t)
    case None    => pipe
  }

  def writeText(bucket: String, key: String): Pipe[F, Byte, Unit] = { s =>
    s.chunkAll.map(_.toByteBuffer.array).evalMap(backend.put(bucket, key, _))
  }

  def writeTextMultipart(bucket: String, key: String, partSizeBytes: Int): fs2.Pipe[F, Byte, Unit] =
    writeText(bucket, key)

  def writeBytes(bucket: String, key: String, contentType: String): fs2.Pipe[F, Byte, Unit] = doOrFail {
    writeText(bucket, key)
  }

  def writeBytesMultipart(
      bucket: String,
      key: String,
      contentType: String,
      partSizeBytes: Int
  ): fs2.Pipe[F, Byte, Unit] = writeBytes(bucket, key, contentType)

}
