package com.rewardsnetwork.pureaws.s3

import cats.kernel.Monoid

/** A listing of S3 objects and all common prefixes between them. A "common prefix" in this case is any delimited part
  * of an S3 object key that has no data, and acts like a directory. For example if you have a key `foo/bar/baz` then
  * `foo/bar` would be a common prefix, as there is no data at that key.
  *
  * There is also a `Monoid` instance for this type, or you can combine listings using the `++` operator.
  */
final case class S3ObjectListing(objects: List[S3ObjectInfo], commonPrefixes: Set[String]) {

  /** Combine all S3 objects and prefixes from a paginated request together into one larger listing. */
  def ++(ol: S3ObjectListing) = copy(
    objects = objects ++ ol.objects,
    commonPrefixes = commonPrefixes ++ ol.commonPrefixes
  )
}

object S3ObjectListing {
  implicit val monoid: Monoid[S3ObjectListing] = new Monoid[S3ObjectListing] {
    def combine(x: S3ObjectListing, y: S3ObjectListing): S3ObjectListing = x ++ y
    def empty: S3ObjectListing = S3ObjectListing(Nil, Set.empty)
  }
}
