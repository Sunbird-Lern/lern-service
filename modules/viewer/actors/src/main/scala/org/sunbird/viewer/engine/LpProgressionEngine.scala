package org.sunbird.viewer.engine

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.activity.util.CertificateUtil
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext
import org.sunbird.viewer.competency.CompetencyService
import org.sunbird.viewer.util.{LpMeta, LpPolicyUtil, ProgressionPolicy}

import java.util
import scala.collection.JavaConverters._

class LpProgressionEngine(cassandraOperation: CassandraOperation,
                          enrolKeyspace: String, enrolTable: String,
                          lpPolicyUtil: LpPolicyUtil,
                          dispatcher: EnrolDispatcher,
                          certificateUtil: CertificateUtil,
                          competencyService: CompetencyService) {

  private val logger = new LoggerUtil(classOf[LpProgressionEngine])

  /**
   * @param leavesOf the leaf content ids of a child course, used to judge completion from the LP
   *                 root's own `contentstatus`. Defaults to empty, which falls back to the
   *                 child-enrolment check alone.
   */
  def advance(userId: String, rootId: String, batchId: String, trackable: List[String],
              status: Map[(String, String), Int], ancestorsOf: String => List[String],
              completedNow: Set[String], ctx: RequestContext,
              leavesOf: String => List[String] = _ => Nil): Unit = {
    val childBatchOf = (c: String) => batchId + ":" + c
    // A child course counts as complete either from its own enrolment row, or - the case that
    // actually occurs - from the LP root's `contentstatus`.
    //
    // The client addresses the PATH, so every view call is keyed to the LP's collection and
    // batch and lands on the root row; the child enrolments this used to rely on are created by
    // `dispatcher.enrol` against a synthetic batch `<lpBatch>:<courseId>` that has no
    // `course_batch` record, so `CourseEnrolmentActor.validateEnrolment` rejects every one of
    // them with invalidCourseBatchId. The dispatch is a fire-and-forget `tell`, so that rejection
    // is never seen and the rows never appear - leaving `courseComplete` false forever, the root
    // at status 0, and every status==2 consumer (certificate, CompetencyProjector) starved.
    lazy val rootContent = rootContentStatus(userId, rootId, batchId, ctx)
    val courseComplete = (c: String) =>
      status.get((c, childBatchOf(c))).contains(2) || {
        val leaves = leavesOf(c)
        leaves.nonEmpty && leaves.forall(l => rootContent.get(l).contains(2))
      }

    val meta = lpPolicyUtil.lpMeta(rootId, ctx)
    // Credit what just finished before deciding anything, so waiving sees the current passbook.
    creditCompleted(userId, rootId, batchId, meta, completedNow, ctx)

    val levelByCourse = ProgressionPolicy.levelByCourse(trackable, ancestorsOf, rootId)
    if (!ensureOptionalityComputed(userId, rootId, batchId, trackable, levelByCourse, courseComplete, meta, ctx)) return
    val optional = readOptionality(userId, rootId, batchId, ctx)._1.toSet
    val levels = ProgressionPolicy.orderedLevels(trackable, levelByCourse)

    def levelComplete(level: String): Boolean =
      ProgressionPolicy.coursesOfLevel(level, trackable, levelByCourse).filterNot(optional.contains).forall(courseComplete)

    levels.find(l => !levelComplete(l)).foreach { level =>
      val courses = ProgressionPolicy.coursesOfLevel(level, trackable, levelByCourse)
      val nextRequired = courses.filterNot(optional.contains).find(c => !courseComplete(c))
      val toOpen = courses.filter(optional.contains) ++ nextRequired.toList
      logger.info(ctx, s"viewer.lp: level opened | user=$userId root=$rootId level=$level open=[${toOpen.mkString(",")}]")
      toOpen.foreach { c =>
        val childBatch = childBatchOf(c)
        if (!status.contains((c, childBatch))) dispatcher.enrol(userId, c, childBatch, ctx)
        else logger.info(ctx, s"viewer.lp: enrol skip(already) | user=$userId course=$c batch=$childBatch")
      }
    }

    val requiredCourses = trackable.filterNot(optional.contains)
    val waived = trackable.count(optional.contains)
    val done = requiredCourses.count(courseComplete) + waived
    val total = trackable.size
    val allComplete = levels.nonEmpty && levels.forall(levelComplete)
    writeRootProgress(userId, rootId, batchId, done, total,
      allComplete, status.get((rootId, batchId)).getOrElse(0), meta, ctx)

    if (allComplete) logger.info(ctx, s"viewer.lp: complete | user=$userId root=$rootId")
  }

  private def writeRootProgress(userId: String, rootId: String, batchId: String, done: Int, total: Int,
                                allComplete: Boolean, currentStatus: Int, meta: LpMeta, ctx: RequestContext): Unit = {
    val pct = if (total <= 0) 100 else math.min(100, done * 100 / total)
    val rootStatus = if (allComplete) 2 else if (done > 0) 1 else 0
    val select = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val update = new util.HashMap[String, AnyRef]() {{
      put("progress", Integer.valueOf(done)); put("completionpercentage", Integer.valueOf(pct)); put("status", Integer.valueOf(rootStatus))
      if (rootStatus == 2 && currentStatus != 2) put("completedon", new java.util.Date())
    }}
    cassandraOperation.updateRecordV2(enrolKeyspace, enrolTable, select, update, true, ctx)
    logger.info(ctx, s"viewer.lp: root progress | user=$userId root=$rootId done=$done/$total pct=$pct status=$rootStatus")
    if (rootStatus == 2 && currentStatus != 2) {
      certificateUtil.publishCertificateIssueEvent(userId, rootId, batchId, ctx)
      // the programme's own competency claims, credited once on the completion transition
      if (meta.competencyFramework.nonEmpty)
        competencyService.onNodeCompleted(userId, meta.competencyFramework, rootId, batchId,
          isRoot = true, System.currentTimeMillis(), ctx)
    }
  }

  /** Course claims plus any assessment banding, for the nodes this aggregate saw complete. */
  private def creditCompleted(userId: String, rootId: String, batchId: String, meta: LpMeta,
                              completedNow: Set[String], ctx: RequestContext): Unit = {
    val fw = meta.competencyFramework
    if (fw.isEmpty || completedNow.isEmpty) return
    val now = System.currentTimeMillis()
    completedNow.filter(_ != rootId).foreach { node =>
      competencyService.onNodeCompleted(userId, fw, node, batchId + ":" + node, isRoot = false, now, ctx)
      creditAssessment(userId, rootId, batchId, fw, node, meta, ctx)
    }
  }

  private def creditAssessment(userId: String, rootId: String, batchId: String, fw: String,
                               courseId: String, meta: LpMeta, ctx: RequestContext): Unit = {
    val questionSets = lpPolicyUtil.questionSetsOf(courseId, meta)
    // assessment rows are keyed at the LP root, which is what the client addressed
    if (questionSets.nonEmpty) competencyService.onAssessed(userId, fw, rootId, batchId, questionSets, ctx)
  }

  private def ensureOptionalityComputed(userId: String, rootId: String, batchId: String, trackable: List[String],
                                        levelByCourse: Map[String, String],
                                        courseComplete: String => Boolean, meta: LpMeta,
                                        ctx: RequestContext): Boolean = {
    if (readOptionality(userId, rootId, batchId, ctx)._2) return true
    val policy = lpPolicyUtil.policyOf(meta)
    if (policy.equalsIgnoreCase("Strict") || trackable.isEmpty) {
      logger.info(ctx, s"viewer.lp: optionality computed(strict) optional=[] | user=$userId root=$rootId")
      writeOptionalNodes(userId, rootId, batchId, Set.empty, ctx); return true
    }
    val levels = ProgressionPolicy.orderedLevels(trackable, levelByCourse)
    val preAssessment = ProgressionPolicy.coursesOfLevel(levels.headOption.getOrElse(""), trackable, levelByCourse)
      .find(c => lpPolicyUtil.isAssessmentCourse(c, meta))
    if (policy.equalsIgnoreCase("Adaptive") && preAssessment.isEmpty) {
      logger.warn(ctx, s"viewer.lp: Adaptive LP has no pre-assessment; halting (misconfigured, opening nothing) | user=$userId root=$rootId", null)
      return false
    }
    // Only Adaptive defers optionality until the pre-assessment is taken (it needs the proven
    // competencies). PriorLearning waives already-completed courses and is not gated on it.
    // `courseComplete` is the caller's predicate, so this sees the LP root's contentstatus too.
    if (policy.equalsIgnoreCase("Adaptive") && preAssessment.exists(pa => !courseComplete(pa))) return true

    val fw = meta.competencyFramework
    if (policy.equalsIgnoreCase("Adaptive") && fw.isEmpty) {
      logger.warn(ctx, s"viewer.lp: Adaptive LP declares no competencyFramework; halting | user=$userId root=$rootId", null)
      return false
    }
    // make sure the gating assessment is banded before the passbook is read
    if (fw.nonEmpty) preAssessment.foreach(pa => creditAssessment(userId, rootId, batchId, fw, pa, meta, ctx))

    val cMeta = competencyService.meta(fw, ctx)
    val heldLevels =
      if (policy.equalsIgnoreCase("Adaptive")) competencyService.heldLevels(userId, ctx).map { case (k, v) => k -> v._2 }
      else Map.empty[String, Int]
    // PriorLearning waives what the learner has already finished. Uses the caller's predicate so
    // a course completed through the path (recorded on the LP root's contentstatus) counts too,
    // not only one with its own child enrolment row.
    val priorCompleted =
      if (policy.equalsIgnoreCase("PriorLearning")) trackable.filter(courseComplete).toSet
      else Set.empty[String]
    val assessmentCourses = lpPolicyUtil.assessmentFlags(trackable, meta).collect { case (c, true) => c }.toSet
    val claimsByCourse = competencyService.claimIndexes(trackable, cMeta, ctx)
    logger.info(ctx, s"viewer.lp: optionality computing($policy) preAssess=${preAssessment.getOrElse("-")} " +
      s"held=${heldLevels.size} priorDone=${priorCompleted.size} | user=$userId root=$rootId")
    writeOptionalNodes(userId, rootId, batchId,
      ProgressionPolicy.computeOptionalNodes(policy, trackable, claimsByCourse, assessmentCourses, heldLevels, priorCompleted), ctx)
    true
  }

  private def writeOptionalNodes(userId: String, rootId: String, batchId: String, optional: Set[String], ctx: RequestContext): Unit = {
    val selectMap = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val updateMap = new util.HashMap[String, AnyRef]() {{
      put("optional_nodes", optional.asJava)
      put("optionality_computed", java.lang.Boolean.TRUE)
    }}
    cassandraOperation.updateRecordV2(enrolKeyspace, enrolTable, selectMap, updateMap, true, ctx)
  }

  /**
   * The waived set and whether optionality has been computed.
   *
   * The flag is persisted rather than held in memory: an empty waived set is a legitimate outcome
   * under Strict and under Adaptive, so "no rows" cannot stand in for "not computed", and a
   * JVM-local latch would neither survive a restart nor be shared across pods.
   */

  private def readOptionality(userId: String, rootId: String, batchId: String, ctx: RequestContext): (List[String], Boolean) = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val rows = cassandraOperation.getRecords(enrolKeyspace, enrolTable, filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isNotEmpty(rows)) {
      val r = rows.get(0)
      val nodes = Option(r.get("optional_nodes")).map(_.asInstanceOf[util.Collection[String]].asScala.toList).getOrElse(List())
      val computed = Option(r.get("optionality_computed")).collect { case b: java.lang.Boolean => b.booleanValue() }.getOrElse(false)
      (nodes, computed)
    } else (List(), false)
  }

  /**
   * The LP root's own `contentstatus` (leaf content id -> status) from its enrolment row.
   *
   * This is where a learner's consumption actually lands: the client addresses the path, so
   * every view call is keyed to the LP's collection and batch. It is the only progress
   * record that exists when no child enrolment does.
   */
  private def rootContentStatus(userId: String, rootId: String, batchId: String,
                                ctx: RequestContext): Map[String, Int] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val rows = cassandraOperation.getRecords(enrolKeyspace, enrolTable, filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isEmpty(rows)) Map.empty[String, Int]
    else Option(rows.get(0).get("contentstatus")).collect {
      case m: util.Map[_, _] => m.asScala.toMap.collect { case (k: String, v: Number) => k -> v.intValue() }
    }.getOrElse(Map.empty[String, Int])
  }

}
