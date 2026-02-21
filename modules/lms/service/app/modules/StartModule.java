package modules;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sunbird.cache.util.RedisCacheUtil;

/**
 * This class is responsible for creating instance of
 * ApplicationStart at server startup time.
 *
 * @author Jaikumar Soundara Rajan
 */
public class StartModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(StartModule.class);

    @Override
    protected void configure() {
        logger.info("StartModule:configure: Start");
        try {
            bind(ApplicationStart.class).asEagerSingleton();
            bind(RedisCacheUtil.class).asEagerSingleton();
        } catch (Exception | Error e) {
            e.printStackTrace();
            throw e;
        }
        logger.info("StartModule:configure: End");

    }
}
