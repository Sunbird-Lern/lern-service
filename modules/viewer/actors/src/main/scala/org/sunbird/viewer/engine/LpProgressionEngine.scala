package org.sunbird.viewer.engine

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.activity.util.{CertificateUtil, LpPolicyUtil}
import org.sunbird.assessment.service.CassandraService
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext
import org.sunbird.viewer.util.ProgressionPolicy

import java.util
import scala.collection.JavaConverters._

/** LP progression: compute optionality once, open the next course(s) level-by-level via EnrolDispatcher,
 *  credit skills at completion. Pure decisions in ProgressionPolicy; caller supplies status + ancestorsOf. */
class LpProgressionEngine(cassandraOperation: CassandraOperation,
                          enrolKeyspace: String, enrolTable: String,
                          lpPolicyUtil: LpPolicyUtil,
                          assessmentService: CassandraService,
                          dispatcher: EnrolDispatcher,
                          certificateUtil: CertificateUtil) {

  private val logger = new LoggerUtil(classOf[LpProgressionEngine])
  private val USER_SKILLS_TABLE = "user_skills"

  // status: (courseId,batchId)->status for all the user's enrolments; ancestorsOf: course->chain (root LAST).
  def advance(userId: String, rootId: String, batchId: String, trackable: List[String],
              status: Map[(String, String), Int], ancestorsOf: String => List[String], ctx: RequestContext): Unit = {
    val childBatchOf = (c: String) => batchId + ":" + c
    val courseComplete = (c: String) => status.get((c, childBatchOf(c))).contains(2)

    val levelByCourse = ProgressionPolicy.levelByCourse(trackable, ancestorsOf, rootId) // ancestorsOf runs once per course
    // Halt only when optionality can't be established (Adaptive misconfigured: no pre-assessment) — open nothing.
    if (!ensureOptionalityComputed(userId, rootId, batchId, trackable, levelByCourse, status, ctx)) return
    val optional = readOptionalNodes(userId, rootId, batchId, ctx).toSet
    val levels = ProgressionPolicy.orderedLevels(trackable, levelByCourse)

    // Level complete = all its required (non-optional) courses complete (empty required set = complete, §5).
    def levelComplete(level: String): Boolean =
      ProgressionPolicy.coursesOfLevel(level, trackable, levelByCourse).filterNot(optional.contains).forall(courseComplete)

    // Open first incomplete level: enrol all its optionals up front + the next single required course (§5).
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

    // Root progress/status/cert from trackable-course completion: the root's own leaf consumption is empty
    // (leaves are consumed under child batches), so derive its progress from how many required courses are done.
    val requiredCourses = trackable.filterNot(optional.contains)
    val allComplete = levels.nonEmpty && levels.forall(levelComplete)
    writeRootProgress(userId, rootId, batchId, requiredCourses.count(courseComplete), requiredCourses.size,
      allComplete, status.get((rootId, batchId)).getOrElse(0), ctx)

    if (allComplete) {
      logger.info(ctx, s"viewer.lp: complete | user=$userId root=$rootId")
      creditSkills(userId, rootId, batchId, trackable, ctx)
    }
  }

  // Update the LP-root user_enrolments row (progress/%/status; completedon on the 2-transition) and fire the
  // LP certificate once when status first reaches 2. Partial update — does not touch contentstatus.
  private def writeRootProgress(userId: String, rootId: String, batchId: String, done: Int, total: Int,
                                allComplete: Boolean, currentStatus: Int, ctx: RequestContext): Unit = {
    val pct = if (total <= 0) 100 else math.min(100, done * 100 / total)
    val rootStatus = if (allComplete) 2 else if (done > 0) 1 else 0
    val select = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val update = new util.HashMap[String, AnyRef]() {{
      put("progress", Integer.valueOf(done)); put("completionpercentage", Integer.valueOf(pct)); put("status", Integer.valueOf(rootStatus))
      if (rootStatus == 2 && currentStatus != 2) put("completedon", new java.util.Date())
    }}
    cassandraOperation.updateRecordV2(enrolKeyspace, enrolTable, select, update, true, ctx)
    logger.info(ctx, s"viewer.lp: root progress | user=$userId root=$rootId done=$done/$total pct=$pct status=$rootStatus")
    if (rootStatus == 2 && currentStatus != 2) certificateUtil.publishCertificateIssueEvent(userId, rootId, batchId, ctx)
  }

  // Returns whether advance() may open courses; false ONLY for a misconfigured Adaptive LP (no pre-assessment).
  private def ensureOptionalityComputed(userId: String, rootId: String, batchId: String, trackable: List[String],
                                        levelByCourse: Map[String, String],
                                        status: Map[(String, String), Int], ctx: RequestContext): Boolean = {
    if (optionalityComputed(userId, rootId, batchId, ctx)) return true
    val meta = lpPolicyUtil.lpMeta(rootId, ctx)
    val policy = lpPolicyUtil.policyOf(meta)
    if (policy.equalsIgnoreCase("Strict") || trackable.isEmpty) {
      logger.info(ctx, s"viewer.lp: optionality computed(strict) optional=[] | user=$userId root=$rootId")
      writeOptionalNodes(userId, rootId, batchId, Set.empty, ctx); return true
    }
    // pre-assessment = an assessment course in the FIRST level (seeds achieved skills before waivers).
    val levels = ProgressionPolicy.orderedLevels(trackable, levelByCourse)
    val preAssessment = ProgressionPolicy.coursesOfLevel(levels.headOption.getOrElse(""), trackable, levelByCourse)
      .find(c => lpPolicyUtil.isAssessmentCourse(c, meta))
    if (policy.equalsIgnoreCase("Adaptive") && preAssessment.isEmpty) {
      logger.warn(ctx, s"viewer.lp: Adaptive LP has no pre-assessment; halting (misconfigured, opening nothing) | user=$userId root=$rootId", null)
      return false
    }
    val childBatchOf = (c: String) => batchId + ":" + c
    val courseComplete = (c: String) => status.get((c, childBatchOf(c))).contains(2)
    // Pre-assessment not done yet: don't compute waivers, but let advance open it (it's the first-level course).
    if (preAssessment.exists(pa => !courseComplete(pa))) return true
    val achieved = preAssessment.map(pa => skillsFromAssessment(userId, rootId, pa, batchId, ctx)).getOrElse(Set.empty)
    // PriorLearning: a content course already completed under ANY batch is optional directly.
    val completedCourses = status.collect { case ((c, _), 2) => c }.toSet // O(status) once, not O(trackable×status)
    val priorCompleted =
      if (policy.equalsIgnoreCase("PriorLearning")) trackable.filter(completedCourses.contains).toSet
      else Set.empty[String]
    val cMeta = lpPolicyUtil.courseMeta(trackable, meta)
    val assessmentCourses = cMeta.collect { case (c, (_, true)) => c }.toSet
    val skillsByCourse = cMeta.map { case (c, (s, _)) => c -> s }
    logger.info(ctx, s"viewer.lp: optionality computing($policy) preAssess=${preAssessment.getOrElse("-")} achieved=${achieved.size} priorDone=${priorCompleted.size} | user=$userId root=$rootId")
    writeOptionalNodes(userId, rootId, batchId,
      ProgressionPolicy.computeOptionalNodes(policy, trackable, skillsByCourse, assessmentCourses, achieved, priorCompleted), ctx)
    true
  }

  private def isAssessmentCourse(rootId: String, courseId: String, ctx: RequestContext): Boolean =
    lpPolicyUtil.isAssessmentCourse(courseId, lpPolicyUtil.lpMeta(rootId, ctx))

  // <category> terms of the questions the learner got fully correct in the pre-assessment's question set(s).
  private def skillsFromAssessment(userId: String, rootId: String, courseId: String, batchId: String, ctx: RequestContext): Set[String] = {
    val meta = lpPolicyUtil.lpMeta(rootId, ctx)
    val childBatch = batchId + ":" + courseId
    val correct = lpPolicyUtil.questionSetsOf(courseId, meta).flatMap { qs =>
      val attempts = assessmentService.getUserAssessments(userId, courseId, childBatch, qs, ctx)
      if (attempts.isEmpty) Nil
      else attempts.maxBy(_.totalScore).questions.collect { case q if q.maxScore > 0 && q.score == q.maxScore => q.questionId }
    }.distinct
    lpPolicyUtil.skillsOfQuestions(correct, meta)
  }

  // Durable skill credit — ONCE at LP completion. Read-union-upsert (portable; no set-append needed).
  private def creditSkills(userId: String, rootId: String, batchId: String, trackable: List[String], ctx: RequestContext): Unit = {
    val earned = trackable.filter(c => isAssessmentCourse(rootId, c, ctx)).flatMap(c => skillsFromAssessment(userId, rootId, c, batchId, ctx)).toSet
    if (earned.isEmpty) return
    val existing = readUserSkills(userId, ctx)
    val merged = existing ++ earned
    if (merged.size == existing.size) return // nothing new — credit already granted
    val row = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("skills", merged.asJava) }}
    cassandraOperation.insertRecord(enrolKeyspace, USER_SKILLS_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
    logger.info(ctx, s"LpProgressionEngine: credited ${earned.size} skills to user=$userId for LP=$rootId")
  }

  private def writeOptionalNodes(userId: String, rootId: String, batchId: String, optional: Set[String], ctx: RequestContext): Unit = {
    val selectMap = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val updateMap = new util.HashMap[String, AnyRef]() {{ put("optional_nodes", optional.asJava) }}
    cassandraOperation.updateRecordV2(enrolKeyspace, enrolTable, selectMap, updateMap, true, ctx)
    LpProgressionEngine.markOptionalityComputed(userId, rootId, batchId) // remember empty results too (no DB column)
  }

  /** Computed? Non-empty optional_nodes is self-evident; an empty result is remembered in an in-process memo. */
  private def optionalityComputed(userId: String, rootId: String, batchId: String, ctx: RequestContext): Boolean =
    readOptionalNodes(userId, rootId, batchId, ctx).nonEmpty ||
      LpProgressionEngine.isOptionalityComputed(userId, rootId, batchId)

  private def readOptionalNodes(userId: String, rootId: String, batchId: String, ctx: RequestContext): List[String] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val rows = cassandraOperation.getRecords(enrolKeyspace, enrolTable, filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isNotEmpty(rows))
      Option(rows.get(0).get("optional_nodes")).map(_.asInstanceOf[util.Collection[String]].asScala.toList).getOrElse(List())
    else List()
  }

  private def readUserSkills(userId: String, ctx: RequestContext): Set[String] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}
    val rows = cassandraOperation.getRecords(enrolKeyspace, USER_SKILLS_TABLE, filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isNotEmpty(rows))
      Option(rows.get(0).get("skills")).map(_.asInstanceOf[util.Collection[String]].asScala.toSet).getOrElse(Set.empty)
    else Set.empty
  }
}

object LpProgressionEngine {
  // JVM-wide memo so empty-optionality isn't recomputed each pass. ponytail: unbounded; add a cap if it grows.
  private val optionalityDone: java.util.Set[String] = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  private def optKey(userId: String, rootId: String, batchId: String): String = s"$userId:$rootId:$batchId"
  def markOptionalityComputed(userId: String, rootId: String, batchId: String): Unit =
    optionalityDone.add(optKey(userId, rootId, batchId))
  def isOptionalityComputed(userId: String, rootId: String, batchId: String): Boolean =
    optionalityDone.contains(optKey(userId, rootId, batchId))
}
