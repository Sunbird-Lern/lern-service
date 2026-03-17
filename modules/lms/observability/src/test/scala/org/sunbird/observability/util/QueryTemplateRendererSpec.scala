package org.sunbird.observability.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class QueryTemplateRendererSpec extends AnyWordSpec with Matchers {

  "QueryTemplateRenderer.renderEs" should {

    "substitute direct placeholders" in {
      val template = """{"key":"{{key}}"}"""
      val result = QueryTemplateRenderer.renderEs(template, Map("key" -> "value"))
      result shouldBe """{"key":"value"}"""
    }

    "expand optional blocks when filter is present" in {
      val template = """{"filters":{"type":"Generic"{{#key}},"key":"{{key}}"{{/key}}}}"""
      val result = QueryTemplateRenderer.renderEs(template, Map("key" -> "value"))
      result shouldBe """{"filters":{"type":"Generic","key":"value"}}"""
    }

    "remove optional blocks when filter is absent" in {
      val template = """{"filters":{"type":"Generic"{{#key}},"key":"{{key}}"{{/key}}}}"""
      val result = QueryTemplateRenderer.renderEs(template, Map.empty)
      result shouldBe """{"filters":{"type":"Generic"}}"""
    }

    "handle multiple optional blocks" in {
      val template = """SELECT 1{{#p1}} AND p1={{p1}}{{/p1}}{{#p2}} AND p2={{p2}}{{/p2}}"""
      QueryTemplateRenderer.renderEs(template, Map("p1" -> "v1")) shouldBe "SELECT 1 AND p1=v1"
      QueryTemplateRenderer.renderEs(template, Map("p2" -> "v2")) shouldBe "SELECT 1 AND p2=v2"
      QueryTemplateRenderer.renderEs(template, Map("p1" -> "v1", "p2" -> "v2")) shouldBe "SELECT 1 AND p1=v1 AND p2=v2"
    }
  }

  "QueryTemplateRenderer.renderSql" should {

    "produce parameterized SQL with correct bind values" in {
      val template = "SELECT * FROM table WHERE key = {{key}}"
      val result = QueryTemplateRenderer.renderSql(template, Map("key" -> "value"))
      result.query shouldBe "SELECT * FROM table WHERE key = ?"
      result.params shouldBe List("value")
    }

    "produce parameterized SQL for optional block" in {
      val template = "SELECT * FROM table WHERE 1=1{{#key}} AND key = {{key}}{{/key}}"
      val result = QueryTemplateRenderer.renderSql(template, Map("key" -> "value"))
      result.query shouldBe "SELECT * FROM table WHERE 1=1 AND key = ?"
      result.params shouldBe List("value")
    }

    "omit optional SQL block when filter absent" in {
      val template = "SELECT * FROM table WHERE 1=1{{#key}} AND key = {{key}}{{/key}}"
      val result = QueryTemplateRenderer.renderSql(template, Map.empty)
      result.query shouldBe "SELECT * FROM table WHERE 1=1"
      result.params shouldBe List.empty
    }

    "collect params in left-to-right order when direct placeholder precedes optional block" in {
      val template = "SELECT * FROM tbl WHERE p1 = {{p1}} {{#p2}}AND p2 = {{p2}} {{/p2}}ALLOW FILTERING"
      val result = QueryTemplateRenderer.renderSql(template,
        Map("p1" -> "v1", "p2" -> "v2"))
      result.query  shouldBe "SELECT * FROM tbl WHERE p1 = ? AND p2 = ? ALLOW FILTERING"
      result.params shouldBe List("v1", "v2")
    }

    "collect params in left-to-right order when optional block precedes direct placeholder" in {
      val template = "SELECT * FROM tbl WHERE {{#p2}}p2 = {{p2}} AND {{/p2}}p1 = {{p1}}"
      val result = QueryTemplateRenderer.renderSql(template,
        Map("p1" -> "v1", "p2" -> "v2"))
      result.query  shouldBe "SELECT * FROM tbl WHERE p2 = ? AND p1 = ?"
      result.params shouldBe List("v2", "v1")
    }

    "handle template with newlines inside optional block (DOTALL)" in {
      val template = "SELECT * FROM tbl WHERE p1 = {{p1}}\n{{#p2}}AND p2 = {{p2}}\n{{/p2}}ALLOW FILTERING"
      val result = QueryTemplateRenderer.renderSql(template,
        Map("p1" -> "v1", "p2" -> "v2"))
      result.query  shouldBe "SELECT * FROM tbl WHERE p1 = ? AND p2 = ? ALLOW FILTERING"
      result.params shouldBe List("v1", "v2")
    }

    "expand a java.util.List value to IN clause placeholders" in {
      val template = "SELECT * FROM tbl WHERE id IN ({{ids}})"
      val ids = new java.util.ArrayList[String](java.util.Arrays.asList("v1", "v2", "v3"))
      val result = QueryTemplateRenderer.renderSql(template, Map("ids" -> ids))
      result.query  shouldBe "SELECT * FROM tbl WHERE id IN (?, ?, ?)"
      result.params shouldBe List("v1", "v2", "v3")
    }

    "expand a Scala List value to IN clause placeholders" in {
      val template = "SELECT * FROM tbl WHERE id IN ({{ids}})"
      val result = QueryTemplateRenderer.renderSql(template, Map("ids" -> List("v1", "v2")))
      result.query  shouldBe "SELECT * FROM tbl WHERE id IN (?, ?)"
      result.params shouldBe List("v1", "v2")
    }

    "expand a single-element collection to a single IN clause placeholder" in {
      val template = "SELECT * FROM tbl WHERE id IN ({{ids}})"
      val ids = new java.util.ArrayList[String](java.util.Arrays.asList("v1"))
      val result = QueryTemplateRenderer.renderSql(template, Map("ids" -> ids))
      result.query  shouldBe "SELECT * FROM tbl WHERE id IN (?)"
      result.params shouldBe List("v1")
    }

    "preserve correct param order when IN clause placeholder is mixed with scalar filters" in {
      val template = "SELECT * FROM tbl WHERE p1 = {{p1}} AND id IN ({{ids}}) ALLOW FILTERING"
      val ids = new java.util.ArrayList[String](java.util.Arrays.asList("v1", "v2"))
      val result = QueryTemplateRenderer.renderSql(template, Map("p1" -> "a", "ids" -> ids))
      result.query  shouldBe "SELECT * FROM tbl WHERE p1 = ? AND id IN (?, ?) ALLOW FILTERING"
      result.params shouldBe List("a", "v1", "v2")
    }

    "expand collection value inside an optional block" in {
      val template = "SELECT * FROM tbl WHERE 1=1{{#ids}} AND id IN ({{ids}}){{/ids}}"
      val ids = new java.util.ArrayList[String](java.util.Arrays.asList("v1", "v2"))
      val result = QueryTemplateRenderer.renderSql(template, Map("ids" -> ids))
      result.query  shouldBe "SELECT * FROM tbl WHERE 1=1 AND id IN (?, ?)"
      result.params shouldBe List("v1", "v2")
    }

    "omit optional block when collection filter is absent" in {
      val template = "SELECT * FROM tbl WHERE 1=1{{#ids}} AND id IN ({{ids}}){{/ids}}"
      val result = QueryTemplateRenderer.renderSql(template, Map.empty)
      result.query  shouldBe "SELECT * FROM tbl WHERE 1=1"
      result.params shouldBe List.empty
    }
  }
}
