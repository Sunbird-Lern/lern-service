package org.sunbird.observability.dao

import org.sunbird.observability.model.ReportMeta

trait StandardReportMetaDao {

  /**
   * Look up a single enabled report by its ID.
   * Returns None if not found or if enabled = FALSE.
   */
  def getById(reportId: String): Option[ReportMeta]

  /**
   * Return summary rows for all enabled reports, ordered by domain then reportId.
   */
  def listAll(): List[ReportMeta]
}
