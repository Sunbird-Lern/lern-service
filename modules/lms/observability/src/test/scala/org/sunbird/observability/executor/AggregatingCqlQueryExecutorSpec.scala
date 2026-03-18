package org.sunbird.observability.executor

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.sunbird.common.ProjectCommonException
import org.sunbird.observability.model._

class AggregatingCqlQueryExecutorSpec extends AnyWordSpec with Matchers {

  "AggregatingCqlQueryExecutor.applyAgg" should {

    "COUNT_IF with matchValue should count rows where value matches exactly" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val rows = List(
        Map("status" -> 2L),
        Map("status" -> 1L),
        Map("status" -> 2L),
        Map("status" -> 0L)
      )
      val agg = CountIfAgg("status", "completed_count", matchValue = Some(2), nonEmpty = None)
      val spec = AggregationSpec(List.empty, List(agg))

      val result = executor.applyAgg(agg, rows)
      result shouldBe 2L
    }

    "COUNT_IF with matchValue should return 0 when no rows match" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val rows = List(
        Map("status" -> 1L),
        Map("status" -> 0L)
      )
      val agg = CountIfAgg("status", "completed_count", matchValue = Some(2), nonEmpty = None)

      val result = executor.applyAgg(agg, rows)
      result shouldBe 0L
    }

    "COUNT_IF with nonEmpty should count non-empty strings" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val rows = List(
        Map("issued_certificates" -> "cert1"),
        Map("issued_certificates" -> null),
        Map("issued_certificates" -> ""),
        Map("issued_certificates" -> "cert2")
      )
      val agg = CountIfAgg("issued_certificates", "certificates_issued", matchValue = None, nonEmpty = Some(true))

      val result = executor.applyAgg(agg, rows)
      result shouldBe 2L  // non-empty strings only
    }

    "COUNT_IF with nonEmpty should count non-empty collections" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val list1 = new java.util.ArrayList[String]()
      list1.add("item1")
      val list2 = new java.util.ArrayList[String]()

      val rows = List(
        Map("issued_certificates" -> list1),
        Map("issued_certificates" -> list2),
        Map("issued_certificates" -> null)
      )
      val agg = CountIfAgg("issued_certificates", "certificates_issued", matchValue = None, nonEmpty = Some(true))

      val result = executor.applyAgg(agg, rows)
      result shouldBe 1L  // only non-empty collection
    }

    "COUNT_IF with both matchValue and nonEmpty unset should throw exception" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val rows = List(Map("status" -> 2L))
      val agg = CountIfAgg("status", "bad_count", matchValue = None, nonEmpty = None)

      assertThrows[ProjectCommonException] {
        executor.applyAgg(agg, rows)
      }
    }

    "COUNT_IF with matchValue should handle numeric toString comparison (integer)" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val rows = List(
        Map("status" -> 2),      // Int
        Map("status" -> 2L),     // Long
        Map("status" -> 1)
      )
      val agg = CountIfAgg("status", "completed_count", matchValue = Some(2), nonEmpty = None)

      val result = executor.applyAgg(agg, rows)
      result shouldBe 2L  // Both 2 (Int) and 2L (Long) should match
    }

    "COUNT_IF with matchValue should handle numeric toString comparison (floating point limitation)" in {
      val executor = new AggregatingCqlQueryExecutor(null, null, null, null)
      val rows = List(
        Map("score" -> 2.0d),
        Map("score" -> 2),
        Map("score" -> 1.5d)
      )
      // Note: This demonstrates the known limitation — 2.0.toString == "2.0", not "2"
      // Users should configure matchValue with exact string representation
      val agg = CountIfAgg("score", "matches", matchValue = Some("2.0"), nonEmpty = None)

      val result = executor.applyAgg(agg, rows)
      result shouldBe 1L  // Only 2.0.toString == "2.0" matches
    }
  }
}
