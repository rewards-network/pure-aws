package com.rewardsnetwork.pureaws.s3

import cats.effect._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3AsyncClient

/** Contains useful builders for an Amazon S3 Client */
object S3Builders {

  /** Builds an AWS S3 Client (Sync) with the specified region */
  def clientResource[F[_]: Sync: ContextShift](awsRegion: Region, blocker: Blocker): Resource[F, S3Client] =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      S3Client.builder().region(awsRegion).build()
    })

  /** Builds an AWS S3 Client (Async) with the specified region */
  def clientResourceAsync[F[_]: Sync: ContextShift](awsRegion: Region, blocker: Blocker): Resource[F, S3AsyncClient] =
    Resource.fromAutoCloseableBlocking(blocker)(Sync[F].delay {
      S3AsyncClient.builder().region(awsRegion).build()
    })

  def pureClientResource[F[_]: Sync: ContextShift](awsRegion: Region, blocker: Blocker): Resource[F, PureS3Client[F]] =
    clientResource[F](awsRegion, blocker).map(c => PureS3Client(blocker, c))

  def pureClientResourceAsync[F[_]: Async: ContextShift](
      awsRegion: Region,
      blocker: Blocker
  ): Resource[F, PureS3Client[F]] =
    clientResourceAsync[F](awsRegion, blocker).map(PureS3Client.apply[F])

  /** Get a file source for S3 using an underlying S3 Client */
  def fileSource[F[_]: Sync: ContextShift](awsRegion: Region, blocker: Blocker) =
    pureClientResource(awsRegion, blocker).map(S3FileSource[F])

  /** Get a file source for S3 using an underlying S3 Async Client */
  def fileSourceAsync[F[_]: Async: ContextShift](awsRegion: Region, blocker: Blocker) =
    pureClientResourceAsync(awsRegion, blocker).map(S3FileSource[F])

  /** Get a file source for S3 using an underlying S3 Client */
  def fileSink[F[_]: Sync: ContextShift](awsRegion: Region, blocker: Blocker) =
    pureClientResource(awsRegion, blocker).map(S3FileSink[F])

  /** Get a file source for S3 using an underlying S3 Async Client */
  def fileSinkAsync[F[_]: Async: ContextShift](awsRegion: Region, blocker: Blocker) =
    pureClientResourceAsync(awsRegion, blocker).map(S3FileSink[F])

  /** Get both a source and sink for S3 using an underlying S3 Client */
  def sourceAndSink[F[_]: Sync: ContextShift](
      awsRegion: Region,
      blocker: Blocker
  ): Resource[F, (S3FileSource[F], S3FileSink[F])] =
    pureClientResource(awsRegion, blocker).map { client =>
      S3FileSource[F](client) -> S3FileSink[F](client)
    }

  /** Get both a source and sink for S3 using an underlying S3 Async Client */
  def sourceAndSinkAsync[F[_]: Async: ContextShift](
      awsRegion: Region,
      blocker: Blocker
  ): Resource[F, (S3FileSource[F], S3FileSink[F])] =
    pureClientResourceAsync(awsRegion, blocker).map { client =>
      S3FileSource[F](client) -> S3FileSink[F](client)
    }

  def fileOps[F[_]: Sync: ContextShift](awsRegion: Region, blocker: Blocker) =
    pureClientResource[F](awsRegion, blocker).map(S3FileOps[F])

  def fileOpsAsync[F[_]: Async: ContextShift](awsRegion: Region, blocker: Blocker) =
    pureClientResourceAsync[F](awsRegion, blocker).map(S3FileOps[F])
}
