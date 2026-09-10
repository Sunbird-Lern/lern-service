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

  def advance(userId: String, rootId: String, batchId: String, trackable: List[String],
              status: Map[(String, String), Int], ancestorsOf: String => List[String],
              completedNow: Set[String], ctx: RequestContext): Unit = {
    val childBatchOf = (c: String) => batchId + ":" + c
    val courseComplete = (c: String) => status.get((c, childBatchOf(c))).contains(2)

    val meta = lpPolicyUtil.lpMeta(rootId, ctx)
    // Credit what just finished before deciding anything, so waiving sees the current profile.
    creditCompleted(userId, rootId, batchId, meta, completedNow, ctx)

    val levelByCourse = ProgressionPolicy.levelByCourse(trackable, ancestorsOf, rootId)
    if (!ensureOptionalityComputed(userId, rootId, batchId, trackable, levelByCourse, status, meta, ctx)) return
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
      allComplete, status.get((rootId, batchId)).getOrElse(0), meta, trackable, ctx)

    if (allComplete) logger.info(ctx, s"viewer.lp: complete | user=$userId root=$rootId")
  }

  private def writeRootProgress(userId: String, rootId: String, batchId: String, done: Int, total: Int,
                                allComplete: Boolean, currentStatus: Int, meta: LpMeta,
                                trackable: List[String], ctx: RequestContext): Unit = {
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
      val fw = meta.competencyFramework
      // the skills the programme itself declares, credited once on the completion transition.
      // Credit before issuing, so the certificate attests what the learner holds afterwards.
      if (fw.nonEmpty)
        competencyService.onNodeCompleted(userId, fw, rootId, batchId,
          isRoot = true, System.currentTimeMillis(), ctx)
      if (fw.isEmpty) certificateUtil.publishCertificateIssueEvent(userId, rootId, batchId, ctx)
      else {
        val attested = competencyService.attestedSkills(userId, fw, trackable, ctx)
        logger.info(ctx, s"viewer.lp: certificate attests ${attested.size} skills | user=$userId root=$rootId")
        certificateUtil.publishSkillAwareCertificateIssueEvent(userId, rootId, batchId, attested, ctx)
      }
    }
  }

  /** Course skills plus any assessment credit, for the nodes this aggregate saw complete. */
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
                                        status: Map[(String, String), Int], meta: LpMeta,
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
    val childBatchOf = (c: String) => batchId + ":" + c
    val courseComplete = (c: String) => status.get((c, childBatchOf(c))).contains(2)
    // Only Adaptive defers optionality until the pre-assessment is taken (it needs the proven
    // skills). PriorLearning waives already-completed courses and is not gated on it.
    if (policy.equalsIgnoreCase("Adaptive") && preAssessment.exists(pa => !courseComplete(pa))) return true

    val fw = meta.competencyFramework
    if (policy.equalsIgnoreCase("Adaptive") && fw.isEmpty) {
      logger.warn(ctx, s"viewer.lp: Adaptive LP declares no competencyFramework; halting | user=$userId root=$rootId", null)
      return false
    }
    // make sure the gating assessment is credited before the profile is read
    if (fw.nonEmpty) preAssessment.foreach(pa => creditAssessment(userId, rootId, batchId, fw, pa, meta, ctx))

    val cMeta = competencyService.meta(fw, ctx)
    val held =
      if (policy.equalsIgnoreCase("Adaptive")) competencyService.heldSkills(userId, ctx)
      else Set.empty[String]
    val completedCourses = status.collect { case ((c, _), 2) => c }.toSet
    val priorCompleted =
      if (policy.equalsIgnoreCase("PriorLearning")) trackable.filter(completedCourses.contains).toSet
      else Set.empty[String]
    val assessmentCourses = lpPolicyUtil.assessmentFlags(trackable, meta).collect { case (c, true) => c }.toSet
    val skillsByCourse = competencyService.claimsOf(trackable, cMeta, ctx)
    logger.info(ctx, s"viewer.lp: optionality computing($policy) preAssess=${preAssessment.getOrElse("-")} " +
      s"held=${held.size} priorDone=${priorCompleted.size} | user=$userId root=$rootId")
    writeOptionalNodes(userId, rootId, batchId,
      ProgressionPolicy.computeOptionalNodes(policy, trackable, skillsByCourse, assessmentCourses, held, priorCompleted), ctx)
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
}
