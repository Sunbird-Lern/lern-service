package org.sunbird.viewer.engine

import org.apache.pekko.actor.ActorContext
import org.sunbird.activity.util.{CertificateUtil, HierarchyRelationsUtil}
import org.sunbird.viewer.util.LpPolicyUtil
import org.sunbird.viewer.competency.CompetencyService
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

import java.util
import scala.collection.JavaConverters._

class LpProgression(context: ActorContext,
                    cassandraOperation: CassandraOperation,
                    hierarchyRelationsUtil: HierarchyRelationsUtil,
                    certificateUtil: CertificateUtil,
                    enrolKeyspace: String, enrolTable: String,
                    courseBatchKeyspace: String, courseBatchTable: String,
                    reAggregate: (String, String, String, RequestContext) => Unit) {

  private val logger = new LoggerUtil(classOf[LpProgression])
  private val lpPolicyUtil = LpPolicyUtil()
  private val enrolDispatcher = EnrolDispatcher(context)
  private[viewer] val competencyService = CompetencyService(cassandraOperation, enrolKeyspace)
  private val lpEngine = new LpProgressionEngine(
    cassandraOperation, enrolKeyspace, enrolTable, lpPolicyUtil, enrolDispatcher, certificateUtil, competencyService)

  def onAggregated(userId: String, courseId: String, batchId: String, completedNow: Set[String], ctx: RequestContext): Unit = {
    val trackable = hierarchyRelationsUtil.getTrackableNodes(courseId, ctx)
    if (trackable.nonEmpty) {
      logger.info(ctx, s"viewer.lp: advance | user=$userId root=$courseId n=${trackable.size}")
      advanceLp(userId, courseId, batchId, trackable, completedNow, ctx)
    } else if (completedNow.contains(courseId)) bridgeToRoot(userId, courseId, batchId, ctx)
  }

  private def advanceLp(userId: String, rootId: String, batchId: String,
                        trackable: List[String], completedNow: Set[String], ctx: RequestContext): Unit = {
    val status = enrolStatusSnapshot(userId, ctx)
    val ancestorsOf = (course: String) =>
      hierarchyRelationsUtil.getLeafNodes(rootId, course, ctx).headOption
        .map(leaf => hierarchyRelationsUtil.getAncestors(rootId, leaf, ctx))
        .getOrElse(List.empty[String])
    // Leaves per child course, so the engine can judge completion from the LP root's own
    // contentstatus when no child enrolment row exists (which is the normal case).
    val leavesOf = (course: String) => hierarchyRelationsUtil.getLeafNodes(rootId, course, ctx)
    lpEngine.advance(userId, rootId, batchId, trackable, status, ancestorsOf, completedNow, ctx, leavesOf)
  }

  private def enrolStatusSnapshot(userId: String, ctx: RequestContext): Map[(String, String), Int] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}
    val rows = cassandraOperation.getRecords(enrolKeyspace, enrolTable,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.asScala.flatMap { r =>
      for {
        c <- Option(r.get("courseId")).map(_.toString)
        b <- Option(r.get("batchId")).map(_.toString)
      } yield (c, b) -> Option(r.get("status")).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
    }.toMap
  }

  private def bridgeToRoot(userId: String, courseId: String, courseBatchId: String, ctx: RequestContext): Unit =
    parentLpOf(courseId, courseBatchId, ctx).foreach { case (lpId, lpBatch) =>
      reAggregate(userId, lpId, lpBatch, ctx)
      logger.info(ctx, s"viewer.lp: child->LP bridge | user=$userId course=$courseId childBatch=$courseBatchId lp=$lpId lpBatch=$lpBatch")
    }

  private def parentLpOf(courseId: String, courseBatchId: String, ctx: RequestContext): Option[(String, String)] =
    LpProgression.resolveParentLp(courseId, courseBatchId, lpBatch => courseBatchRowsByBatchId(lpBatch, ctx))

  private def courseBatchRowsByBatchId(batchId: String, ctx: RequestContext): util.List[util.Map[String, AnyRef]] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("batchid", batchId) }}
    cassandraOperation.getRecords(courseBatchKeyspace, courseBatchTable,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
  }
}

object LpProgression {
  private[engine] def resolveParentLp(courseId: String, courseBatchId: String,
      fetchByBatchId: String => java.util.List[java.util.Map[String, AnyRef]]): Option[(String, String)] = {
    if (courseBatchId == null || !courseBatchId.contains(":")) None
    else {
      val lpBatch = courseBatchId.substring(0, courseBatchId.indexOf(":"))
      fetchByBatchId(lpBatch).asScala
        .flatMap(r => Option(r.get("courseId")).map(_.toString))
        .find(_ != courseId).map(lpId => (lpId, lpBatch))
    }
  }
}
