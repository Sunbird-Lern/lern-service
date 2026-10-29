package org.sunbird.viewer.competency

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.sunbird.cassandra.CassandraOperation
import org.sunbird.keys.JsonKey
import org.sunbird.response.Response

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Role handling end to end through CompetencyService and the real CompetencyDao, over an
 * in-memory stand-in for Cassandra and a canned framework read.
 */
class RoleHandlingSpec extends AnyFlatSpec with Matchers {

  private val fw = "fw_health_competency"
  private val learner = "user-1"

  // Three tiers; leaves a, b, c, d.
  private val framework =
    """{"result":{"framework":{"identifier":"fw_health_competency","type":"Competency",
       "categories":[{"code":"competency","terms":[
         {"code":"area","children":[
           {"code":"comp-1","children":[{"code":"a"},{"code":"b"}]},
           {"code":"comp-2","children":[{"code":"c"},{"code":"d"}]}]}]}]}}}"""

  /** Primary key columns per table, so an insert overwrites the row Cassandra would. */
  private val keys = Map(
    "role_skill" -> List("framework_id", "roleid", "skillid"),
    "user_role" -> List("userid"),
    "user_skill_evidence" -> List("userid", "skillid", "evidenceid"))

  /**
   * Just enough of CassandraOperation for the role and profile tables.
   *
   * An insert merges into the existing row, as a CQL INSERT does: columns it does not name keep
   * their value. A batch applies all of its rows or none of them.
   */
  private class InMemoryCassandra extends InvocationHandler {
    val tables = mutable.Map[String, mutable.LinkedHashMap[List[AnyRef], util.Map[String, AnyRef]]]()

    private var failAtRow = 0
    private var rowsWritten = 0

    /** The Nth row written from now on fails, together with the rest of its statement. */
    def failNthRow(n: Int): Unit = failAtRow = rowsWritten + n

    private def table(name: String) = tables.getOrElseUpdate(name, mutable.LinkedHashMap())

    private def matches(row: util.Map[String, AnyRef], filter: util.Map[String, _]): Boolean =
      filter.asScala.forall { case (k, v) => row.get(k) == v }

    /** Counts `n` rows against the injected failure, before any of them is applied. */
    private def write(n: Int): Unit = {
      if (failAtRow > 0 && rowsWritten < failAtRow && rowsWritten + n >= failAtRow) {
        failAtRow = 0
        throw new RuntimeException("injected write timeout")
      }
      rowsWritten += n
    }

    private def insert(tableName: String, values: util.Map[String, AnyRef]): Unit = {
      val t = table(tableName)
      val key = keys(tableName).map(values.get)
      t.getOrElseUpdate(key, new util.HashMap[String, AnyRef]()).putAll(values)
    }

    private def delete(tableName: String, key: util.Map[String, _]): Unit = {
      val t = table(tableName)
      t.filter { case (_, row) => matches(row, key) }.keys.toList.foreach(t.remove)
    }

    override def invoke(proxy: AnyRef, m: Method, args: Array[AnyRef]): AnyRef = m.getName match {
      case "insertRecord" if args.length == 4 =>
        write(1)
        insert(args(1).toString, args(2).asInstanceOf[util.Map[String, AnyRef]])
        new Response()
      case "batchInsertLogged" =>
        val rows = args(2).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
        write(rows.size)
        rows.foreach(insert(args(1).toString, _))
        new Response()
      case "getRecords" if args.length == 5 =>
        val filter = args(2).asInstanceOf[util.Map[String, AnyRef]]
        val found = table(args(1).toString).values.filter(matches(_, filter)).toList
        val r = new Response()
        r.put(JsonKey.RESPONSE, new util.ArrayList[util.Map[String, AnyRef]](found.asJava))
        r
      case "deleteRecord" if args.length == 4 && args(2).isInstanceOf[util.Map[_, _]] =>
        write(1)
        delete(args(1).toString, args(2).asInstanceOf[util.Map[String, String]])
        null
      case "batchDelete" =>
        val ks = args(2).asInstanceOf[util.List[util.Map[String, AnyRef]]].asScala
        write(ks.size)
        ks.foreach(delete(args(1).toString, _))
        new Response()
      case other => throw new UnsupportedOperationException(s"not faked: $other")
    }
  }

