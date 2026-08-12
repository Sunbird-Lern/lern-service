package org.sunbird.viewer.actor

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.activity.domain.{ContentStatus, UserContentConsumption, UserEnrolmentAgg}
import org.sunbird.activity.util.{ActivityAggregateUtil, CertificateUtil, HierarchyRelationsUtil}
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.ProjectUtil
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.{Request, RequestContext}

import java.util
import scala.collection.JavaConverters._

/**
 * Sync, in-request recursive rollup for the viewer module. Invoked from viewEnd (per-userId serialized).
 *
 * REUSE: all aggregation math is ActivityAggregateUtil (same calls ActivityAggregatorActor uses).
 * The util treats courseId as the activity_id slot and batchId as the context slot — it does not
 * care about the names. Viewer deltas vs ActivityAggregatorActor:
 *   - optionality is PER-USER: `optional_nodes` from user_enrolments (NOT hierarchy getOptionalNodes).
 *     required = collectionLeafNodes.diff(userOpt) at leaf AND every ancestor level.
 *   - recompute from DB state each call -> idempotent, safe under per-user serialization.
 *
 * ASSUMPTIONS to verify against live schema (v2 snake_case):
 *   - user_content_consumption PK (user_id, collection_id, context_id, content_id); status per content.
 *   - user_enrolments keyed (userid, courseid, batchid) with optional_nodes set<text>.
 *   - user_activity_agg is the aggregate target (activity_id = collection do-id).
 */
class ViewerAggregatorActor extends BaseEnrolmentActor {

