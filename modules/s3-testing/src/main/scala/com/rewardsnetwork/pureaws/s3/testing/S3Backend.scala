package com.rewardsnetwork.pureaws.s3.testing

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._

/** Defines a basic backend implementation for testing S3 interface usage */
trait S3Backend[F[_]] {

  /** If the payload exists, get it and its metadata as a `Map`. */
  def get(bucket: String, key: String): F[Option[(Map[String, String], Array[Byte])]]
  def delete(bucket: String, key: String): F[Unit]
  def put(bucket: String, key: String, payload: Array[Byte], metadata: Map[String, String] = Map.empty): F[Unit]

  /** Key of map is is (bucket, key), results are (metadata, payload) */
  def getAll: F[Map[(String, String), (Map[String, String], Array[Byte])]]
}

object S3Backend {
  def inMemory[F[_]: Sync] =
    Ref[F].of(Map.empty[(String, String), (Map[String, String], Array[Byte])]).map { ref =>
      new S3Backend[F] {
        def get(bucket: String, key: String): F[Option[(Map[String, String], Array[Byte])]] =
          ref.get.map(_.get(bucket -> key))

        def delete(bucket: String, key: String): F[Unit] =
          ref.update(_ - (bucket -> key))

        def put(bucket: String, key: String, payload: Array[Byte], metadata: Map[String, String] = Map.empty): F[Unit] =
          ref.update(_ + ((bucket -> key) -> (metadata -> payload)))

        def getAll: F[Map[(String, String), (Map[String, String], Array[Byte])]] = ref.get
      }
    }
}
