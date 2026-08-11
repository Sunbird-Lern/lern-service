package org.sunbird.viewer.engine

import org.apache.pekko.actor.{ActorContext, ActorRef}
import org.sunbird.common.ProjectUtil
import org.sunbird.http.HttpClientUtil
import org.sunbird.keys.JsonKey
import org.sunbird.logging.LoggerUtil
import org.sunbird.request.{Request, RequestContext}

import java.util

/** System enrol via the standard `enrol` op; transport picked by deployment_mode (monolith tell vs HTTP POST).
 *  requestId="system" is stored as addedBy; re-enrol safety is the caller's. */
trait EnrolDispatcher {
  def enrol(userId: String, courseId: String, batchId: String, ctx: RequestContext): Unit
}

object EnrolDispatcher {
  private def isMonolith: Boolean = !"distributed".equalsIgnoreCase(ProjectUtil.getConfigValue("deployment_mode")) // default monolith
  def apply(context: ActorContext): EnrolDispatcher =
    if (isMonolith) new MonolithEnrolDispatcher(context) else new HttpEnrolDispatcher()
}

/** In-JVM: tell the enrolment actor. noSender so its success reply goes to deadLetters, not the caller. */
class MonolithEnrolDispatcher(context: ActorContext) extends EnrolDispatcher {
  private val logger = new LoggerUtil(classOf[MonolithEnrolDispatcher])
  override def enrol(userId: String, courseId: String, batchId: String, ctx: RequestContext): Unit = {
    val req = new Request()
    req.setRequestContext(ctx)
    req.setRequestId("system")
    req.setOperation("enrol")
    req.put(JsonKey.USER_ID, userId); req.put(JsonKey.COURSE_ID, courseId); req.put(JsonKey.BATCH_ID, batchId)
    val path = Option(ProjectUtil.getConfigValue("enrolment_actor_path")).filter(_.nonEmpty).getOrElse("/user/course-enrolment-actor")
    context.actorSelection(path).tell(req, ActorRef.noSender)
    logger.info(ctx, s"viewer.lp: enrol dispatched | user=$userId course=$courseId batch=$batchId mode=monolith")
  }
}

/** Distributed: POST the full enrol op to the enrolment service, authenticated with the system token. */
class HttpEnrolDispatcher extends EnrolDispatcher {
  private val logger = new LoggerUtil(classOf[HttpEnrolDispatcher])
  override def enrol(userId: String, courseId: String, batchId: String, ctx: RequestContext): Unit = {
    val base = Option(ProjectUtil.getConfigValue("enrolment_service_base_url")).filter(_.nonEmpty).getOrElse("http://lern-service:9000")
    val body = s"""{"request":{"userId":"$userId","courseId":"$courseId","batchId":"$batchId"}}"""
    val headers = new util.HashMap[String, String]() {{
      put("Content-Type", "application/json")
      Option(ProjectUtil.getConfigValue("viewer_system_auth_token")).filter(_.nonEmpty)
        .foreach(t => put("x-authenticated-user-token", t))
    }}
    HttpClientUtil.post(base + "/v1/course/enroll", body, headers, ctx)
    logger.info(ctx, s"viewer.lp: enrol dispatched | user=$userId course=$courseId batch=$batchId mode=distributed")
  }
}
