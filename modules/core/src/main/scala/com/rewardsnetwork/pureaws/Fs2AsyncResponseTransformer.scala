package com.rewardsnetwork.pureaws

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import cats.effect.kernel.Async
import fs2.{Chunk, Stream}
import software.amazon.awssdk.core.async.{AsyncResponseTransformer, SdkPublisher}

/** An implementation of the `AsyncResponseTransformer` interface, but using FS2 Stream */
trait Fs2AsyncResponseTransformer[F[_], A] extends AsyncResponseTransformer[A, (A, Stream[F, Byte])] {
  def prepare(): CompletableFuture[(A, Stream[F, Byte])]

  def onResponse(x: A): Unit

  def onStream(x: SdkPublisher[ByteBuffer]): Unit

  def exceptionOccurred(x: Throwable): Unit
}

object Fs2AsyncResponseTransformer {

  /** Creates an `Fs2AsyncResponseTransformer` that returns your response object as well as a stream of bytes. */
  def apply[F[_]: Async, A]: Fs2AsyncResponseTransformer[F, A] =
    new Fs2AsyncResponseTransformer[F, A] {

      private val cf: CompletableFuture[Stream[F, Byte]] = new CompletableFuture[Stream[F, Byte]]()

      private val response: CompletableFuture[A] = new CompletableFuture[A]()

      def prepare(): CompletableFuture[(A, Stream[F, Byte])] = {
        response.thenCompose(a => cf.thenApply(s => (a, s)))
      }

      def onResponse(x: A): Unit = {
        val _ = response.complete(x)
      }

      def onStream(x: SdkPublisher[ByteBuffer]): Unit = {
        val publish = fs2.interop.reactivestreams
          .fromPublisher[F, ByteBuffer](x)
          .map(Chunk.byteBuffer)
          .flatMap(Stream.chunk)
        val _ = cf.complete(publish)
        ()
      }

      def exceptionOccurred(x: Throwable): Unit = {
        val _ = cf.completeExceptionally(x)
      }
    }
}
