package org.sunbird.observability.actor

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{ImplicitSender, TestKit}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.sunbird.observability.service.ObservabilityReportService
import org.sunbird.request.{Request, RequestContext}
import org.sunbird.response.Response
import org.sunbird.keys.JsonKey

class ObservabilityReportActorSpec
    extends TestKit(ActorSystem("ObservabilityReportActorSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  private def buildRequest(operation: String, params: Map[String, AnyRef] = Map.empty): Request = {
    val req = new Request()
    req.setOperation(operation)
    req.setRequestContext(new RequestContext())
    params.foreach { case (k, v) => req.getRequest.put(k, v) }
    req
  }

  private def successResponse(reportId: String = "test_report"): Response = {
    val result = new java.util.HashMap[String, AnyRef]()
    result.put("reportId", reportId)
    result.put("count", Integer.valueOf(0))
    result.put("data", new java.util.ArrayList[AnyRef]())
    val resp = new Response()
    resp.put(JsonKey.RESPONSE, result)
    resp
  }

  "ObservabilityReportActor" should {

    "return report data for generateReport operation" in {
      val mockService = mock(classOf[ObservabilityReportService])
      when(mockService.generateReport(any())).thenReturn(successResponse("active_users_weekly"))

      val actor = system.actorOf(Props(new ObservabilityReportActor(mockService)))
      val request = buildRequest("generateReport", Map("reportId" -> "active_users_weekly"))
      actor ! request

      val response = expectMsgClass(classOf[Response])
      response should not be null
      val result = response.get(JsonKey.RESPONSE).asInstanceOf[java.util.Map[String, AnyRef]]
      result.get("reportId") shouldBe "active_users_weekly"
    }

    "return report list for listReports operation" in {
      val mockService = mock(classOf[ObservabilityReportService])
      val listResp = new Response()
      val result = new java.util.HashMap[String, AnyRef]()
      result.put("count", Integer.valueOf(13))
      result.put("reports", new java.util.ArrayList[AnyRef]())
      listResp.put(JsonKey.RESPONSE, result)
      when(mockService.listReports(any())).thenReturn(listResp)

      val actor = system.actorOf(Props(new ObservabilityReportActor(mockService)))
      actor ! buildRequest("listReports")

      val response = expectMsgClass(classOf[Response])
      response should not be null
    }

    "throw exception for unsupported operation" in {
      val mockService = mock(classOf[ObservabilityReportService])
      val actor = system.actorOf(Props(new ObservabilityReportActor(mockService)))
      actor ! buildRequest("unknownOperation")

      expectMsgClass(classOf[Exception])
    }
  }
}
