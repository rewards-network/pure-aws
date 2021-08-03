package com.rewardsnetwork.pureaws

import java.nio.ByteBuffer

import cats.effect.IO
import fs2.interop.reactivestreams._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.core.internal.async.SdkPublishers

import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import cats.effect.unsafe.IORuntime

class Fs2AsyncResponseTransformerSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {
  implicit val iort = IORuntime.global

  "Fs2AsyncResponseTransformer" - {
    "should provide the stream of bytes to the user" in {
      forAll { exampleStr: String =>
        val exampleStrBytes = exampleStr.getBytes
        val exampleStrByteBuf = ByteBuffer.wrap(exampleStrBytes)
        val transformer = Fs2AsyncResponseTransformer[IO, String]
        val fs2publisher = fs2.Stream.emit[IO, ByteBuffer](exampleStrByteBuf).toUnicastPublisher

        val program = fs2publisher.use { p =>
          val sdkPublisher = SdkPublishers.envelopeWrappedPublisher(p, "", "")
          val cf = transformer.prepare()

          //Use the transformer the way the SDK would
          transformer.onResponse(exampleStr)
          transformer.onStream(sdkPublisher)

          //Get and test results
          val (str, byteStream) = cf.get()
          str shouldBe exampleStr
          byteStream.compile
            .to(Array)
            .flatMap(bytes =>
              IO {
                bytes shouldBe exampleStrBytes
              }
            )
        }

        program.unsafeRunSync()
      }
    }
  }
}
