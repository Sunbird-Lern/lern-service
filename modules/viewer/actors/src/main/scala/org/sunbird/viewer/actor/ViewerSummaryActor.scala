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

// enrolment-level summary APIs (read/delete user_enrolments only; never writes consumption)
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

    val enrolFilters = new util.HashMap[String, AnyRef]()
    enrolFilters.put("userid", userId)
    if (StringUtils.isNotBlank(courseId)) enrolFilters.put("courseid", courseId)
    if (StringUtils.isNotBlank(batchId)) enrolFilters.put("batchid", batchId)
    val enrolments = getRecords(enrolmentDBInfo.getKeySpace, enrolmentDBInfo.getTableName, enrolFilters, ctx)
    logger.info(ctx, s"summary: read | user=$userId course=$courseId rows=${enrolments.size}")
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
    logger.info(ctx, s"summary: list | user=$userId rows=${enrolments.size}")
    val response = new Response
    response.put(JsonKey.RESPONSE, enrolments)
    sender().tell(response, self)
  }

  // format=json (default) returns rows; format=csv uploads the CSV and returns its signed url
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
    logger.info(ctx, s"summary: download | user=$userId format=$format rows=${enrolments.size}")
    sender().tell(response, self)
  }

  // upload the CSV via CloudStorageUtil (provider/container/prefix all config-driven) and return a signed url
  private def uploadSummaryCsv(userId: String, csv: String): String = {
    val storageType = ProjectUtil.getConfigValue("sunbird_cloud_service_provider")
    val container = Option(ProjectUtil.getConfigValue("viewer_summary_cloud_storage_container")).filter(StringUtils.isNotBlank)
      .getOrElse(ProjectUtil.getConfigValue("sunbird_content_cloud_storage_container"))
    val prefix = Option(ProjectUtil.getConfigValue("viewer_summary_upload_path")).getOrElse("").trim.stripSuffix("/")
    val objectKey = (if (StringUtils.isNotBlank(prefix)) prefix + "/" else "") + userId + "_viewer_summary.csv"
    val tmp = java.io.File.createTempFile(userId + "_viewer_summary", ".csv")
    try {
      val w = new java.io.PrintWriter(tmp, "UTF-8")
      try w.write(csv) finally w.close()
      CloudStorageUtil.upload(storageType, container, objectKey, tmp.getAbsolutePath)
      // return a time-limited signed URL for the private learner data (mirrors BulkUploadManagementActor)
      CloudStorageUtil.getSignedUrl(storageType, container, objectKey)
    } finally tmp.delete()
  }

  // (csv header, result-row key); createResponse returns camelCase field names
  private val csvCols = List(
    ("courseid", "courseId"), ("batchid", "batchId"), ("progress", "progress"),
    ("status", "status"), ("completionpercentage", "completionPercentage"), ("completedon", "completedOn"))
  // RFC-4180: quote any field containing a comma, quote, or line break; escape embedded quotes as "".
  private def csvField(v: String): String =
    if (v.exists(c => c == ',' || c == '"' || c == '\n' || c == '\r')) "\"" + v.replace("\"", "\"\"") + "\""
    else v
  private def toCsv(rows: util.List[util.Map[String, AnyRef]]): String = {
    val sb = new StringBuilder(csvCols.map(_._1).mkString(",")).append("\n")
    rows.asScala.foreach { r =>
      sb.append(csvCols.map { case (_, key) => csvField(Option(r.get(key)).map(_.toString).getOrElse("")) }.mkString(",")).append("\n")
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
    logger.info(ctx, s"summary: delete | user=$userId course=${Option(courseId).getOrElse("ALL")}")
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
