package org.sunbird.observability.transform

import com.typesafe.config.{Config, ConfigFactory}
import org.sunbird.logging.LoggerUtil

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Config-driven registry that maps request field names to transform utilities.
 *
 * Config structure (field-mapping is utilKey → list of trigger field names):
 *
 *   observability.transform {
 *     field-mapping {
 *       user       = ["userid", "userIdentifier"]
 *       collection = ["courseid", "courseIdentifier"]
 *     }
 *     user {
 *       fields         = ["firstname", "lastname", "email"]
 *       result-key     = "userDetails"
 *       cache-ttl      = 300
 *       cache-max-size = 1000
 *     }
 *     collection {
 *       fields         = ["name", "identifier", "contentType", "description"]
 *       result-key     = "collectionDetails"
 *       cache-ttl      = 3600
 *       cache-max-size = 500
 *     }
 *   }
 *
 * Adding a new alias (e.g. "userIdentifier" → user): add one string to the array — no code change.
 * Adding a new utility: add it to utilInstances and add its config block.
 *
 * Built once at startup; immutable thereafter.
 */
object TransformRegistry {

  private val logger = new LoggerUtil(TransformRegistry.getClass)

  /**
   * @param utilKey      Logical key ("user", "collection"). Used as the cache namespace.
   * @param util         The utility that fetches details from the source.
   * @param fields       Allowlisted fields passed to fetchDetails (projection).
   * @param resultKey    Key inserted into each enriched row (e.g. "userDetails").
   *                     Config is the single source of truth — not the util class.
   * @param cacheTtl     In-memory expireAfterWrite TTL in seconds.
   * @param cacheMaxSize Max entries before LRU eviction; bounds heap growth.
   */
  case class TransformEntry(
      utilKey:      String,
      util:         TransformUtil,
      fields:       List[String],
      resultKey:    String,
      cacheTtl:     Int,
      cacheMaxSize: Int
  )

  // ---- Register new utilities here (one line per util) ----------------------
  private val utilInstances: Map[String, TransformUtil] = Map(
    "user"       -> new UserTransformUtil(),
    "collection" -> new CollectionTransformUtil(),
    "content"    -> new CollectionTransformUtil()   // same fetch logic, separate result-key
  )
  // ---------------------------------------------------------------------------

  // Built once at class-loading time; safe to read from many threads.
  private val fieldToEntry: Map[String, TransformEntry] = buildRegistry()

  // Reverse index: utilKey → all trigger field names registered for it.
  // Used by applyTransforms to try every alias when looking up a value in a row.
  private val utilToFields: Map[String, List[String]] =
    fieldToEntry.groupBy(_._2.utilKey).map { case (k, entries) => k -> entries.keys.toList }

  /** Returns the TransformEntry for a given request field name, if configured. */
  def lookup(fieldName: String): Option[TransformEntry] = fieldToEntry.get(fieldName)

  /**
   * Returns all field names registered for the same util as the given trigger field.
   * E.g. aliasesFor("userid") might return ["userid", "user_id", "userIdentifier"].
   * Used to try every synonym when looking up an ID in a result row.
   */
  def aliasesFor(utilKey: String): List[String] = utilToFields.getOrElse(utilKey, List.empty)

  // ---------------------------------------------------------------------------

  private[transform] def buildRegistry(cfg: Config = ConfigFactory.load()): Map[String, TransformEntry] = {
    Try {
      val transformCfg    = cfg.getConfig("observability.transform")
      val fieldMappingCfg = transformCfg.getConfig("field-mapping")

      // Each key in field-mapping is a utilKey; its value is the list of trigger field names.
      fieldMappingCfg.root().keySet().asScala.flatMap { utilKey =>
        val triggerFields = fieldMappingCfg.getStringList(utilKey).asScala.toList

        utilInstances.get(utilKey) match {
          case None =>
            logger.info(
              s"TransformRegistry: no util registered for key '$utilKey', " +
              s"skipping trigger fields: ${triggerFields.mkString(", ")}"
            )
            Nil

          case Some(util) =>
            val fields = Try(
              transformCfg.getStringList(s"$utilKey.fields").asScala.toList
            ).getOrElse {
              logger.info(s"TransformRegistry: no fields configured for '$utilKey', defaulting to empty")
              List.empty
            }

            val resultKey = Try(
              transformCfg.getString(s"$utilKey.result-key")
            ).getOrElse(s"${utilKey}Details")   // sensible fallback

            val cacheTtl = Try(
              transformCfg.getInt(s"$utilKey.cache-ttl")
            ).getOrElse(300)                    // default 5 minutes

            val cacheMaxSize = Try(
              transformCfg.getInt(s"$utilKey.cache-max-size")
            ).getOrElse(1000)                   // default 1000 entries

            logger.info(
              s"TransformRegistry: registered '$utilKey' for fields ${triggerFields.mkString(", ")} " +
              s"(resultKey=$resultKey, cacheTtl=${cacheTtl}s, maxSize=$cacheMaxSize)"
            )

            // Each trigger field maps to its own entry (shares the same util instance)
            triggerFields.map { fieldName =>
              fieldName -> TransformEntry(utilKey, util, fields, resultKey, cacheTtl, cacheMaxSize)
            }
        }
      }.toMap
    }.getOrElse {
      logger.info("TransformRegistry: observability.transform config not found — transforms disabled")
      Map.empty
    }
  }
}
