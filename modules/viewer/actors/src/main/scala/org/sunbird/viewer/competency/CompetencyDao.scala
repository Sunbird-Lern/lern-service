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

  /** Every evidence row a learner has, revoked included. One partition read. */
  def evidenceOfUser(userId: String, ctx: RequestContext): List[Evidence] =
    read(EVIDENCE_TABLE, Map("userid" -> userId), ctx).map(toEvidence)

  def revokeEvidence(userId: String, skillId: String, evidenceId: String,
                     reason: String, ctx: RequestContext): Unit = {
    val select = mapOf("userid" -> userId, "skillid" -> skillId, "evidenceid" -> evidenceId)
    val update = mapOf("revoked" -> java.lang.Boolean.TRUE, "revoked_reason" -> reason)
    cassandra.updateRecordV2(keyspace, EVIDENCE_TABLE, select, update, true, ctx)
  }

  // ---- role assignment ------------------------------------------------------------------------

  def roleOf(userId: String, ctx: RequestContext): Option[RoleAssignment] =
    read(ROLE_TABLE, Map("userid" -> userId), ctx).headOption.map { r =>
      RoleAssignment(
        userId = userId,
        frameworkId = get(r, "framework_id").getOrElse(""),
        currentRole = get(r, "assigned_role"),
        targetRoles = Option(r.get("target_roles"))
          .map(_.asInstanceOf[util.Collection[String]].asScala.toSet).getOrElse(Set.empty),
        source = get(r, "source").getOrElse(""),
        assignedOn = dateOf(r, "assigned_on").getOrElse(0L))
    }

  def upsertRole(a: RoleAssignment, ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", a.userId)
    row.put("framework_id", a.frameworkId)
    a.currentRole.foreach(v => row.put("assigned_role", v))
    // Always written: an empty set is how a learner clears their targets.
    row.put("target_roles", a.targetRoles.asJava)
    row.put("source", a.source)
    row.put("assigned_on", new util.Date(if (a.assignedOn > 0) a.assignedOn else System.currentTimeMillis()))
    cassandra.insertRecord(keyspace, ROLE_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
  }

  // ---- role requirements ----------------------------------------------------------------------

  /**
   * Every role in a framework, with the leaf skills it requires.
   *
   * This table is the source of truth for the role -> skill map. Roles are not read from the Knowlg
   * framework: two authorities on what a role requires would drift.
   *
   * Reads the whole `framework_id` partition in one go, which is why the table partitions on
   * framework_id alone. RETIRED roles are returned, flagged, not dropped: a retired role must stop
   * being offered as a target, but anyone already holding it is still measured against it.
   *
   * Leaf validation is NOT done here - this returns what was authored. `CompetencyFrameworkUtil`
   * filters against the tree, because only it knows the leaves.
   */
  def readRoleSkills(frameworkId: String, ctx: RequestContext): RoleIndex = {
    val rows = read(ROLE_SKILL_TABLE, Map("framework_id" -> frameworkId), ctx)
    val skills = rows
      .flatMap(r => for { role <- get(r, "roleid"); skill <- get(r, "skillid") } yield role -> skill)
      .groupBy(_._1)
      .map { case (role, pairs) => role -> pairs.map(_._2).toSet }
    val retired = rows
      .filter(r => get(r, "status").exists(_.equalsIgnoreCase(RoleStatus.RETIRED)))
      .flatMap(r => get(r, "roleid")).toSet
    RoleIndex(skills, retired)
  }

  /** Display names, where authored. Denormalised onto every row of a role, so any row will do. */
  def readRoleNames(frameworkId: String, ctx: RequestContext): Map[String, String] =
    read(ROLE_SKILL_TABLE, Map("framework_id" -> frameworkId), ctx)
      .flatMap(r => for { role <- get(r, "roleid"); name <- get(r, "role_name") } yield role -> name)
      .toMap

  /** Raw rows, one per requirement, with status and version. The writer needs to see everything. */
  def roleRowsOf(frameworkId: String, ctx: RequestContext): List[RoleSkillRow] =
    read(ROLE_SKILL_TABLE, Map("framework_id" -> frameworkId), ctx).flatMap { r =>
      for { role <- get(r, "roleid"); skill <- get(r, "skillid") } yield RoleSkillRow(
        frameworkId = frameworkId, roleId = role, skillId = skill,
        roleName = get(r, "role_name").getOrElse(role),
        status = get(r, "status").getOrElse(RoleStatus.LIVE),
        version = intOf(r, "version").getOrElse(1),
        addedOn = dateOf(r, "added_on").getOrElse(0L))
    }

  /**
   * Writes rows of one role as a single logged batch, so a rename, a retire or a new requirement set
   * reaches every row or none. The rows share `framework_id`, so the batch stays in one partition.
   */
  def writeRoleSkills(rows: List[RoleSkillRow], ctx: RequestContext): Unit = if (rows.nonEmpty) {
    val now = System.currentTimeMillis()
    val records = rows.map { r =>
      val row = new util.HashMap[String, AnyRef]()
      row.put("framework_id", r.frameworkId)
      row.put("roleid", r.roleId)
      row.put("skillid", r.skillId)
      row.put("role_name", r.roleName)
      row.put("status", r.status)
      row.put("version", Integer.valueOf(r.version))
      row.put("added_on", new util.Date(if (r.addedOn > 0) r.addedOn else now))
      row.asInstanceOf[util.Map[String, AnyRef]]
    }
    cassandra.batchInsertLogged(keyspace, ROLE_SKILL_TABLE, records.asJava, ctx)
  }

  /**
   * Removes requirements of one role, in one batch. This IS a delete, unlike a role, which is
   * retired: an un-ticked box in the matrix means the role never required that skill, so there is
   * nothing to preserve. Evidence the learner already earned for the skill is untouched - it lives
   * in user_skill_evidence and is not keyed by role.
   */
  def deleteRoleSkills(frameworkId: String, roleId: String, skillIds: Set[String],
                       ctx: RequestContext): Unit = if (skillIds.nonEmpty) {
    val keys = skillIds.toList.map(sk =>
      mapOf("framework_id" -> frameworkId, "roleid" -> roleId, "skillid" -> sk))
    cassandra.batchDelete(keyspace, ROLE_SKILL_TABLE, keys.asJava, ctx)
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
  val ROLE_TABLE = "user_role"
  val ROLE_SKILL_TABLE = "role_skill"
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
}
