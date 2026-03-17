package org.sunbird.observability.util

import com.fasterxml.jackson.databind.ObjectMapper

import scala.collection.JavaConverters._
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

  private val jsonMapper = new ObjectMapper()

  // (?s) enables DOTALL mode so that `.` matches newlines as well.
  // This is necessary when templates are stored in the DB with line breaks inside optional blocks.
  private val BLOCK_PATTERN: Regex = """(?s)\{\{#(\w+)\}\}(.*?)\{\{/\1\}\}""".r
  private val PLACEHOLDER_PATTERN: Regex = """\{\{(\w+)\}\}""".r

  // Combined pattern used by renderSql to process all tokens in strict left-to-right order.
  // Group 1 + 2 → block match ({{#key}}...{{/key}}); Group 3 → direct placeholder ({{key}}).
  // Correct left-to-right ordering is essential for positional PreparedStatement param binding.
  private val COMBINED_PATTERN: Regex = """(?s)\{\{#(\w+)\}\}(.*?)\{\{/\1\}\}|\{\{(\w+)\}\}""".r

  /** Render an Elasticsearch query template — values substituted directly. */
  def renderEs(template: String, filters: Map[String, Any]): String = {
    val afterBlocks = resolveOptionalBlocks(template, filters, useQuestionMark = false)
    resolveDirectPlaceholders(afterBlocks, filters, useQuestionMark = false)._1
  }

  /**
   * Render a SQL (or CQL) query template — returns the parameterized query and ordered bind params.
   *
   * Uses a single left-to-right pass over the template so that params are collected in exactly
   * the same order as their corresponding `?` placeholders appear in the output query.
   * The previous two-phase approach (blocks first, then direct placeholders) caused params to be
   * collected in block-encounter order rather than query position order, silently swapping bind
   * values when a direct placeholder appeared before an optional block in the template.
   */
  def renderSql(template: String, filters: Map[String, Any]): RenderedQuery = {
    val params = ListBuffer[Any]()

    val result = COMBINED_PATTERN.replaceAllIn(template, m => {
      val blockKey  = m.group(1)  // non-null only for {{#key}}...{{/key}} matches
      val directKey = m.group(3)  // non-null only for {{key}} matches

      if (blockKey != null) {
        filters.get(blockKey) match {
          case Some(value) =>
            val expanded = PLACEHOLDER_PATTERN.replaceAllIn(m.group(2), pm => {
              if (pm.group(1) == blockKey) {
                Regex.quoteReplacement(bindValue(value, params))
              } else pm.matched
            })
            Regex.quoteReplacement(expanded)
          case None => ""
        }
      } else {
        filters.get(directKey) match {
          case Some(value) => bindValue(value, params)
          case None        => ""
        }
      }
    })

    // Note: whitespace normalization is safe here because renderSql uses parameterized `?`
    // placeholders — string values are never embedded in the query text, only in params.
    RenderedQuery(result.trim.replaceAll("\\s+", " "), params.toList)
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

  /**
   * Binds a filter value to params and returns the corresponding placeholder string.
   *
   * For scalar values (String, Int, etc.) this is a single `?`.
   * For array values (java.util.Collection or Scala Iterable) this expands to `?, ?, ?` —
   * one `?` per element — enabling safe IN clause binding in PreparedStatements.
   *
   * Template usage for IN clauses:
   *   Template: `WHERE courseid IN ({{courseids}})`
   *   Input:    `courseids -> List("id1", "id2", "id3")`
   *   Output:   `WHERE courseid IN (?, ?, ?)` with params `["id1", "id2", "id3"]`
   */
  private def bindValue(value: Any, params: ListBuffer[Any]): String = value match {
    case javaList: java.util.Collection[_] =>
      val items = javaList.asScala.toList
      items.foreach(params += _)
      items.map(_ => "?").mkString(", ")
    case scalaSeq: Iterable[_] =>
      val items = scalaSeq.toList
      items.foreach(params += _)
      items.map(_ => "?").mkString(", ")
    case scalar =>
      params += scalar
      "?"
  }

  /**
   * Escapes a string for safe embedding inside a JSON string literal.
   * Uses ObjectMapper to handle all control characters (\n, \t, \r, null bytes, etc.)
   * in addition to backslash and double-quote — preventing Elasticsearch DSL injection.
   * The surrounding quotes produced by writeValueAsString are stripped since the
   * surrounding quotes already exist in the template.
   */
  private def escapeForJson(value: String): String = {
    val withQuotes = jsonMapper.writeValueAsString(value)
    withQuotes.substring(1, withQuotes.length - 1)
  }
}
