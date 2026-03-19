package org.sunbird.observability.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.sunbird.exception.ProjectCommonException

class FilterValidatorSpec extends AnyWordSpec with Matchers {

  "FilterValidator.validate" should {

    "accept valid scalar filters" in {
      FilterValidator.validate(Map("courseid" -> "c1"), List("courseid"))
      // passes without exception
    }

    "accept valid non-empty array filters" in {
      val ids = new java.util.ArrayList[String]()
      ids.add("id1")
      ids.add("id2")
      FilterValidator.validate(Map("courseids" -> ids), List("courseids"))
      // passes without exception
    }

    "reject unknown filter keys" in {
      assertThrows[ProjectCommonException] {
        FilterValidator.validate(Map("unknown" -> "value"), List("courseid"))
      }
    }

    "reject empty array filters" in {
      val ids = new java.util.ArrayList[String]()
      val ex = intercept[ProjectCommonException] {
        FilterValidator.validate(Map("courseids" -> ids), List("courseids"))
      }
      ex.getMessage should include("must not be empty")
    }

    "reject oversized array filters (> 100 elements)" in {
      val ids = new java.util.ArrayList[String]()
      (1 to 101).foreach(_ => ids.add("id"))
      val ex = intercept[ProjectCommonException] {
        FilterValidator.validate(Map("courseids" -> ids), List("courseids"))
      }
      ex.getMessage should include("exceed maximum size of 100")
    }

    "accept array filters with exactly 100 elements" in {
      val ids = new java.util.ArrayList[String]()
      (1 to 100).foreach(_ => ids.add("id"))
      FilterValidator.validate(Map("courseids" -> ids), List("courseids"))
      // passes without exception
    }

    "reject object/Map filters" in {
      val obj = new java.util.HashMap[String, String]()
      obj.put("key", "value")
      val ex = intercept[ProjectCommonException] {
        FilterValidator.validate(Map("filter" -> obj), List("filter"))
      }
      ex.getMessage should include("must be a scalar or array value, not an object")
    }

    "allow null request filters (early return)" in {
      FilterValidator.validate(null, List("courseid"))
      FilterValidator.validate(Map.empty, List("courseid"))
      // passes without exception
    }
  }
}
