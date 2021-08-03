package com.rewardsnetwork.pureaws.s3.testing

import cats.Applicative
import cats.effect.Sync
import cats.syntax.all._
import com.rewardsnetwork.pureaws.s3.S3Source
import fs2.Stream

/** A test utility for integrating with the `S3Source` algebra.
  *
  * @param backend Your `S3TestingBackend`.
  * @param failWith An optional `Throwable` that you would like all requests to fail with, to test error recovery.
  */
class TestS3Source[F[_]: Sync](backend: S3TestingBackend[F], failWith: Option[Throwable] = none) extends S3Source[F] {
  private def doOrFail[A](fa: F[A]): F[A] = failWith match {
    case Some(t) => Sync[F].raiseError(t)
    case None    => fa
  }

  private def doOrFailStream[A](fa: Stream[F, A]): Stream[F, A] = failWith match {
    case Some(t) => Stream.raiseError[F](t)
    case None    => fa
  }

  def readObject(bucket: String, key: String): Stream[F, Byte] = doOrFailStream {
    Stream.eval(readObjectWithMetadata(bucket, key)).flatMap(_._2)
  }

  def readWholeObject(bucket: String, key: String)(implicit F: Applicative[F]): F[Array[Byte]] = doOrFail {
    readObject(bucket, key).compile.to(Array)
  }

  def readObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Stream[F, Byte])] = doOrFail {
    backend.get(bucket, key).flatMap {
      case Some((meta, payload)) => (meta -> Stream.emits(payload.toList).covary[F]).pure[F]
      case None                  => Sync[F].raiseError(new Exception("Object not found"))
    }
  }

  def readWholeObjectWithMetadata(bucket: String, key: String)(implicit
      F: Applicative[F]
  ): F[(Map[String, String], Array[Byte])] = doOrFail {
    readObjectWithMetadata(bucket, key).flatMap { case (meta, stream) => stream.compile.to(Array).tupleLeft(meta) }
  }
}
