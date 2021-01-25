package com.rewardsnetwork.pureaws.s3.testing

import java.time.Instant
import cats.effect.Sync

object internal {
  def newInstant[F[_]: Sync]: F[Instant] = Sync[F].delay(Instant.now())
}
