package org.sunbird.observability.service

import org.sunbird.exception.ProjectCommonException
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.message.ResponseCode
import org.sunbird.observability.dao.{StandardReportMetaDao, StandardReportMetaDaoImpl}
import org.sunbird.observability.executor.{AggregatingCqlQueryExecutor, EsExecutor, QueryExecutor, SearchServiceQueryExecutor, YugabyteCqlQueryExecutor, YugabyteQueryExecutor}
import org.sunbird.observability.transform.{TransformCache, TransformRegistry}
import org.sunbird.observability.util.{FilterValidator, QueryTemplateRenderer}
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response

import scala.collection.JavaConverters._

/**
 * Core service implementation for the Observability Reporting API.
 *
 * Orchestrates the full report generation pipeline:
 *  1. Resolves report metadata (query template, data source, supported filters) from [[StandardReportMetaDao]].
 *  2. Validates request filters against the report's `supportedFilters` allowlist.
 *  3. Routes query execution to the correct executor based on `dataSource`:
 *     - `SEARCHSERVICE`          → [[SearchServiceQueryExecutor]]
 *     - `ELASTICSEARCH`          → [[EsExecutor]] (user creation count)
 *     - `YUGABYTE_SQL`      → [[YugabyteQueryExecutor]]
 *     - `YUGABYTE_CQL`      → [[YugabyteCqlQueryExecutor]]
 *     - `YUGABYTE_CQL_AGG`  → [[AggregatingCqlQueryExecutor]] (in-memory grouping + aggregation)
 *  4. Optionally enriches rows with entity details (user, collection, content) via [[TransformCache]].
 *
 * All constructor parameters are injectable for unit testing; production code uses the defaults.
 */
