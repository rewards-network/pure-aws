package com.rewardsnetwork.pureaws.s3

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import cats.effect._
import monix.catnap.syntax._
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Client}
import software.amazon.awssdk.services.s3.model._

trait PureS3Client[F[_]] {
  def getObjectBytes(r: GetObjectRequest): F[ResponseBytes[GetObjectResponse]]
  def putObject(r: PutObjectRequest, body: ByteBuffer): F[PutObjectResponse]
  def copyObject(r: CopyObjectRequest): F[CopyObjectResponse]
  def deleteObject(r: DeleteObjectRequest): F[DeleteObjectResponse]
}

object PureS3Client {
  def apply[F[_]: Sync: ContextShift](blocker: Blocker, client: S3Client) =
    new PureS3Client[F] {
      private def block[A](f: => A): F[A] = blocker.blockOn(Sync[F].delay(f))

      def getObjectBytes(r: GetObjectRequest): F[ResponseBytes[GetObjectResponse]] =
        block(client.getObjectAsBytes(r))

      def putObject(r: PutObjectRequest, body: ByteBuffer): F[PutObjectResponse] =
        block(client.putObject(r, RequestBody.fromByteBuffer(body)))

      def copyObject(r: CopyObjectRequest): F[CopyObjectResponse] =
        block(client.copyObject(r))

      def deleteObject(r: DeleteObjectRequest): F[DeleteObjectResponse] =
        block(client.deleteObject(r))

    }

  def apply[F[_]: Async](client: S3AsyncClient) =
    new PureS3Client[F] {
      private def lift[A](f: => CompletableFuture[A]): F[A] = Sync[F].delay(f).futureLift

      def getObjectBytes(r: GetObjectRequest): F[ResponseBytes[GetObjectResponse]] =
        lift(client.getObject(r, AsyncResponseTransformer.toBytes[GetObjectResponse]()))

      def putObject(r: PutObjectRequest, body: ByteBuffer): F[PutObjectResponse] =
        lift(client.putObject(r, AsyncRequestBody.fromByteBuffer(body)))

      def copyObject(r: CopyObjectRequest): F[CopyObjectResponse] =
        lift(client.copyObject(r))

      def deleteObject(r: DeleteObjectRequest): F[DeleteObjectResponse] =
        lift(client.deleteObject(r))

    }
}
