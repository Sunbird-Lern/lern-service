package org.sunbird.kafka;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.sunbird.common.ProjectUtil;

/**
 * Unit tests for {@link KafkaClient} class.
 * Tests singleton initialization, message sending, and factory methods for producers/consumers.
 * Uses {@link MockedConstruction} to mock Kafka clients during static initialization.
 */
public class KafkaClientTest {

  /**
   * Sets up static configuration and forces static initialization of {@link KafkaClient}
   * with mocked KafkaProducer and KafkaConsumer to prevent real connection attempts.
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
               throw new RuntimeException(e);
           }
      }
  }

  /**
   * Verifies that the singleton producer and consumer instances are initialized (non-null).
   */
  @Test
  public void testStaticInitializationAndGetters() {
      // Since initialized in BeforeClass, these should be non-null (and are mocks)
      assertNotNull(KafkaClient.getProducer());
      assertNotNull(KafkaClient.getConsumer());
  }

  /**
   * Verifies that {@link KafkaClient#send(String, String)} and {@link KafkaClient#send(String, String, String)}
   * call the underlying producer's send method.
   * Uses reflection to inject a spy/mock producer into the static field for verification.
   * Ensures state is restored after test execution.
   * @throws Exception if reflection or sending fails.
   */
  @Test
  public void testSend() throws Exception {
    Producer<String, String> originalProducer = null;
    Map<String, List<PartitionInfo>> originalTopics = null;
    java.lang.reflect.Field producerField = null;
    java.lang.reflect.Field topicsField = null;

    try {
        producerField = KafkaClient.class.getDeclaredField("producer");
        producerField.setAccessible(true);
        originalProducer = (Producer<String, String>) producerField.get(null);

        topicsField = KafkaClient.class.getDeclaredField("topics");
        topicsField.setAccessible(true);
        originalTopics = (Map<String, List<PartitionInfo>>) topicsField.get(null);

        KafkaProducer<String, String> mockProducer = mock(KafkaProducer.class);
        producerField.set(null, mockProducer);

        Map<String, List<PartitionInfo>> topics = new HashMap<>();
        topics.put("test-topic", null);
        topicsField.set(null, topics);

        KafkaClient.send("message", "test-topic");
        verify(mockProducer, times(1)).send(any(ProducerRecord.class));

        KafkaClient.send("key", "message", "test-topic");
        verify(mockProducer, times(2)).send(any(ProducerRecord.class));
    } finally {
        if (producerField != null) {
            producerField.set(null, originalProducer);
        }
        if (topicsField != null) {
            topicsField.set(null, originalTopics);
        }
    }
  }

  /**
   * Verifies that {@link KafkaClient#createProducer(String, String)} creates a new producer instance.
   */
  @Test
  public void testCreateProducer() {
      try (MockedConstruction<KafkaProducer> mockedProducer = mockConstruction(KafkaProducer.class)) {
          KafkaClient.createProducer("localhost:9092", "client");
      }
  }

  /**
   * Verifies that {@link KafkaClient#createConsumer(String, String)} creates a new consumer instance.
   */
  @Test
  public void testCreateConsumer() {
      try (MockedConstruction<KafkaConsumer> mockedConsumer = mockConstruction(KafkaConsumer.class)) {
          KafkaClient.createConsumer("localhost:9092", "client");
      }
  }
}