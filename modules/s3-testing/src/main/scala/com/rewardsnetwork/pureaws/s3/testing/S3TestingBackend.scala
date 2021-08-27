package com.rewardsnetwork.pureaws.s3.testing

import java.time.Instant

import cats.data.OptionT
import cats.effect.Sync
import cats.effect.kernel.Ref
import cats.syntax.all._
import internal.newInstant
import S3TestingBackend._

/** Defines a basic backend implementation for testing S3 interface usage */
trait S3TestingBackend[F[_]] {

  /** If the payload exists, get it and its metadata as a `Map`. */
  def get(bucket: String, key: String): F[Option[(Metadata, Payload)]]

  /** Delete a bucket and all of its objects. */
  def deleteBucket(bucket: String): F[Unit]

  /** Delete an object from a bucket, if it exists. */
  def deleteObject(bucket: String, key: String): F[Unit]

  /** Create a new bucket. Not required if `autoCreateBuckets` is enabled. Spe */
  def newBucket(bucket: String, createdAt: Option[Instant] = none): F[Unit]

  /** Put an object in a particular bucket with configured metadata. */
  def put(bucket: String, key: String, payload: Array[Byte], metadata: Map[String, String] = Map.empty): F[Unit]

  /** Map of all buckets, containing a map of all keys in that bucket to associated metadata and payload information. */
  def getAll: F[BucketMap]
}

object S3TestingBackend {

  type Metadata = Map[String, String]
  type Payload = Array[Byte]
  type ObjectMap = Map[String, (Metadata, Payload)]
  type BucketMap = Map[String, (Instant, ObjectMap)]

  /** An in-memory fake S3 backend.
    *
    * @param autoCreateBuckets
    *   Whether or not to automatically assume there is a bucket for each new object uploaded. Defaults to `true`.
    * @return
    *   An `F[S3TestingBackend]` for plugging into other test utilities in the S3 Testing module.
    */
  def inMemory[F[_]: Sync](autoCreateBuckets: Boolean = true) =
    Ref[F].of[BucketMap](Map.empty).map { ref =>
      new S3TestingBackend[F] {
        private def getBucketObjects(bucket: String): F[Option[ObjectMap]] =
          ref.get.map(_.get(bucket).map(_._2))

        def get(bucket: String, key: String): F[Option[(Metadata, Payload)]] =
          OptionT(getBucketObjects(bucket)).flatMap(om => OptionT.fromOption(om.get(key))).value

        def deleteBucket(bucket: String): F[Unit] = ref.modify { buckets =>
          buckets.get(bucket) match {
            case None    => buckets -> Sync[F].raiseError[Unit](new Exception(s"Bucket $bucket does not exist"))
            case Some(_) => buckets - bucket -> Sync[F].unit
          }
        }.flatten

        def deleteObject(bucket: String, key: String): F[Unit] = ref.modify { buckets =>
          buckets.get(bucket) match {
            case None =>
              buckets -> Sync[F].raiseError[Unit](new Exception(s"Object $key does not exist in bucket $bucket"))
            case Some((instant, b)) =>
              b.get(key) match {
                case None =>
                  buckets -> Sync[F].raiseError[Unit](new Exception(s"Object $key does not exist in bucket $bucket"))
                case Some(_) =>
                  val newBuckets = buckets + (bucket -> (instant -> (b - key)))
                  newBuckets -> Sync[F].unit
              }
          }
        }.flatten

        def newBucket(bucket: String, createdAt: Option[Instant] = none): F[Unit] = {
          val getCreatedAt = OptionT.fromOption[F](createdAt).getOrElseF(newInstant[F])

          getCreatedAt.flatMap { instant =>
            ref.modify { buckets =>
              buckets.get(bucket) match {
                case Some(_) => buckets -> Sync[F].raiseError[Unit](new Exception(s"Bucket $bucket already exists"))
                case None =>
                  buckets + (bucket -> (instant -> Map
                    .empty[String, (Map[String, String], Array[Byte])])) -> Sync[F].unit
              }
            }.flatten
          }
        }

        def put(bucket: String, key: String, payload: Array[Byte], metadata: Map[String, String] = Map.empty): F[Unit] =
          newInstant[F].flatMap { instant =>
            ref.modify { buckets =>
              buckets.get(bucket) match {
                case None if (autoCreateBuckets) =>
                  buckets + (bucket -> (instant -> Map(key -> (metadata -> payload)))) -> Sync[F].unit
                case None => buckets -> Sync[F].raiseError[Unit](new Exception(s"Bucket $bucket does not exist"))
                case Some((instant, b)) =>
                  val newBuckets = buckets + (bucket -> (instant -> (b + (key -> (metadata -> payload)))))
                  newBuckets -> Sync[F].unit
              }
            }.flatten
          }

        def getAll: F[BucketMap] = ref.get
      }
    }
}
