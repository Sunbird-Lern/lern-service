package org.sunbird.activity.util

import org.sunbird.cache.util.RedisCacheUtil
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext

import java.security.MessageDigest

class DeDupUtil {

  private lazy val cacheUtil: RedisCacheUtil = new RedisCacheUtil()

  private lazy val deDupRedisIndex = ProjectUtil.getConfigValue("dedup_redis_index") match {
    case value if value != null => value.toInt
    case _ => 3
  }
  
  private lazy val deDupExpirySec = ProjectUtil.getConfigValue("dedup_redis_expiry") match {
    case value if value != null => value.toInt
    case _ => 604800
  }
  
  private lazy val dedupEnabled = ProjectUtil.getConfigValue("activity_input_dedup_enabled") match {
    case value if value != null => value.toBoolean
    case _ => false
  }

  private lazy val redisEnabled: Boolean = RedisCacheUtil.isRedisEnabled

  def getMessageId(courseId: String, batchId: String, userId: String, contentId: String, status: Int): String = {
    val key = Array(courseId, batchId, userId, contentId, status).mkString("|")
    MessageDigest.getInstance("MD5").digest(key.getBytes).map("%02X".format(_)).mkString
  }

  def isUniqueEvent(checksum: String, requestContext: RequestContext): Boolean = {
    if (!dedupEnabled || !redisEnabled) true
    else {
      val jedis = cacheUtil.getConnection(deDupRedisIndex)
      try !jedis.exists(checksum)
      finally jedis.close()
    }
  }

  def storeChecksum(checksum: String, requestContext: RequestContext): Unit = {
    if (dedupEnabled && redisEnabled) {
      val jedis = cacheUtil.getConnection(deDupRedisIndex)
      try jedis.setex(checksum, deDupExpirySec, "1")
      finally jedis.close()
    }
  }
}

object DeDupUtil {
  def apply(): DeDupUtil = new DeDupUtil()
}
