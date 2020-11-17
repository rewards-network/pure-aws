package com.rewardsnetwork.pureaws.sqs

import com.rewardsnetwork.pureaws.compat.Conversions._
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName

final case class MessageAttributes(
    approximateReceiveCount: Option[Int],
    approximateFirstReceiveTimestampEpochMillis: Option[Long],
    messageDeduplicationId: Option[String],
    messageGroupId: Option[String],
    senderId: Option[String],
    sentTimestampEpochMillis: Option[Long],
    sequenceNumber: Option[Long]
)

object MessageAttributes {
  def fromMap(m: Map[MessageSystemAttributeName, String]): MessageAttributes = {
    import MessageSystemAttributeName._
    val approxReceiveCount = m.get(APPROXIMATE_RECEIVE_COUNT).flatMap(toIntOption)
    val approxFirstReceiveTimestamp = m.get(APPROXIMATE_FIRST_RECEIVE_TIMESTAMP).flatMap(toLongOption)
    val dedupeId = m.get(MESSAGE_DEDUPLICATION_ID)
    val groupId = m.get(MESSAGE_DEDUPLICATION_ID)
    val senderId = m.get(SENDER_ID)
    val sentTimestamp = m.get(SENT_TIMESTAMP).flatMap(toLongOption)
    val sequenceNumber = m.get(SEQUENCE_NUMBER).flatMap(toLongOption)

    MessageAttributes(
      approxReceiveCount,
      approxFirstReceiveTimestamp,
      dedupeId,
      groupId,
      senderId,
      sentTimestamp,
      sequenceNumber
    )
  }
}
