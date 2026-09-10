package org.sunbird.viewer.competency

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.sunbird.common.ProjectUtil
import org.sunbird.kafka.KafkaClient
import org.sunbird.request.RequestContext

import java.util
import java.util.UUID

/**
 * Publishes a badge instruction when a skill starts or stops being held.
 *
 * One badge per skill, decoupled from programme structure: a learner who picks a skill up through
 * a standalone course, a private path or an imported credential gets the same badge as one who
 * finished a programme.
 *
 * Two properties this must keep, because it sits on the learner write path:
 *
 *  - Silent when unconfigured. A deployment with no badge topic set is a deployment that does not
 *    want badges, not a broken one.
 *  - Never throws. KafkaClient.send declares a checked exception, and a broker being down must not
 *    fail the crediting that triggered it. The profile is the record; the badge is a notification.
 */
class SkillBadgeUtil {

  private val logger = LoggerFactory.getLogger(classOf[SkillBadgeUtil])

  private def topic: Option[String] =
    Option(ProjectUtil.getConfigValue(SkillBadgeUtil.TOPIC_KEY)).map(_.trim).filter(_.nonEmpty)

  def issue(userId: String, entry: SkillEntry, ctx: RequestContext): Unit =
    publish(SkillBadgeUtil.issueEvent(userId, entry), s"issue ${entry.skillId}", ctx)

  def revoke(userId: String, skillId: String, frameworkId: String, ctx: RequestContext): Unit =
    publish(SkillBadgeUtil.revokeEvent(userId, skillId, frameworkId), s"revoke $skillId", ctx)

  private def publish(event: String, what: String, ctx: RequestContext): Unit = topic match {
    case None => // no badge topic configured; badges are opt-in
    case Some(t) =>
      try KafkaClient.send(event, t)
      catch {
        case ex: Throwable =>
          logger.warn(s"SkillBadgeUtil: $what not published to $t; the profile is unaffected", ex)
      }
  }
}

object SkillBadgeUtil {

  val TOPIC_KEY = "kafka_topics_skill_badge_instruction"

  private val mapper = new ObjectMapper()

  def apply(): SkillBadgeUtil = new SkillBadgeUtil()

  private[competency] def issueEvent(userId: String, e: SkillEntry,
                                     ets: Long = System.currentTimeMillis(),
                                     mid: String = UUID.randomUUID().toString): String = {
    val edata = ordered(
      "userIds" -> util.Arrays.asList(userId),
      "action" -> "issue-skill-badge",
      "iteration" -> Integer.valueOf(1),
      "trigger" -> "auto-issue",
      "skillId" -> e.skillId,
      "frameworkId" -> e.frameworkId,
      "sourceType" -> e.sourceType,
      "evidenceId" -> e.governingEvidenceId,
      "attainedOn" -> java.lang.Long.valueOf(e.attainedOn))
    event(s"${e.frameworkId}_${e.skillId}", edata, ets, mid)
  }

  private[competency] def revokeEvent(userId: String, skillId: String, frameworkId: String,
                                      ets: Long = System.currentTimeMillis(),
                                      mid: String = UUID.randomUUID().toString): String = {
    val edata = ordered(
      "userIds" -> util.Arrays.asList(userId),
      "action" -> "revoke-skill-badge",
      "iteration" -> Integer.valueOf(1),
      "trigger" -> "auto-revoke",
      "skillId" -> skillId,
      "frameworkId" -> frameworkId)
    event(s"${frameworkId}_$skillId", edata, ets, mid)
  }

  /** Same BE_JOB_REQUEST envelope the certificate instruction uses. */
  private def event(objectId: String, edata: util.Map[String, AnyRef],
                    ets: Long, mid: String): String = {
    val actor = ordered("id" -> "Skill Badge Generator", "type" -> "System")
    val pdata = ordered("ver" -> "1.0", "id" -> "org.sunbird.platform")
    val obj = ordered("id" -> objectId, "type" -> "SkillBadgeGeneration")
    mapper.writeValueAsString(ordered(
      "eid" -> "BE_JOB_REQUEST",
      "ets" -> java.lang.Long.valueOf(ets),
      "mid" -> s"LP.$ets.$mid",
      "actor" -> actor,
      "context" -> ordered("pdata" -> pdata),
      "object" -> obj,
      "edata" -> edata))
  }

  /** Insertion-ordered, so the serialised event is byte-stable and therefore testable. */
  private def ordered(kv: (String, AnyRef)*): util.Map[String, AnyRef] = {
    val m = new util.LinkedHashMap[String, AnyRef]()
    kv.foreach { case (k, v) => m.put(k, v) }
    m
  }
}
