package com.rewardsnetwork.pureaws

import cats.effect.IO
import fs2.interop.reactivestreams._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.core.internal.async.SdkPublishers

import scala.concurrent.ExecutionContext.global
import java.nio.ByteBuffer

class Fs2AsyncResponseTransformerSpec extends AnyFreeSpec with Matchers {
  implicit val cs = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  "Fs2AsyncResponseTransformer" - {
    "should provide the stream of bytes to the user" in {
      val exampleStr = "Hello world!"
      val exampleStrBytes = exampleStr.getBytes
      val exampleStrByteBuf = ByteBuffer.wrap(exampleStrBytes)
      val transformer = Fs2AsyncResponseTransformer[IO, String]
      val fs2publisher = fs2.Stream.emit[IO, ByteBuffer](exampleStrByteBuf).toUnicastPublisher
      val sdkPublisher = SdkPublishers.envelopeWrappedPublisher(fs2publisher, "", "")
      val cf = transformer.prepare()

      //Use the transformer the way the SDK would
      transformer.onResponse(exampleStr)
      transformer.onStream(sdkPublisher)

      //Get and test results
      val (str, byteStream) = cf.get()
      str shouldBe exampleStr
      byteStream.compile.to(Array).unsafeRunSync() shouldBe exampleStrBytes
    }
  }
}
