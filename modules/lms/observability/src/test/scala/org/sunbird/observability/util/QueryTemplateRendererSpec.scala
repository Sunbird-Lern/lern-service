package org.sunbird.observability.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class QueryTemplateRendererSpec extends AnyWordSpec with Matchers {

  "QueryTemplateRenderer.renderEs" should {

    "substitute direct placeholders" in {
      val template = """{"channel":"{{channel}}"}"""
      val result = QueryTemplateRenderer.renderEs(template, Map("channel" -> "testChannel"))
      result shouldBe """{"channel":"testChannel"}"""
    }

    "expand optional blocks when filter is present" in {
      val template = """{"filters":{"objectType":"User"{{#channel}},"channel":"{{channel}}"{{/channel}}}}"""
      val result = QueryTemplateRenderer.renderEs(template, Map("channel" -> "testChannel"))
      result shouldBe """{"filters":{"objectType":"User","channel":"testChannel"}}"""
    }

    "remove optional blocks when filter is absent" in {
      val template = """{"filters":{"objectType":"User"{{#channel}},"channel":"{{channel}}"{{/channel}}}}"""
      val result = QueryTemplateRenderer.renderEs(template, Map.empty)
      result shouldBe """{"filters":{"objectType":"User"}}"""
    }

    "handle multiple optional blocks" in {
      val template = """SELECT 1{{#a}} AND a={{a}}{{/a}}{{#b}} AND b={{b}}{{/b}}"""
      QueryTemplateRenderer.renderEs(template, Map("a" -> "1")) shouldBe "SELECT 1 AND a=1"
      QueryTemplateRenderer.renderEs(template, Map("b" -> "2")) shouldBe "SELECT 1 AND b=2"
      QueryTemplateRenderer.renderEs(template, Map("a" -> "1", "b" -> "2")) shouldBe "SELECT 1 AND a=1 AND b=2"
    }
  }

  "QueryTemplateRenderer.renderSql" should {

    "produce parameterized SQL with correct bind values" in {
      val template = "SELECT * FROM usr WHERE channel = {{channel}}"
      val result = QueryTemplateRenderer.renderSql(template, Map("channel" -> "testChannel"))
      result.query shouldBe "SELECT * FROM usr WHERE channel = ?"
      result.params shouldBe List("testChannel")
    }

    "produce parameterized SQL for optional block" in {
      val template = "SELECT * FROM usr WHERE 1=1{{#channel}} AND channel = {{channel}}{{/channel}}"
      val result = QueryTemplateRenderer.renderSql(template, Map("channel" -> "testChannel"))
      result.query shouldBe "SELECT * FROM usr WHERE 1=1 AND channel = ?"
      result.params shouldBe List("testChannel")
    }

    "omit optional SQL block when filter absent" in {
      val template = "SELECT * FROM usr WHERE 1=1{{#channel}} AND channel = {{channel}}{{/channel}}"
      val result = QueryTemplateRenderer.renderSql(template, Map.empty)
      result.query shouldBe "SELECT * FROM usr WHERE 1=1"
      result.params shouldBe List.empty
    }

    "collect params in left-to-right order when direct placeholder precedes optional block" in {
      val template = "SELECT * FROM t WHERE courseid = {{courseid}} {{#batchid}}AND batchid = {{batchid}} {{/batchid}}ALLOW FILTERING"
      val result = QueryTemplateRenderer.renderSql(template,
        Map("courseid" -> "do_123", "batchid" -> "batch_456"))
      result.query  shouldBe "SELECT * FROM t WHERE courseid = ? AND batchid = ? ALLOW FILTERING"
      result.params shouldBe List("do_123", "batch_456")
    }

    "collect params in left-to-right order when optional block precedes direct placeholder" in {
      val template = "SELECT * FROM t WHERE {{#batchid}}batchid = {{batchid}} AND {{/batchid}}courseid = {{courseid}}"
      val result = QueryTemplateRenderer.renderSql(template,
        Map("courseid" -> "do_123", "batchid" -> "batch_456"))
      result.query  shouldBe "SELECT * FROM t WHERE batchid = ? AND courseid = ?"
      result.params shouldBe List("batch_456", "do_123")
    }

    "handle template with newlines inside optional block (DOTALL)" in {
      val template = "SELECT * FROM t WHERE courseid = {{courseid}}\n{{#batchid}}AND batchid = {{batchid}}\n{{/batchid}}ALLOW FILTERING"
      val result = QueryTemplateRenderer.renderSql(template,
        Map("courseid" -> "do_123", "batchid" -> "batch_456"))
      result.query  shouldBe "SELECT * FROM t WHERE courseid = ? AND batchid = ? ALLOW FILTERING"
      result.params shouldBe List("do_123", "batch_456")
    }
  }
}
