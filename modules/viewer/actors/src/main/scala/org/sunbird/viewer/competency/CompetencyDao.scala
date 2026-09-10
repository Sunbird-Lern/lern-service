package org.sunbird.viewer.competency

import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

/**
 * All Cassandra access for the skill tables.
 *
 * Writes use raw column names. Reads must tolerate both raw and camel-cased keys, because
 * cassandratablecolumn.properties renames some columns on the way out (`userid` becomes `userId`,
 * `batchid` becomes `batchId`) and leaves the rest alone.
 */
class CompetencyDao(cassandra: CassandraOperation, keyspace: String) {

  import CompetencyDao._

  private def rows(resp: org.sunbird.response.Response): List[util.Map[String, AnyRef]] =
    resp.getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]())
      .asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala.toList

  private def read(table: String, filters: Map[String, AnyRef], ctx: RequestContext): List[util.Map[String, AnyRef]] =
    rows(cassandra.getRecords(keyspace, table, filters.asJava.asInstanceOf[util.Map[String, AnyRef]], null, ctx))

  // ---- evidence ledger ------------------------------------------------------------------------

  def insertEvidence(e: Evidence, ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", e.userId)
    row.put("skillid", e.skillId)
    row.put("evidenceid", e.evidenceId)
    row.put("framework_id", e.frameworkId)
    row.put("source_type", e.sourceType)
    row.put("source_id", e.sourceId)
    row.put("batchid", e.batchId)
    e.score.foreach(v => row.put("score", java.lang.Double.valueOf(v)))
    e.maxScore.foreach(v => row.put("max_score", java.lang.Double.valueOf(v)))
    e.issuerId.foreach(v => row.put("issuer_id", v))
    e.note.foreach(v => row.put("note", v))
    row.put("occurred_on", new util.Date(e.occurredOn))
    row.put("revoked", java.lang.Boolean.valueOf(e.revoked))
    e.revokedReason.foreach(v => row.put("revoked_reason", v))
    cassandra.insertRecord(keyspace, EVIDENCE_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  def evidenceOf(userId: String, skillId: String, ctx: RequestContext): List[Evidence] =
    read(EVIDENCE_TABLE, Map("userid" -> userId, "skillid" -> skillId), ctx).map(toEvidence)

  def revokeEvidence(userId: String, skillId: String, evidenceId: String,
                     reason: String, ctx: RequestContext): Unit = {
    val select = mapOf("userid" -> userId, "skillid" -> skillId, "evidenceid" -> evidenceId)
    val update = mapOf("revoked" -> java.lang.Boolean.TRUE, "revoked_reason" -> reason)
    cassandra.updateRecordV2(keyspace, EVIDENCE_TABLE, select, update, true, ctx)
  }

  // ---- skill profile --------------------------------------------------------------------------

  def upsertSkill(userId: String, e: SkillEntry, ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", userId)
    row.put("skillid", e.skillId)
    row.put("framework_id", e.frameworkId)
    row.put("source_type", e.sourceType)
    row.put("governing_evidence_id", e.governingEvidenceId)
    row.put("attained_on", new util.Date(e.attainedOn))
    row.put("updated_on", new util.Date())
    cassandra.insertRecord(keyspace, PROFILE_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  def profileOf(userId: String, ctx: RequestContext): List[SkillEntry] =
    read(PROFILE_TABLE, Map("userid" -> userId), ctx).flatMap(toSkill)

  /**
   * Removes a skill from the profile. Called only when every supporting evidence row has been
   * revoked; the evidence itself is never deleted.
   */
  def deleteSkill(userId: String, skillId: String, ctx: RequestContext): Unit = {
    val key = new util.HashMap[String, String]()
    key.put("userid", userId)
    key.put("skillid", skillId)
    cassandra.deleteRecord(keyspace, PROFILE_TABLE, key, ctx)
  }

  // ---- role assignment ------------------------------------------------------------------------

  def roleOf(userId: String, ctx: RequestContext): Option[RoleAssignment] =
    read(ROLE_TABLE, Map("userid" -> userId), ctx).headOption.map { r =>
      RoleAssignment(
        userId = userId,
        frameworkId = get(r, "framework_id").getOrElse(""),
        currentRole = get(r, "current_role"),
        targetRoles = Option(r.get("target_roles"))
          .map(_.asInstanceOf[util.Collection[String]].asScala.toSet).getOrElse(Set.empty),
        source = get(r, "source").getOrElse(""),
        assignedOn = dateOf(r, "assigned_on").getOrElse(0L))
    }

  def upsertRole(a: RoleAssignment, ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", a.userId)
    row.put("framework_id", a.frameworkId)
    a.currentRole.foreach(v => row.put("current_role", v))
    if (a.targetRoles.nonEmpty) row.put("target_roles", a.targetRoles.asJava)
    row.put("source", a.source)
    row.put("assigned_on", new util.Date(if (a.assignedOn > 0) a.assignedOn else System.currentTimeMillis()))
    cassandra.insertRecord(keyspace, ROLE_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  // ---- enrolments (read-only, for reprojection) -----------------------------------------------

  def enrolmentsOf(userId: String, ctx: RequestContext): List[EnrolmentRow] =
    read(ENROLMENT_TABLE, Map("userid" -> userId), ctx).flatMap { r =>
      for { c <- get(r, "courseid"); b <- get(r, "batchid") } yield EnrolmentRow(
        courseId = c, batchId = b,
        status = intOf(r, "status").getOrElse(0),
        completedOn = dateOf(r, "completedon").getOrElse(0L))
    }

  /**
   * Distinct question sets a learner has attempted under one collection. Reads the
   * (user_id, collection_id) partition of assessment_aggregator, so a skill added to the framework
   * after the fact can be credited from attempts that predate it.
   */
  def assessedContentIds(userId: String, collectionId: String, contextId: String,
                         ctx: RequestContext): List[String] = {
    val filters = Map("user_id" -> userId, "collection_id" -> collectionId, "context_id" -> contextId)
    read(ASSESSMENT_TABLE, filters, ctx).flatMap(r => get(r, "content_id")).distinct
  }
}

case class EnrolmentRow(courseId: String, batchId: String, status: Int, completedOn: Long)

object CompetencyDao {
  val EVIDENCE_TABLE = "user_skill_evidence"
  val PROFILE_TABLE = "user_skill"
  val ROLE_TABLE = "user_role"
  val ENROLMENT_TABLE = "user_enrolments"
  val ASSESSMENT_TABLE = "assessment_aggregator"

  private def mapOf(kv: (String, AnyRef)*): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    kv.foreach { case (k, v) => m.put(k, v) }
    m
  }

  /** Reads a column under its raw name or the camel-cased alias the property reader may apply. */
  private[competency] def get(r: util.Map[String, AnyRef], col: String): Option[String] =
    Option(r.get(col)).orElse(Option(r.get(camel(col)))).map(_.toString).filter(_.nonEmpty)

  private[competency] def camel(col: String): String = col match {
    case "userid" => "userId"
    case "courseid" => "courseId"
    case "batchid" => "batchId"
    case "skillid" => "skillId"
    case other => other
  }

  private[competency] def intOf(r: util.Map[String, AnyRef], col: String): Option[Int] =
    Option(r.get(col)).orElse(Option(r.get(camel(col)))).collect { case n: Number => n.intValue() }

  private def dbl(r: util.Map[String, AnyRef], col: String): Option[Double] =
    Option(r.get(col)).collect { case n: Number => n.doubleValue() }

  private[competency] def dateOf(r: util.Map[String, AnyRef], col: String): Option[Long] =
    Option(r.get(col)).collect { case d: util.Date => d.getTime }

  private def bool(r: util.Map[String, AnyRef], col: String): Boolean =
    Option(r.get(col)).collect { case b: java.lang.Boolean => b.booleanValue() }.getOrElse(false)

  private[competency] def toEvidence(r: util.Map[String, AnyRef]): Evidence = Evidence(
    userId = get(r, "userid").getOrElse(""),
    skillId = get(r, "skillid").getOrElse(""),
    evidenceId = get(r, "evidenceid").getOrElse(""),
    frameworkId = get(r, "framework_id").getOrElse(""),
    sourceType = get(r, "source_type").getOrElse(""),
    sourceId = get(r, "source_id").getOrElse(""),
    batchId = get(r, "batchid").getOrElse(""),
    score = dbl(r, "score"),
    maxScore = dbl(r, "max_score"),
    issuerId = get(r, "issuer_id"),
    note = get(r, "note"),
    occurredOn = dateOf(r, "occurred_on").getOrElse(0L),
    revoked = bool(r, "revoked"),
    revokedReason = get(r, "revoked_reason"))

  private[competency] def toSkill(r: util.Map[String, AnyRef]): Option[SkillEntry] =
    get(r, "skillid").map { sid =>
      SkillEntry(
        skillId = sid,
        frameworkId = get(r, "framework_id").getOrElse(""),
        sourceType = get(r, "source_type").getOrElse(""),
        governingEvidenceId = get(r, "governing_evidence_id").getOrElse(""),
        attainedOn = dateOf(r, "attained_on").getOrElse(0L))
    }
}
