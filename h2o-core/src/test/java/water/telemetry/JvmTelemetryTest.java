package water.telemetry;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests for the JVM telemetry emitter. These exercise the wire-contract
 * derivations (bucket label strings, topology, OS/arch normalization, JSON
 * escaping) plus one end-to-end delivery smoke test over real HTTP.
 *
 * Note: {@link JvmTelemetry#scheduleInitEmit()} is intentionally a no-op under
 * JUnit (the emitter disables itself when org.junit.Test is on the classpath),
 * so the full emit path can't be driven from here — these tests target the
 * pure logic and the transport directly.
 */
public class JvmTelemetryTest {

  // -- bucket label strings (must stay byte-identical to the Python/R clients) --

  @Test
  public void testBucketNodes() {
    assertEquals("1", JvmTelemetry.bucketNodes(1));
    assertEquals("16", JvmTelemetry.bucketNodes(16));   // exact up to 16
    assertEquals("17-20", JvmTelemetry.bucketNodes(17));
    assertEquals("49-64", JvmTelemetry.bucketNodes(64));
    assertEquals("65-128", JvmTelemetry.bucketNodes(100));
    assertEquals(">256", JvmTelemetry.bucketNodes(300));
  }

  @Test
  public void testBucketMemGb() {
    assertEquals("<4", JvmTelemetry.bucketMemGb(3.9));
    assertEquals("4-8", JvmTelemetry.bucketMemGb(4));
    assertEquals("16-32", JvmTelemetry.bucketMemGb(16));
    assertEquals("512-1024", JvmTelemetry.bucketMemGb(1000));
    assertEquals(">4096", JvmTelemetry.bucketMemGb(5000));
  }

  @Test
  public void testTopology() {
    assertEquals("single_node", JvmTelemetry.topology(1, null));
    // A non-empty hadoop version short-circuits before any env-var heuristic.
    assertEquals("multi_node_hadoop", JvmTelemetry.topology(4, "3.2.1"));
  }

  @Test
  public void testNormalizeOs() {
    assertEquals("macos", JvmTelemetry.normalizeOs("Mac OS X"));
    assertEquals("windows", JvmTelemetry.normalizeOs("Windows 10"));
    assertEquals("linux", JvmTelemetry.normalizeOs("Linux"));
    assertNull(JvmTelemetry.normalizeOs("SunOS"));   // unsupported -> don't emit
    assertNull(JvmTelemetry.normalizeOs(null));
  }

  @Test
  public void testCpuArch() {
    String old = System.getProperty("os.arch");
    try {
      System.setProperty("os.arch", "amd64");
      assertEquals("x86_64", JvmTelemetry.cpuArch("linux"));
      System.setProperty("os.arch", "aarch64");
      // Same ISA, OS-native split: macOS reports arm64, Linux stays aarch64.
      assertEquals("arm64", JvmTelemetry.cpuArch("macos"));
      assertEquals("aarch64", JvmTelemetry.cpuArch("linux"));
    } finally {
      if (old != null) System.setProperty("os.arch", old);
    }
  }

  @Test
  public void testJsonStrEscaping() {
    assertEquals("null", JvmTelemetry.jsonStr(null));
    assertEquals("\"plain\"", JvmTelemetry.jsonStr("plain"));
    assertEquals("\"a\\\"b\\\\c\"", JvmTelemetry.jsonStr("a\"b\\c"));
    assertEquals("\"line\\nbreak\"", JvmTelemetry.jsonStr("line\nbreak"));
  }

  // -- transport: prove a payload actually gets POSTed over HTTP ----------------

  @Test
  public void testHttpDeliverySmoke() throws Exception {
    final AtomicReference<String> received = new AtomicReference<>();
    final AtomicReference<String> method = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/event", exchange -> {
      try {
        method.set(exchange.getRequestMethod());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        InputStream in = exchange.getRequestBody();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        received.set(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        exchange.sendResponseHeaders(200, -1);
      } finally {
        exchange.close();
        latch.countDown();
      }
    });
    server.start();
    try {
      int port = server.getAddress().getPort();
      String url = "http://127.0.0.1:" + port + "/v1/event";
      String body = "{\"event\":\"cluster_started\",\"client\":\"jvm\"}";

      JvmTelemetry.post(body, url);

      assertTrue("server did not receive the event", latch.await(5, TimeUnit.SECONDS));
      assertEquals("POST", method.get());
      assertEquals(body, received.get());
    } finally {
      server.stop(0);
    }
  }
}