  private def newService(store: InMemoryCassandra = new InMemoryCassandra): (CompetencyService, InMemoryCassandra) = {
    CompetencyFrameworkUtil.invalidate("") // the cache is JVM-wide; start each test cold
    val ops = Proxy.newProxyInstance(getClass.getClassLoader, Array(classOf[CassandraOperation]), store)
      .asInstanceOf[CassandraOperation]
    val service = new CompetencyService(ops, "sunbird_courses") {
      override protected def newFrameworkUtil(dao: CompetencyDao): CompetencyFrameworkUtil =
        new CompetencyFrameworkUtil(dao.readRoleSkills) {
          override protected def fetchFramework(frameworkId: String): String = framework
        }
    }
    (service, store)
  }

  private def role(id: String, skills: String*) = RoleDefinition(id, id, skills.toSet)

  /** (skills required, readiness) for one role, or None when the role cannot be measured. */
  private def measured(s: CompetencyService, roleId: String): Option[(Int, Int)] =
    s.gap(learner, fw, roleId, null).map { case (rows, readiness) => (rows.size, readiness) }

  // ---- D1: readiness must not fail open ---------------------------------------------------------

  "gap" should "still measure a learner against their assigned role after it is retired" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.assignRole(learner, fw, "staff-nurse-icu", null)
    s.roleRetire(fw, "staff-nurse-icu", null)

