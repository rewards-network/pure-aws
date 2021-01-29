package com.rewardsnetwork.pureaws.s3.testing

import cats._
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3ObjectOps
import com.rewardsnetwork.pureaws.s3.S3ObjectInfo
import software.amazon.awssdk.services.s3.model.RequestPayer
import java.time.Instant
import com.rewardsnetwork.pureaws.s3.S3ObjectListing

/** A test utility for integrating with the `S3ObjectOps` algebra.
  *
  * @param backend Your `S3TestingBackend`.
  * @param failWith An optional `Throwable` that you would like all requests to fail with, to test error recovery.
  */
class TestS3ObjectOps[F[_]](backend: S3TestingBackend[F], failWith: Option[Throwable] = none)(implicit
    F: MonadError[F, Throwable]
) extends S3ObjectOps[F] {

  private def doOrFail[A](fa: F[A]): F[A] = failWith match {
    case Some(t) => F.raiseError(t)
    case None    => fa
  }

  def copyObject(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = doOrFail {
    backend.get(oldBucket, oldKey).flatMap {
      case None              => F.raiseError(new Exception("Object not found"))
      case Some((meta, obj)) => backend.put(newBucket, newKey, obj, meta)
    }
  }

  def deleteObject(bucket: String, key: String): F[Unit] = doOrFail {
    backend.deleteObject(bucket, key)
  }

  def moveObject(oldBucket: String, oldKey: String, newBucket: String, newKey: String): F[Unit] = doOrFail {
    copyObject(oldBucket, oldKey, newBucket, newKey) >> deleteObject(oldBucket, oldKey)
  }

  /** `expectedBucketOwner` and `requestPayer` are ignored.
    * All object parameters besides bucket and key are faked and should not be relied upon.
    * The list of common
    */
  def listObjects(
      bucket: String,
      delimiter: Option[String],
      prefix: Option[String],
      expectedBucketOwner: Option[String],
      requestPayer: Option[RequestPayer]
  ): F[S3ObjectListing] = doOrFail {
    backend.getAll.flatMap { bm =>
      bm.get(bucket) match {
        case None => F.raiseError(new Exception(s"Bucket $bucket does not exist"))
        case Some((_, objects)) =>
          val objs = objects.toList.map { case (key, (_, payload)) =>
            S3ObjectInfo(bucket, key, Instant.EPOCH, "", "", "", payload.length.toLong)
          }
          val resultsForDelimitedPrefix: Option[(List[S3ObjectInfo], Set[String])] = for {
            d <- delimiter
            p <- prefix
          } yield {
            val splitObjs: List[(Array[String], S3ObjectInfo)] = objs.map(o => o.key.split(d) -> o)
            val prefixes: Set[String] = splitObjs.mapFilter { case (splitKey, _) =>
              val keyPrefix: String = splitKey.dropRight(1).mkString("", d, d)
              if (keyPrefix.startsWith(p)) keyPrefix.some
              else none
            }.toSet
            val filteredObjs: List[S3ObjectInfo] = splitObjs.mapFilter { case (splitKey, obj) =>
              val keyPrefix = splitKey.dropRight(1).mkString("", d, d)
              if (keyPrefix == p) obj.some
              else none
            }

            filteredObjs -> prefixes
          }

          resultsForDelimitedPrefix
            .map { case (o, p) => S3ObjectListing(o, p) }
            .getOrElse(
              S3ObjectListing(objs, Set.empty)
            )
            .pure[F]

      }
    }
  }

}
