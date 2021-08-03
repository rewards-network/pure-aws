package com.rewardsnetwork.pureaws.s3.testing

import java.time.Instant

import cats.Traverse
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import cats.syntax.all._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen
import com.rewardsnetwork.pureaws.s3.{S3ObjectInfo, S3ObjectListing}

class TestS3ObjectOpsSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {
  implicit val iort = IORuntime.global

  "TestS3ObjectOps" - {
    "listObjectsPaginated" - {
      "should paginate objects and common prefixes" in {
        forAll(Gen.alphaStr, Gen.alphaStr, Gen.alphaStr) { (preFirst, preSecond, preThird) =>
          //Ensure the inputs for prefixes are non-empty
          val (first, second, third) = (s"first-$preFirst", s"second-$preSecond", s"third-$preThird")

          val bucket = "test-bucket"
          val program = S3TestingBackend.inMemory[IO]().flatMap { backend =>
            val objOps = new TestS3ObjectOps(backend)
            val objIds = (0 to 100).toList
            val prepS3 = objIds.traverse { i =>
              backend.put(bucket, s"$first/$second/$third/$i.txt", Array.emptyByteArray)
            }

            //Should return all objects 0 to 100 as the prefix is exactly the same common prefix
            val listPaginated =
              objOps.listObjectsPaginated(bucket, 10, "/".some, s"$first/$second/$third/".some).compile.toList

            //A prefix of only the first two dirs is not a "common prefix", so results are empty
            //It should still return common prefixes starting with this string, but no objects.
            val listPaginatedEmpty =
              objOps.listObjectsPaginated(bucket, 10, "/".some, s"$first/$second/".some).compile.toList

            //Normal results should have up to 10 objects (101 in total) and share the same common prefix
            val expectedResults = objIds
              .sortBy(i => s"$first/$second/$third/$i.txt")
              .map { i =>
                val info = S3ObjectInfo(bucket, s"$first/$second/$third/$i.txt", Instant.EPOCH, "", None, 0L)
                S3ObjectListing(List(info), Set(s"$first/$second/$third/"))
              }
              .sliding(10, 10)
              .map(Traverse[List].fold(_))
              .toList

            //Should be 11 instances of the same prefix with no objects
            val expectedEmptyResults = List.fill(11)(S3ObjectListing(Nil, Set(s"$first/$second/$third/")))

            val results = prepS3 >> listPaginated.product(listPaginatedEmpty)

            results.flatMap { case (mainResults, emptyResults) =>
              val mainAssertion = IO(mainResults shouldBe expectedResults)
              val emptyAssertion = IO(emptyResults shouldBe expectedEmptyResults)
              mainAssertion >> emptyAssertion
            }
          }

          program.unsafeRunSync()
        }
      }
    }
  }
}
