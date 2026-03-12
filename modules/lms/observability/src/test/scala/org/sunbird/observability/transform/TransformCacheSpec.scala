package org.sunbird.observability.transform

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.sunbird.request.RequestContext

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for TransformCache.fetchWithCache().
 *
 * Uses a simple counter-based fetchFn to verify that the cache-aside logic:
 *   1. Serves hits from in-memory cache without invoking fetchFn.
 *   2. Calls fetchFn only for uncached IDs.
 *   3. Populates the cache so subsequent requests are served from cache.
 *   4. Merges cached + fresh results correctly.
 */
class TransformCacheSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach {

  // Reset the shared singleton cache between test cases to avoid cross-test pollution
  override def afterEach(): Unit = TransformCache.invalidateAll()

  private val ctx = new RequestContext()

  // A deterministic fetchFn: returns Map(id -> Map("value" -> id)) for each requested id,
  // and increments a counter so tests can verify call count.
  private def makeFetchFn(callCount: AtomicInteger)
      : (List[String], List[String]) => Map[String, Map[String, AnyRef]] =
    (ids, _) => {
      callCount.incrementAndGet()
      ids.map(id => id -> (Map("value" -> id.asInstanceOf[AnyRef]): Map[String, AnyRef])).toMap
    }

  "TransformCache.fetchWithCache" should {

    "call fetchFn and return all IDs on first (cold) request" in {
      val count = new AtomicInteger(0)
      val result = TransformCache.fetchWithCache(
        utilKey = "user",
        ids     = List("u1", "u2", "u3"),
        fields  = List("value"),
        ttl     = 300,
        maxSize = 1000,
        fetchFn = makeFetchFn(count),
        context = ctx
      )

      count.get() shouldBe 1
      result should have size 3
      result("u1") shouldBe Map("value" -> "u1")
      result("u3") shouldBe Map("value" -> "u3")
    }

    "not call fetchFn for IDs already in cache (warm path)" in {
      val count = new AtomicInteger(0)
      val fn = makeFetchFn(count)

      // First call — populates cache
      TransformCache.fetchWithCache("user", List("u1", "u2"), List.empty, 300, 1000, fn, ctx)
      count.get() shouldBe 1

      // Second call with same IDs — should be served entirely from cache
      val result = TransformCache.fetchWithCache("user", List("u1", "u2"), List.empty, 300, 1000, fn, ctx)
      count.get() shouldBe 1  // fetchFn was NOT called again

      result("u1") shouldBe Map("value" -> "u1")
      result("u2") shouldBe Map("value" -> "u2")
    }

    "call fetchFn only for uncached IDs on a partial hit" in {
      val count = new AtomicInteger(0)
      val fn = makeFetchFn(count)

      // Warm cache with u1, u2
      TransformCache.fetchWithCache("user", List("u1", "u2"), List.empty, 300, 1000, fn, ctx)

      // Second call: u1 cached, u3 and u4 are misses
      val result = TransformCache.fetchWithCache("user", List("u1", "u3", "u4"), List.empty, 300, 1000, fn, ctx)
      count.get() shouldBe 2  // fetchFn called once more (for u3, u4 only)

      result should have size 3
      result("u1") shouldBe Map("value" -> "u1")   // from cache
      result("u3") shouldBe Map("value" -> "u3")   // fresh
      result("u4") shouldBe Map("value" -> "u4")   // fresh
    }

    "return empty map for empty IDs list without calling fetchFn" in {
      val count = new AtomicInteger(0)
      val result = TransformCache.fetchWithCache(
        "user", List.empty, List.empty, 300, 1000, makeFetchFn(count), ctx)

      count.get() shouldBe 0
      result shouldBe empty
    }

    "use separate cache buckets per utilKey" in {
      val userCount       = new AtomicInteger(0)
      val collectionCount = new AtomicInteger(0)

      // Populate user cache
      TransformCache.fetchWithCache("user",       List("u1"), List.empty, 300, 1000, makeFetchFn(userCount),       ctx)
      // Populate collection cache
      TransformCache.fetchWithCache("collection", List("c1"), List.empty, 300, 1000, makeFetchFn(collectionCount), ctx)

      // Re-fetch both — should still be cached independently
      TransformCache.fetchWithCache("user",       List("u1"), List.empty, 300, 1000, makeFetchFn(userCount),       ctx)
      TransformCache.fetchWithCache("collection", List("c1"), List.empty, 300, 1000, makeFetchFn(collectionCount), ctx)

      userCount.get()       shouldBe 1  // no second call for user
      collectionCount.get() shouldBe 1  // no second call for collection
    }

    "invalidateAll clears all caches" in {
      val count = new AtomicInteger(0)
      val fn    = makeFetchFn(count)

      TransformCache.fetchWithCache("user", List("u1"), List.empty, 300, 1000, fn, ctx)
      count.get() shouldBe 1

      TransformCache.invalidateAll()

      // After invalidation, cache is empty → fetchFn must be called again
      TransformCache.fetchWithCache("user", List("u1"), List.empty, 300, 1000, fn, ctx)
      count.get() shouldBe 2
    }
  }
}
