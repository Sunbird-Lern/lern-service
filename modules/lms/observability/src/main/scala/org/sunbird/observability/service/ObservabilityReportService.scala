package org.sunbird.observability.service

import org.sunbird.request.Request
import org.sunbird.response.Response

trait ObservabilityReportService {

  /** Execute the given report with the supplied filters and return the data. */
  def generateReport(request: Request): Response

  /** Return summary rows for all enabled reports. */
  def listReports(request: Request): Response
}
