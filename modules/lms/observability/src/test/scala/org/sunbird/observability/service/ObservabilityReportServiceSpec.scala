package org.sunbird.observability.service

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.{mock, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.sunbird.exception.ProjectCommonException
import org.sunbird.keys.JsonKey
import org.sunbird.observability.dao.StandardReportMetaDao
import org.sunbird.observability.executor.QueryExecutor
import org.sunbird.observability.model.ReportMeta
import org.sunbird.request.{Request, RequestContext}

class ObservabilityReportServiceSpec extends AnyWordSpec with Matchers {

  private def buildRequest(params: Map[String, AnyRef] = Map.empty): Request = {
    val req = new Request()
    req.setOperation("generateReport")
    req.setRequestContext(new RequestContext())
    params.foreach { case (k, v) => req.getRequest.put(k, v) }
    req
  }

  private def esReportMeta(reportId: String = "active_users_weekly"): ReportMeta =
    ReportMeta(
      reportId         = reportId,
      title            = "Active Users Weekly",
      description      = None,
      domain           = "generic",
      dataSource       = "ELASTICSEARCH",
      queryTemplate    = """{"request":{"filters":{"objectType":"User"{{#channel}},"channel":"{{channel}}"{{/channel}}}}}""",
      supportedFilters = List("channel", "startDate", "endDate"),
      enabled          = true
    )

  private def sqlReportMeta(reportId: String = "users_by_state"): ReportMeta =
    ReportMeta(
      reportId         = reportId,
      title            = "Users by State",
      description      = None,
      domain           = "user_profile",
      dataSource       = "YUGABYTE_SQL",
      queryTemplate    = "SELECT state, COUNT(*) FROM usr WHERE 1=1{{#channel}} AND channel = {{channel}}{{/channel}} GROUP BY state",
      supportedFilters = List("channel"),
      enabled          = true
    )

  "ObservabilityReportServiceImpl.generateReport" should {

    "execute ES report successfully" in {
      val dao         = mock(classOf[StandardReportMetaDao])
      val esExecutor  = mock(classOf[QueryExecutor])
      val sqlExecutor = mock(classOf[QueryExecutor])

      when(dao.getById("active_users_weekly")).thenReturn(Some(esReportMeta()))
      when(esExecutor.execute(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(
        List(Map("date" -> "2025-01-01".asInstanceOf[Any], "count" -> 100.asInstanceOf[Any]))
      )

      val service = new ObservabilityReportServiceImpl(dao, esExecutor, sqlExecutor)
      val request = buildRequest(Map("reportId" -> "active_users_weekly", "filters" ->
        new java.util.HashMap[String, AnyRef]() {{ put("channel", "testChannel") }}))

      val response = service.generateReport(request)
      response should not be null
      val result = response.get(JsonKey.RESPONSE).asInstanceOf[java.util.Map[String, AnyRef]]
      result.get("reportId") shouldBe "active_users_weekly"
      result.get("count").asInstanceOf[Int] shouldBe 1
    }

    "execute SQL report successfully" in {
      val dao         = mock(classOf[StandardReportMetaDao])
      val esExecutor  = mock(classOf[QueryExecutor])
      val sqlExecutor = mock(classOf[QueryExecutor])

      when(dao.getById("users_by_state")).thenReturn(Some(sqlReportMeta()))
      when(sqlExecutor.execute(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(
        List(Map("state" -> "Karnataka".asInstanceOf[Any], "count" -> 5000.asInstanceOf[Any]))
      )

      val service = new ObservabilityReportServiceImpl(dao, esExecutor, sqlExecutor)
      val response = service.generateReport(buildRequest(Map("reportId" -> "users_by_state")))
      val result = response.get(JsonKey.RESPONSE).asInstanceOf[java.util.Map[String, AnyRef]]
      result.get("count").asInstanceOf[Int] shouldBe 1
    }

    "throw exception for missing reportId" in {
      val dao = mock(classOf[StandardReportMetaDao])
      val service = new ObservabilityReportServiceImpl(dao)

      a[ProjectCommonException] should be thrownBy {
        service.generateReport(buildRequest())
      }
    }

    "throw exception for unknown reportId" in {
      val dao = mock(classOf[StandardReportMetaDao])
      when(dao.getById("unknown")).thenReturn(None)
      val service = new ObservabilityReportServiceImpl(dao)

      a[ProjectCommonException] should be thrownBy {
        service.generateReport(buildRequest(Map("reportId" -> "unknown")))
      }
    }

    "throw exception for unsupported filter" in {
      val dao = mock(classOf[StandardReportMetaDao])
      when(dao.getById("active_users_weekly")).thenReturn(Some(esReportMeta()))
      val service = new ObservabilityReportServiceImpl(dao)

      a[ProjectCommonException] should be thrownBy {
        service.generateReport(buildRequest(Map(
          "reportId" -> "active_users_weekly",
          "filters" -> new java.util.HashMap[String, AnyRef]() {{ put("unknownFilter", "value") }}
        )))
      }
    }
  }

  "ObservabilityReportServiceImpl.listReports" should {

    "return list of enabled reports" in {
      val dao = mock(classOf[StandardReportMetaDao])
      when(dao.listAll()).thenReturn(List(esReportMeta(), sqlReportMeta()))

      val service = new ObservabilityReportServiceImpl(dao)
      val req = new Request()
      req.setRequestContext(new RequestContext())
      val response = service.listReports(req)

      val result = response.get(JsonKey.RESPONSE).asInstanceOf[java.util.Map[String, AnyRef]]
      result.get("count").asInstanceOf[Int] shouldBe 2
    }

    "return empty list when no reports enabled" in {
      val dao = mock(classOf[StandardReportMetaDao])
      when(dao.listAll()).thenReturn(List.empty)

      val service = new ObservabilityReportServiceImpl(dao)
      val req = new Request()
      req.setRequestContext(new RequestContext())
      val response = service.listReports(req)

      val result = response.get(JsonKey.RESPONSE).asInstanceOf[java.util.Map[String, AnyRef]]
      result.get("count").asInstanceOf[Int] shouldBe 0
    }
  }
}
