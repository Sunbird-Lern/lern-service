package org.sunbird.observability.util

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
 * Renders query templates by substituting {{key}} placeholders.
 *
 * Template syntax:
 *  - Optional block: {{#key}}...{{key}}...{{/key}}
 *      If `key` is present in filters → expand the block and substitute {{key}} with the value.
 *      If `key` is absent              → remove the entire block.
 *  - Direct placeholder: {{key}}
 *      Replaced with the actual value.
 *
 * For SQL queries, substitution uses `?` and the ordered param list is returned separately
 * to enable safe PreparedStatement binding.
 *
 * For ES queries, values are substituted directly into the JSON template string.
 */
object QueryTemplateRenderer {

  case class RenderedQuery(query: String, params: List[Any])

  private val BLOCK_PATTERN: Regex = """\{\{#(\w+)\}\}(.*?)\{\{/\1\}\}""".r
  private val PLACEHOLDER_PATTERN: Regex = """\{\{(\w+)\}\}""".r

  /** Render an Elasticsearch query template — values substituted directly. */
  def renderEs(template: String, filters: Map[String, Any]): String = {
    val afterBlocks = resolveOptionalBlocks(template, filters, useQuestionMark = false)
    resolveDirectPlaceholders(afterBlocks, filters, useQuestionMark = false)._1
  }

  /** Render a SQL query template — returns the parameterized SQL and ordered bind params. */
  def renderSql(template: String, filters: Map[String, Any]): RenderedQuery = {
    val params = ListBuffer[Any]()

    val afterBlocks = resolveOptionalBlocks(template, filters, useQuestionMark = true, params)
    val (finalQuery, _) = resolveDirectPlaceholders(afterBlocks, filters, useQuestionMark = true, params)

    RenderedQuery(finalQuery.trim, params.toList)
  }

  /**
   * Expand or remove {{#key}}...{{/key}} blocks.
   * When useQuestionMark is true, inner {{key}} becomes `?` and the value is added to params.
   */
  private def resolveOptionalBlocks(
      template: String,
      filters: Map[String, Any],
      useQuestionMark: Boolean,
      params: ListBuffer[Any] = ListBuffer.empty
  ): String = {
    BLOCK_PATTERN.replaceAllIn(template, m => {
      val key = m.group(1)
      val innerTemplate = m.group(2)
      filters.get(key) match {
        case Some(value) =>
          if (useQuestionMark) {
            val expanded = PLACEHOLDER_PATTERN.replaceAllIn(innerTemplate, pm => {
              if (pm.group(1) == key) { params += value; "?" }
              else pm.matched
            })
            Regex.quoteReplacement(expanded)
          } else {
            val expanded = innerTemplate.replace(s"{{$key}}", escapeForJson(value.toString))
            Regex.quoteReplacement(expanded)
          }
        case None => ""  // remove the entire optional block
      }
    })
  }

  /**
   * Replace remaining standalone {{key}} placeholders.
   */
  private def resolveDirectPlaceholders(
      template: String,
      filters: Map[String, Any],
      useQuestionMark: Boolean,
      params: ListBuffer[Any] = ListBuffer.empty
  ): (String, ListBuffer[Any]) = {
    val result = PLACEHOLDER_PATTERN.replaceAllIn(template, m => {
      val key = m.group(1)
      filters.get(key) match {
        case Some(value) =>
          if (useQuestionMark) { params += value; "?" }
          else Regex.quoteReplacement(escapeForJson(value.toString))
        case None => ""  // unknown placeholder — remove it
      }
    })
    (result, params)
  }

  private def escapeForJson(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")
}
