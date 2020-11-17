package com.rewardsnetwork.pureaws.s3.testing

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3FileSource
import fs2.Stream

class TestS3FileSource[F[_]: Sync](backend: S3Backend[F]) extends S3FileSource[F] {
  def readFile(bucket: String, key: String): Stream[F, Byte] =
    Stream.eval(readFileWithMetadata(bucket, key)).flatMap(_._2)
  def readFileWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Stream[F, Byte])] =
    backend.get(bucket, key).flatMap {
      case Some((meta, payload)) => (meta -> Stream.emits(payload.toList).covary[F]).pure[F]
      case None                  => Sync[F].raiseError(new Exception("File not found"))
    }
}
