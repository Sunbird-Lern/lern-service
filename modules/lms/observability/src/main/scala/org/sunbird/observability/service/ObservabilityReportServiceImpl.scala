package org.sunbird.observability.service

import org.sunbird.exception.ProjectCommonException
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.message.ResponseCode
import org.sunbird.observability.dao.{StandardReportMetaDao, StandardReportMetaDaoImpl}
import org.sunbird.observability.executor.{QueryExecutor, SearchServiceQueryExecutor, YugabyteQueryExecutor}
import org.sunbird.observability.util.{FilterValidator, QueryTemplateRenderer}
import org.sunbird.request.Request
import org.sunbird.response.Response

import scala.collection.JavaConverters._

class ObservabilityReportServiceImpl(
    dao: StandardReportMetaDao        = new StandardReportMetaDaoImpl(),
    esExecutor: QueryExecutor         = new SearchServiceQueryExecutor(),
    sqlExecutor: QueryExecutor        = new YugabyteQueryExecutor()
) extends ObservabilityReportService {

  private val logger = new LoggerUtil(classOf[ObservabilityReportServiceImpl])

  override def generateReport(request: Request): Response = {
    val reportId = Option(request.getRequest.get("reportId").asInstanceOf[String])
      .getOrElse(throw new ProjectCommonException(
        ResponseCode.mandatoryParameterMissing.getErrorCode,
        "reportId is mandatory",
        ResponseCode.CLIENT_ERROR.getResponseCode
      ))

    val filtersRaw = request.getRequest.get("filters")
    val filters: Map[String, Any] = filtersRaw match {
      case m: java.util.Map[_, _] => m.asScala.map { case (k, v) => k.toString -> v.asInstanceOf[Any] }.toMap
      case _                      => Map.empty
    }

    val reportMeta = dao.getById(reportId).getOrElse(
      throw new ProjectCommonException(
        "RESOURCE_NOT_FOUND",
        s"Report '$reportId' not found or is disabled",
        ResponseCode.RESOURCE_NOT_FOUND.getResponseCode
      )
    )

    FilterValidator.validate(filters, reportMeta.supportedFilters)

    val rows: List[Map[String, Any]] = reportMeta.dataSource.toUpperCase match {
      case "ELASTICSEARCH" =>
        val renderedQuery = QueryTemplateRenderer.renderEs(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing ES report $reportId")
        esExecutor.execute(renderedQuery, List.empty)

      case "YUGABYTE_SQL" =>
        val rendered = QueryTemplateRenderer.renderSql(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing SQL report $reportId")
        sqlExecutor.execute(rendered.query, rendered.params)

      case unknown =>
        throw new ProjectCommonException(
          ResponseCode.serverError.getErrorCode,
          s"Unknown data_source '$unknown' for report '$reportId'",
          ResponseCode.SERVER_ERROR.getResponseCode
        )
    }

    val javaRows = rows.map(_.asJava.asInstanceOf[java.util.Map[String, AnyRef]]).asJava

    val result = new java.util.HashMap[String, AnyRef]()
    result.put("reportId", reportId)
    result.put("count", Integer.valueOf(rows.size))
    result.put("data", javaRows)

    val response = new Response()
    response.put(JsonKey.RESPONSE, result)
    response
  }

  override def listReports(request: Request): Response = {
    logger.info(request.getRequestContext, "listReports: Fetching all enabled reports")
    val reports = dao.listAll()

    val javaReports = reports.map { r =>
      val m = new java.util.HashMap[String, AnyRef]()
      m.put("reportId", r.reportId)
      m.put("title", r.title)
      m.put("description", r.description.orNull)
      m.put("domain", r.domain)
      m.put("dataSource", r.dataSource)
      m.put("supportedFilters", r.supportedFilters.asJava)
      m
    }.asJava

    val result = new java.util.HashMap[String, AnyRef]()
    result.put("count", Integer.valueOf(reports.size))
    result.put("reports", javaReports)

    val response = new Response()
    response.put(JsonKey.RESPONSE, result)
    response
  }
}
