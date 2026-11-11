package org.sunbird.viewer.actor

import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.RequestContext
import org.sunbird.viewer.actor.CarryForwardRules.{Candidate, Target}

import java.util
import scala.collection.JavaConverters._

// Copy overlay: carry a user's qualifying prior completions (+ assessment scores) into a target (course, batch).
// Pure carry mechanics; the caller (enrol hook / reverse fan-out) recomputes the enrolment afterwards.
class CarryForwardService(cassandraOperation: CassandraOperation) {

  private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
  private val assessmentDBInfo = Util.dbInfoMap.get(JsonKey.ASSESSMENT_AGGREGATOR_DB)
  private val CONSUMPTION_TABLE = "user_content_consumption"

  /**
   * Copy each of `leaves` whose best qualifying prior completion passes the rules into (courseId, batchId).
   * scope=collection carries only in-collection completions; scope=content carries any (incl. organic).
   * Returns the number of leaves carried.
   */
  def carryForward(userId: String, courseId: String, batchId: String, leaves: List[String], scope: String,
                   target: Target, now: Long, ctx: RequestContext, rulesJson: String = CarryForwardRules.configuredRules()): Int = {
    val sourceRows = readUserConsumption(userId, ctx).asScala.toList
    var copied = 0
    leaves.foreach { leaf =>
      val eligible = sourceRows.filter { r =>
        val cont = field(r, "contentId", "contentid")
        val coll = field(r, "collectionid", "collectionId")
        cont == leaf && num(r.get("status")).toInt == 2 &&
          !(coll == courseId && field(r, "contextid", "contextId") == batchId) &&   // don't re-copy into the target itself
          (scope != "collection" || (coll != null && coll != cont))          // collection scope: only in-collection (not organic)
      }
      eligible
        .filter(r => CarryForwardRules.passes(Candidate(lastCompletedMillis(r)), target, now, rulesJson))
        .sortBy(r => lastCompletedMillis(r).getOrElse(0L)).lastOption
        .foreach { src =>
          copyConsumption(userId, courseId, batchId, leaf, src, ctx)
          copyAssessment(userId, courseId, batchId, leaf, field(src, "collectionid", "collectionId"), field(src, "contextid", "contextId"), ctx)
          copied += 1
        }
    }
    copied
  }

  private def copyConsumption(userId: String, courseId: String, batchId: String, leaf: String,
                              src: util.Map[String, AnyRef], ctx: RequestContext): Unit = {
    val row = new util.HashMap[String, AnyRef]()
    row.put("userid", userId); row.put("collectionid", courseId); row.put("contextid", batchId); row.put("contentid", leaf)
    row.put("status", Integer.valueOf(2)); row.put("progress", Integer.valueOf(100))
    Option(src.get("lastCompletedTime")).orElse(Option(src.get("last_completed_time"))).foreach(row.put("last_completed_time", _))
    row.put("last_updated_time", org.sunbird.common.ProjectUtil.getTimeStamp)
    cassandraOperation.upsertRecord(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE, row, ctx)
  }

  // score carry: re-key the source assessment_aggregator rows to (courseId, batchId). Best-effort (fail-safe).
  private def copyAssessment(userId: String, courseId: String, batchId: String, leaf: String,
                             srcColl: String, srcCtx: String, ctx: RequestContext): Unit = {
    if (srcColl == null || srcCtx == null || (srcColl == courseId && srcCtx == batchId)) return
    try {
      val filters = new util.HashMap[String, AnyRef]() {{
        put("user_id", userId); put("collection_id", srcColl); put("context_id", srcCtx); put("content_id", leaf)
      }}
      getRecords(assessmentDBInfo.getKeySpace, assessmentDBInfo.getTableName, filters, ctx).asScala.foreach { a =>
        // reads come back with some columns camelCased; write them under their real column names
        val copy = new util.HashMap[String, AnyRef]()
        a.asScala.foreach { case (k, v) => copy.put(CarryForwardService.assessmentColumn.getOrElse(k, k), v) }
        copy.put("collection_id", courseId); copy.put("context_id", batchId)
        cassandraOperation.upsertRecord(assessmentDBInfo.getKeySpace, assessmentDBInfo.getTableName, copy, ctx)
      }
    } catch { case _: Throwable => /* score carry is best-effort; the completion still carries */ }
  }

  private def readUserConsumption(userId: String, ctx: RequestContext): util.List[util.Map[String, AnyRef]] =
    getRecords(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE, new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}, ctx)

  private def getRecords(keyspace: String, table: String, filters: util.HashMap[String, AnyRef], ctx: RequestContext): util.List[util.Map[String, AnyRef]] =
    cassandraOperation.getRecords(keyspace, table, filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]

  private def num(v: AnyRef): Double = v match {
    case n: Number => n.doubleValue()
    case s: String if s.nonEmpty => try s.toDouble catch { case _: Throwable => 0.0 }
    case _ => 0.0
  }
  // production CassandraUtil camelCases some columns on read (cassandratablecolumn.properties); accept either spelling
  private def field(r: util.Map[String, AnyRef], keys: String*): String = keys.iterator.map(r.get).find(_ != null).map(_.toString).orNull
  private def lastCompletedMillis(r: util.Map[String, AnyRef]): Option[Long] = Option(r.get("lastCompletedTime")).orElse(Option(r.get("last_completed_time"))).orNull match {
    case d: java.util.Date => Some(d.getTime)
    case n: Number => Some(n.longValue())
    case s: String if s.nonEmpty => try Some(s.toLong) catch { case _: Throwable => None }
    case _ => None
  }
}

object CarryForwardService {
  // assessment_aggregator columns CassandraUtil renames on read (cassandratablecolumn.properties) -> real column names
  private[actor] val assessmentColumn: Map[String, String] = Map(
    "attemptId" -> "attempt_id", "lastAttemptedOn" -> "last_attempted_on",
    "totalScore" -> "total_score", "totalMaxScore" -> "total_max_score")
}
