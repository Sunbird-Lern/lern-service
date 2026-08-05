package org.sunbird.viewer.actor

import org.apache.commons.lang3.StringUtils
import org.sunbird.request.Request

/**
 * Canonical viewer request keys — the viewer contract is courseId / batchId / contentId only.
 * Legacy courseId/batchId resolution is the caller's job (the content-consumption adapter maps them
 * before dispatching), so no fallback lives here. Null/blank-safe extraction in one place.
 */
object ViewerRequestKeys {

  private def value(request: Request, key: String): Option[String] =
    Option(request.get(key)).collect { case s: String if StringUtils.isNotBlank(s) => s }

  def courseId(request: Request): Option[String] = value(request, "courseId")
  def batchId(request: Request): Option[String] = value(request, "batchId")
  def contentId(request: Request): String = value(request, "contentId").orNull
}
