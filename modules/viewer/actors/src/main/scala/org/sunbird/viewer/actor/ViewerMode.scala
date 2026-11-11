package org.sunbird.viewer.actor

import org.sunbird.common.ProjectUtil

// Instance-level Config Modes. One resolver computes the consumption key (collectionid, contextid)
// from the configured mode + scope; contentid is always the content and stays with the caller.
// strict reproduces today's ViewConsumptionActor.viewKey cascade, so the default is a no-op.
object ViewerMode {

  // strict | noContext | copy
  def mode(): String = normalize(ProjectUtil.getConfigValue("viewer_context_mode"), "strict")

  // content | collection
  def scope(): String = normalize(ProjectUtil.getConfigValue("viewer_carry_scope"), "content")

  private[actor] def normalize(raw: String, default: String): String =
    Option(raw).map(_.trim).filter(_.nonEmpty).getOrElse(default)

  /**
   * Resolve the (collectionid, contextid) a user_content_consumption row is written/read under.
   * contentid is always the content and is handled by the caller.
   *
   *  - strict / copy      : today's cascade - collection<-content, context<-collection<-content
   *  - noContext content  : always (content, content)
   *  - noContext collection: (collection, collection); (content, content) when there is no collection
   *
   * copy resolves like strict here; its carry-forward overlay is applied elsewhere (Phase 5).
   * An unknown / blank / null mode or scope falls back to strict / content.
   */
  def resolveKey(mode: String, scope: String,
                 collectionId: Option[String], contextId: Option[String], contentId: String): (String, String) = {
    normalize(mode, "strict") match {
      case "noContext" if normalize(scope, "content") == "collection" =>
        collectionId.map(c => (c, c)).getOrElse((contentId, contentId))
      case "noContext" =>
        (contentId, contentId)
      case _ => // strict, copy, or unknown -> strict cascade
        (collectionId.getOrElse(contentId), contextId.orElse(collectionId).getOrElse(contentId))
    }
  }
}
