package org.sunbird.viewer.actor

import com.google.gson.Gson
import org.sunbird.common.ProjectUtil

import scala.collection.JavaConverters._

// Carry-forward rule engine (copy mode). A filter over candidate source completions:
//   viewer_carry_forward_rules = {"match":"any|all","rules":[{"type":"completedWithin","unit":"months","value":3}, ...]}
// Empty / blank / unparseable => carry NOTHING (fail-closed). Rule types are a dispatch table, so more can be added later.
object CarryForwardRules {

  // the source completion under test
  final case class Candidate(lastCompletedTime: Option[Long])
  // the target context the completion would carry into
  final case class Target(batchStart: Option[Long], batchEnd: Option[Long], enrolledDate: Option[Long])

  private val gson = new Gson()
  private val DAY = 86400000L

  def configuredRules(): String = Option(ProjectUtil.getConfigValue("viewer_carry_forward_rules")).map(_.trim).getOrElse("")

  /** Keep the candidate? `now` is passed in (Date.now is unavailable/undesirable in pure logic). */
  def passes(candidate: Candidate, target: Target, now: Long, rulesJson: String = configuredRules()): Boolean = {
    if (rulesJson == null || rulesJson.trim.isEmpty) return false // copy enabled but no rules => carry nothing
    val parsed = try Option(gson.fromJson(rulesJson, classOf[java.util.Map[String, AnyRef]])) catch { case _: Throwable => None }
    parsed match {
      case Some(m) =>
        val matchMode = Option(m.get("match")).map(_.toString.toLowerCase).getOrElse("all")
        val rules = Option(m.get("rules")).collect { case l: java.util.List[_] => l.asScala.toList }.getOrElse(Nil)
        if (rules.isEmpty) false
        else {
          val preds = rules.collect { case r: java.util.Map[_, _] => evalRule(r.asInstanceOf[java.util.Map[String, AnyRef]], candidate, target, now) }
          if (matchMode == "any") preds.exists(identity) else preds.nonEmpty && preds.forall(identity)
        }
      case None => false
    }
  }

  private def evalRule(rule: java.util.Map[String, AnyRef], c: Candidate, t: Target, now: Long): Boolean =
    Option(rule.get("type")).map(_.toString).getOrElse("") match {
      case "always" => true
      case "completedWithin" =>
        val unit = Option(rule.get("unit")).map(_.toString.toLowerCase).getOrElse("days")
        val window = (if (unit.startsWith("month")) 30L else 1L) * numOf(rule.get("value")).toLong * DAY
        c.lastCompletedTime.exists(lt => now - lt <= window)
      case "withinContextDuration" =>
        // fail-closed when the target window is unknown (don't carry everything on a missing date)
        c.lastCompletedTime.exists(lt => (t.batchStart.isDefined || t.batchEnd.isDefined) && t.batchStart.forall(lt >= _) && t.batchEnd.forall(lt <= _))
      case _ => false // unknown rule type -> conservative: does not pass
    }

  private def numOf(v: AnyRef): Double = v match {
    case n: Number => n.doubleValue()
    case s: String => try s.toDouble catch { case _: Throwable => 0.0 }
    case _ => 0.0
  }
}
