package org.sunbird.enrolments

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util

/**
 * `updateProgressData` must not overwrite a stored progress figure.
 *
 * `progress` and `leafNodesCount` are not always in the same unit: for a Learning Path root
 * LpProgressionEngine writes `progress` as a count of COURSES, while `leafNodesCount` counts
 * LEAVES. Recomputing unconditionally divided one by the other - a learner 6/7 courses through a
 * 12-leaf path was reported as 50% in the profile while the path itself said 85%, and a finished
 * path (progress 7, stored 100/status 2) came back as 58% with status 1, so a completed Learning
 * Path never showed as complete.
 */
class EnrolmentProgressDataTest extends AnyFlatSpec with Matchers {


  private def row(kv: (String, AnyRef)*): util.Map[String, AnyRef] = {
    val m = new util.HashMap[String, AnyRef]()
    kv.foreach { case (k, v) => m.put(k, v) }
    m
  }

  /** Mirrors what updateProgressData does per row. */
  private def update(r: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    val leaves = r.getOrDefault("leafNodesCount", Integer.valueOf(0)).asInstanceOf[Integer].intValue()
    val progress = r.getOrDefault("progress", Integer.valueOf(0)).asInstanceOf[Integer].intValue()
    CourseEnrolmentActor.fillProgressData(r, progress, leaves)
    r
  }

  "updateProgressData" should "preserve a stored LP percentage rather than dividing courses by leaves" in {
    val r = update(row(
      "progress" -> Integer.valueOf(6),          // 6 of 7 COURSES
      "leafNodesCount" -> Integer.valueOf(12),   // 12 LEAVES
      "completionPercentage" -> Integer.valueOf(85),
      "status" -> Integer.valueOf(1)))
    r.get("completionPercentage") shouldBe Integer.valueOf(85) // not 6*100/12 = 50
    r.get("status") shouldBe Integer.valueOf(1)
  }

  it should "keep a completed Learning Path complete" in {
    val r = update(row(
      "progress" -> Integer.valueOf(7),
      "leafNodesCount" -> Integer.valueOf(12),
      "completionPercentage" -> Integer.valueOf(100),
      "status" -> Integer.valueOf(2)))
    // previously: 7*100/12 = 58 and getCompletionStatus(7,12) = 1 -> "Ongoing"
    r.get("completionPercentage") shouldBe Integer.valueOf(100)
    r.get("status") shouldBe Integer.valueOf(2)
  }

  it should "derive both figures when the row carries neither" in {
    val r = update(row("progress" -> Integer.valueOf(3), "leafNodesCount" -> Integer.valueOf(12)))
    r.get("completionPercentage") shouldBe Integer.valueOf(25)
    r.get("status") shouldBe Integer.valueOf(1)
  }

  it should "derive only the missing one" in {
    val r = update(row(
      "progress" -> Integer.valueOf(6),
      "leafNodesCount" -> Integer.valueOf(12),
      "completionPercentage" -> Integer.valueOf(85)))
    r.get("completionPercentage") shouldBe Integer.valueOf(85) // kept
    r.get("status") shouldBe Integer.valueOf(1)                // derived
  }

  it should "recognise a stored value under the raw lower-case column name" in {
    val r = update(row(
      "progress" -> Integer.valueOf(6),
      "leafNodesCount" -> Integer.valueOf(12),
      "completionpercentage" -> Integer.valueOf(85)))
    // the camel-case key must NOT be filled with the recomputed 50
    Option(r.get("completionPercentage")).foreach(v => v shouldBe Integer.valueOf(85))
    r.get("completionpercentage") shouldBe Integer.valueOf(85)
  }

  it should "still complete an ordinary course whose progress is leaf-based" in {
    val r = update(row("progress" -> Integer.valueOf(4), "leafNodesCount" -> Integer.valueOf(4)))
    r.get("completionPercentage") shouldBe Integer.valueOf(100)
    r.get("status") shouldBe Integer.valueOf(2)
  }

  "hasNumber" should "ignore non-numeric and absent values" in {
    CourseEnrolmentActor.hasNumber(row("status" -> "2"), "status") shouldBe false
    CourseEnrolmentActor.hasNumber(row(), "status") shouldBe false
    CourseEnrolmentActor.hasNumber(row("status" -> Integer.valueOf(0)), "status") shouldBe true
  }
}
