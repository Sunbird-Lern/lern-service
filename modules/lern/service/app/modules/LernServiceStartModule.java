package modules;

import com.google.inject.AbstractModule;
import org.sunbird.logging.LoggerUtil;

public class LernServiceStartModule extends AbstractModule {
    private LoggerUtil logger = new LoggerUtil(LernServiceStartModule.class);

    @Override
    protected void configure() {
        logger.info("LernServiceStartModule:configure: Start");
        try {
            bind(SignalHandler.class).asEagerSingleton();
            bind(LernServiceApplicationStart.class).asEagerSingleton();
        } catch (Exception | Error e) {
            logger.error("Exception occurred while starting Lern Service module", e);
            throw e;
        }
        logger.info("LernServiceStartModule:configure: End");
    }
}
