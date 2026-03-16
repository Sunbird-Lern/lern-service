package org.sunbird.observability.transform

import com.google.common.cache.{Cache, CacheBuilder}
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.RequestContext

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.collection.JavaConverters._

/**
 * Per-utility in-memory cache backed by Guava CacheBuilder.
 *
 * Each utilKey ("user", "collection", ...) gets its own Cache instance with
 * independent TTL and max-size — so collections can be cached for an hour
 * while user profiles expire in 5 minutes.
 *
 * Design choices:
 *   - In-memory over Redis: cache hits cost ~100ns (HashMap lookup) instead of
 *     ~0.5-2ms per network round trip. For N report rows all cached, Guava costs
 *     sub-millisecond; Redis would cost N * 0.5ms. No serialisation overhead either.
 *   - Cross-instance sharing is not required: reports are low-frequency admin
 *     operations where a few minutes of staleness per JVM is acceptable.
 *   - Guava (v32.1.2-jre) is already on the classpath via sunbird-platform-common.
 *     No new dependency needed.
 *   - Memory impact: ~200 bytes per entry × 1000 max = ~200KB per util — negligible.
 *
 * Thread-safety: ConcurrentHashMap.computeIfAbsent + Guava Cache are both thread-safe.
 */
object TransformCache {

  private val logger = new LoggerUtil(TransformCache.getClass)

  // One Guava cache per utilKey; created lazily on first use.
  private val cacheMap =
    new ConcurrentHashMap[String, Cache[String, Map[String, AnyRef]]]()

  /**
   * Cache-aside fetch for any TransformUtil.
   *
   * @param utilKey  Namespace / cache bucket identifier (e.g. "user", "collection").
   * @param ids      All IDs needed, already deduplicated by the caller.
   * @param fields   Allowlisted fields — forwarded to fetchFn for uncached IDs.
   * @param ttl      Seconds before a cached entry expires (expireAfterWrite).
   * @param maxSize  Maximum number of entries; LRU eviction beyond this.
   * @param fetchFn  Source fetch; receives only the uncached IDs.
   * @param context  Request context for logging.
   * @return         Merged map of id -> projected detail map (cached + fresh).
   */
  def fetchWithCache(
      utilKey: String,
      ids:     List[String],
      fields:  List[String],
      ttl:     Int,
      maxSize: Int,
      fetchFn: (List[String], List[String]) => Map[String, Map[String, AnyRef]],
      context: RequestContext
  ): Map[String, Map[String, AnyRef]] = {

    val cache = getOrCreateCache(utilKey, ttl, maxSize)

    // Step 1 — Probe in-memory cache for all IDs at once (~100ns each, no I/O).
    // Known TOCTOU: two concurrent requests for the same uncached ID will both call
    // fetchFn and both populate the cache. This is an idempotent write race — the
    // result is correct (last writer wins with the same value), just slightly wasteful.
    // For this use case (low-frequency admin reports) the trade-off is acceptable.
    // A per-ID lock or Guava LoadingCache would eliminate the race but would require
    // fetching one ID at a time, losing the batching optimisation in fetchFn.
    val cached: Map[String, Map[String, AnyRef]] =
      ids.flatMap(id => Option(cache.getIfPresent(id)).map(id -> _)).toMap

    val hitCount  = cached.size
    val missCount = ids.size - hitCount
    logger.info(context,
      s"TransformCache[$utilKey]: $hitCount hit(s), $missCount miss(es) out of ${ids.size}")

    // Step 2 — Fetch only the misses from the actual source
    val uncachedIds = ids.filterNot(cached.contains)
    val fresh: Map[String, Map[String, AnyRef]] =
      if (uncachedIds.isEmpty) Map.empty
      else fetchFn(uncachedIds, fields)

    // Step 3 — Populate cache with fresh entries for future requests
    fresh.foreach { case (id, details) => cache.put(id, details) }

    // Step 4 — Return the merged result
    cached ++ fresh
  }

  /**
   * Invalidates all caches. Intended for use in tests to ensure isolation
   * between test cases; not for production use.
   */
  def invalidateAll(): Unit =
    cacheMap.values().asScala.foreach(_.invalidateAll())

  // ---------------------------------------------------------------------------

  private def getOrCreateCache(
      utilKey: String,
      ttl:     Int,
      maxSize: Int
  ): Cache[String, Map[String, AnyRef]] =
    cacheMap.computeIfAbsent(
      utilKey,
      _ => CacheBuilder.newBuilder()
             .maximumSize(maxSize)
             .expireAfterWrite(ttl, TimeUnit.SECONDS)
             .build[String, Map[String, AnyRef]]()
    )
}
