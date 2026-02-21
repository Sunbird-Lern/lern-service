package org.sunbird.actor.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class BaseActorTest {

    @Test
    public void testBaseActor_classIsAbstract() {
        // Verify the class is abstract (it's meant to be extended, not instantiated directly)
        assertTrue("BaseActor should be abstract",
            java.lang.reflect.Modifier.isAbstract(BaseActor.class.getModifiers()));
    }

    @Test
    public void testBaseActor_implementsOnReceive() throws NoSuchMethodException {
        // Verify the onReceive contract exists
        java.lang.reflect.Method onReceive = BaseActor.class.getDeclaredMethod(
            "onReceive", org.sunbird.request.Request.class);
        assertNotNull("BaseActor should declare onReceive(Request) method", onReceive);
    }

    @Test
    public void testPekkoWaitTime_isConfigured() {
        // Verify the PEKKO_WAIT_TIME constant is set correctly
        assertEquals("PEKKO_WAIT_TIME should be 30 seconds", 30, BaseActor.PEKKO_WAIT_TIME);
    }

    @Test
    public void testTimeout_isInitialized() {
        // Verify that the timeout field is initialized
        assertNotNull("BaseActor.timeout should be initialized", BaseActor.timeout);
    }

    @Test
    public void testBaseActor_extendsUntypedAbstractActor() {
        // Verify BaseActor properly extends the Pekko actor framework
        assertTrue("BaseActor should extend UntypedAbstractActor",
            org.apache.pekko.actor.UntypedAbstractActor.class.isAssignableFrom(BaseActor.class));
    }
}
