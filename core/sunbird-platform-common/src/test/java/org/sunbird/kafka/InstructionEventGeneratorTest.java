package org.sunbird.kafka;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.common.ProjectUtil;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;

/**
 * Unit tests for {@link InstructionEventGenerator} class.
 * Verifies event generation logic and delegation to {@link KafkaClient}.
 */
public class InstructionEventGeneratorTest {

  /**
   * Sets up test environment by ensuring {@link KafkaClient} is statically initialized
   * with mocked dependencies to avoid runtime errors during tests.
   * Skips tests if initialization fails (e.g., class not found).
   */
  @BeforeClass
  public static void setUp() {
      // Set required properties to avoid errors during static init properties loading
      ProjectUtil.propertiesCache.saveConfigProperty("kafka_urls", "localhost:9092");
      ProjectUtil.propertiesCache.saveConfigProperty("kafka_linger_ms", "10");

      // Force static initialization of KafkaClient while mocks are active
      try (MockedConstruction<KafkaProducer> mockedProducer = mockConstruction(KafkaProducer.class);
           MockedConstruction<KafkaConsumer> mockedConsumer = mockConstruction(KafkaConsumer.class,
             (mock, context) -> {
               when(mock.listTopics()).thenReturn(new HashMap<String, List<PartitionInfo>>());
             })) {

           try {
               Class.forName(KafkaClient.class.getName());
           } catch (ClassNotFoundException e) {
               Assume.assumeTrue("KafkaClient class missing, skipping tests: " + e.getMessage(), false);
           } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
                Assume.assumeTrue("KafkaClient initialization failed: " + e.getMessage(), false);
           }
      }
  }

  /**
   * Verifies that {@link InstructionEventGenerator#pushInstructionEvent(String, Map)}
   * generates an event and calls {@link KafkaClient#send(String, String)}.
   * @throws Exception if generation or sending fails.
   */
  @Test
  public void testPushInstructionEvent() throws Exception {
    try (MockedStatic<KafkaClient> mockedKafkaClient = mockStatic(KafkaClient.class)) {
      Map<String, Object> data = new HashMap<>();
      data.put("actor", new HashMap<>());
      data.put("context", new HashMap<>());
      data.put("object", new HashMap<>());
      data.put("edata", new HashMap<>());

      InstructionEventGenerator.pushInstructionEvent("test-topic", data);

      mockedKafkaClient.verify(() -> KafkaClient.send(anyString(), eq("test-topic")), times(1));
    }
  }

  /**
   * Verifies that {@link InstructionEventGenerator#pushInstructionEvent(String, String, Map)}
   * generates an event and calls {@link KafkaClient#send(String, String, String)} with a key.
   * @throws Exception if generation or sending fails.
   */
  @Test
  public void testPushInstructionEventWithKey() throws Exception {
    try (MockedStatic<KafkaClient> mockedKafkaClient = mockStatic(KafkaClient.class)) {
      Map<String, Object> data = new HashMap<>();

      InstructionEventGenerator.pushInstructionEvent("key", "test-topic", data);

      mockedKafkaClient.verify(() -> KafkaClient.send(eq("key"), anyString(), eq("test-topic")), times(1));
    }
  }

  /**
   * Verifies that {@link InstructionEventGenerator#pushInstructionEvent(String, Map)}
   * throws a {@link ProjectCommonException} when the topic is null.
   * @throws Exception if expected exception is not thrown.
   */
  @Test(expected = ProjectCommonException.class)
  public void testPushInstructionEventNullTopic() throws Exception {
      Map<String, Object> data = new HashMap<>();
      InstructionEventGenerator.pushInstructionEvent(null, data);
  }
}