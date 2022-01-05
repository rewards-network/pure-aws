package com.rewardsnetwork.pureaws.sqs.refined

import eu.timepit.refined.numeric._
import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.api.RefType

final case class RefinedStreamMessageSettings(
    maxMessages: MaxMessages,
    visibilityTimeoutSeconds: VisibilityTimeout,
    waitTimeSeconds: Int Refined NonNegative
)

object RefinedStreamMessageSettings {
  val default = {
    // This is gross but necessary to get literals working in Scala 3 for the moment
    val _ = refineV[Positive](5)
    val mm = RefType.applyRef[MaxMessages](10).getOrElse(???)
    val vts = RefType.applyRef[VisibilityTimeout](30).getOrElse(???)
    val wts = refineV[NonNegative](0).getOrElse(???)

    RefinedStreamMessageSettings(mm, vts, wts)
  }
}
