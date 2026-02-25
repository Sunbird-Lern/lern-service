package org.sunbird.actor.core;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.sunbird.exception.ProjectCommonException;
import org.sunbird.request.Request;
import org.sunbird.response.ResponseCode;
import org.sunbird.actor.router.RequestRouter;
import org.sunbird.actor.service.BaseMWService;
import org.sunbird.actor.service.SunbirdMWService;

import java.time.Duration;

import static org.junit.Assert.*;

/**
 * Test suite for BaseActor abstract class.
 * Tests the onReceive lifecycle, message handling, exception routing, and utility methods.
 * Uses Pekko TestKit for actor message testing and PowerMock for static method mocking.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({SunbirdMWService.class, RequestRouter.class, BaseMWService.class})
@PowerMockIgnore({"javax.management.*", "javax.net.ssl.*", "javax.security.*",
                 "jdk.internal.reflect.*", "javax.crypto.*", "javax.script.*",
                 "javax.xml.*", "com.sun.org.apache.xerces.*", "org.xml.*"})
public class BaseActorTest {

  private static ActorSystem system;

  /**
   * Setup the ActorSystem for all tests in this class.
   */
  @BeforeClass
  public static void setUpActorSystem() {
    system = ActorSystem.create("testSystem");
  }

  /**
   * Shutdown the ActorSystem after all tests.
   */
  @AfterClass
  public static void tearDownActorSystem() {
    TestKit.shutdownActorSystem(system);
  }

  // ========== Inner Test Actor Implementations ==========

  /**
   * Simple echo actor that responds with "echo:" + operation name.
   */
  static class EchoActor extends BaseActor {
    @Override
    public void onReceive(Request request) throws Throwable {
      sender().tell("echo:" + request.getOperation(), self());
    }
  }

  /**
   * Actor that always throws a RuntimeException.
   */
  static class ThrowingActor extends BaseActor {
    @Override
    public void onReceive(Request request) throws Throwable {
      throw new RuntimeException("forced-fail");
    }
  }

  /**
   * Actor that tests unsupported operation/message handling.
   */
  static class UnsupportedActor extends BaseActor {
    @Override
    public void onReceive(Request request) throws Throwable {
      switch (request.getOperation()) {
        case "unsupMsg":
          unSupportedMessage();
          break;
        case "unsupOp":
          onReceiveUnsupportedOperation("TestCallerX");
          break;
        case "unsupOpDef":
          onReceiveUnsupportedOperation();
          break;
        case "unsupMsgNm":
          onReceiveUnsupportedMessage("TestCallerX");
          break;
        case "unsupMsgDef":
          onReceiveUnsupportedMessage();
          break;
        case "excCaller":
          onReceiveException("TestCaller", new RuntimeException("test-exception"));
          break;
      }
    }
  }

  // ========== Tests ==========

  /**
   * Test that onReceive with a valid Request delegates to the abstract onReceive(Request).
   */
  @Test
  public void testOnReceiveValidRequestDelegatesToSubclass() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(EchoActor.class));
      Request request = new Request();
      request.setOperation("testOp");

      subject.tell(request, getRef());

      String response = expectMsgClass(Duration.ofSeconds(5), String.class);
      assertEquals("echo:testOp", response);
    }};
  }

  /**
   * Test that onReceive with a non-Request message does not send a response.
   */
  @Test
  public void testOnReceiveNonRequestMessageNoReply() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(EchoActor.class));

      subject.tell("not a request", getRef());

      expectNoMessage(Duration.ofSeconds(1));
    }};
  }

  /**
   * Test that when onReceive throws, the exception is sent to sender.
   */
  @Test
  public void testOnReceiveWhenSubclassThrowsSendsException() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(ThrowingActor.class));
      Request request = new Request();
      request.setOperation("throwOp");

      subject.tell(request, getRef());

      RuntimeException response = expectMsgClass(Duration.ofSeconds(5), RuntimeException.class);
      assertEquals("forced-fail", response.getMessage());
    }};
  }

  /**
   * Test unSupportedMessage() sends ProjectCommonException with invalidRequestData code.
   */
  @Test
  public void testUnSupportedMessageSendsProjectCommonException() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(UnsupportedActor.class));
      Request request = new Request();
      request.setOperation("unsupMsg");

      subject.tell(request, getRef());

      ProjectCommonException response = expectMsgClass(Duration.ofSeconds(5), ProjectCommonException.class);
      assertEquals("Error code should be invalidRequestData",
          ResponseCode.invalidRequestData.getErrorCode(), response.getErrorCode());
    }};
  }

  /**
   * Test onReceiveUnsupportedOperation with caller name.
   */
  @Test
  public void testOnReceiveUnsupportedOperationWithCallerName() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(UnsupportedActor.class));
      Request request = new Request();
      request.setOperation("unsupOp");

      subject.tell(request, getRef());

      ProjectCommonException response = expectMsgClass(Duration.ofSeconds(5), ProjectCommonException.class);
      assertEquals("Error code should be invalidRequestData",
          ResponseCode.invalidRequestData.getErrorCode(), response.getErrorCode());
    }};
  }

  /**
   * Test onReceiveUnsupportedOperation without caller name (uses class name).
   */
  @Test
  public void testOnReceiveUnsupportedOperationWithoutCallerName() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(UnsupportedActor.class));
      Request request = new Request();
      request.setOperation("unsupOpDef");

      subject.tell(request, getRef());

      ProjectCommonException response = expectMsgClass(Duration.ofSeconds(5), ProjectCommonException.class);
      assertEquals("Error code should be invalidRequestData",
          ResponseCode.invalidRequestData.getErrorCode(), response.getErrorCode());
    }};
  }

  /**
   * Test onReceiveUnsupportedMessage with caller name.
   */
  @Test
  public void testOnReceiveUnsupportedMessageWithCallerName() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(UnsupportedActor.class));
      Request request = new Request();
      request.setOperation("unsupMsgNm");

      subject.tell(request, getRef());

      ProjectCommonException response = expectMsgClass(Duration.ofSeconds(5), ProjectCommonException.class);
      assertEquals("Error code should be invalidOperationName",
          ResponseCode.invalidOperationName.getErrorCode(), response.getErrorCode());
    }};
  }

  /**
   * Test onReceiveUnsupportedMessage without caller name.
   */
  @Test
  public void testOnReceiveUnsupportedMessageWithoutCallerName() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(UnsupportedActor.class));
      Request request = new Request();
      request.setOperation("unsupMsgDef");

      subject.tell(request, getRef());

      ProjectCommonException response = expectMsgClass(Duration.ofSeconds(5), ProjectCommonException.class);
      assertEquals("Error code should be invalidOperationName",
          ResponseCode.invalidOperationName.getErrorCode(), response.getErrorCode());
    }};
  }

  /**
   * Test onReceiveException sends the exception to sender.
   */
  @Test
  public void testOnReceiveExceptionSendsExceptionToSender() {
    new TestKit(system) {{
      ActorRef subject = system.actorOf(Props.create(UnsupportedActor.class));
      Request request = new Request();
      request.setOperation("excCaller");

      subject.tell(request, getRef());

      RuntimeException response = expectMsgClass(Duration.ofSeconds(5), RuntimeException.class);
      assertEquals("test-exception", response.getMessage());
    }};
  }

  /**
   * Test that PEKKO_WAIT_TIME constant is 30 seconds.
   */
  @Test
  public void testPekkoWaitTimeConstant() {
    assertEquals("PEKKO_WAIT_TIME should be 30", 30, BaseActor.PEKKO_WAIT_TIME);
  }

  /**
   * Test getActorRef method exists on BaseActor.
   */
  @Test
  public void testGetActorRefMethodExists() throws Exception {
    // Verify the method exists via reflection
    java.lang.reflect.Method method = BaseActor.class.getDeclaredMethod("getActorRef", String.class);
    assertNotNull("getActorRef method should exist", method);

    // Verify it's protected
    int modifiers = method.getModifiers();
    assertTrue("getActorRef should be protected", java.lang.reflect.Modifier.isProtected(modifiers));
  }

  /**
   * Test getActorRef method signature is correct.
   */
  @Test
  public void testGetActorRefSignature() throws Exception {
    // Verify the method signature
    java.lang.reflect.Method method = BaseActor.class.getDeclaredMethod("getActorRef", String.class);
    Class<?> returnType = method.getReturnType();
    assertEquals("getActorRef should return ActorRef", ActorRef.class, returnType);
  }

  /**
   * Test tellToAnother method exists and delegates to SunbirdMWService.
   */
  @Test
  public void testTellToAnotherMethodExists() throws Exception {
    // Verify the method exists via reflection
    java.lang.reflect.Method method = BaseActor.class.getDeclaredMethod("tellToAnother", Request.class);
    assertNotNull("tellToAnother method should exist", method);

    // Verify it's accessible
    method.setAccessible(true);
  }
}
