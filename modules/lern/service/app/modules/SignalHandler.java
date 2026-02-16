package modules;

import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.sunbird.logging.LoggerUtil;
import play.api.inject.ApplicationLifecycle;

@Singleton
public class SignalHandler {
    private static LoggerUtil logger = new LoggerUtil(SignalHandler.class);

    private volatile boolean isShuttingDown = false;

    @Inject
    public SignalHandler(ApplicationLifecycle applicationLifecycle) {
        logger.info("SignalHandler: Initializing");
        applicationLifecycle.addStopHook(
            () -> {
                isShuttingDown = true;
                logger.info("SignalHandler: Clean shutdown init");
                // Add any cleanup logic here
                return CompletableFuture.completedFuture(null);
            });
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }
}
