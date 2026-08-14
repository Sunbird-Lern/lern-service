package org.sunbird.viewer.actor

import org.apache.commons.lang3.StringUtils
import org.sunbird.request.Request

// viewer contract is courseId / batchId / contentId only; legacy-key resolution is the caller's job (no fallback here)
object ViewerRequestKeys {

  private def value(request: Request, key: String): Option[String] =
    Option(request.get(key)).collect { case s: String if StringUtils.isNotBlank(s) => s }

  def courseId(request: Request): Option[String] = value(request, "courseId")
  def batchId(request: Request): Option[String] = value(request, "batchId")
  def contentId(request: Request): String = value(request, "contentId").orNull
}
