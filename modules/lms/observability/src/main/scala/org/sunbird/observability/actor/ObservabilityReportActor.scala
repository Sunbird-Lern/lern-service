package org.sunbird.observability.actor

import org.sunbird.actor.core.BaseActor
import org.sunbird.logging.LoggerUtil
import org.sunbird.observability.service.{ObservabilityReportService, ObservabilityReportServiceImpl}
import org.sunbird.request.Request

/**
 * Pekko actor that handles observability report requests.
 *
 * Supported operations:
 *  - generateReport : Execute a report by ID with optional filters
 *  - listReports    : List all enabled reports
 *
 * The service dependency is constructor-injectable for unit testing.
 */
class ObservabilityReportActor(
    service: ObservabilityReportService = ObservabilityReportActor.sharedService
) extends BaseActor {

  private val log = new LoggerUtil(classOf[ObservabilityReportActor])

  override def onReceive(request: Request): Unit = {
    request.getOperation match {
      case "generateReport" =>
        log.info(request.getRequestContext, "ObservabilityReportActor: handling generateReport")
        sender().tell(service.generateReport(request), self)

      case "listReports" =>
        log.info(request.getRequestContext, "ObservabilityReportActor: handling listReports")
        sender().tell(service.listReports(request), self)

      case _ =>
        onReceiveUnsupportedOperation(request.getOperation)
    }
  }
}

object ObservabilityReportActor {
  lazy val sharedService: ObservabilityReportService = new ObservabilityReportServiceImpl()
}
