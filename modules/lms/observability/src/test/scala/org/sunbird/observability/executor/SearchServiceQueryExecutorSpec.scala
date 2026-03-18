package org.sunbird.observability.executor

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SearchServiceQueryExecutorSpec extends AnyWordSpec with Matchers {

  "SearchServiceQueryExecutor facet extraction" should {

    "extract facets and flatten into rows with facet name as key" in {
      // Simulates Search Service response with facets
      val responseJson = """{
        "result": {
          "facets": [
            {
              "name": "status",
              "values": [
                {"name": "Draft", "count": 5},
                {"name": "Live", "count": 10}
              ]
            },
            {
              "name": "createdBy",
              "values": [
                {"name": "user1", "count": 8},
                {"name": "user2", "count": 7}
              ]
            }
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 4  // 2 status + 2 createdBy values
      result.head.keySet should contain("facet")

      // Verify facet names are used as keys
      val statusRows = result.filter(r => r("facet") == "status")
      statusRows should have length 2
      statusRows.head.keySet should contain("status")
      statusRows.head("status") shouldBe "Draft"
      statusRows.head("count") shouldBe 5
    }

    "handle missing name key in facet (skip gracefully)" in {
      val responseJson = """{
        "result": {
          "facets": [
            {
              "values": [
                {"name": "value1", "count": 5}
              ]
            }
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 0  // facet with missing "name" is skipped
    }

    "handle missing name key in facet value (skip gracefully)" in {
      val responseJson = """{
        "result": {
          "facets": [
            {
              "name": "status",
              "values": [
                {"count": 5},
                {"name": "Valid", "count": 3}
              ]
            }
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 1  // only value with name is included
      result.head("status") shouldBe "Valid"
    }

    "handle missing count key in facet value (skip gracefully)" in {
      val responseJson = """{
        "result": {
          "facets": [
            {
              "name": "status",
              "values": [
                {"name": "NoCount"},
                {"name": "WithCount", "count": 3}
              ]
            }
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 1
      result.head("count") shouldBe 3
    }

    "handle null facets value gracefully (fall through to content/data extraction)" in {
      val responseJson = """{
        "result": {
          "facets": null,
          "content": [
            {"id": "1", "title": "Item1"}
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 1
      result.head("id") shouldBe "1"
      result.head.keySet should not contain "facet"  // not facet response
    }

    "handle missing facets key entirely (fall through to content/data extraction)" in {
      val responseJson = """{
        "result": {
          "content": [
            {"id": "1", "title": "Item1"}
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 1
      result.head("id") shouldBe "1"
    }

    "handle malformed facets (not an array) gracefully" in {
      val responseJson = """{
        "result": {
          "facets": {"name": "status"},
          "content": [
            {"id": "fallback"}
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      // Should fall through to content extraction
      result should have length 1
      result.head("id") shouldBe "fallback"
    }

    "handle null facets values array gracefully (skip facet)" in {
      val responseJson = """{
        "result": {
          "facets": [
            {
              "name": "status",
              "values": null
            },
            {
              "name": "category",
              "values": [
                {"name": "cat1", "count": 2}
              ]
            }
          ]
        }
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 1  // only category facet with valid values
      result.head("category") shouldBe "cat1"
    }

    "return empty list on malformed JSON" in {
      val malformedJson = "{broken json"

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(malformedJson, List.empty)

      result should have length 0
    }

    "return empty list when result key is missing or null" in {
      val responseJson = """{
        "result": null
      }"""

      val executor = new SearchServiceQueryExecutor()
      val result = executor.execute(responseJson, List.empty)

      result should have length 0
    }
  }
}
