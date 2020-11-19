package com.rewardsnetwork.pureaws.s3

import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

import cats.effect._
import monix.catnap.syntax._
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Client}
import software.amazon.awssdk.services.s3.model._

/** A pure-functional wrapper for the AWS S3 client.
  * Supports a common subset of operations between sync and async client backends.
  */
trait PureS3Client[F[_]] {

  /** Gets a requested object as raw bytes.
    *
    * @param r A `GetObjectRequest` with the bucket, key, and other parameters for your object.
    * @return `ResponseBytes` of your object if successful along with a `GetObjectResponse` indicating your response status.
    */
  def getObjectBytes(r: GetObjectRequest): F[ResponseBytes[GetObjectResponse]]

  /** Puts the requested byte buffer in S3 as an object.
    *
    * @param r A `PutObjectRequest` with the bucket, key, and other parameters for your object.
    * @param body A `ByteBuffer` containing the raw bytes of your object.
    * @return A `PutObjectResponse` indicating the status of your request.
    */
  def putObject(r: PutObjectRequest, body: ByteBuffer): F[PutObjectResponse]

  /** Copies an object from one location to another.
    *
    * @param r A `CopyObjectRequest` with the buckets, keys, and other parameters for your object being copied.
    * @return A `CopyObjectResponse` indicating the status of your request.
    */
  def copyObject(r: CopyObjectRequest): F[CopyObjectResponse]

  /** Deletes an object from S3.
    *
    * @param r A `DeleteObjectRequest` with the bucket, key, and other parameters for your object being deleted.
    * @return A `DeleteObjectRequest` indicating the status of your request.
    */
  def deleteObject(r: DeleteObjectRequest): F[DeleteObjectResponse]
}

object PureS3Client {

  /** Builds a PureS3Client from an AWS SDK `S3Client`.
    *
    * @param blocker A Cats Effect `Blocker` for handling potentially blocking operations.
    * @param client A synchronous `S3Client` directly from the AWS SDK.
    * @return A shiny new `PureS3Client` with a synchronous backend.
    */
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

  /** Builds a `PureS3Client` from an AWS SDK `S3AsyncClient`.
    *
    * @param client An asynchronous `S3AsyncClient` directly from the AWS SDK.
    * @return A shiny new `PureS3Client` with an asynchronous backend.
    */
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

  /** Creates a `PureS3Client` using a synchronous backend with default settings.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return A `Resource` containing a `PureS3Client` using a synchronous backend.
    */
  def sync[F[_]: Sync: ContextShift](blocker: Blocker, awsRegion: Region): Resource[F, PureS3Client[F]] =
    S3ClientBackend.sync[F](blocker, awsRegion)().map(apply[F](blocker, _))

  /** Creates a `PureS3Client` using an asynchronous backend with default settings.
    *
    * @param blocker A Cats Effect `Blocker`.
    * @param awsRegion The AWS region you are operating in.
    * @return A `Resource` containing a `PureS3Client` using an asynchronous backend.
    */
  def async[F[_]: Async: ContextShift](blocker: Blocker, awsRegion: Region): Resource[F, PureS3Client[F]] =
    S3ClientBackend.async[F](blocker, awsRegion)().map(apply[F](_))
}
