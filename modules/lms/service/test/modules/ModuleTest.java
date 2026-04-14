package modules;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.auth.verifier.KeyManager;
import org.sunbird.learner.util.SchedulerManager;
import org.sunbird.learner.util.Util;
import play.Application;
import play.Mode;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.Helpers;

import java.io.File;

@RunWith(PowerMockRunner.class)
// @SuppressStaticInitializationFor("org.sunbird.learner.util.Util")
@PrepareForTest({Util.class, SchedulerManager.class, KeyManager.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*", "jdk.internal.reflect.*",
        "sun.security.ssl.*", "javax.net.ssl.*", "javax.crypto.*",
        "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*"})
public class ModuleTest {
  @Before
  public void setup() throws Exception {
    PowerMockito.mockStatic(Util.class);
    PowerMockito.mockStatic(SchedulerManager.class);
    PowerMockito.mockStatic(KeyManager.class);
    PowerMockito.doNothing().when(Util.class, "checkCassandraDbConnections");
    PowerMockito.doNothing().when(SchedulerManager.class);
    SchedulerManager.schedule();
    PowerMockito.doNothing().when(KeyManager.class, "init");
  }

  @Test
  public void startApplicationTest() {
    Application application =
        new GuiceApplicationBuilder().in(new File("path/to/app")).in(Mode.TEST).build();
    Helpers.start(application);
  }
}
