package com.rewardsnetwork.pureaws.s3

/** A type of permission that may be granted for an S3 bucket. */
sealed trait S3BucketPermission extends Product with Serializable

object S3BucketPermission {

  /** Permission to read objects in this bucket. */
  case object Read extends S3BucketPermission

  /** Permission to read the ACL of this bucket. */
  case object ReadACL extends S3BucketPermission

  /** Permission to write objects to this bucket. */
  case object Write extends S3BucketPermission

  /** Permissions to modify the ACL of this bucket. */
  case object WriteACL extends S3BucketPermission

  /** Permissions to read/write objects and the ACL of this bucket. */
  case object FullControl extends S3BucketPermission
}
