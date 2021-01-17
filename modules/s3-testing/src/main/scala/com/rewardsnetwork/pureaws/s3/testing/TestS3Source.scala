package com.rewardsnetwork.pureaws.s3.testing

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3Source
import fs2.Stream

class TestS3Source[F[_]: Sync](backend: S3TestingBackend[F]) extends S3Source[F] {
  def readObject(bucket: String, key: String): Stream[F, Byte] =
    Stream.eval(readObjectWithMetadata(bucket, key)).flatMap(_._2)

  def readWholeObject(bucket: String, key: String)(implicit F: Applicative[F]): F[Array[Byte]] =
    readObject(bucket, key).compile.to(Array)

  def readObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Stream[F, Byte])] =
    backend.get(bucket, key).flatMap {
      case Some((meta, payload)) => (meta -> Stream.emits(payload.toList).covary[F]).pure[F]
      case None                  => Sync[F].raiseError(new Exception("Object not found"))
    }

  def readWholeObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Array[Byte])] =
    readObjectWithMetadata(bucket, key).flatMap { case (meta, stream) => stream.compile.to(Array).tupleLeft(meta) }
}
