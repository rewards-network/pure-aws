package com.rewardsnetwork.pureaws

import java.security.MessageDigest

object utils {
  def md5String(bytes: Array[Byte]) =
    scodec.bits.ByteVector(MessageDigest.getInstance("MD5").digest(bytes)).toBase64
}
