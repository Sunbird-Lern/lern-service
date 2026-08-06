package org.sunbird.viewer.actor

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.sunbird.common.ProjectUtil
import org.sunbird.enrolments.BaseEnrolmentActor
import org.sunbird.helper.ServiceFactory
import org.sunbird.keys.JsonKey
import org.sunbird.learner.util.Util
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response
import org.sunbird.utils.CloudStorageUtil

import java.util
import scala.collection.JavaConverters._

/**
 * Summary APIs for the viewer module — enrolment-level (distinct from the per-content view actor).
 * Reads/deletes user_enrolments only; never writes consumption. Raw ucc reads (viewRead) live on
 * ViewConsumptionActor.
 *
 *   summaryRead   -> per-enrolment progress/status/contentstatus for one collection
 *   summaryList   -> all enrolment summaries for a user
 *   summaryDelete -> delete enrolment rows (all, or one collection+context)
 */
class ViewerSummaryActor extends BaseEnrolmentActor {

  private var cassandraOperation = ServiceFactory.getInstance
  private val enrolmentDBInfo = Util.dbInfoMap.get(JsonKey.LEARNER_COURSE_DB)

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "summaryRead"     => summaryRead(request)
      case "summaryList"     => summaryList(request)
      case "summaryDownload" => summaryDownload(request)
      case "summaryDelete"   => summaryDelete(request)
      case _                 => onReceiveUnsupportedOperation(request.getOperation)
    }
  }

  /** Per-enrolment progress/status from user_enrolments (identified by courseId). */
  private def summaryRead(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = request.get(JsonKey.USER_ID).asInstanceOf[String]
    val courseId = ViewerRequestKeys.courseId(request).orNull
    val batchId = ViewerRequestKeys.batchId(request).orNull

    // Identified by courseId (courseid). user_enrolments carries progress/status/completionpercentage
    // + per-content contentstatus for the enrolment — no activity_type needed.
    val enrolFilters = new util.HashMap[String, AnyRef]()
    enrolFilters.put("userid", userId)
    if (StringUtils.isNotBlank(courseId)) enrolFilters.put("courseid", courseId)
    if (StringUtils.isNotBlank(batchId)) enrolFilters.put("batchid", batchId)
    val enrolments = getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, enrolFilters, ctx)

    val response = new Response
    response.put(JsonKey.RESPONSE, enrolments)
    sender().tell(response, self)
  }

  /** All enrolment summaries for a user (partition by user_id). */
  private def summaryList(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = Option(request.get(JsonKey.USER_ID).asInstanceOf[String])
      .getOrElse(request.get("userId").asInstanceOf[String])
    val filters = new util.HashMap[String, AnyRef]()
    filters.put("userid", userId)
    val enrolments = getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, filters, ctx)
    val response = new Response
    response.put(JsonKey.RESPONSE, enrolments)
    sender().tell(response, self)
  }

  /**
   * Download a user's enrolment summaries. format=json (default) returns the rows under "response";
   * format=csv uploads the CSV to cloud storage (generic CloudStorageUtil) and returns its "url".
   */
  private def summaryDownload(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = Option(request.get(JsonKey.USER_ID).asInstanceOf[String])
      .getOrElse(request.get("userId").asInstanceOf[String])
    val format = Option(request.get("format").asInstanceOf[String]).map(_.toLowerCase).getOrElse("json")
    val filters = new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}
    val enrolments = getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, filters, ctx)
    val response = new Response
    response.put("format", format)
    if (format == "csv") response.put("url", uploadSummaryCsv(userId, toCsv(enrolments)))
    else response.put(JsonKey.RESPONSE, enrolments)
    sender().tell(response, self)
  }

  /**
   * Write the summary CSV to a temp file and upload it to cloud storage; return the object URL.
   * Provider-agnostic via CloudStorageUtil (StorageServiceFactory). All config-driven, nothing hardcoded:
   *   sunbird_cloud_service_provider (provider), sunbird_content_cloud_storage_container (existing container),
   *   viewer_summary_upload_path (object-key prefix; blank = container root). Object = <prefix>/<userId>_viewer_summary.csv
   */
  private def uploadSummaryCsv(userId: String, csv: String): String = {
    val storageType = ProjectUtil.getConfigValue("sunbird_cloud_service_provider")
    val container = ProjectUtil.getConfigValue("sunbird_content_cloud_storage_container")
    val prefix = Option(ProjectUtil.getConfigValue("viewer_summary_upload_path")).getOrElse("").trim.stripSuffix("/")
    val objectKey = (if (StringUtils.isNotBlank(prefix)) prefix + "/" else "") + userId + "_viewer_summary.csv"
    val tmp = java.io.File.createTempFile(userId + "_viewer_summary", ".csv")
    try {
      val w = new java.io.PrintWriter(tmp, "UTF-8")
      try w.write(csv) finally w.close()
      CloudStorageUtil.upload(storageType, container, objectKey, tmp.getAbsolutePath)
    } finally tmp.delete()
  }

  // (csv header, result-row key) — getRecords/createResponse returns camelCase field names
  // (courseid->courseId, completionpercentage->completionPercentage, ...), so read those keys.
  private val csvCols = List(
    ("courseid", "courseId"), ("batchid", "batchId"), ("progress", "progress"),
    ("status", "status"), ("completionpercentage", "completionPercentage"), ("completedon", "completedOn"))
  private def toCsv(rows: util.List[util.Map[String, AnyRef]]): String = {
    val sb = new StringBuilder(csvCols.map(_._1).mkString(",")).append("\n")
    rows.asScala.foreach { r =>
      sb.append(csvCols.map { case (_, key) => Option(r.get(key)).map(_.toString.replace(",", " ")).getOrElse("") }.mkString(",")).append("\n")
    }
    sb.toString
  }

  /** Delete enrolment rows: all for the user, or a single collection[+batch]. */
  private def summaryDelete(request: Request): Unit = {
    val ctx = request.getRequestContext
    val userId = Option(request.get(JsonKey.USER_ID).asInstanceOf[String])
      .getOrElse(request.get("userId").asInstanceOf[String])
    val courseId = ViewerRequestKeys.courseId(request).orNull
    val batchId = ViewerRequestKeys.batchId(request).orNull

    if (StringUtils.isBlank(courseId)) {
      // delete all: fetch keys then delete each row
      val rows = getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName,
        new util.HashMap[String, AnyRef]() {{ put("userid", userId) }}, ctx)
      rows.asScala.foreach(r => deleteEnrolment(userId, strOrNull(r.get("courseId")), strOrNull(r.get("batchId")), ctx))
    } else {
      deleteEnrolment(userId, courseId, batchId, ctx)
    }
    sender().tell(successResponse(), self)
  }

  private def deleteEnrolment(userId: String, courseId: String, batchId: String, ctx: RequestContext): Unit = {
    val key = new util.HashMap[String, String]()
    key.put("userid", userId)
    if (StringUtils.isNotBlank(courseId)) key.put("courseid", courseId)
    if (StringUtils.isNotBlank(batchId)) key.put("batchid", batchId)
    cassandraOperation.deleteRecord(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, key, ctx)
  }

  private def getRecords(keyspace: String, table: String, filters: util.HashMap[String, AnyRef], ctx: RequestContext): util.List[util.Map[String, AnyRef]] = {
    val response = cassandraOperation.getRecords(keyspace, table, filters.asInstanceOf[util.Map[String, AnyRef]], null, ctx)
    response.getResult.getOrDefault(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]])
      .asInstanceOf[util.List[util.Map[String, AnyRef]]]
  }

  private def strOrNull(v: AnyRef): String = if (v == null) null else v.asInstanceOf[String]

  // for tests
  def setCassandraOperation(ops: org.sunbird.cassandra.CassandraOperation): ViewerSummaryActor = {
    cassandraOperation = ops
    this
  }
}
