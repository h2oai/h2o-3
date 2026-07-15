package water.telemetry;

import water.H2O;
import water.H2ONode;
import water.HeartBeat;
import water.Paxos;
import water.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Fire-and-forget JVM-side telemetry.
 *
 * Emits a single {@code cluster_started} event (with {@code client="jvm"}) once
 * the cluster has formed (Paxos common knowledge) and its node count has settled.
 * This is the server-side counterpart to the clients' {@code init} event. It makes
 * standalone {@code java -jar h2o.jar} and {@code hadoop jar h2odriver.jar}
 * clusters — which no Python/R client ever attaches to — visible to telemetry.
 *
 * Design constraints:
 * <ul>
 *   <li><b>Leader-only:</b> only {@code H2O.CLOUD.leader()} emits, so an N-node
 *       cluster produces exactly one event, not N.</li>
 *   <li><b>Fires post cloud-lock:</b> waits for {@link Paxos#_commonKnowledge}
 *       ("Cloud of size N formed"), then waits for the size to stabilize so a
 *       staggered multi-node / YARN launch reports its final node count.</li>
 *   <li><b>Never blocks or throws:</b> runs on a daemon thread, swallows every
 *       exception, hard-caps every wait. Startup and shutdown are never delayed.</li>
 *   <li><b>Java 8 compatible:</b> plain {@link HttpURLConnection}, no Java 11+ API.</li>
 *   <li><b>Opt-in by default-off:</b> telemetry stays off unless it is explicitly
 *       enabled by setting the disable flag to false — {@code -Dsys.ai.h2o.telemetry.disabled=false}.
 *       The {@code DO_NOT_TRACK} env var (reacts to 1/0/true/false) and
 *       {@code -Dsys.ai.h2o.telemetry.disabled=true} force it off. These switches flip
 *       {@link #isEnabled()} / the status row.</li>
 *   <li><b>No double-count:</b> a Python/R-spawned local server sets
 *       {@code -Dsys.ai.h2o.telemetry.clientLaunched=true}; the calling client
 *       already reports that session, so the spawned JVM skips its {@code cluster_started}.
 *       This is dedup, not an opt-out — telemetry stays {@link #isEnabled() enabled}.</li>
 * </ul>
 *
 * Wire shape mirrors the Python/R clients byte-for-byte (buckets, field names).
 */
public class JvmTelemetry {

  private static final String DEFAULT_URL = "https://telemetry.h2o.ai/v1/event";
  private static final int    PAYLOAD_VERSION = 1;
  private static final String PRODUCT = "h2o-3-oss";
  private static final int    TIMEOUT_MS = 2000;
  private static final int    MAX_VERSION_LEN = 64;

  private static volatile boolean _fired = false;

  /**
   * Spawn a daemon thread that waits for cloud formation and then emits one
   * {@code cluster_started} event from the leader. No-op when telemetry is opted out.
   * Safe to call from any startup path; only the leader actually emits.
   */
  public static void scheduleInitEmit() {
    if (disabled()) return;
    if (clientLaunched()) return;
    Thread t = new Thread(new Runnable() {
      @Override public void run() {
        try { waitAndEmit(); } catch (Throwable ignore) { /* never propagate */ }
      }
    }, "TelemetryInit");
    t.setDaemon(true);
    t.start();
  }

  private static void waitAndEmit() throws InterruptedException {
    // 1) Wait for the cloud to reach common knowledge ("Cloud of size N formed").
    long deadline = System.currentTimeMillis() + 60_000L;
    while (!Paxos._commonKnowledge && System.currentTimeMillis() < deadline) Thread.sleep(250L);
    if (!Paxos._commonKnowledge) return;

    // 2) Let membership settle (staggered multi-node / YARN containers keep joining):
    //    emit once the size is unchanged for ~3 consecutive polls, capped at 30s.
    int last = -1, stable = 0;
    long cap = System.currentTimeMillis() + 30_000L;
    while (System.currentTimeMillis() < cap) {
      int sz = H2O.CLOUD.size();
      if (sz == last) { if (++stable >= 3) break; } else { stable = 0; last = sz; }
      Thread.sleep(1000L);
    }

    // 3) Only the leader emits — exactly one event per cluster.
    H2ONode leader = H2O.CLOUD.leaderOrNull();
    if (leader == null || H2O.SELF == null || !H2O.SELF.equals(leader)) return;
    if (_fired) return;
    _fired = true;

    // os must be one of linux/macos/windows; skip emission on anything else.
    String os = normalizeOs(System.getProperty("os.name"));
    if (os == null) return;

    maybePrintNotice();
    post(buildClusterStarted(os, H2O.CLOUD.size()));
  }

  private static final String NOTICE_TEXT =
      "H2O-3 collects anonymous usage telemetry (H2O version, OS, and coarse cluster\n" +
      "buckets) to help prioritize features and platforms. It never sends your code,\n" +
      "data, file paths, or any identifiers.\n" +
      "To opt out: set DO_NOT_TRACK=1, or pass\n" +
      "-Dsys.ai.h2o.telemetry.disabled=true.\n" +
      "Docs: https://docs.h2o.ai/h2o/latest-stable/h2o-docs/telemetry.html\n" +
      "(This notice is shown only once.)";

  /** Print the disclosure notice once per environment (per-client marker under ~/.h2oai). */
  private static void maybePrintNotice() {
    try {
      String home = System.getProperty("user.home");
      if (home == null || home.isEmpty()) return;
      File marker = new File(new File(home, ".h2oai"), ".telemetry_notice_jvm");
      if (marker.exists()) return;
      // Route through the logger (not System.out) so the notice is emitted as
      // regular INFO lines. This keeps h2o.jar startup output uniformly at
      // INFO level, which the CI "INFO check" relies on (any raw stdout would
      // be flagged as an ERROR/WARNING). Log splits on newlines, so each line
      // of the notice gets its own INFO header.
      Log.info(NOTICE_TEXT);
      marker.getParentFile().mkdirs();
      FileWriter w = new FileWriter(marker);
      try { w.write("1\n"); } finally { w.close(); }
    } catch (Throwable ignore) {
      // best-effort — never block or fail startup over a notice
    }
  }

  // -- opt-out -----------------------------------------------------------------

  private static boolean disabled() {
    // A client-mode node (-client) backs some other session and is never a
    // server cluster; it must not emit cluster_started.
    if (H2O.ARGS != null && H2O.ARGS.client) return true;
    // Never emit from a test JVM. The production h2o.jar does not bundle JUnit,
    // so its presence on the classpath reliably marks a test/CI run (covers the
    // H2OStarter-based test node-starters and gradle-spawned multinode tests).
    if (runningUnderJUnit()) return true;
    if (envTruthy("DO_NOT_TRACK")) return true;   // cross-tool standard opt-out; always wins
    // Opt-in: off by default. Enabled only when the (unchanged) disable flag is
    // explicitly set to false — i.e. -Dsys.ai.h2o.telemetry.disabled=false.
    // Unset, "true", or anything else keeps telemetry off.
    String p = System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + "telemetry.disabled");
    return p == null || !p.trim().equalsIgnoreCase("false");
  }

  // Dedup, not an opt-out: a client-spawned local server skips its own cluster_started
  // because the calling client already reports the session. Kept out of disabled() so it
  // doesn't flip isEnabled() / the status row.
  private static boolean clientLaunched() {
    return Boolean.getBoolean(H2O.OptArgs.SYSTEM_PROP_PREFIX + "telemetry.clientLaunched");
  }

  private static boolean runningUnderJUnit() {
    try { Class.forName("org.junit.Test"); return true; }
    catch (Throwable t) { return false; }
  }

  /** Whether telemetry would currently emit on this JVM — the inverse of {@link #disabled()}.
   *  Surfaced on {@code CloudV3.telemetry_enabled} so clients can show the server's state. */
  public static boolean isEnabled() { return !disabled(); }

  /** True iff env var {@code name} is set to a truthy value (1/true/yes/on, case-insensitive);
   *  0/false/no/off/empty/unset all read as false. */
  private static boolean envTruthy(String name) {
    String v = System.getenv(name);
    if (v == null) return false;
    v = v.trim().toLowerCase();
    return !(v.isEmpty() || v.equals("0") || v.equals("false") || v.equals("no") || v.equals("off"));
  }

  // -- payload -----------------------------------------------------------------

  private static String buildClusterStarted(String os, int size) {
    String hadoopVer  = (H2O.ARGS != null) ? H2O.ARGS.ga_hadoop_ver : null;
    String javaVer    = cap(System.getProperty("java.version"));
    String javaVendor = cap(System.getProperty("java.vendor"));
    String osVersion  = System.getProperty("os.version");
    String h2oVer;
    try { h2oVer = H2O.ABV.projectVersion(); } catch (Throwable t) { h2oVer = ""; }
    if (h2oVer == null) h2oVer = "";
    if (osVersion == null) osVersion = "";

    StringBuilder sb = new StringBuilder(512);
    sb.append('{');
    sb.append("\"event\":\"cluster_started\",");
    sb.append("\"payload_version\":").append(PAYLOAD_VERSION).append(',');
    sb.append("\"client\":\"jvm\",");
    sb.append("\"h2o_version\":").append(jsonStr(h2oVer)).append(',');
    sb.append("\"os\":").append(jsonStr(os)).append(',');
    sb.append("\"os_version\":").append(jsonStr(osVersion)).append(',');
    sb.append("\"session_id\":").append(jsonStr(UUID.randomUUID().toString())).append(',');
    sb.append("\"ts\":").append(System.currentTimeMillis() / 1000L).append(',');
    sb.append("\"product\":").append(jsonStr(PRODUCT)).append(',');
    if (notEmpty(javaVer))    sb.append("\"java_version\":").append(jsonStr(javaVer)).append(',');
    if (notEmpty(javaVendor)) sb.append("\"java_vendor\":").append(jsonStr(javaVendor)).append(',');
    sb.append("\"cpu_arch\":").append(jsonStr(cpuArch(os))).append(',');
    sb.append("\"cluster_topology\":").append(jsonStr(topology(size, hadoopVer))).append(',');
    sb.append("\"cluster_nodes_bucket\":").append(jsonStr(bucketNodes(size))).append(',');
    sb.append("\"cluster_memory_gb_bucket\":").append(jsonStr(bucketMemGb(totalClusterMemGb())));
    sb.append('}');
    return sb.toString();
  }

  // -- derivations (byte-identical to the Python/R clients) --------------------

  static String normalizeOs(String osName) {
    if (osName == null) return null;
    String s = osName.toLowerCase();
    if (s.contains("mac") || s.contains("darwin")) return "macos";
    if (s.contains("win")) return "windows";
    if (s.contains("linux")) return "linux";
    return null;  // Solaris/AIX/BSD etc. — not an accepted value, so don't emit
  }

  static String cpuArch(String os) {
    String a = System.getProperty("os.arch");
    if (a == null) return "other";
    a = a.toLowerCase();
    if (a.equals("x86_64") || a.equals("amd64")) return "x86_64";
    // Mirror the clients' OS-native split: Apple-Silicon macOS reports "arm64",
    // Linux ARM64 stays "aarch64" (the JVM reports "aarch64" on both).
    if (a.equals("aarch64") || a.equals("arm64")) return "macos".equals(os) ? "arm64" : "aarch64";
    if (a.contains("ppc64")) return "ppc64le";
    if (a.equals("s390x")) return "s390x";
    return "other";
  }

  static String topology(int size, String hadoopVer) {
    if (size == 1) return "single_node";
    if (notEmpty(hadoopVer)) return "multi_node_hadoop";
    if (notEmpty(System.getenv("KUBERNETES_SERVICE_HOST"))) return "kubernetes";
    if (notEmpty(System.getenv("HADOOP_HOME")) ||
        notEmpty(System.getenv("HADOOP_CONF_DIR")) ||
        notEmpty(System.getenv("HADOOP_PREFIX"))) return "multi_node_hadoop";
    if (size > 1) return "multi_node_standalone";
    return "unknown";
  }

  static String bucketNodes(int n) {
    if (n <= 16)  return Integer.toString(n);
    if (n <= 20)  return "17-20";
    if (n <= 24)  return "21-24";
    if (n <= 32)  return "25-32";
    if (n <= 48)  return "33-48";
    if (n <= 64)  return "49-64";
    if (n <= 128) return "65-128";
    if (n <= 256) return "129-256";
    return ">256";
  }

  static String bucketMemGb(double gb) {
    if (gb < 4)    return "<4";
    if (gb < 8)    return "4-8";
    if (gb < 16)   return "8-16";
    if (gb < 32)   return "16-32";
    if (gb < 64)   return "32-64";
    if (gb < 128)  return "64-128";
    if (gb < 256)  return "128-256";
    if (gb < 512)  return "256-512";
    if (gb < 1024) return "512-1024";
    if (gb < 2048) return "1024-2048";
    if (gb < 4096) return "2048-4096";
    return ">4096";
  }

  /** Total cluster heap, summed per node exactly as CloudV3 computes max_mem. */
  private static double totalClusterMemGb() {
    long bytes = 0L;
    H2ONode[] members = H2O.CLOUD._memary;
    if (members != null) {
      for (H2ONode n : members) {
        HeartBeat hb = (n == null) ? null : n._heartbeat;
        if (hb != null) bytes += hb.get_pojo_mem() + hb.get_free_mem() + hb.get_kv_mem();
      }
    }
    return bytes / (1024.0 * 1024.0 * 1024.0);
  }

  // -- transport ---------------------------------------------------------------

  private static void post(String body) {
    String urlStr = notEmpty(System.getenv("H2O_TELEMETRY_URL"))
        ? System.getenv("H2O_TELEMETRY_URL") : DEFAULT_URL;
    post(body, urlStr);
  }

  static void post(String body, String urlStr) {
    HttpURLConnection con = null;
    try {
      con = (HttpURLConnection) new URL(urlStr).openConnection();
      con.setConnectTimeout(TIMEOUT_MS);
      con.setReadTimeout(TIMEOUT_MS);
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", "application/json");
      con.setDoOutput(true);
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      OutputStream os = con.getOutputStream();
      try { os.write(bytes); } finally { os.close(); }
      con.getResponseCode();  // triggers the request; result intentionally ignored
    } catch (Throwable ignore) {
      // fire-and-forget — never raise into the caller
    } finally {
      if (con != null) { try { con.disconnect(); } catch (Throwable ignore) { } }
    }
  }

  // -- small helpers -----------------------------------------------------------

  private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

  private static String cap(String s) {
    if (s == null) return null;
    return s.length() > MAX_VERSION_LEN ? s.substring(0, MAX_VERSION_LEN) : s;
  }

  static String jsonStr(String s) {
    if (s == null) return "null";
    StringBuilder b = new StringBuilder(s.length() + 2);
    b.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':  b.append("\\\""); break;
        case '\\': b.append("\\\\"); break;
        case '\n': b.append("\\n");  break;
        case '\r': b.append("\\r");  break;
        case '\t': b.append("\\t");  break;
        default:
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
      }
    }
    b.append('"');
    return b.toString();
  }
}
