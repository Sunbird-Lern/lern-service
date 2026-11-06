package org.sunbird.enrolments

import org.apache.commons.collections4.CollectionUtils
import org.sunbird.actor.base.BaseActor
import org.sunbird.common.ElasticSearchHelper
import org.sunbird.common.factory.EsClientFactory
import org.sunbird.common.inf.ElasticSearchService
import org.sunbird.keys.JsonKey
import org.sunbird.common.ProjectUtil
import org.sunbird.request.RequestContext
import org.sunbird.dto.SearchDTO

import java.util

abstract class BaseEnrolmentActor extends BaseActor {

    var esService = EsClientFactory.getInstance()
    
    def getBatches(requestContext: RequestContext, batchIds: java.util.List[String], requestedFields: java.util.List[String]): java.util.List[java.util.Map[String, AnyRef]] = {
        val ttlMs = BaseEnrolmentActor.batchCacheTtlMs
        if (ttlMs <= 0 || CollectionUtils.isNotEmpty(requestedFields) || CollectionUtils.isEmpty(batchIds))
            return searchBatches(requestContext, batchIds, requestedFields)
        // course_batch_cache_ttl_seconds > 0: serve full batch documents from a per-JVM cache (OpenSearch is
        // itself an async copy of the DB, so a short TTL adds little staleness); only misses go to OpenSearch
        val now = System.currentTimeMillis()
        val result = new java.util.ArrayList[java.util.Map[String, AnyRef]]()
        val missing = new java.util.ArrayList[String]()
        batchIds.forEach(id => {
            val cached = BaseEnrolmentActor.batchCache.get(id)
            if (cached != null && cached._1 > now) result.add(new java.util.HashMap[String, AnyRef](cached._2)) else missing.add(id)
        })
        if (!missing.isEmpty) {
            // simple bound: entries are only replaced, never evicted, so reset if it ever grows past the cap
            if (BaseEnrolmentActor.batchCache.size() > BaseEnrolmentActor.BatchCacheMaxEntries) BaseEnrolmentActor.batchCache.clear()
            searchBatches(requestContext, missing, requestedFields).forEach(batch => {
                val id = batch.get(JsonKey.BATCH_ID)
                if (id != null) BaseEnrolmentActor.batchCache.put(id.toString, (now + ttlMs, new java.util.HashMap[String, AnyRef](batch)))
                result.add(batch)
            })
        }
        result
    }

    private def searchBatches(requestContext: RequestContext, batchIds: java.util.List[String], requestedFields: java.util.List[String]): java.util.List[java.util.Map[String, AnyRef]] = {
        val dto = new SearchDTO
        dto.setLimit(batchIds.size())
        dto.getAdditionalProperties().put(JsonKey.FILTERS, new java.util.HashMap[String, AnyRef](){{ put(JsonKey.BATCH_ID, batchIds)}})
        if(CollectionUtils.isNotEmpty(requestedFields))
            dto.setFields(requestedFields)
        val future = esService.search(dto, ProjectUtil.EsType.courseBatch.getTypeName, requestContext)
        val response = ElasticSearchHelper.getResponseFromFuture(future).asInstanceOf[java.util.Map[String, AnyRef]]
        response.getOrDefault(JsonKey.CONTENT, new java.util.ArrayList[util.Map[String, AnyRef]]).asInstanceOf[util.List[util.Map[String, AnyRef]]]
    }
    
    def setEsService(es: ElasticSearchService) = {
        esService = es
        this
    }

}

object BaseEnrolmentActor {
    private[enrolments] lazy val batchCacheTtlMs: Long =
        Option(ProjectUtil.getConfigValue("course_batch_cache_ttl_seconds"))
          .map(_.trim).filter(v => v.nonEmpty && v.forall(_.isDigit)).map(_.toLong * 1000).getOrElse(0L)
    private[enrolments] val BatchCacheMaxEntries = 50000
    private[enrolments] val batchCache =new java.util.concurrent.ConcurrentHashMap[String, (Long, java.util.Map[String, AnyRef])]()
}
