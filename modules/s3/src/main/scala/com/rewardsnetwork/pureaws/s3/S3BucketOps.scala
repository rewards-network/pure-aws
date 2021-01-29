package com.rewardsnetwork.pureaws.s3

import cats.Functor
import cats.implicits._
import com.rewardsnetwork.pureaws.s3.S3BucketPermission._
import software.amazon.awssdk.services.s3.model._

import scala.jdk.CollectionConverters._

trait S3BucketOps[F[_]] {

  /** Create an S3 bucket with the given parameters.
    *
    * @param name The name of the bucket.
    * @param location The AWS region (as a `BucketLocationConstraint`) that the bucket should be created in.
    * @param acl The Access Control List for this bucket.
    * @param permissions Pairs of grantees and their S3BucketPermissions. Optional.
    * @return `Unit` if successful, will throw if failed.
    */
  def createBucket(
      name: String,
      location: BucketLocationConstraint,
      acl: BucketCannedACL,
      permissions: List[(String, S3BucketPermission)] = Nil
  ): F[Unit]

  /** Delete an S3 bucket with the given parameters.
    *
    * @param name The name of the bucket.
    * @param expectedBucketOwner The expected owner of the bucket, if needed.
    * @return `Unit` if successful, will throw if failed.
    */
  def deleteBucket(name: String, expectedBucketOwner: Option[String] = none): F[Unit]

  /** Returns a list of available S3 buckets.
    *
    * @return A list of buckets indicating what buckets you have access to and when they were created.
    */
  def listBuckets: F[List[S3BucketInfo]]
}

object S3BucketOps {
  def apply[F[_]: Functor](client: PureS3Client[F]): S3BucketOps[F] = new S3BucketOps[F] {
    def createBucket(
        name: String,
        location: BucketLocationConstraint,
        acl: BucketCannedACL,
        permissions: List[(String, S3BucketPermission)] = Nil
    ): F[Unit] = {
      val bucketConfig = CreateBucketConfiguration.builder.locationConstraint(location).build
      val initialReq = CreateBucketRequest.builder.bucket(name).acl(acl).createBucketConfiguration(bucketConfig)
      val finalReq = permissions
        .foldRight(initialReq) { case ((name, permissions), req) =>
          permissions match {
            case Read        => req.grantRead(name)
            case ReadACL     => req.grantReadACP(name)
            case Write       => req.grantWrite(name)
            case WriteACL    => req.grantWriteACP(name)
            case FullControl => req.grantFullControl(name)
          }
        }
        .build

      client.createBucket(finalReq).void
    }

    def deleteBucket(name: String, expectedBucketOwner: Option[String] = none): F[Unit] = {
      val initialReq = DeleteBucketRequest.builder.bucket(name)
      val finalReq = expectedBucketOwner.fold(initialReq)(initialReq.expectedBucketOwner).build

      client.deleteBucket(finalReq).void
    }

    def listBuckets: F[List[S3BucketInfo]] =
      client
        .listBuckets()
        .map(_.buckets.asScala.toList)
        .map(_.map(bucket => S3BucketInfo(bucket.name, bucket.creationDate)))

  }
}