    measured(s, "staff-nurse-icu") shouldBe Some((2, 0))
  }

  it should "not report an unknown role as 100% ready" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    measured(s, "no-such-role") shouldBe None
  }

  it should "still measure a live role normally" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    measured(s, "staff-nurse-icu") shouldBe Some((2, 0))
  }

  // ---- D3: a role edit is visible on every instance at once -------------------------------------

  // The framework cache is per JVM. Another pod's role write reaches the shared database but not
  // this JVM's cache, so it is modelled as rows written straight into the store, with no
  // invalidation here.
  private def writtenByAnotherPod(store: InMemoryCassandra, roleId: String, skills: String*): Unit = {
    val t = store.tables.getOrElseUpdate("role_skill", mutable.LinkedHashMap())
    skills.foreach { sk =>
      val row = new util.HashMap[String, AnyRef]()
      row.put("framework_id", fw); row.put("roleid", roleId); row.put("skillid", sk)
      row.put("role_name", roleId); row.put("status", RoleStatus.LIVE)
      row.put("version", Integer.valueOf(1)); row.put("added_on", new util.Date())
      t.put(List(fw, roleId, sk), row)
    }
  }

  "a role authored on another pod" should "be measured here straight away" in {
    val (s, store) = newService()
    s.meta(fw, null) // this pod's cache is warm before the role exists

    writtenByAnotherPod(store, "staff-nurse-icu", "a", "b")

    measured(s, "staff-nurse-icu") shouldBe Some((2, 0))
  }

  it should "show a requirement change made on another pod straight away" in {
    val (s, store) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    measured(s, "staff-nurse-icu") shouldBe Some((2, 0)) // this pod caches the two-skill version

    writtenByAnotherPod(store, "staff-nurse-icu", "c")

    measured(s, "staff-nurse-icu") shouldBe Some((3, 0))
  }

  // ---- D2: role assignments must name a live role -----------------------------------------------

  "updateRole" should "reject a target role that does not exist" in {
    val (s, store) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    an[IllegalArgumentException] should be thrownBy
      s.updateRole(RoleAssignment(learner, fw, None, Set("foo"), RoleSource.SELF, 0L), null)
    store.tables.get("user_role").forall(_.isEmpty) shouldBe true
  }

  it should "reject a current role that does not exist" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    an[IllegalArgumentException] should be thrownBy
      s.updateRole(RoleAssignment(learner, fw, Some("bar"), Set.empty, RoleSource.HRMS, 0L), null)
  }

  it should "reject a newly chosen target role that is retired" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.roleRetire(fw, "staff-nurse-icu", null)

    an[IllegalArgumentException] should be thrownBy
      s.updateRole(RoleAssignment(learner, fw, None, Set("staff-nurse-icu"), RoleSource.SELF, 0L), null)
  }

  it should "keep a retired role the learner already holds when only the targets change" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.roleUpsert(fw, role("nursing-officer", "c"), null)
    s.assignRole(learner, fw, "staff-nurse-icu", null)
    s.roleRetire(fw, "staff-nurse-icu", null)

    s.updateRole(RoleAssignment(learner, fw, Some("staff-nurse-icu"), Set("nursing-officer"),
      RoleSource.SELF, 0L), null)
    s.role(learner, null).map(a => (a.currentRole, a.targetRoles)) shouldBe
      Some((Some("staff-nurse-icu"), Set("nursing-officer")))
  }

  it should "accept live roles" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.roleUpsert(fw, role("nursing-officer", "c"), null)

    s.updateRole(RoleAssignment(learner, fw, Some("staff-nurse-icu"), Set("nursing-officer"),
      RoleSource.HRMS, 0L), null)
    s.role(learner, null).flatMap(_.currentRole) shouldBe Some("staff-nurse-icu")
  }

  // ---- D4: an upsert must never delete a role ---------------------------------------------------

  "roleUpsert" should "reject an empty skill set and leave the role intact" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    an[IllegalArgumentException] should be thrownBy s.roleUpsert(fw, role("staff-nurse-icu"), null)
    s.roleRead(fw, Some("staff-nurse-icu"), null).map(_.skills) shouldBe List(Set("a", "b"))
  }

  it should "reject a skill set with no valid leaf and leave the role intact" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    an[IllegalArgumentException] should be thrownBy
      s.roleUpsert(fw, role("staff-nurse-icu", "comp-1", "typo"), null)
    s.roleRead(fw, Some("staff-nurse-icu"), null).map(_.skills) shouldBe List(Set("a", "b"))
  }

  "roleImport" should "write nothing when any role in the matrix has no valid skill" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)

    an[IllegalArgumentException] should be thrownBy
      s.roleImport(fw, List(role("nursing-officer", "c"), role("staff-nurse-icu")), dryRun = false, null)
    s.roleRead(fw, None, null).map(r => r.roleId -> r.skills) shouldBe
      List("staff-nurse-icu" -> Set("a", "b"))
  }

  // ---- D6: a role write that fails part-way must not leave the role half changed ----------------

  private def statuses(store: InMemoryCassandra, roleId: String): Set[AnyRef] =
    store.tables("role_skill").values.filter(_.get("roleid") == roleId).map(_.get("status")).toSet

  "roleRetire" should "retire every row of the role or none when a write fails" in {
    val (s, store) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b", "c"), null)
    store.failNthRow(2)

    an[Exception] should be thrownBy s.roleRetire(fw, "staff-nurse-icu", null)
    statuses(store, "staff-nurse-icu") should have size 1
  }

  "roleUpsert" should "never leave a role short of both its old and new skills when a write fails" in {
    val (s, store) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    store.failNthRow(2)

    an[Exception] should be thrownBy s.roleUpsert(fw, role("staff-nurse-icu", "c", "d"), null)
    val skills = s.roleRead(fw, Some("staff-nurse-icu"), null).map(_.skills).head
    Set(Set("a", "b"), Set("c", "d"), Set("a", "b", "c", "d")) should contain(skills)
  }

  // ---- D7: a retired role stays retired; targets can be cleared ---------------------------------

  private def statusOf(s: CompetencyService, roleId: String): String =
    s.roleRead(fw, Some(roleId), null).map(_.status).head

  it should "keep a retired role retired when its requirements are edited" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.roleRetire(fw, "staff-nurse-icu", null)

    s.roleUpsert(fw, role("staff-nurse-icu", "a", "c"), null)
    statusOf(s, "staff-nurse-icu") shouldBe RoleStatus.RETIRED
  }

  it should "bring a retired role back only when asked to reactivate it" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.roleRetire(fw, "staff-nurse-icu", null)

    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null, reactivate = true)
    statusOf(s, "staff-nurse-icu") shouldBe RoleStatus.LIVE
  }

  "roleImport" should "not bring back a retired role named in an old matrix" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.roleRetire(fw, "staff-nurse-icu", null)

    s.roleImport(fw, List(role("staff-nurse-icu", "a", "b"), role("nursing-officer", "c")),
      dryRun = false, null)
    statusOf(s, "staff-nurse-icu") shouldBe RoleStatus.RETIRED
    statusOf(s, "nursing-officer") shouldBe RoleStatus.LIVE
  }

  "updateRole" should "clear the target roles when given an empty set" in {
    val (s, _) = newService()
    s.roleUpsert(fw, role("staff-nurse-icu", "a", "b"), null)
    s.updateRole(RoleAssignment(learner, fw, None, Set("staff-nurse-icu"), RoleSource.SELF, 0L), null)

    s.updateRole(RoleAssignment(learner, fw, None, Set.empty, RoleSource.SELF, 0L), null)
    s.role(learner, null).map(_.targetRoles) shouldBe Some(Set.empty)
  }
}
