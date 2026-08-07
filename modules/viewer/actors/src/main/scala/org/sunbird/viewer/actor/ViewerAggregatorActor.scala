package org.sunbird.viewer.actor

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.activity.domain.{ContentStatus, UserContentConsumption, UserEnrolmentAgg}
import org.sunbird.activity.util.{ActivityAggregateUtil, CertificateUtil, HierarchyRelationsUtil}
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.common.ProjectUtil
import org.sunbird.http.HttpClientUtil
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.viewer.util.ProgressionPolicy

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

  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)
  private val activityAggDBInfo = Util.dbInfoMap.get(JsonKey.GROUP_ACTIVITY_DB)
  private val consumptionDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_CONTENT_DB)
  private val CONSUMPTION_TABLE = "user_content_consumption"

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

    // 1. Read this user's consumption for the (root) collection+context from viewer ucc, build status map
    val rows = readConsumption(userId, courseId, batchId, ctx)
    if (CollectionUtils.isEmpty(rows)) {
      // No consumption yet. For an LP, still advance (bootstrap: open the first required course).
      if (trackable.nonEmpty) advanceLp(userId, courseId, batchId, trackable, ctx)
      else logger.info(ctx, s"ViewerAggregatorActor: no consumption for userId=$userId courseId=$courseId")
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

    // 6. Per-node progress: nodeId -> (completedCount, requiredLeaves) for root + every trackable ancestor.
    val nodeProgress = scala.collection.mutable.LinkedHashMap[String, (Int, List[String])]()
    courseAgg.foreach(a => nodeProgress(courseId) = (completedCountOf(a), leafNodes.diff(effectiveOptional)))
    moduleAggs.foreach(a => nodeProgress(a.activityAgg.activity_id) =
      (completedCountOf(a), collectionsWithLeafNodes.getOrElse(a.activityAgg.activity_id, Nil)))

    // 7. Update user_enrolments status for EVERY enrolled node in this tree (approach #1: key off the
    //    child enrolment rows that already exist; root included). Cert fires once, on transition to complete.
    writeAllNodeEnrolments(userId, courseId, batchId, nodeProgress.toMap, contentStatusMap, ctx)

    // 8. LP progression (only when this root is an LP): optionality once, open next course(s), credit at completion.
    if (trackable.nonEmpty) advanceLp(userId, courseId, batchId, trackable, ctx)
  }

  private def completedCountOf(a: UserEnrolmentAgg): Int =
    a.activityAgg.aggregates.getOrElse("completedCount", 0.0).toInt

  // ─────────────────────────── LP progression (the engine) ───────────────────────────
  // Pure decisions come from ProgressionPolicy; this orchestrates reads/writes. Strict is fully
  // functional. Adaptive/PriorLearning are wired but their skill inputs (policy source, se_skills,
  // diagnostic assessment scores) are marked VERIFY-ON-DEPLOY — they default to "no skills" so the
  // system compiles and behaves as Strict until those integrations are wired against the live env.

  private val USER_SKILLS_TABLE = "user_skills"

  private def advanceLp(userId: String, rootId: String, batchId: String,
                        trackable: List[String], ctx: RequestContext): Unit = {
    // One read of this user's enrolments, reused for every completion/enrolment check below. Safe because
    // advanceLp runs AFTER writeAllNodeEnrolments has committed this pass's statuses, so the snapshot is
    // current; collapses the LP's former O(courses) single-row status reads into a single query.
    val status = enrolStatusSnapshot(userId, ctx)
    val childBatchOf = (c: String) => batchId + ":" + c
    val courseComplete = (c: String) => status.get((c, childBatchOf(c))).contains(2)

    ensureOptionalityComputed(userId, rootId, batchId, trackable, courseComplete, ctx)
    val optional = readOptionalNodes(userId, rootId, batchId, ctx).toSet
    val ancestorsOf = (n: String) => hierarchyRelationsUtil.getAncestors(rootId, n, ctx)
    val levels = ProgressionPolicy.orderedLevels(trackable, ancestorsOf, rootId)

    // Level complete = all its required (non-optional) courses complete (empty required set = complete, §5).
    // Derived from persisted enrolment status only, so it's recompute-safe (force-sync repairs identically).
    def levelComplete(level: String): Boolean =
      ProgressionPolicy.coursesOfLevel(level, trackable, ancestorsOf, rootId).filterNot(optional.contains).forall(courseComplete)

    // Open first incomplete level: enrol all its optionals up front + the next single required course (§5).
    levels.find(l => !levelComplete(l)).foreach { level =>
      val courses = ProgressionPolicy.coursesOfLevel(level, trackable, ancestorsOf, rootId)
      val nextRequired = courses.filterNot(optional.contains).find(c => !courseComplete(c))
      (courses.filter(optional.contains) ++ nextRequired.toList).foreach { c =>
        val childBatch = childBatchOf(c)
        if (!status.contains((c, childBatch))) internalEnrol(userId, c, childBatch, ctx)
      }
    }

    // LP completion = every level complete -> credit durable skills (once; creditSkills no-ops if nothing new).
    if (levels.nonEmpty && levels.forall(levelComplete)) creditSkills(userId, rootId, trackable, ctx)
  }

  private def ensureOptionalityComputed(userId: String, rootId: String, batchId: String, trackable: List[String],
                                        courseComplete: String => Boolean, ctx: RequestContext): Unit = {
    // Compute once (§Step 4); optionalityComputed remembers empty results without a DB column.
    if (optionalityComputed(userId, rootId, batchId, ctx)) return
    val policy = policyOf(rootId, ctx)
    if (policy.equalsIgnoreCase("Strict") || trackable.isEmpty) { writeOptionalNodes(userId, rootId, batchId, Set.empty, ctx); return }
    val diagnostic = trackable.head
    val hasDiagnostic = isAssessmentCourse(diagnostic, ctx)
    if (hasDiagnostic && !courseComplete(diagnostic)) return // wait for the diagnostic
    val prior = if (policy.equalsIgnoreCase("PriorLearning")) readUserSkills(userId, ctx) else Set.empty[String]
    val fromDiag = if (hasDiagnostic) skillsFromAssessment(userId, rootId, diagnostic, ctx) else Set.empty[String]
    val achieved = prior ++ fromDiag
    val meta = courseMeta(trackable, ctx)
    val assessmentCourses = meta.collect { case (c, (_, true)) => c }.toSet
    val skillsByCourse = meta.map { case (c, (s, _)) => c -> s }
    writeOptionalNodes(userId, rootId, batchId,
      ProgressionPolicy.computeOptionalNodes(policy, trackable, skillsByCourse, assessmentCourses, achieved), ctx)
  }

  // VERIFY-ON-DEPLOY: policy source. Read the LP's policy from collection/batch metadata; absent => Strict.
  private def policyOf(rootId: String, ctx: RequestContext): String = "Strict"
  // VERIFY-ON-DEPLOY: assessment detection. Needs a Practice-Question-Set child via /v3/search or content_hierarchy.
  private def isAssessmentCourse(courseId: String, ctx: RequestContext): Boolean = false
  // VERIFY-ON-DEPLOY: per-course skills + assessment flag via /v3/search. Skills = framework last-category
  // terms (se_<category>Ids), not an se_skills field (see design §6).
  private def courseMeta(trackable: List[String], ctx: RequestContext): Map[String, (Set[String], Boolean)] =
    trackable.map(c => c -> (Set.empty[String], isAssessmentCourse(c, ctx))).toMap
  // VERIFY-ON-DEPLOY: achieved = assessment_aggregator best attempts × question skill ids (all correct); ids = framework last-category terms (design §6).
  private def skillsFromAssessment(userId: String, rootId: String, courseId: String, ctx: RequestContext): Set[String] = Set.empty

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

  /**
   * System-driven enrol via the standard `enrol` op. MONOLITH -> in-JVM message; DISTRIBUTED -> POST
   * /v1/course/enroll. Re-enrol safe: advanceLp only enrols courses absent from the snapshot.
   */
  private def isMonolith: Boolean = !"distributed".equalsIgnoreCase(ProjectUtil.getConfigValue("deployment_mode")) // default monolith

  private def internalEnrol(userId: String, courseId: String, batchId: String, ctx: RequestContext): Unit = {
    if (isMonolith) {
      val req = new Request()
      req.setRequestContext(ctx)
      req.setRequestId("system")   // enrol stores this as addedBy (via context REQUEST_ID)
      req.setOperation("enrol")
      req.put(JsonKey.USER_ID, userId); req.put(JsonKey.COURSE_ID, courseId); req.put(JsonKey.BATCH_ID, batchId)
      // VERIFY-ON-DEPLOY: bound path of the enrolment actor in the monolith actor system.
      val path = Option(ProjectUtil.getConfigValue("enrolment_actor_path")).filter(_.nonEmpty).getOrElse("/user/course-enrolment-actor")
      // noSender: fire-and-forget; the enrol actor's success reply must NOT bounce back to this actor
      // (it only handles "aggregate" Requests) — let the reply go to deadLetters.
      context.actorSelection(path).tell(req, org.apache.pekko.actor.ActorRef.noSender)
    } else {
      // DISTRIBUTED: call the enrolment service over HTTP (full enrol op).
      // VERIFY-ON-DEPLOY: use a system-enrol endpoint (not the public one that fans out/notifies) + forward auth token.
      val base = Option(ProjectUtil.getConfigValue("enrolment_service_base_url")).filter(_.nonEmpty).getOrElse("http://lern-service:9000")
      val body = s"""{"request":{"userId":"$userId","courseId":"$courseId","batchId":"$batchId"}}"""
      val headers = new util.HashMap[String, String]() {{
        put("Content-Type", "application/json")
        // System-driven enrol: authenticate with the configured system token (else 401 in distributed mode).
        Option(ProjectUtil.getConfigValue("viewer_system_auth_token")).filter(_.nonEmpty)
          .foreach(t => put("x-authenticated-user-token", t))
      }}
      HttpClientUtil.post(base + "/v1/course/enroll", body, headers, ctx)
    }
    logger.info(ctx, s"ViewerAggregatorActor: system-enrol requested course=$courseId ctx=$batchId user=$userId mode=${ProjectUtil.getConfigValue("deployment_mode")}")
  }

  private def writeOptionalNodes(userId: String, rootId: String, batchId: String, optional: Set[String], ctx: RequestContext): Unit = {
    val selectMap = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("courseid", rootId); put("batchid", batchId) }}
    val updateMap = new util.HashMap[String, AnyRef]() {{ put("optional_nodes", optional.asJava) }}
    cassandraOperation.updateRecordV2(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, selectMap, updateMap, true, ctx)
    ViewerAggregatorActor.markOptionalityComputed(userId, rootId, batchId) // remember empty results too (no DB column)
  }

  /** Computed? Non-empty optional_nodes is self-evident; an empty result is remembered in an in-process memo. */
  private def optionalityComputed(userId: String, rootId: String, batchId: String, ctx: RequestContext): Boolean =
    readOptionalNodes(userId, rootId, batchId, ctx).nonEmpty ||
      ViewerAggregatorActor.isOptionalityComputed(userId, rootId, batchId)

  private def readUserSkills(userId: String, ctx: RequestContext): Set[String] = {
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}
    val rows = cassandraOperation.getRecords(enrolmentDBInfo.getKeySpace, USER_SKILLS_TABLE,
      filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
      .getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    if (CollectionUtils.isNotEmpty(rows))
      Option(rows.get(0).get("skills")).map(_.asInstanceOf[util.Collection[String]].asScala.toSet).getOrElse(Set.empty)
    else Set.empty
  }

  // Durable skill credit — ONCE at LP completion. Read-union-upsert (portable; no set-append needed).
  private def creditSkills(userId: String, rootId: String, trackable: List[String], ctx: RequestContext): Unit = {
    val earned = trackable.filter(c => isAssessmentCourse(c, ctx)).flatMap(c => skillsFromAssessment(userId, rootId, c, ctx)).toSet
    if (earned.isEmpty) return // no-op until se_skills is wired (VERIFY-ON-DEPLOY)
    val existing = readUserSkills(userId, ctx)
    val merged = existing ++ earned
    if (merged.size == existing.size) return // nothing new — credit already granted (advanceLp calls this every completed pass)
    val row = new util.HashMap[String, AnyRef]() {{ put("userid", userId); put("skills", merged.asJava) }}
    cassandraOperation.insertRecord(enrolmentDBInfo.getKeySpace, USER_SKILLS_TABLE, row.asInstanceOf[util.Map[String, AnyRef]], ctx)
    logger.info(ctx, s"ViewerAggregatorActor: credited ${earned.size} skills to user=$userId for LP=$rootId")
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
                                     contentStatusMap: Map[String, ContentStatus], ctx: RequestContext): Unit = {
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
          logger.info(ctx, s"ViewerAggregatorActor: node completed userId=$userId courseId=$nodeId; issuing cert")
          certificateUtil.publishCertificateIssueEvent(userId, nodeId, nodeCtx, ctx)
        }
      }
    }
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

  // JVM-wide memo of enrolments whose (empty) LP optionality is computed, so we don't recompute each pass.
  // ponytail: unbounded set, entries live for the process lifetime; add a size cap / TTL only if it grows.
  private val optionalityDone: java.util.Set[String] = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  private def optKey(userId: String, rootId: String, batchId: String): String = s"$userId:$rootId:$batchId"
  def markOptionalityComputed(userId: String, rootId: String, batchId: String): Unit =
    optionalityDone.add(optKey(userId, rootId, batchId))
  def isOptionalityComputed(userId: String, rootId: String, batchId: String): Boolean =
    optionalityDone.contains(optKey(userId, rootId, batchId))
}
