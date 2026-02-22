package modules;

import org.apache.pekko.routing.FromConfig;
import org.apache.pekko.routing.RouterConfig;
import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.pekko.PekkoGuiceSupport;
import util.ACTOR_NAMES;

public class ActorStartModule extends AbstractModule implements PekkoGuiceSupport {
  private static final Logger logger = LoggerFactory.getLogger(ActorStartModule.class);

  @Override
  protected void configure() {
    logger.info("binding actors for dependency injection");
    final RouterConfig config = new FromConfig();
    for (ACTOR_NAMES actor : ACTOR_NAMES.values()) {
      bindActor(
          actor.getActorClass(),
          actor.getActorName(),
          (props) -> {
            return props.withRouter(config);
          });
    }
    logger.info("binding completed");
  }
}
