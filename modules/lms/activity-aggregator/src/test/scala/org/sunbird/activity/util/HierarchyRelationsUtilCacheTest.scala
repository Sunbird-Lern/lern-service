package org.sunbird.activity.util

import java.util

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.request.RequestContext
import org.sunbird.response.Response

/**
 * Tests the JVM-wide TTL cache added to HierarchyRelationsUtil.readFromDB: a repeated lookup for the
 * same relationship_key is served from memory (DB hit once), and empty results are NOT cached (so a
 * freshly-published collection is not held stale). Unique keys per test avoid cross-test cache bleed.
 */
class HierarchyRelationsUtilCacheTest extends AnyFlatSpec with Matchers with MockFactory {

  private def responseWith(nodeIds: util.List[String]): Response = {
    val row = new util.HashMap[String, AnyRef]() {{ put("node_ids", nodeIds) }}
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(row)
    val r = new Response(); r.put("response", rows); r
  }

  private def nodeList(ids: String*): util.List[String] = {
    val l = new util.ArrayList[String](); ids.foreach(l.add); l
  }

  "getLeafNodes" should "hit the DB once and serve the repeat from cache" in {
    val ops = mock[CassandraOperation]
    // 4-arg getRecordsByProperties is what readFromDB uses; expect EXACTLY one DB call for two lookups
    (ops.getRecordsByProperties(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(responseWith(nodeList("leaf-A", "leaf-B"))).once()
    val util0 = HierarchyRelationsUtil(ops)
    val col = "cacheHit-collection-unique-1"
    val first = util0.getLeafNodes(col, col, null)
    val second = util0.getLeafNodes(col, col, null)
    first should contain allOf("leaf-A", "leaf-B")
    second shouldBe first
  }

  "readFromDB" should "not cache empty results (freshly-published collection is re-read)" in {
    val ops = mock[CassandraOperation]
    val emptyResp = new Response(); emptyResp.put("response", new util.ArrayList[util.Map[String, AnyRef]]())
    val populated = responseWith(nodeList("leaf-X"))
    // first lookup empty (not published yet), second returns data -> BOTH must hit the DB
    (ops.getRecordsByProperties(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(emptyResp).once()
    (ops.getRecordsByProperties(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(populated).once()
    val util0 = HierarchyRelationsUtil(ops)
    val col = "negativeCache-collection-unique-2"
    util0.getLeafNodes(col, col, null) shouldBe empty
    util0.getLeafNodes(col, col, null) should contain("leaf-X")
  }
}
