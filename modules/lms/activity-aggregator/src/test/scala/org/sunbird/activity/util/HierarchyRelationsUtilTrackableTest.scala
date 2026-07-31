package org.sunbird.activity.util

import java.util

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.request.RequestContext
import org.sunbird.response.Response

/** getTrackableNodes reads the `<root>:<root>:trackablenodes` relation, ordered, without dedup. */
class HierarchyRelationsUtilTrackableTest extends AnyFlatSpec with Matchers with MockFactory {

  private def responseWith(nodeIds: util.List[String]): Response = {
    val row = new util.HashMap[String, AnyRef]() {{ put("node_ids", nodeIds) }}
    val rows = new util.ArrayList[util.Map[String, AnyRef]](); rows.add(row)
    val r = new Response(); r.put("response", rows); r
  }
  private def nodeList(ids: String*): util.List[String] = {
    val l = new util.ArrayList[String](); ids.foreach(l.add); l
  }

  "getTrackableNodes" should "return the ordered trackable ids for the root" in {
    val ops = mock[CassandraOperation]
    (ops.getRecordsByProperties(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(responseWith(nodeList("CRS-A", "CRS-B", "CRS-C"))).once()
    val u = HierarchyRelationsUtil(ops)
    u.getTrackableNodes("trk-root-unique-1", null) shouldBe List("CRS-A", "CRS-B", "CRS-C")
  }

  it should "return empty when no trackablenodes relation exists" in {
    val ops = mock[CassandraOperation]
    val emptyResp = new Response(); emptyResp.put("response", new util.ArrayList[util.Map[String, AnyRef]]())
    (ops.getRecordsByProperties(_: String, _: String, _: util.Map[String, AnyRef], _: RequestContext))
      .expects(*, *, *, *).returns(emptyResp).once()
    val u = HierarchyRelationsUtil(ops)
    u.getTrackableNodes("trk-root-unique-2", null) shouldBe empty
  }
}
