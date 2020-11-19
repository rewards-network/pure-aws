package com.rewardsnetwork.pureaws.s3.testing

import cats.effect.Sync
import com.rewardsnetwork.pureaws.s3.S3Sink

import fs2.Pipe

class TestS3FileSink[F[_]: Sync](backend: S3TestingBackend[F]) extends S3Sink[F] {
  def writeText(bucket: String, key: String): Pipe[F, Byte, Unit] = { s =>
    s.chunkAll.map(_.toByteBuffer.array).evalMap(backend.put(bucket, key, _))
  }

  def writeBytes(bucket: String, key: String, contentType: String): fs2.Pipe[F, Byte, Unit] = writeText(bucket, key)

}