  private var cassandraOperation: CassandraOperation = ServiceFactory.getInstance
  private var hierarchyRelationsUtil: HierarchyRelationsUtil = HierarchyRelationsUtil(cassandraOperation)
  private var certificateUtil: CertificateUtil = CertificateUtil()
  private val activityAggUtil = new ActivityAggregateUtil()
  private val lpPolicyUtil: org.sunbird.activity.util.LpPolicyUtil = org.sunbird.activity.util.LpPolicyUtil()
  private val assessmentService = new org.sunbird.assessment.service.CassandraService(Some(cassandraOperation))

  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)
  private val activityAggDBInfo = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB)
  private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
  private val courseBatchDBInfo = Util.dbInfoMap.get(JsonKey.COURSE_BATCH_DB)
  private val CONSUMPTION_TABLE = "user_content_consumption"
  // Course-level certificates on course completion (the LP cert is always issued by the engine).
  // Default true; set course_certificate_enabled=false to suppress course certs only.
  private val courseCertEnabled: Boolean =
    ViewerAggregatorActor.parseCourseCertEnabled(ProjectUtil.getConfigValue("course_certificate_enabled"))
  // LP progression extracted to a focused, injectable engine (SRP); transport behind a dispatcher (OCP).
  // lazy so `context` is set by the time they initialize.
  private lazy val enrolDispatcher: org.sunbird.viewer.engine.EnrolDispatcher = org.sunbird.viewer.engine.EnrolDispatcher(context)
  private lazy val lpEngine = new org.sunbird.viewer.engine.LpProgressionEngine(
    cassandraOperation, enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, lpPolicyUtil, assessmentService, enrolDispatcher, certificateUtil)

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "aggregate" =>
        // Fire-and-forget rollup: log failures (caller already acked); /v1/view/agg force-sync is the repair path.
        try aggregate(request)
        catch { case ex: Exception => logger.error(request.getRequestContext, s"ViewerAggregatorActor.aggregate failed: ${ex.getMessage}", ex) }
        sender().tell(successResponse(), self)
      case _           => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  private def aggregate(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    // accept courseId (else legacy courseId) / batchId (else legacy batchId)
    val courseId = ViewerRequestKeys.courseId(request).orNull
    val batchId = ViewerRequestKeys.batchId(request).orNull
    if (userId == null || courseId == null) {
      logger.warn(ctx, s"ViewerAggregatorActor: missing userId/courseId, skipping", null)
      return
    }

    // trackablenodes non-empty => this root is a Learning Path (structural detection; §Step 2/5).
    val trackable = hierarchyRelationsUtil.getTrackableNodes(courseId, ctx)
    logger.info(ctx, s"viewer.rollup: start | user=$userId course=$courseId batch=$batchId " +
      (if (trackable.nonEmpty) s"LP detected n=${trackable.size}" else "not-an-LP"))

    // 1. Read this user's consumption for the (root) collection+context from viewer ucc, build status map
    val rows = readConsumption(userId, courseId, batchId, ctx)
    if (CollectionUtils.isEmpty(rows)) {
      // No consumption yet. For an LP, still advance (bootstrap: open the first required course).
      if (trackable.nonEmpty) { logger.info(ctx, s"viewer.rollup: no-consumption -> LP bootstrap | user=$userId course=$courseId"); advanceLp(userId, courseId, batchId, trackable, ctx) }
      else logger.info(ctx, s"viewer.rollup: no-consumption skip | user=$userId course=$courseId")
      return
    }
    val contentStatusMap: Map[String, ContentStatus] = activityAggUtil.getContentStatusFromContents(rows)
    val uc = UserContentConsumption(userId, batchId, courseId, contentStatusMap)

    // 2. Per-learner optional COURSES from user_enrolments.optional_nodes (LP policy; course-level).
    val perLearnerOptionalCourses: List[String] = readOptionalNodes(userId, courseId, batchId, ctx)

    // 3. Root leaves + the tree's nodes (via ancestors) — needed before computing effectiveOptional.
    val leafNodes = hierarchyRelationsUtil.getLeafNodes(courseId, courseId, ctx)
    if (leafNodes.isEmpty) {
      logger.warn(ctx, s"ViewerAggregatorActor: no leafNodes for courseId=$courseId; is hierarchy_relations published?", null)
      return
    }
    val ancestors: Map[String, List[String]] = uc.contents.map { case (contentId, content) =>
      (contentId, hierarchyRelationsUtil.getAncestors(courseId, content.contentId, ctx))
    }.toMap
    val childCollections = ancestors.values.flatten.filter(_ != courseId).toList.distinct

    // effectiveOptional LEAVES (§5.1) = author-marked hierarchy `optionalnodes` (content-level, all nodes)
    //   ∪ leaves of per-learner optional courses (course-level, expanded to leaves so the leaf-vs-leaf diff works).
    val treeNodes = courseId :: childCollections
    val hierarchyOptionalLeaves = treeNodes.flatMap(n => hierarchyRelationsUtil.getOptionalNodes(courseId, n, ctx)).distinct
    val optionalCourseLeaves = perLearnerOptionalCourses.flatMap(c => hierarchyRelationsUtil.getLeafNodes(courseId, c, ctx)).distinct
    val effectiveOptional: List[String] = (hierarchyOptionalLeaves ++ optionalCourseLeaves).distinct

    // 4. Aggregates: root + every ancestor node; required per node = its leafNodes − effectiveOptional.
    val courseAgg = activityAggUtil.computeCourseActivityAgg(uc, leafNodes, effectiveOptional, ctx)
    val collectionsWithLeafNodes: Map[String, List[String]] = childCollections.map { col =>
      (col, hierarchyRelationsUtil.getLeafNodes(courseId, col, ctx).diff(effectiveOptional))
    }.toMap
    val moduleAggs = activityAggUtil.computeModuleActivityAgg(uc, courseId, ancestors, collectionsWithLeafNodes, ctx)

    val allAggs: List[UserEnrolmentAgg] = courseAgg.toList ++ moduleAggs

    // 5. Write user_activity_agg (frozen content_status + agg) for root + every node
    writeActivityAggregates(allAggs, ctx)
    logger.info(ctx, s"viewer.rollup: nodes rolled-up n=${allAggs.size} | user=$userId course=$courseId batch=$batchId")

    // 6. Per-node progress: nodeId -> (completedCount, requiredLeaves) for root + every trackable ancestor.
    val nodeProgress = scala.collection.mutable.LinkedHashMap[String, (Int, List[String])]()
    courseAgg.foreach(a => nodeProgress(courseId) = (completedCountOf(a), leafNodes.diff(effectiveOptional)))
    moduleAggs.foreach(a => nodeProgress(a.activityAgg.activity_id) =
      (completedCountOf(a), collectionsWithLeafNodes.getOrElse(a.activityAgg.activity_id, Nil)))

    // 7. Update user_enrolments status for EVERY enrolled node in this tree (approach #1: key off the
    //    child enrolment rows that already exist; root included). Cert fires once, on transition to complete.
    //    Returns the node ids that transitioned to complete (status != 2 -> 2) in THIS pass.
    val completedNow = writeAllNodeEnrolments(userId, courseId, batchId, nodeProgress.toMap, contentStatusMap, ctx)

    // 8. LP progression: for the LP root, advance. For a chained child, bridge to the LP root ONLY when the
    //    child course just COMPLETED this pass (not on every partial view) — the completion is the trigger.
    if (trackable.nonEmpty) advanceLp(userId, courseId, batchId, trackable, ctx)
    else if (completedNow.contains(courseId)) bridgeToRoot(userId, courseId, batchId, ctx)
  }

  /** A child course just completed -> if it belongs to an LP, re-fire the LP-root aggregate so it re-advances. */
  private def bridgeToRoot(userId: String, courseId: String, courseBatchId: String, ctx: RequestContext): Unit =
    parentLpOf(courseId, courseBatchId, ctx).foreach { case (lpId, lpBatch) =>
      val req = new Request(); req.setRequestContext(ctx); req.setOperation("aggregate")
      req.put(JsonKey.USER_ID, userId); req.put("courseId", lpId); req.put("batchId", lpBatch)
      self.tell(req, org.apache.pekko.actor.ActorRef.noSender)
      logger.info(ctx, s"viewer.rollup: child->LP bridge | user=$userId course=$courseId childBatch=$courseBatchId lp=$lpId lpBatch=$lpBatch")
    }

  /**
   * Parent LP of a just-completed course, or None for a standalone course. Resolves from the authoritative
   * structural map (course_batch): strip the ":" prefix to get the LP batch, read course_batch by batchid,
   * and pick the row whose courseid differs from the completed course (that's the LP). Requires the
   * course_batch(batchid) secondary index (see migrations) so this is a keyed read, not a scan — same
   * pattern as user_enrolments_by_batch.
   * ponytail: leans on the ":" child-batch convention (rootBatch:childId) to recover the LP batch id.
   * Ceiling: assumes no standalone batch id contains ":". Upgrade: stamp parent_collection_id/
   * parent_context_id on the course_batch/enrolment row at creation and read those directly (migration-time).
   */
  private def parentLpOf(courseId: String, courseBatchId: String, ctx: RequestContext): Option[(String, String)] =
    ViewerAggregatorActor.resolveParentLp(courseId, courseBatchId, lpBatch => courseBatchRowsByBatchId(lpBatch, ctx))

  /** course_batch rows for a batch id — keyed read via the course_batch(batchid) secondary index. */
  private def courseBatchRowsByBatchId(batchId: String, ctx: RequestContext): util.List[util.Map[String, AnyRef]] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("batchid", batchId) }}
    cassandraOperation.getRecords(courseBatchDBInfo.getKeySpace, courseBatchDBInfo.getTableName,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
  }

  private def completedCountOf(a: UserEnrolmentAgg): Int =
    a.activityAgg.aggregates.getOrElse("completedCount", 0.0).toInt

  // LP progression: read the status snapshot + derive ancestorsOf here, then delegate to LpProgressionEngine.
  private def advanceLp(userId: String, rootId: String, batchId: String,
                        trackable: List[String], ctx: RequestContext): Unit = {
    // One read of this user's enrolments, reused for every completion/enrolment check below. Safe because
    // advanceLp runs AFTER writeAllNodeEnrolments has committed this pass's statuses, so the snapshot is
    // current; collapses the LP's former O(courses) single-row status reads into a single query.
    val status = enrolStatusSnapshot(userId, ctx)
    // Course ancestors aren't published (only leaf ancestors are), so derive a course's chain from one of
    // its leaves: leaf ancestors = [..course, level, root] (root LAST) -> levelOf = last non-root = the level.
    val ancestorsOf = (course: String) =>
      hierarchyRelationsUtil.getLeafNodes(rootId, course, ctx).headOption
        .map(leaf => hierarchyRelationsUtil.getAncestors(rootId, leaf, ctx))
        .getOrElse(List.empty[String])
    lpEngine.advance(userId, rootId, batchId, trackable, status, ancestorsOf, ctx)
  }

  /**
   * All of this user's enrolments as (courseId, batchId) -> status, in one read. Replaces the LP's former
   * per-course status queries. Result rows are camelCase (createResponse); rows without a status map to 0
   * (enrol always sets one) so presence-of-key still answers "is enrolled".
   */
  private def enrolStatusSnapshot(userId: String, ctx: RequestContext): Map[(String, String), Int] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}
    val rows = cassandraOperation.getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    rows.asScala.flatMap { r =>
      for {
        c <- Option(r.get("courseId")).map(_.toString)
        b <- Option(r.get("batchId")).map(_.toString)
      } yield (c, b) -> Option(r.get("status")).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
    }.toMap
  }

  private def writeActivityAggregates(aggs: List[UserEnrolmentAgg], ctx: RequestContext): Unit = {
    val aggQueries = aggs.map(a => activityAggUtil.createActivityAggUpdateMap(a.activityAgg)).asJava
    if (!aggQueries.isEmpty)
      cassandraOperation.batchUpdateWithPutAll(activityAggDBInfo.getKeySpace, activityAggDBInfo.getTableName, aggQueries, ctx)
  }

  /**
   * Approach #1: update user_enrolments status for every node in this tree that has an enrolment row.
   * Matches each row on courseid ∈ tree AND this LP's batchid (§4: standalone enrolments untouched).
   * Cert fires once, only on the transition to complete (status != 2 -> 2).
   */
  private def writeAllNodeEnrolments(userId: String, rootId: String, batchId: String,
                                     nodeProgress: Map[String, (Int, List[String])],
                                     contentStatusMap: Map[String, ContentStatus], ctx: RequestContext): Set[String] = {
    val completedNow = scala.collection.mutable.Set[String]()
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}
    val enrolRows = cassandraOperation.getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]
    enrolRows.asScala.foreach { row =>
      // createResponse maps columns to camelCase field names (courseid->courseId, batchid->batchId
      // via cassandratablecolumn.properties), so result rows are always camelCase.
      val nodeId = Option(row.get("courseId")).map(_.toString).orNull
      val nodeCtx = Option(row.get("batchId")).map(_.toString).orNull
      // This LP only (root=batchId, child=batchId:childId); a standalone enrolment's batchid differs (§4).
      val expectedCtx = if (nodeId == rootId) batchId else batchId + ":" + nodeId
      nodeProgress.get(nodeId).filter(_ => nodeCtx == expectedCtx).foreach { case (completedCount, requiredLeaves) =>
        val required = requiredLeaves.size
        val status = activityAggUtil.getCompletionStatus(completedCount, required)
        val pct = activityAggUtil.getCompletionPercentage(completedCount, required)
        val currentStatus = Option(row.get("status")).map(_.asInstanceOf[Number].intValue()).getOrElse(0)
        val nodeContentStatus: Map[String, AnyRef] =
          requiredLeaves.flatMap(l => contentStatusMap.get(l).map(cs => l -> Integer.valueOf(cs.status).asInstanceOf[AnyRef])).toMap
        // updateRecordV2 replaces the whole contentstatus column — merge into the row's existing map so a
        // root-keyed rollup that only sees some leaves doesn't clobber the rest (see mergeContentStatus).
        val mergedContentStatus = ViewerAggregatorActor.mergeContentStatus(row.get("contentStatus"), nodeContentStatus)
        val selectMap = new util.HashMap[String, AnyRef]() {{
          put("userid", userId); put("courseid", nodeId); put("batchid", nodeCtx)
        }}
        val updateMap = new util.HashMap[String, AnyRef]() {{
          put("progress", Integer.valueOf(completedCount))
          put("status", Integer.valueOf(status))
          put("completionpercentage", Integer.valueOf(pct))
          put("contentstatus", mergedContentStatus)
          if (status == 2 && currentStatus != 2) put("completedon", new java.util.Date())
        }}
        cassandraOperation.updateRecordV2(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, selectMap, updateMap, true, ctx)
        if (status == 2 && currentStatus != 2) {
          completedNow += nodeId
          if (courseCertEnabled) {
            logger.info(ctx, s"viewer.rollup: node completed -> cert | user=$userId course=$nodeId batch=$nodeCtx")
            certificateUtil.publishCertificateIssueEvent(userId, nodeId, nodeCtx, ctx)
          } else logger.info(ctx, s"viewer.rollup: node completed, course cert suppressed (course_certificate_enabled=false) | user=$userId course=$nodeId batch=$nodeCtx")
        }
      }
    }
    completedNow.toSet
  }

  /**
   * Read this user's ucc rows for the collection, scoped to the context.
   * (userid, courseid, batchid) is a clustering-prefix slice on PK
   * (userid, courseid, batchid, contentid) -> efficient, no scan.
   * batchId omitted only when absent (no-context viewer), falling back to collection-wide read.
   */
  private def readConsumption(userId: String, courseId: String, batchId: String, ctx: RequestContext): util.List[util.Map[String, AnyRef]] = {
    val filters = new util.HashMap[String, AnyRef]() {{
      put("userid", userId)
      put("courseid", courseId)
      if (batchId != null) put("batchid", batchId)
    }}
    val response = cassandraOperation.getRecords(consumptionDBInfo.getKeySpace, CONSUMPTION_TABLE,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
    response.getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]
  }

  /** Per-user optional_nodes from user_enrolments (empty for strict policy). */
  private def readOptionalNodes(userId: String, courseId: String, batchId: String, ctx: RequestContext): List[String] = {
    val filters = new util.HashMap[String, AnyRef]() {{
      put("userid", userId)
      put("courseid", courseId)
      put("batchid", batchId)
    }}
    val response = cassandraOperation.getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
    val rows = response.getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isNotEmpty(rows)) {
      Option(rows.get(0).get("optional_nodes"))
        .map(_.asInstanceOf[util.Collection[String]].asScala.toList)
        .getOrElse(List())
    } else List()
  }

  // for tests
  def configure(ops: CassandraOperation, hru: HierarchyRelationsUtil, cu: CertificateUtil): ViewerAggregatorActor = {
    cassandraOperation = ops; hierarchyRelationsUtil = hru; certificateUtil = cu; this
  }
}

