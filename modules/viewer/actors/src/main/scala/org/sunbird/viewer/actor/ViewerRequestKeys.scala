package org.sunbird.viewer.actor

import org.apache.commons.lang3.StringUtils
import org.sunbird.request.Request

/**
 * Backward-compatible request-key resolution for the viewer APIs.
 *
 * The viewer generalises course -> collection. Clients (and the content-state delegation) may send
 * the OLD keys courseId/batchId or the NEW keys collectionId/contextId — both are accepted, mapped
 * to the collection/context concept. camelCase (API convention) with lowercase fallbacks.
 * This is the REQUEST-payload layer only; DB columns are handled separately.
 */
object ViewerRequestKeys {

  private def firstNonBlank(request: Request, keys: String*): Option[String] =
    keys.iterator
      .map(k => request.get(k))
      .collectFirst { case v: String if StringUtils.isNotBlank(v) => v }

  /** collectionId, else legacy courseId. */
  def collectionId(request: Request): Option[String] =
    firstNonBlank(request, "collectionId", "collectionid", "courseId", "courseid")

  /** contextId, else legacy batchId. */
  def contextId(request: Request): Option[String] =
    firstNonBlank(request, "contextId", "contextid", "batchId", "batchid")

  def contentId(request: Request): String =
    firstNonBlank(request, "contentId", "contentid").orNull
}
