package com.rewardsnetwork.pureaws.s3.testing

import cats.effect.Sync
import com.rewardsnetwork.pureaws.s3.S3Sink
import fs2.Pipe

/** A test utility for integrating with the `S3Sink` algebra.
  *
  * @param backend Your `S3TestingBackend`.
  * @param emitEtag An iterator of strings for sequentially assigning eTags.
  */
class TestS3Sink[F[_]: Sync](
    backend: S3TestingBackend[F],
    emitEtag: Iterator[String] = Iterator.from(0).map(_.toString)
) extends S3Sink[F] {

  def writeText(bucket: String, key: String): Pipe[F, Byte, String] = { s =>
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
