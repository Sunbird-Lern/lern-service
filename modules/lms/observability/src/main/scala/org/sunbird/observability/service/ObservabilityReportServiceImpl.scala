package org.sunbird.observability.service

import org.sunbird.exception.ProjectCommonException
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.message.ResponseCode
import org.sunbird.observability.dao.{StandardReportMetaDao, StandardReportMetaDaoImpl}
import org.sunbird.observability.executor.{QueryExecutor, SearchServiceQueryExecutor, YugabyteCqlQueryExecutor, YugabyteQueryExecutor}
import org.sunbird.observability.transform.{TransformCache, TransformRegistry}
import org.sunbird.observability.util.{FilterValidator, QueryTemplateRenderer}
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import scala.collection.JavaConverters._

class ObservabilityReportServiceImpl(
    dao: StandardReportMetaDao        = new StandardReportMetaDaoImpl(),
    esExecutor: QueryExecutor         = new SearchServiceQueryExecutor(),
    sqlExecutor: QueryExecutor        = new YugabyteQueryExecutor(),
    cqlExecutor: QueryExecutor        = new YugabyteCqlQueryExecutor()
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

      case "YUGABYTE_CQL" =>
        val rendered = QueryTemplateRenderer.renderSql(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing CQL report $reportId")
        cqlExecutor.execute(rendered.query, rendered.params)

      case unknown =>
        throw new ProjectCommonException(
          ResponseCode.serverError.getErrorCode,
          s"Unknown data_source '$unknown' for report '$reportId'",
          ResponseCode.SERVER_ERROR.getResponseCode
        )
    }

    // Extract optional transform field list from the request
    val transformFields: List[String] =
      Option(request.getRequest.get("transform"))
        .collect { case l: java.util.List[_] => l.asScala.map(_.toString).toList }
        .getOrElse(List.empty)

    val enrichedRows: List[Map[String, Any]] =
      if (transformFields.isEmpty) rows
      else applyTransforms(rows, transformFields, request.getRequestContext)

    val javaRows = enrichedRows.map(_.asJava.asInstanceOf[java.util.Map[String, AnyRef]]).asJava

    val result = new java.util.HashMap[String, AnyRef]()
    result.put("reportId", reportId)
    result.put("count", Integer.valueOf(enrichedRows.size))
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

  // ---------------------------------------------------------------------------

  /**
   * Enriches each row by appending detail maps for the requested transform fields.
   *
   * For each field in transformFields that has a registered TransformEntry:
   *   1. Collect all unique values of that field across all rows.
   *   2. Fetch details via TransformCache (in-memory cache-aside → source on miss).
   *   3. Merge the detail map back into each row as a sibling key (entry.resultKey).
   *
   * Unknown transform fields are silently skipped.
   * Rows that lack a value for the transform field are returned unchanged.
   */
  private def applyTransforms(
      rows:            List[Map[String, Any]],
      transformFields: List[String],
      context:         RequestContext
  ): List[Map[String, Any]] = {

    // Resolve each requested field to its registered entry (unknown fields skipped)
    val activeTransforms: List[(String, TransformRegistry.TransformEntry)] =
      transformFields.flatMap(f => TransformRegistry.lookup(f).map(e => (f, e)))

    if (activeTransforms.isEmpty) {
      logger.info(context, s"applyTransforms: no registered entries for fields ${transformFields.mkString(", ")}")
      return rows
    }

    // For each active transform: gather unique IDs and fetch (cache-aside)
    val lookupMaps: Map[String, Map[String, Map[String, AnyRef]]] =
      activeTransforms.map { case (fieldName, entry) =>
        val ids = rows.flatMap(r => Option(r.get(fieldName)).map(_.toString)).distinct
        logger.info(context, s"applyTransforms: ${ids.size} unique '$fieldName' value(s) to transform")
        val details = TransformCache.fetchWithCache(
          utilKey = entry.utilKey,
          ids     = ids,
          fields  = entry.fields,
          ttl     = entry.cacheTtl,
          maxSize = entry.cacheMaxSize,
          fetchFn = (uncachedIds, fs) => entry.util.fetchDetails(uncachedIds, fs, context),
          context = context
        )
        fieldName -> details
      }.toMap

    // Merge detail maps back into each row
    rows.map { row =>
      activeTransforms.foldLeft(row) { case (acc, (fieldName, entry)) =>
        val idOpt   = acc.get(fieldName).map(_.toString)
        val details = idOpt.flatMap(id => lookupMaps(fieldName).get(id))
        details match {
          case Some(d) => acc + (entry.resultKey -> d.asInstanceOf[Any])
          case None    => acc
        }
      }
    }
  }
}
