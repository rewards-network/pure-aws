package com.rewardsnetwork.pureaws.s3.testing

import cats.effect.Sync
import com.rewardsnetwork.pureaws.s3.S3FileSink

import fs2.Pipe

class TestS3FileSink[F[_]: Sync](backend: S3Backend[F]) extends S3FileSink[F] {
  def writeText(bucket: String, path: String): Pipe[F, Byte, Unit] = { s =>
    s.chunkAll.map(_.toByteBuffer.array).evalMap(backend.put(bucket, path, _))
  }

}
