package com.rewardsnetwork.pureaws.s3

/** A listing of S3 objects and all common prefixes between them.
  * A "common prefix" in this case is any delimited part of an S3 object key that has no data, and acts like a directory.
  * For example if you have a key `foo/bar/baz` then `foo/bar` would be a common prefix, as there is no data at that key.
  */
final case class S3ObjectListing(objects: List[S3ObjectInfo], commonPrefixes: List[String])
