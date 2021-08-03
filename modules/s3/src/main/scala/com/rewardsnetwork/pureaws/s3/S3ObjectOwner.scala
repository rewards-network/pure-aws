package com.rewardsnetwork.pureaws.s3

import software.amazon.awssdk.services.s3.model.Owner

/** Information about an S3 object's owner from a request to list objects.
  *
  * @param ownerDisplayName The display name of the owner.
  * @param ownerId The ID of the owner.
  */
final case class S3ObjectOwner(ownerDisplayName: String, ownerId: String)

object S3ObjectOwner {

  /** Turn an `s3.model.Owner` into an `S3ObjectOwner` given owner of the object.
    *
    * @param owner The `s3.model.Owner` you are turning into an `S3ObjectOwner`.
    */
  def apply(owner: Option[Owner]): Option[S3ObjectOwner] =
    owner.map(owner => S3ObjectOwner(owner.displayName(), owner.id()))
}
