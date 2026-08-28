package org.sunbird.learner.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.cassandra.CassandraOperation;
import org.sunbird.common.ProjectUtil;
import org.sunbird.helper.ServiceFactory;
import org.sunbird.keys.JsonKey;
import org.sunbird.request.RequestContext;

/**
 * Shared "is this a Learning Path?" helper. A collection with nested trackable courses is a Learning
 * Path; a plain course has none. Reads the published {@code rootId:rootId:trackablenodes} relation
 * from hierarchy_relations. Used by both child-batch fan-out (course-actors) and the enrol->viewer
 * bootstrap gate (enrolment-actor) so the predicate lives in one place.
 */
public final class HierarchyTrackableUtil {

  private static final CassandraOperation cassandraOperation = ServiceFactory.getInstance();

  private HierarchyTrackableUtil() {}

  @SuppressWarnings("unchecked")
  public static List<String> getTrackableNodes(String rootId, RequestContext ctx) {
    String keyspace = Optional.ofNullable(ProjectUtil.getConfigValue("hierarchy_store_keyspace"))
        .filter(StringUtils::isNotBlank).orElse("dev_hierarchy_store");
    String table = Optional.ofNullable(ProjectUtil.getConfigValue("hierarchy_relations_table"))
        .filter(StringUtils::isNotBlank).orElse("hierarchy_relations");
    Map<String, Object> filters = new HashMap<>();
    filters.put("relationship_key", rootId + ":" + rootId + ":trackablenodes");
    List<Map<String, Object>> rows = (List<Map<String, Object>>) cassandraOperation
        .getRecordsByProperties(keyspace, table, filters, ctx)
        .getResult().getOrDefault(JsonKey.RESPONSE, new ArrayList<>());
    if (rows.isEmpty()) return Collections.emptyList();
    List<String> nodeIds = (List<String>) rows.get(0).get("node_ids");
    return nodeIds == null ? Collections.emptyList() : nodeIds;
  }

  /**
   * True when the collection has nested trackable courses (i.e. it is a Learning Path); a plain
   * course has none. Self is excluded, since the stored trackablenodes list can include the root.
   */
  public static boolean hasNestedTrackables(String rootId, RequestContext ctx) {
    return getTrackableNodes(rootId, ctx).stream().anyMatch(n -> !StringUtils.equalsIgnoreCase(n, rootId));
  }
}
