package org.sunbird.viewer.competency

import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

/**
 * All Cassandra access for the competency tables.
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
    row.put("competencyid", e.competencyId)
    row.put("evidenceid", e.evidenceId)
    row.put("framework_id", e.frameworkId)
    row.put("level", e.level)
    row.put("level_index", Integer.valueOf(e.levelIndex))
    row.put("source_type", e.sourceType)
    row.put("source_id", e.sourceId)
    row.put("batchid", e.batchId)
    e.score.foreach(v => row.put("score", java.lang.Double.valueOf(v)))
    e.maxScore.foreach(v => row.put("max_score", java.lang.Double.valueOf(v)))
    row.put("evidence_count", Integer.valueOf(e.evidenceCount))
    e.issuerId.foreach(v => row.put("issuer_id", v))
    e.note.foreach(v => row.put("note", v))
    row.put("occurred_on", new util.Date(e.occurredOn))
    e.expiresOn.foreach(v => row.put("expires_on", new util.Date(v)))
    row.put("revoked", java.lang.Boolean.valueOf(e.revoked))
    e.revokedReason.foreach(v => row.put("revoked_reason", v))
    cassandra.insertRecord(keyspace, EVIDENCE_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  def evidenceOf(userId: String, competencyId: String, ctx: RequestContext): List[Evidence] =
    read(EVIDENCE_TABLE, Map("userid" -> userId, "competencyid" -> competencyId), ctx).map(toEvidence)

  def revokeEvidence(userId: String, competencyId: String, evidenceId: String,
                     reason: String, ctx: RequestContext): Unit = {
    val select = mapOf("userid" -> userId, "competencyid" -> competencyId, "evidenceid" -> evidenceId)
    val update = mapOf("revoked" -> java.lang.Boolean.TRUE, "revoked_reason" -> reason)
    cassandra.updateRecordV2(keyspace, EVIDENCE_TABLE, select, update, true, ctx)
  }

  // ---- passbook -------------------------------------------------------------------------------

  def upsertPassbook(userId: String, e: PassbookEntry, ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", userId)
    row.put("competencyid", e.competencyId)
    row.put("framework_id", e.frameworkId)
    row.put("level", e.level)
    row.put("level_index", Integer.valueOf(e.levelIndex))
    row.put("status", e.status)
    row.put("source_type", e.sourceType)
    row.put("governing_evidence_id", e.governingEvidenceId)
    row.put("attained_on", new util.Date(e.attainedOn))
    e.expiresOn.foreach(v => row.put("expires_on", new util.Date(v)))
    row.put("updated_on", new util.Date())
    cassandra.insertRecord(keyspace, PASSBOOK_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  def passbookOf(userId: String, ctx: RequestContext): List[PassbookEntry] =
    read(PASSBOOK_TABLE, Map("userid" -> userId), ctx).flatMap(toPassbook)

  def setPassbookStatus(userId: String, competencyId: String, status: String, ctx: RequestContext): Unit =
    cassandra.updateRecordV2(keyspace, PASSBOOK_TABLE,
      mapOf("userid" -> userId, "competencyid" -> competencyId),
      mapOf("status" -> status, "updated_on" -> new util.Date()), true, ctx)

  // ---- expiry index ---------------------------------------------------------------------------

  def indexExpiry(userId: String, competencyId: String, expiresOn: Long, ctx: RequestContext): Unit = {
    val row = mapOf(
      "expiry_bucket" -> AttainmentRules.expiryBucket(expiresOn),
      "expires_on" -> new util.Date(expiresOn),
      "userid" -> userId,
      "competencyid" -> competencyId)
    cassandra.insertRecord(keyspace, EXPIRY_INDEX_TABLE, row, ctx)
  }

  /** One bucket of pending expiries. The sweep walks the current and previous buckets. */
  def expiriesIn(bucket: String, ctx: RequestContext): List[(String, String, Long)] =
    read(EXPIRY_INDEX_TABLE, Map("expiry_bucket" -> bucket), ctx).flatMap { r =>
      for {
        u <- get(r, "userid")
        c <- get(r, "competencyid")
      } yield (u, c, dateOf(r, "expires_on").getOrElse(0L))
    }

  // ---- position -------------------------------------------------------------------------------

  def positionOf(userId: String, ctx: RequestContext): Option[PositionAssignment] =
    read(POSITION_TABLE, Map("userid" -> userId), ctx).headOption.map { r =>
      PositionAssignment(
        userId = userId,
        frameworkId = get(r, "framework_id").getOrElse(""),
        currentPosition = get(r, "current_position"),
        targetPositions = Option(r.get("target_positions"))
          .map(_.asInstanceOf[util.Collection[String]].asScala.toSet).getOrElse(Set.empty),
        source = get(r, "source").getOrElse(""),
        assignedOn = dateOf(r, "assigned_on").getOrElse(0L))
    }

  def upsertPosition(p: PositionAssignment, ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", p.userId)
    row.put("framework_id", p.frameworkId)
    p.currentPosition.foreach(v => row.put("current_position", v))
    if (p.targetPositions.nonEmpty) row.put("target_positions", p.targetPositions.asJava)
    row.put("source", p.source)
    row.put("assigned_on", new util.Date(if (p.assignedOn > 0) p.assignedOn else System.currentTimeMillis()))
    cassandra.insertRecord(keyspace, POSITION_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  // ---- requirement projection -----------------------------------------------------------------

  /** Written so reporting can join on it. The runtime authority stays the cached framework. */
  def upsertRequirement(frameworkId: String, positionId: String, r: RequirementDef, ctx: RequestContext): Unit = {
    val row = mapOf(
      "framework_id" -> frameworkId,
      "positionid" -> positionId,
      "competencyid" -> r.competencyId,
      "required_level" -> r.requiredLevel,
      "required_level_index" -> Integer.valueOf(r.requiredLevelIndex),
      "criticality" -> r.criticality)
    cassandra.insertRecord(keyspace, REQUIREMENT_TABLE, row, ctx)
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
   * (user_id, collection_id) partition of assessment_aggregator, so newly added competencies can be
   * credited retroactively from attempts that predate them.
   */
  def assessedContentIds(userId: String, collectionId: String, contextId: String,
                         ctx: RequestContext): List[String] = {
    val filters = Map("user_id" -> userId, "collection_id" -> collectionId, "context_id" -> contextId)
    read(ASSESSMENT_TABLE, filters, ctx).flatMap(r => get(r, "content_id")).distinct
  }
}

case class EnrolmentRow(courseId: String, batchId: String, status: Int, completedOn: Long)

object CompetencyDao {
  val EVIDENCE_TABLE = "user_competency_evidence"
  val PASSBOOK_TABLE = "user_competency"
  val EXPIRY_INDEX_TABLE = "competency_expiry_index"
  val POSITION_TABLE = "user_competency_position"
  val REQUIREMENT_TABLE = "competency_requirement"
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
    competencyId = get(r, "competencyid").getOrElse(""),
    evidenceId = get(r, "evidenceid").getOrElse(""),
    frameworkId = get(r, "framework_id").getOrElse(""),
    level = get(r, "level").getOrElse(""),
    levelIndex = intOf(r, "level_index").getOrElse(0),
    sourceType = get(r, "source_type").getOrElse(""),
    sourceId = get(r, "source_id").getOrElse(""),
    batchId = get(r, "batchid").getOrElse(""),
    score = dbl(r, "score"),
    maxScore = dbl(r, "max_score"),
    evidenceCount = intOf(r, "evidence_count").getOrElse(0),
    issuerId = get(r, "issuer_id"),
    note = get(r, "note"),
    occurredOn = dateOf(r, "occurred_on").getOrElse(0L),
    expiresOn = dateOf(r, "expires_on"),
    revoked = bool(r, "revoked"),
    revokedReason = get(r, "revoked_reason"))

  private[competency] def toPassbook(r: util.Map[String, AnyRef]): Option[PassbookEntry] =
    get(r, "competencyid").map { cid =>
      PassbookEntry(
        competencyId = cid,
        frameworkId = get(r, "framework_id").getOrElse(""),
        level = get(r, "level").getOrElse(""),
        levelIndex = intOf(r, "level_index").getOrElse(0),
        status = get(r, "status").getOrElse(AttainmentRules.IN_PROGRESS),
        sourceType = get(r, "source_type").getOrElse(""),
        governingEvidenceId = get(r, "governing_evidence_id").getOrElse(""),
        attainedOn = dateOf(r, "attained_on").getOrElse(0L),
        expiresOn = dateOf(r, "expires_on"))
    }
}