object ViewerAggregatorActor {
  // Merge freshly computed per-leaf statuses into the enrolment row's existing contentstatus map instead of
  // replacing it: updateRecordV2 overwrites the whole column, so a root-keyed rollup that only sees some
  // leaves must not wipe the rest (C2). `existing` may be null; fresh values win on key conflicts.
  private[actor] def mergeContentStatus(existing: AnyRef, fresh: Map[String, AnyRef]): java.util.Map[String, AnyRef] = {
    val merged = new java.util.HashMap[String, AnyRef]()
    Option(existing).foreach(m => merged.putAll(m.asInstanceOf[java.util.Map[String, AnyRef]]))
    fresh.foreach { case (k, v) => merged.put(k, v) }
    merged
  }

  // Parent LP of a completed course: strip the ":" child-batch prefix to get the LP batch, read course_batch
  // by that batch id, and pick the row whose courseid differs from the completed course (a course may hold
  // its own records under the same batch string; only a DIFFERENT courseid is the parent LP). None for a
  // standalone (colon-free) batch — fetchByBatchId is not invoked in that case.
  private[actor] def resolveParentLp(courseId: String, courseBatchId: String,
      fetchByBatchId: String => java.util.List[java.util.Map[String, AnyRef]]): Option[(String, String)] = {
    if (courseBatchId == null || !courseBatchId.contains(":")) None
    else {
      val lpBatch = courseBatchId.substring(0, courseBatchId.indexOf(":"))
      fetchByBatchId(lpBatch).asScala
        .flatMap(r => Option(r.get("courseId")).map(_.toString))
        .find(_ != courseId).map(lpId => (lpId, lpBatch))
    }
  }

  // Course-cert toggle: default true; only the literal "false" disables it (the LP cert is unaffected).
  private[actor] def parseCourseCertEnabled(cfg: String): Boolean = !"false".equalsIgnoreCase(Option(cfg).getOrElse(""))
}