class ObservabilityReportServiceImpl(
    dao: StandardReportMetaDao              = new StandardReportMetaDaoImpl(),
    esExecutor: QueryExecutor               = new SearchServiceQueryExecutor(),
    sqlExecutor: QueryExecutor              = new YugabyteQueryExecutor(),
    cqlExecutor: QueryExecutor              = new YugabyteCqlQueryExecutor(),
    aggCqlExecutor: AggregatingCqlQueryExecutor = new AggregatingCqlQueryExecutor(),
    esCountExecutor: QueryExecutor          = new EsExecutor()
) extends ObservabilityReportService {

  private val logger = new LoggerUtil(classOf[ObservabilityReportServiceImpl])

  override def generateReport(request: Request): Response = {
    // Pattern match instead of asInstanceOf — avoids ClassCastException if caller
    // sends a non-String type for reportId (e.g. a number or object).
    val reportId = request.getRequest.get("reportId") match {
      case s: String if s.nonEmpty => s
      case _ => throw new ProjectCommonException(
        ResponseCode.mandatoryParameterMissing.getErrorCode,
        "reportId is mandatory",
        ResponseCode.CLIENT_ERROR.getResponseCode
      )
    }

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
      case "SEARCHSERVICE" =>
        val renderedQuery = QueryTemplateRenderer.renderEs(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing Search Service report $reportId")
        esExecutor.execute(renderedQuery, List.empty, request.getRequestContext)

      case "YUGABYTE_SQL" =>
        val rendered = QueryTemplateRenderer.renderSql(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing SQL report $reportId")
        sqlExecutor.execute(rendered.query, rendered.params, request.getRequestContext)

      case "YUGABYTE_CQL" =>
        val rendered = QueryTemplateRenderer.renderSql(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing CQL report $reportId")
        cqlExecutor.execute(rendered.query, rendered.params, request.getRequestContext)

      case "ELASTICSEARCH" =>
        val renderedQuery = QueryTemplateRenderer.renderEs(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing ES report $reportId")
        esCountExecutor.execute(renderedQuery, List.empty, request.getRequestContext)

      case "YUGABYTE_CQL_AGG" =>
        val spec = reportMeta.aggregationSpec.getOrElse(
          throw new ProjectCommonException(
            ResponseCode.serverError.getErrorCode,
            s"Report '$reportId' has dataSource=YUGABYTE_CQL_AGG but no aggregation_spec configured",
            ResponseCode.SERVER_ERROR.getResponseCode
          )
        )
        val rendered = QueryTemplateRenderer.renderSql(reportMeta.queryTemplate, filters)
        logger.info(request.getRequestContext, s"generateReport: Executing CQL_AGG report $reportId with groupBy=${spec.groupBy.mkString(",")}")
        aggCqlExecutor.execute(rendered.query, rendered.params, spec)

      case unknown =>
        throw new ProjectCommonException(
          ResponseCode.serverError.getErrorCode,
          s"Unknown data_source '$unknown' for report '$reportId'",
          ResponseCode.SERVER_ERROR.getResponseCode
        )
    }

    // Extract optional transform field list — collect only String elements to avoid
    // ClassCastException if a caller sends mixed-type array; non-strings are silently dropped
    // (validator already rejects non-string elements, so this is a safe-by-default fallback).
    // TODO: consider caching reportMeta lookups with a short TTL to avoid one DB round-trip
    //       per request once report volume grows — see StandardReportMetaDaoImpl.
    val transformFields: List[String] =
      Option(request.getRequest.get("transform"))
        .collect { case l: java.util.List[_] => l.asScala.collect { case s: String => s }.toList }
        .getOrElse(List.empty)

    val enrichedRows: List[Map[String, Any]] =
      if (transformFields.isEmpty) rows
      else applyTransforms(rows, transformFields, request.getRequestContext)

    // TODO: Facet detection should use an explicit flag on StandardReportMeta (e.g. isFacetResponse: Boolean)
    // rather than inspecting the first row for a "facet" key. Current heuristic is fragile and can cause
    // silent data corruption if a standard CQL report legitimately has a column named "facet".
    // If rows are facet rows (each has a "facet" key), group them by facet name so all
    // values for the same facet are nested under one entry in the response array.
    val isFacetResponse = enrichedRows.nonEmpty && enrichedRows.head.contains("facet")

    val data: java.util.List[java.util.Map[String, AnyRef]] = if (isFacetResponse) {
      enrichedRows
        .groupBy(row => row("facet").toString)
        .toSeq
        .sortBy(_._1)  // Sort by facet name for deterministic, stable ordering
        .map { case (facetName, rows) =>
          val values = rows.map(row => (row - "facet").asJava.asInstanceOf[java.util.Map[String, AnyRef]]).asJava
          val facetMap = new java.util.HashMap[String, AnyRef]()
          facetMap.put("facet", facetName)
          facetMap.put("values", values)
          facetMap.asInstanceOf[java.util.Map[String, AnyRef]]
        }.toList.asJava
    } else {
      enrichedRows.map(_.asJava.asInstanceOf[java.util.Map[String, AnyRef]]).asJava
    }

    val result = new java.util.HashMap[String, AnyRef]()
    result.put("reportId", reportId)
    result.put("count", Integer.valueOf(enrichedRows.size))
    result.put("data", data)

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

    // For each active transform: gather unique IDs and fetch (cache-aside).
    // We try all registered aliases for the util (e.g. "userid" and "user_id") so the
    // transform works regardless of which column name the data source uses in its output.
    val lookupMaps: Map[String, Map[String, Map[String, AnyRef]]] =
      activeTransforms.map { case (fieldName, entry) =>
        val aliases = TransformRegistry.aliasesFor(entry.utilKey)
        val ids = rows.flatMap { r =>
          aliases.flatMap(f => r.get(f)).headOption.map(_.toString)
        }.distinct
        logger.info(context, s"applyTransforms: ${ids.size} unique '$fieldName' value(s) to transform (aliases: ${aliases.mkString(", ")})")
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

    // Merge detail maps back into each row.
    // Same alias-aware lookup: find the ID from whichever field name is present in the row.
    rows.map { row =>
      activeTransforms.foldLeft(row) { case (acc, (fieldName, entry)) =>
        val aliases = TransformRegistry.aliasesFor(entry.utilKey)
        val idOpt   = aliases.flatMap(f => acc.get(f)).headOption.map(_.toString)
        val details = idOpt.flatMap(id => lookupMaps(fieldName).get(id))
        details match {
          case Some(d) => acc + (entry.resultKey -> d.asInstanceOf[Any])
          case None    => acc
        }
      }
    }
  }
}
