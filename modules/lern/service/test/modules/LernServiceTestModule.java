package modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.mockito.Mockito;
import org.sunbird.helper.CassandraConnectionManager;

/**
 * Test module that provides mocked versions of critical dependencies.
 * Used to allow tests to run without Cassandra.
 */
public class LernServiceTestModule extends AbstractModule {

    /**
     * Provides a mocked Cassandra connection manager that doesn't actually connect.
     */
    @Provides
    @Singleton
    CassandraConnectionManager provideMockedCassandra() {
        CassandraConnectionManager mockCassandra = Mockito.mock(CassandraConnectionManager.class);
        // Mock the createConnection method to do nothing
        Mockito.doNothing().when(mockCassandra).createConnection(Mockito.any(String[].class));
        return mockCassandra;
    }
}
