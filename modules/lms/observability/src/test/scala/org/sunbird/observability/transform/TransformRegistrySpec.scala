package org.sunbird.observability.transform

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Tests for TransformRegistry.buildRegistry().
 *
 * Uses ConfigFactory.parseString() so that tests are hermetic — the production
 * application.conf is never involved, meaning tests are independent of
 * deployment configuration.
 */
class TransformRegistrySpec extends AnyWordSpec with Matchers {

  // ---- helpers -----------------------------------------------------------------

  /** Build a registry from a HOCON string. */
  private def buildFromHocon(hocon: String): Map[String, TransformRegistry.TransformEntry] =
    TransformRegistry.buildRegistry(ConfigFactory.parseString(hocon))

  /** Minimal valid HOCON with both user and collection configured. */
  private val fullConfig =
    """
      |observability.transform {
      |  field-mapping {
      |    user       = ["userid", "userIdentifier"]
      |    collection = ["courseid", "courseIdentifier"]
      |  }
      |  user {
      |    fields         = ["firstname", "lastname", "email"]
      |    result-key     = "userDetails"
      |    cache-ttl      = 300
      |    cache-max-size = 1000
      |  }
      |  collection {
      |    fields         = ["name", "identifier", "contentType"]
      |    result-key     = "collectionDetails"
      |    cache-ttl      = 3600
      |    cache-max-size = 500
      |  }
      |}
      |""".stripMargin

  // ---- tests -------------------------------------------------------------------

  "TransformRegistry.buildRegistry" should {

    "map every trigger field name to the correct TransformEntry" in {
      val registry = buildFromHocon(fullConfig)

      // user triggers
      registry.keys should contain allOf ("userid", "userIdentifier")
      // collection triggers
      registry.keys should contain allOf ("courseid", "courseIdentifier")
      registry should have size 4
    }

    "set the correct utilKey on each entry" in {
      val registry = buildFromHocon(fullConfig)
      registry("userid").utilKey      shouldBe "user"
      registry("userIdentifier").utilKey shouldBe "user"
      registry("courseid").utilKey    shouldBe "collection"
    }

    "set the correct fields list from config" in {
      val registry = buildFromHocon(fullConfig)
      registry("userid").fields should contain allOf ("firstname", "lastname", "email")
      registry("courseid").fields should contain allOf ("name", "identifier", "contentType")
    }

    "set the correct resultKey from config" in {
      val registry = buildFromHocon(fullConfig)
      registry("userid").resultKey    shouldBe "userDetails"
      registry("courseid").resultKey  shouldBe "collectionDetails"
    }

    "set the correct cacheTtl and cacheMaxSize" in {
      val registry = buildFromHocon(fullConfig)
      registry("userid").cacheTtl       shouldBe 300
      registry("userid").cacheMaxSize   shouldBe 1000
      registry("courseid").cacheTtl     shouldBe 3600
      registry("courseid").cacheMaxSize shouldBe 500
    }

    "use sensible fallback resultKey when result-key is omitted" in {
      val hocon =
        """
          |observability.transform {
          |  field-mapping { user = ["userid"] }
          |  user { fields = ["firstname"] }
          |}
          |""".stripMargin
      val registry = buildFromHocon(hocon)
      // fallback: "${utilKey}Details"
      registry("userid").resultKey shouldBe "userDetails"
    }

    "use default cacheTtl=300 and cacheMaxSize=1000 when omitted" in {
      val hocon =
        """
          |observability.transform {
          |  field-mapping { user = ["userid"] }
          |  user { fields = ["firstname"] }
          |}
          |""".stripMargin
      val registry = buildFromHocon(hocon)
      registry("userid").cacheTtl     shouldBe 300
      registry("userid").cacheMaxSize shouldBe 1000
    }

    "share the same util instance for all trigger fields of the same utilKey" in {
      val registry = buildFromHocon(fullConfig)
      // userid and userIdentifier must resolve to exactly the same util instance
      registry("userid").util should be theSameInstanceAs registry("userIdentifier").util
    }

    "return empty map when observability.transform config is absent" in {
      val registry = buildFromHocon("other.key = 42")
      registry shouldBe empty
    }

    "skip utilKey that has no registered util instance" in {
      // "unknown" is not in utilInstances, so its trigger fields should be skipped
      val hocon =
        """
          |observability.transform {
          |  field-mapping {
          |    user    = ["userid"]
          |    unknown = ["unknownField"]
          |  }
          |  user    { fields = ["firstname"] }
          |  unknown { fields = ["foo"] }
          |}
          |""".stripMargin
      val registry = buildFromHocon(hocon)
      registry should contain key "userid"
      registry should not contain key ("unknownField")
    }

    "return empty fields list when util's fields block is missing" in {
      val hocon =
        """
          |observability.transform {
          |  field-mapping { user = ["userid"] }
          |  // no user block at all
          |}
          |""".stripMargin
      val registry = buildFromHocon(hocon)
      // fields defaults to empty list (no NPE)
      registry("userid").fields shouldBe empty
    }
  }
}
