package com.rewardsnetwork.pureaws

import java.nio.ByteBuffer

import cats.effect.IO
// import cats.syntax.all._
import fs2.interop.reactivestreams._
import software.amazon.awssdk.core.internal.async.SdkPublishers

import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.effect.PropF

class Fs2AsyncResponseTransformerSpec extends CatsEffectSuite with ScalaCheckEffectSuite {

  // override val scalaCheckInitialSeed = "N970TxTfAj2MWiu6urxwnQmBIldO1GjEWBUVtC8LGpF="

  test("provide the stream of bytes") {
    PropF.forAllF { (exampleStr: String) =>
      val exampleStrBytes = exampleStr.getBytes
      val exampleStrByteBuf = ByteBuffer.wrap(exampleStrBytes)
      val fs2publisher = fs2.Stream.emit[IO, ByteBuffer](exampleStrByteBuf).toUnicastPublisher

      fs2publisher.use { p =>
        val bufferSize = 1
        val transformer = Fs2AsyncResponseTransformer[IO, String](bufferSize)
        val sdkPublisher = SdkPublishers.envelopeWrappedPublisher(p, "", "")
        val prep = IO {
          val cf = transformer.prepare

          // Use the transformer the way the SDK would
          transformer.onResponse(exampleStr)
          transformer.onStream(sdkPublisher)

          cf
        }

        // Get and test results
        IO.fromCompletableFuture(prep).flatMap { case (str, byteStream) =>
          assertEquals(str, exampleStr)
          byteStream.compile
            .to(Array)
            .map(b => assert(b.sameElements(exampleStrBytes)))
        }
      }
    }
  }
}
