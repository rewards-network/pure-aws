package com.rewardsnetwork.pureaws.s3.testing

import cats.effect.Sync
import cats.syntax.all._
import com.rewardsnetwork.pureaws.s3.S3Sink
import fs2.{Pipe, Stream}

/** A test utility for integrating with the `S3Sink` algebra.
  *
  * @param backend
  *   Your `S3TestingBackend`.
  * @param failWith
  *   An optional `Throwable` that you would like all requests to fail with, to test error recovery.
  * @param emitEtag
  *   An iterator of strings for sequentially assigning eTags. Defaults to the sequence 0, 1, 2... as strings.
  */
class TestS3Sink[F[_]: Sync](
    backend: S3TestingBackend[F],
    failWith: Option[Throwable] = none,
    emitEtag: Iterator[String] = Iterator.from(0).map(_.toString)
) extends S3Sink[F] {

  private def doOrFail[A](pipe: Pipe[F, Byte, String]): Pipe[F, Byte, String] = failWith match {
    case Some(t) => _ >> Stream.raiseError[F](t)
    case None    => pipe
  }

  def writeText(bucket: String, key: String): Pipe[F, Byte, String] = doOrFail { s =>
    s.chunkAll.map(_.toByteBuffer.array).evalMap(backend.put(bucket, key, _)).as(emitEtag.next())
  }

  def writeTextMultipart(bucket: String, key: String, partSizeBytes: Int): fs2.Pipe[F, Byte, String] =
    writeText(bucket, key)

  def writeBytes(bucket: String, key: String, contentType: String): fs2.Pipe[F, Byte, String] =
    writeText(bucket, key)

  def writeBytesMultipart(
      bucket: String,
      key: String,
      contentType: String,
      partSizeBytes: Int
  ): fs2.Pipe[F, Byte, String] = writeText(bucket, key)

}
