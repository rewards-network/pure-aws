package com.rewardsnetwork.pureaws.s3

import java.time.Instant

import software.amazon.awssdk.services.s3.model.S3Object

final case class S3ObjectInfo(
    bucket: String,
    key: String,
    lastModified: Instant,
    eTag: String,
    ownerDisplayName: String,
    ownerId: String
)

object S3ObjectInfo {
  def fromObject(o: S3Object, bucket: String) =
    S3ObjectInfo(bucket, o.key, o.lastModified, o.eTag, o.owner.displayName, o.owner.id)
}
