package com.rewardsnetwork.pureaws.s3

import java.time.Instant

import software.amazon.awssdk.services.s3.model.S3Object

/** Information about an S3 object from a request to list objects.
  *
  * @param bucket The bucket the object was found in.
  * @param key The key of the object.
  * @param lastModified The `java.time.Instant` that this object was last modified.
  * @param eTag The `ETag`, or unique identifier, of this object.
  * @param ownerDisplayName The display name of the owner.
  * @param ownerId The ID of the owner.
  * @param sizeBytes The size of the object in bytes.
  */
final case class S3ObjectInfo(
    bucket: String,
    key: String,
    lastModified: Instant,
    eTag: String,
    ownerDisplayName: String,
    ownerId: String,
    sizeBytes: Long
)

object S3ObjectInfo {

  /** Turn an `S3Object` into an `S3ObjectInfo` given the bucket it came from.
    *
    * @param o The `S3Object` you are turning into an `S3ObjectInfo`.
    * @param bucket The bucket that the object came from.
    */
  def fromS3Object(o: S3Object, bucket: String) =
    S3ObjectInfo(bucket, o.key, o.lastModified, o.eTag, o.owner.displayName, o.owner.id, o.size)
}
