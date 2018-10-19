package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import water.H2O;
import water.H2OStarter;
import water.ProxyStarter;
import water.network.SecurityUtils;
import water.server.Credentials;
import water.util.ArrayUtils;
import water.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static water.util.JavaVersionUtils.JAVA_VERSION;

/**
 * Driver class to start a Hadoop mapreduce job which wraps an H2O cluster launch.
 *
 * All mapreduce I/O is typed as <Text, Text>.
 * The first Text is the Key (Mapper Id).
 * The second Text is the Value (a log output).
 *
 * Adapted from
 * https://svn.apache.org/repos/asf/hadoop/common/trunk/hadoop-mapreduce-project/hadoop-mapreduce-client/hadoop-mapreduce-client-jobclient/src/test/java/org/apache/hadoop/SleepJob.java
 */
@SuppressWarnings("deprecation")
public class h2odriver extends Configured implements Tool {

  final static String ARGS_CONFIG_FILE_PATTERN = "/etc/h2o/%s.args";
  final static String DEFAULT_ARGS_CONFIG = "h2odriver";
  final static String ARGS_CONFIG_PROP = "ai.h2o.args.config";
  final static String DRIVER_JOB_CALL_TIMEOUT_SEC = "ai.h2o.driver.call.timeout";

  static {
      if(!JAVA_VERSION.isKnown()) {
          System.err.println("Couldn't parse Java version: " + System.getProperty("java.version"));
          System.exit(1);
      }
  }

  final static int DEFAULT_CLOUD_FORMATION_TIMEOUT_SECONDS = 120;
  final static int CLOUD_FORMATION_SETTLE_DOWN_SECONDS = 2;
  final static int DEFAULT_EXTRA_MEM_PERCENT = 10;

  // Options that are parsed by the main thread before other threads are created.
  static String jobtrackerName = null;
  static int numNodes = -1;
  static String outputPath = null;
  static String mapperXmx = null;
  static int extraMemPercent = -1;            // Between 0 and 10, typically.  Cannot be negative.
  static String mapperPermSize = null;
  static String driverCallbackIp = null;
  static int driverCallbackPort = 0;          // By default, let the system pick the port.
  static PortRange driverCallbackPortRange = null;
  static String network = null;
  static boolean disown = false;
  static String clusterReadyFileName = null;
  static String hadoopJobId = "";
  static String applicationId = "";
  static int cloudFormationTimeoutSeconds = DEFAULT_CLOUD_FORMATION_TIMEOUT_SECONDS;
  static int nthreads = -1;
  static String contextPath = null;
  static int basePort = -1;
  static int portOffset = -1;
  static boolean beta = false;
  static boolean enableRandomUdpDrop = false;
  static boolean enableExceptions = false;
  static boolean enableVerboseGC = true;
  static boolean enablePrintGCDetails = true;
  static boolean enablePrintGCTimeStamps = true;
  static boolean enableVerboseClass = false;
  static boolean enablePrintCompilation = false;
  static boolean enableExcludeMethods = false;
  static boolean enableLog4jDefaultInitOverride = true;
  static boolean enableDebug = false;
  static boolean enableSuspend = false;
  static int debugPort = 5005;    // 5005 is the default from IDEA
  static String flowDir = null;
  static ArrayList<String> extraArguments = new ArrayList<String>();
  static ArrayList<String> extraJvmArguments = new ArrayList<String>();
  static String jksFileName = null;
  static String jksPass = null;
  static String securityConf = null;
  static boolean internal_secure_connections = false;
  static boolean hashLogin = false;
  static boolean ldapLogin = false;
  static boolean kerberosLogin = false;
  static boolean pamLogin = false;
  static String loginConfFileName = null;
  static boolean formAuth = false;
  static String sessionTimeout = null;
  static String userName = System.getProperty("user.name");
  static boolean client = false;
  static boolean proxy = false;
  static String runAsUser = null;
  static String principal = null;
  static String keytabPath = null;
  static boolean reportHostname = false;
  static boolean driverDebug = false;

  String proxyUrl = null;
  // Runtime state that might be touched by different threads.
  volatile ServerSocket driverCallbackSocket = null;
  volatile JobWrapper job = null;
  volatile CtrlCHandler ctrlc = null;
  volatile boolean clusterIsUp = false;
  volatile boolean clusterFailedToComeUp = false;
  volatile boolean clusterHasNodeWithLocalhostIp = false;
  volatile boolean shutdownRequested = false;
  volatile AtomicInteger numNodesStarted = new AtomicInteger();
  volatile AtomicInteger numNodesReportingFullCloudSize = new AtomicInteger();
  volatile String clusterIp = null;
  volatile int clusterPort = -1;
  volatile String flatfileContent = null;

  public void setShutdownRequested() {
    shutdownRequested = true;
  }

  public boolean getShutdownRequested() {
    return shutdownRequested;
  }

  public void setClusterIpPort(String ip, int port) {
    clusterIp = ip;
    clusterPort = port;
  }

  public String getPublicUrl() {
    String url;
    if (client) {
      url = H2O.getURL(H2O.getJetty().getScheme());
    } else if (proxy) {
      url = proxyUrl;
    } else {
      url = getClusterUrl();
    }
    return url;
  }

  private String getClusterUrl() {
    String scheme = (jksFileName == null) ? "http" : "https";
    return scheme + "://" + clusterIp + ":" + clusterPort;
  }

  public static boolean usingYarn() {
    Class clazz = null;
    try {
      clazz = Class.forName("water.hadoop.H2OYarnDiagnostic");
    }
    catch (Exception ignore) {}

    return (clazz != null);
  }

  public static void maybePrintYarnLogsMessage(boolean printExtraNewlines) {
    if (usingYarn()) {
      if (printExtraNewlines) {
        System.out.println();
      }

      System.out.println("For YARN users, logs command is 'yarn logs -applicationId " + applicationId + "'");

      if (printExtraNewlines) {
        System.out.println();
      }
    }
  }

  public static void maybePrintYarnLogsMessage() {
    maybePrintYarnLogsMessage(true);
  }

  private static class PortRange {
    int from;
    int to;

    public PortRange(int from, int to) {
      this.from = from;
      this.to = to;
    }

    void validate() {
      if (from > to)
        error("Invalid port range (lower bound larger than upper bound: " + this + ").");
      if ((from == 0) && (to != 0))
        error("Invalid port range (lower bound cannot be 0).");
      if (to > 65535)
        error("Invalid port range (upper bound > 65535).");
    }

    boolean isSinglePort() {
      return from == to;
    }

    static PortRange parse(String rangeSpec) {
      if (rangeSpec == null)
        throw new NullPointerException("Port range is not specified (null).");
      String[] ports = rangeSpec.split("-");
      if (ports.length != 2)
        throw new IllegalArgumentException("Invalid port range specification (" + rangeSpec + ")");
      return new PortRange(parseIntLenient(ports[0]), parseIntLenient(ports[1]));
    }

    private static int parseIntLenient(String s) { return Integer.parseInt(s.trim()); }

    @Override
    public String toString() { return "[" + from + "-" + to + "]"; }
  }

  public static class H2ORecordReader extends RecordReader<Text, Text> {
    H2ORecordReader() {
    }

    @Override
    public void initialize(InputSplit split, TaskAttemptContext context) {
    }

    @Override
    public boolean nextKeyValue() throws IOException {
      return false;
    }

    @Override
    public Text getCurrentKey() { return null; }
    @Override
    public Text getCurrentValue() { return null; }
    @Override
    public void close() throws IOException { }
    @Override
    public float getProgress() throws IOException { return 0; }
  }

  public static class EmptySplit extends InputSplit implements Writable {
    @Override
    public void write(DataOutput out) throws IOException { }
    @Override
    public void readFields(DataInput in) throws IOException { }
    @Override
    public long getLength() { return 0L; }
    @Override
    public String[] getLocations() { return new String[0]; }
  }

  public static class H2OInputFormat extends InputFormat<Text, Text> {
    H2OInputFormat() {
    }

    @Override
    public List<InputSplit> getSplits(JobContext context) throws IOException, InterruptedException {
      List<InputSplit> ret = new ArrayList<InputSplit>();
      int numSplits = numNodes;
      for (int i = 0; i < numSplits; ++i) {
        ret.add(new EmptySplit());
      }
      return ret;
    }

    @Override
    public RecordReader<Text, Text> createRecordReader(
            InputSplit ignored, TaskAttemptContext taskContext)
            throws IOException {
      return(new H2ORecordReader());
    }
  }

  public static void killJobAndWait(JobWrapper job) {
    boolean killed = false;

    try {
      System.out.println("Attempting to clean up hadoop job...");
      job.killJob();
      for (int i = 0; i < 5; i++) {
        if (job.isComplete()) {
          System.out.println("Killed.");
          killed = true;
          break;
        }

        Thread.sleep(1000);
      }
    }
    catch (Exception ignore) {
    }
    finally {
      if (! killed) {
        System.out.println("Kill attempt failed, please clean up job manually.");
      }
    }
  }

  /**
   * Handle Ctrl-C and other catchable shutdown events.
   * If we successfully catch one, then try to kill the hadoop job if
   * we have not already been told it completed.
   *
   * (Of course kill -9 cannot be handled.)
   */
  class CtrlCHandler extends Thread {
    volatile boolean _complete = false;

    public void setComplete() {
      _complete = true;
    }

    @Override
    public void run() {
      if (_complete) {
        return;
      }
      _complete = true;

      try {
        if (job.isComplete()) {
          return;
        }
      }
      catch (Exception ignore) {
      }

      killJobAndWait(job);
      maybePrintYarnLogsMessage();
    }
  }

  private void reportClientReady(String ip, int port) throws Exception {
    assert client;
    if (clusterReadyFileName != null) {
      createClusterReadyFile(ip, port);
      System.out.println("Cluster notification file (" + clusterReadyFileName + ") created (using Client Mode).");
    }
  }

  private void reportProxyReady(String proxyUrl) throws Exception {
    assert proxy;
    if (clusterReadyFileName != null) {
      URL url = new URL(proxyUrl);
      createClusterReadyFile(url.getHost(), url.getPort());
      System.out.println("Cluster notification file (" + clusterReadyFileName + ") created (using Proxy Mode).");
    }
  }

  private void reportClusterReady(String ip, int port) throws Exception {
    setClusterIpPort(ip, port);
    if (client || proxy)
      return; // Hadoop cluster ready but we have to wait for client or proxy to come up
    if (clusterReadyFileName != null) {
      createClusterReadyFile(ip, port);
      System.out.println("Cluster notification file (" + clusterReadyFileName + ") created.");
    }
  }

  private static void createClusterReadyFile(String ip, int port) throws Exception {
    String fileName = clusterReadyFileName + ".tmp";
    String text1 = ip + ":" + port + "\n";
    String text2 = hadoopJobId + "\n";
    try {
      File file = new File(fileName);
      BufferedWriter output = new BufferedWriter(new FileWriter(file));
      output.write(text1);
      output.write(text2);
      output.flush();
      output.close();

      File file2 = new File(clusterReadyFileName);
      boolean success = file.renameTo(file2);
      if (! success) {
        throw new Exception ("Failed to create file " + clusterReadyFileName);
      }
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

  /**
   * Read and handle one Mapper->Driver Callback message.
   */
  class CallbackHandlerThread extends Thread {
    private Socket _s;
    private CallbackManager _cm;

    public void setSocket (Socket value) {
      _s = value;
    }

    public void setCallbackManager (CallbackManager value) {
      _cm = value;
    }

    @Override
    public void run() {
      MapperToDriverMessage msg = new MapperToDriverMessage();
      try {
        msg.read(_s);
        char type = msg.getType();
        if (type == MapperToDriverMessage.TYPE_EOF_NO_MESSAGE) {
          // Ignore it.
          _s.close();
          return;
        }

        // System.out.println("Read message with type " + (int)type);
        if (type == MapperToDriverMessage.TYPE_EMBEDDED_WEB_SERVER_IP_PORT) {
          // System.out.println("H2O node " + msg.getEmbeddedWebServerIp() + ":" + msg.getEmbeddedWebServerPort() + " started");
          _s.close();
        }
        else if (type == MapperToDriverMessage.TYPE_FETCH_FLATFILE) {
          // DO NOT close _s here!
          // Callback manager accumulates sockets to H2O nodes so it can
          // a synthesized flatfile once everyone has arrived.

          System.out.println("H2O node " + msg.getEmbeddedWebServerIp() + ":" + msg.getEmbeddedWebServerPort() + " requested flatfile");
          if (msg.getEmbeddedWebServerIp().equals("127.0.0.1")) {
            clusterHasNodeWithLocalhostIp = true;
          }
          numNodesStarted.incrementAndGet();
          _cm.registerNode(msg.getEmbeddedWebServerIp(), msg.getEmbeddedWebServerPort(), _s);
        }
        else if (type == MapperToDriverMessage.TYPE_CLOUD_SIZE) {
          _s.close();
          System.out.println("H2O node " + msg.getEmbeddedWebServerIp() + ":" + msg.getEmbeddedWebServerPort() +
                  " reports H2O cluster size " + msg.getCloudSize() + " [leader is " + msg.getLeaderWebServerIp() + ":" + msg.getLeaderWebServerPort() + "]");
          if (msg.getCloudSize() == numNodes) {
            // Do this under a synchronized block to avoid getting multiple cluster ready notification files.
            synchronized (h2odriver.class) {
              if (! clusterIsUp) {
                int n = numNodesReportingFullCloudSize.incrementAndGet();
                if (n == numNodes) {
                  reportClusterReady(msg.getLeaderWebServerIp(), msg.getLeaderWebServerPort());
                  clusterIsUp = true;
                }
              }
            }
          }
        }
        else if (type == MapperToDriverMessage.TYPE_EXIT) {
          System.out.println(
                  "H2O node " + msg.getEmbeddedWebServerIp() + ":" + msg.getEmbeddedWebServerPort() +
                  " on host " + _s.getInetAddress().getHostAddress() +
                  " exited with status " + msg.getExitStatus()
          );
          _s.close();
          if (! clusterIsUp) {
            clusterFailedToComeUp = true;
          }
        }
        else {
          _s.close();
          System.err.println("MapperToDriverMessage: Read invalid type (" + type + ") from socket, ignoring...");
        }
      }
      catch (Exception e) {
        System.out.println("Exception occurred in CallbackHandlerThread");
        System.out.println(e.toString());
        if (e.getMessage() != null) {
          System.out.println(e.getMessage());
        }
        e.printStackTrace();
      }
    }
  }

  /**
   * Start a long-running thread ready to handle Mapper->Driver messages.
   */
  class CallbackManager extends Thread {
    private ServerSocket _ss;

    // Nodes and socks
    private final HashSet<String> _dupChecker = new HashSet<String>();
    private final ArrayList<String> _nodes = new ArrayList<String>();
    private final ArrayList<Socket> _socks = new ArrayList<Socket>();

    public void setServerSocket (ServerSocket value) {
      _ss = value;
    }

    public void registerNode (String ip, int port, Socket s) {
      synchronized (_dupChecker) {
        String entry = ip + ":" + port;

        if (_dupChecker.contains(entry)) {
          // This is bad.
          System.out.println("ERROR: Duplicate node registered (" + entry + "), exiting");
          System.exit(1);
        }

        _dupChecker.add(entry);
        _nodes.add(entry);
        _socks.add(s);
        if (_nodes.size() != numNodes) {
          return;
        }

        System.out.println("Sending flatfiles to nodes...");

        assert (_nodes.size() == numNodes);
        assert (_nodes.size() == _socks.size());

        // Build the flatfile and send it to all nodes.
        String flatfile = "";
        for (String val : _nodes) {
          flatfile += val;
          flatfile += "\n";
        }

        for (int i = 0; i < _socks.size(); i++) {
          Socket nodeSock = _socks.get(i);
          DriverToMapperMessage msg = new DriverToMapperMessage();
          msg.setMessageFetchFlatfileResponse(flatfile);
          try {
            System.out.println("    [Sending flatfile to node " + _nodes.get(i) + "]");
            msg.write(nodeSock);
            nodeSock.close();
          }
          catch (Exception e) {
            System.out.println("ERROR: Failed to write to H2O node " + _nodes.get(i));
            System.out.println(e.toString());
            if (e.getMessage() != null) {
              System.out.println(e.getMessage());
            }
            e.printStackTrace();
            System.exit(1);
          }
        }

        // only set if everything went fine
        flatfileContent = flatfile;
      }
    }

    @Override
    public void run() {
      while (true) {
        try {
          Socket s = _ss.accept();
          CallbackHandlerThread t = new CallbackHandlerThread();
          t.setSocket(s);
          t.setCallbackManager(this);
          t.start();
        }
        catch (SocketException e) {
          if (getShutdownRequested()) {
            _ss = null;
            return;
          }
          else {
            System.out.println("Exception occurred in CallbackManager");
            System.out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
            e.printStackTrace();
          }
        }
        catch (Exception e) {
          System.out.println("Exception occurred in CallbackManager");
          System.out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Print usage and exit 1.
   */
  static void usage() {
    System.err.printf(
            "\n" +
                    "Usage: h2odriver\n" +
                    "          [generic Hadoop ToolRunner options]\n" +
                    "          -n | -nodes <number of H2O nodes (i.e. mappers) to create>\n" +
                    "          -mapperXmx <per mapper Java Xmx heap size>\n" +
                    "          [-h | -help]\n" +
                    "          [-jobname <name of job in jobtracker (defaults to: 'H2O_nnnnn')>]\n" +
                    "              (Note nnnnn is chosen randomly to produce a unique name)\n" +
                    "          [-principal <kerberos principal> -keytab <keytab path> [-run_as_user <impersonated hadoop username>] | -run_as_user <hadoop username>]\n" +
                    "          [-driverif <ip address of mapper->driver callback interface>]\n" +
                    "          [-driverport <port of mapper->driver callback interface>]\n" +
                    "          [-driverportrange <range portX-portY of mapper->driver callback interface>; eg: 50000-55000]\n" +
                    "          [-network <IPv4network1Specification>[,<IPv4network2Specification> ...]\n" +
                    "          [-timeout <seconds>]\n" +
                    "          [-disown]\n" +
                    "          [-notify <notification file name>]\n" +
                    "          [-extramempercent <0 to 20>]\n" +
                    "          [-nthreads <maximum typical worker threads, i.e. cpus to use>]\n" +
                    "          [-context_path <context_path> the context path for jetty]\n" +
                    "          [-baseport <starting HTTP port for H2O nodes; default is 54321>]\n" +
                    "          [-flow_dir <server side directory or hdfs directory>]\n" +
                    "          [-ea]\n" +
                    "          [-verbose:gc]\n" +
                    "          [-XX:+PrintGCDetails]\n" +
                    "          [-XX:+PrintGCTimeStamps]\n" +
                    "          [-Xlog:gc=info]\n" +
                    "          [-license <license file name (local filesystem, not hdfs)>]\n" +
                    "          [-o | -output <hdfs output dir>]\n" +
                    "\n" +
                    "Notes:\n" +
                    "          o  Each H2O node runs as a mapper.\n" +
                    "          o  Only one mapper may be run per host.\n" +
                    "          o  There are no combiners or reducers.\n" +
                    "          o  Each H2O cluster should have a unique jobname.\n" +
                    "          o  -mapperXmx and -nodes are required.\n" +
                    "\n" +
                    "          o  -mapperXmx is set to both Xms and Xmx of the mapper to reserve\n" +
                    "             memory up front.\n" +
                    "          o  -extramempercent is a percentage of mapperXmx.  (Default: " + DEFAULT_EXTRA_MEM_PERCENT + ")\n" +
                    "             Extra memory for internal JVM use outside of Java heap.\n" +
                    "                 mapreduce.map.memory.mb = mapperXmx * (1 + extramempercent/100)\n" +
                    "          o  -libjars with an h2o.jar is required.\n" +
                    "          o  -driverif and -driverport/-driverportrange let the user optionally\n" +
                    "             specify the network interface and port/port range (on the driver host)\n" +
                    "             for callback messages from the mapper to the driver.\n" +
                    "          o  -network allows the user to specify a list of networks that the\n" +
                    "             H2O nodes can bind to.  Use this if you have multiple network\n" +
                    "             interfaces on the hosts in your Hadoop cluster and you want to\n" +
                    "             force H2O to use a specific one.\n" +
                    "             (Example network specification: '10.1.2.0/24' allows 256 legal\n" +
                    "             possibilities.)\n" +
                    "          o  -timeout specifies how many seconds to wait for the H2O cluster\n" +
                    "             to come up before giving up.  (Default: " + DEFAULT_CLOUD_FORMATION_TIMEOUT_SECONDS + " seconds\n" +
                    "          o  -disown causes the driver to exit as soon as the cloud forms.\n" +
                    "             Otherwise, Ctrl-C of the driver kills the Hadoop Job.\n" +
                    "          o  -notify specifies a file to write when the cluster is up.\n" +
                    "             The file contains one line with the IP and port of the embedded\n" +
                    "             web server for one of the H2O nodes in the cluster.  e.g.\n" +
                    "                 192.168.1.100:54321\n" +
                    "          o  Flags [-verbose:gc], [-XX:+PrintGCDetails] and [-XX:+PrintGCTimeStamps]" +
                    "             are deperacated in Java 9 and removed in Java 10." +
                    "             The option [-Xlog:gc=info] replaces these flags since Java 9." +
                    "          o  All mappers must start before the H2O cloud is considered up.\n" +
                    "\n" +
                    "Examples:\n" +
                    "          hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g\n" +
                    "          hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g -notify notify.txt -disown\n" +
                    "\n" +
                    "Exit value:\n" +
                    "          0 means the cluster exited successfully with an orderly Shutdown.\n" +
                    "              (From the Web UI or the REST API.)\n" +
                    "\n" +
                    "          non-zero means the cluster exited with a failure.\n" +
                    "              (Note that Ctrl-C is treated as a failure.)\n" +
                    "\n"
    );

    System.exit(1);
  }

  /**
   * Print an error message, print usage, and exit 1.
   * @param s Error message
   */
  static void error(String s) {
    System.err.printf("\nERROR: " + "%s\n\n", s);
    usage();
  }

  /**
   * Print a warning message.
   * @param s Warning message
   */
  static void warning(String s) {
    System.err.printf("\nWARNING: " + "%s\n\n", s);
  }

  /**
   * Read a file into a string.
   * @param fileName File to read.
   * @return Byte contents of file.
   */
  static private byte[] readBinaryFile(String fileName) throws IOException {
    ByteArrayOutputStream ous = null;
    InputStream ios = null;
    try {
      byte[] buffer = new byte[4096];
      ous = new ByteArrayOutputStream();
      ios = new FileInputStream(new File(fileName));
      int read;
      while ((read = ios.read(buffer)) != -1) {
        ous.write(buffer, 0, read);
      }
    }
    finally {
      try {
        if (ous != null)
          ous.close();
      }
      catch (IOException ignore) {}

      try {
        if (ios != null)
          ios.close();
      }
      catch (IOException ignore) {}
    }
    return ous.toByteArray();
  }

  static public void writeBinaryFile(String fileName, byte[] byteArr) throws IOException {
    FileOutputStream out = new FileOutputStream(fileName);
    for (byte b : byteArr) {
      out.write(b);
    }
    out.close();
  }

  /**
   * Array of bytes to brute-force convert into a hexadecimal string.
   * The length of the returned string is byteArr.length * 2.
   *
   * @param byteArr byte array to convert
   * @return hexadecimal string
   */
  static private String convertByteArrToString(byte[] byteArr) {
    StringBuilder sb = new StringBuilder();
    for (byte b : byteArr) {
      int i = b;
      i = i & 0xff;
      sb.append(String.format("%02x", i));
    }
    return sb.toString();
  }

  /**
   * Hexadecimal string to brute-force convert into an array of bytes.
   * The length of the string must be even.
   * The length of the string is 2x the length of the byte array.
   *
   * @param s Hexadecimal string
   * @return byte array
   */
  static public byte[] convertStringToByteArr(String s) {
    if ((s.length() % 2) != 0) {
      throw new RuntimeException("String length must be even (was " + s.length() + ")");
    }

    ArrayList<Byte> byteArrayList = new ArrayList<Byte>();
    for (int i = 0; i < s.length(); i = i + 2) {
      String s2 = s.substring(i, i + 2);
      Integer i2 = Integer.parseInt(s2, 16);
      Byte b2 = (byte)(i2 & 0xff);
      byteArrayList.add(b2);
    }

    byte[] byteArr = new byte[byteArrayList.size()];
    for (int i = 0; i < byteArr.length; i++) {
      byteArr[i] = byteArrayList.get(i);
    }
    return byteArr;
  }

  /**
   * Parse remaining arguments after the ToolRunner args have already been removed.
   * @param args Argument list
   */
  String[] parseArgs(String[] args) {
    int i = 0;
    boolean driverArgs = true;
    while (driverArgs) {
      if (i >= args.length) {
        break;
      }

      String s = args[i];
      if (s.equals("-h") ||
              s.equals("help") ||
              s.equals("-help") ||
              s.equals("--help")) {
        usage();
      }
      else if (s.equals("-n") ||
              s.equals("-nodes")) {
        i++; if (i >= args.length) { usage(); }
        numNodes = Integer.parseInt(args[i]);
      }
      else if (s.equals("-o") ||
              s.equals("-output")) {
        i++; if (i >= args.length) { usage(); }
        outputPath = args[i];
      }
      else if (s.equals("-jobname")) {
        i++; if (i >= args.length) { usage(); }
        jobtrackerName = args[i];
      }
      else if (s.equals("-mapperXmx")) {
        i++; if (i >= args.length) { usage(); }
        mapperXmx = args[i];
      }
      else if (s.equals("-extramempercent")) {
        i++; if (i >= args.length) { usage(); }
        extraMemPercent = Integer.parseInt(args[i]);
      }
      else if (s.equals("-mapperPermSize")) {
        i++; if (i >= args.length) { usage(); }
        mapperPermSize = args[i];
      }
      else if (s.equals("-driverif")) {
        i++; if (i >= args.length) { usage(); }
        driverCallbackIp = args[i];
      }
      else if (s.equals("-driverport")) {
        i++; if (i >= args.length) { usage(); }
        driverCallbackPort = Integer.parseInt(args[i]);
      }
      else if (s.equals("-driverportrange")) {
        i++; if (i >= args.length) { usage(); }
        driverCallbackPortRange = PortRange.parse(args[i]);
      }
      else if (s.equals("-network")) {
        i++; if (i >= args.length) { usage(); }
        network = args[i];
      }
      else if (s.equals("-timeout")) {
        i++; if (i >= args.length) { usage(); }
        cloudFormationTimeoutSeconds = Integer.parseInt(args[i]);
      }
      else if (s.equals("-disown")) {
        disown = true;
      }
      else if (s.equals("-notify")) {
        i++; if (i >= args.length) { usage(); }
        clusterReadyFileName = args[i];
      }
      else if (s.equals("-nthreads")) {
        i++; if (i >= args.length) { usage(); }
        nthreads = Integer.parseInt(args[i]);
      }
      else if (s.equals("-context_path")) {
        i++; if (i >= args.length) { usage(); }
        contextPath = args[i];
      }
      else if (s.equals("-baseport")) {
        i++; if (i >= args.length) { usage(); }
        basePort = Integer.parseInt(args[i]);
        if ((basePort < 0) || (basePort > 65535)) {
            error("Base port must be between 1 and 65535");
        }
      }
      else if (s.equals("-port_offset")) {
        i++; if (i >= args.length) { usage(); }
        portOffset = Integer.parseInt(args[i]);
        if ((portOffset <= 0) || (portOffset > 65534)) {
          error("Port offset must be between 1 and 65534");
        }
      }
      else if (s.equals("-beta")) {
        beta = true;
      }
      else if (s.equals("-random_udp_drop")) {
        enableRandomUdpDrop = true;
      }
      else if (s.equals("-ea")) {
        enableExceptions = true;
      }
      else if (s.equals("-verbose:gc") && !JAVA_VERSION.useUnifiedLogging()) {
        if (!JAVA_VERSION.useUnifiedLogging()) {
          enableVerboseGC = true;
        } else {
          error("Parameter -verbose:gc is unusable, running on JVM with deprecated GC debugging flags.");
        }
      } else if (s.equals("-Xlog:gc=info")) {
        if (JAVA_VERSION.useUnifiedLogging()) {
          enableVerboseGC = true;
        } else {
          error("Parameter -verbose:gc is unusable, running on JVM without unified JVM logging.");
        }
      }
      else if (s.equals("-verbose:class")) {
        enableVerboseClass = true;
      }
      else if (s.equals("-XX:+PrintCompilation")) {
        enablePrintCompilation = true;
      }
      else if (s.equals("-exclude")) {
        enableExcludeMethods = true;
      }
      else if (s.equals("-Dlog4j.defaultInitOverride=true")) {
        enableLog4jDefaultInitOverride = true;
      }
      else if (s.equals("-debug")) {
        enableDebug = true;
      }
      else if (s.equals("-suspend")) {
        enableSuspend = true;
      }
      else if (s.equals("-debugport")) {
        i++; if (i >= args.length) { usage(); }
        debugPort = Integer.parseInt(args[i]);
        if ((debugPort < 0) || (debugPort > 65535)) {
          error("Debug port must be between 1 and 65535");
        }
      } else if (s.equals("-XX:+PrintGCDetails")) {
        if (!JAVA_VERSION.useUnifiedLogging()) {
          enablePrintGCDetails = true;
        } else {
          error("Parameter -XX:+PrintGCDetails is unusable, running on JVM with deprecated GC debugging flags.");
        }
      }
      else if (s.equals("-XX:+PrintGCTimeStamps")) {
        if (!JAVA_VERSION.useUnifiedLogging()) {
          enablePrintGCDetails = true;
        } else {
          error("Parameter -XX:+PrintGCTimeStamps is unusable, running on JVM with deprecated GC debugging flags.");
        }
      }
      else if (s.equals("-gc")) {
        enableVerboseGC = true;
        enablePrintGCDetails = true;
        enablePrintGCTimeStamps = true;
      }
      else if (s.equals("-nogc")) {
        enableVerboseGC = false;
        enablePrintGCDetails = false;
        enablePrintGCTimeStamps = false;
      }
      else if (s.equals("-flow_dir")) {
        i++; if (i >= args.length) { usage(); }
        flowDir = args[i];
      }
      else if (s.equals("-J")) {
        i++; if (i >= args.length) { usage(); }
        extraArguments.add(args[i]);
      }
      else if (s.equals("-JJ")) {
        i++; if (i >= args.length) { usage(); }
        extraJvmArguments.add(args[i]);
      }
      else if (s.equals("-jks")) {
        i++; if (i >= args.length) { usage(); }
        jksFileName = args[i];
      }
      else if (s.equals("-jks_pass")) {
        i++; if (i >= args.length) { usage(); }
        jksPass = args[i];
      }
      else if (s.equals("-internal_secure_connections")) {
        internal_secure_connections = true;
      }
      else if (s.equals("-internal_security_conf") || s.equals("-internal_security")) {
        if(s.equals("-internal_security")){
          System.out.println("The '-internal_security' configuration is deprecated. " +
                  "Please use '-internal_security_conf' instead.");
        }
        i++; if (i >= args.length) { usage(); }
        securityConf = args[i];
      }
      else if (s.equals("-hash_login")) {
        hashLogin = true;
      }
      else if (s.equals("-ldap_login")) {
        ldapLogin = true;
      }
      else if (s.equals("-kerberos_login")) {
        kerberosLogin = true;
      }
      else if (s.equals("-pam_login")) {
        pamLogin = true;
      }
      else if (s.equals("-login_conf")) {
        i++; if (i >= args.length) { usage(); }
        loginConfFileName = args[i];
      }
      else if (s.equals("-form_auth")) {
        formAuth = true;
      }
      else if (s.equals("-session_timeout")) {
        i++; if (i >= args.length) { usage(); }
        sessionTimeout = args[i];
      }
      else if (s.equals("-user_name")) {
        i++; if (i >= args.length) { usage(); }
        userName = args[i];
      }
      else if (s.equals("-client")) {
        client = true;
        driverArgs = false;
      }
      else if (s.equals("-proxy")) {
        proxy = true;
        driverArgs = false;
      } else if (s.equals("-run_as_user")) {
        i++; if (i >= args.length) { usage(); }
        runAsUser = args[i];
      } else if (s.equals("-principal")) {
        i++; if (i >= args.length) { usage(); }
        principal = args[i];
      } else if (s.equals("-keytab")) {
        i++; if (i >= args.length) { usage (); }
        keytabPath = args[i];
      } else if (s.equals("-report_hostname")) {
        reportHostname = true;
      } else if (s.equals("-driver_debug")) {
        driverDebug = true;
      } else {
        error("Unrecognized option " + s);
      }

      i++;
    }
    String[] otherArgs = new String[Math.max(args.length - i, 0)];
    for (int j = 0; j < otherArgs.length; j++)
      otherArgs[j] = args[i++];
    return otherArgs;
  }

  void validateArgs() {
    // Check for mandatory arguments.
    if (numNodes < 1) {
      error("Number of H2O nodes must be greater than 0 (must specify -n)");
    }
    if (mapperXmx == null) {
      error("Missing required option -mapperXmx");
    }

    // Check for sane arguments.
    if (! mapperXmx.matches("[1-9][0-9]*[mgMG]")) {
      error("-mapperXmx invalid (try something like -mapperXmx 4g)");
    }

    if (mapperPermSize != null) {
      if (!mapperPermSize.matches("[1-9][0-9]*[mgMG]")) {
        error("-mapperPermSize invalid (try something like -mapperPermSize 512m)");
      }
    }

    if (extraMemPercent < 0) {
      extraMemPercent = DEFAULT_EXTRA_MEM_PERCENT;
    }

    if (jobtrackerName == null) {
      Random rng = new Random();
      int num = rng.nextInt(99999);
      jobtrackerName = "H2O_" + num;
    }

    if (network == null) {
      network = "";
    }
    else {
      String[] networks;
      if (network.contains(",")) {
        networks = network.split(",");
      }
      else {
        networks = new String[1];
        networks[0] = network;
      }

      for (String n : networks) {
        Pattern p = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)");
        Matcher m = p.matcher(n);
        boolean b = m.matches();
        if (! b) {
          error("network invalid: " + n);
        }

        for (int k = 1; k <=4; k++) {
          int o = Integer.parseInt(m.group(k));
          if ((o < 0) || (o > 255)) {
            error("network invalid: " + n);
          }

          int bits = Integer.parseInt(m.group(5));
          if ((bits < 0) || (bits > 32)) {
            error("network invalid: " + n);
          }
        }
      }
    }

    if (network == null) {
      error("Internal error, network should not be null at this point");
    }

    if ((nthreads >= 0) && (nthreads < 4)) {
      error("nthreads invalid (must be >= 4): " + nthreads);
    }

    if ((driverCallbackPort != 0) && (driverCallbackPortRange != null)) {
      error("cannot specify both -driverport and -driverportrange (remove one of these options)");
    }

    if (driverCallbackPortRange != null)
      driverCallbackPortRange.validate();

    if (sessionTimeout != null) {
      if (! formAuth) {
        error("session timeout can only be enabled for Form-based authentication (use the '-form_auth' option)");
      }
      int timeout = 0;
      try { timeout = Integer.parseInt(sessionTimeout); } catch (Exception e) { /* ignored */ }
      if (timeout <= 0) {
        error("invalid session timeout specification (" + sessionTimeout + ")");
      }
    }

    if (principal != null || keytabPath != null) {
      if (principal == null) {
        error("keytab requires a valid principal (use the '-principal' option)");
      }
      if (keytabPath == null) {
        error("principal requires a valid keytab path (use the '-keytab' option)");
      }
      if (runAsUser != null) {
        warning("will attempt secure impersonation with user from '-run_as_user', " + runAsUser);
      }
    }

    if (client && disown) {
      error("client mode doesn't support the '-disown' option");
    }

    if (proxy && disown) {
      error("proxy mode doesn't support the '-disown' option");
    }
  }

  static String calcMyIp() throws Exception {
    Enumeration nis = NetworkInterface.getNetworkInterfaces();

    System.out.println("Determining driver host interface for mapper->driver callback...");
    while (nis.hasMoreElements()) {
      NetworkInterface ni = (NetworkInterface) nis.nextElement();
      Enumeration ias = ni.getInetAddresses();
      while (ias.hasMoreElements()) {
        InetAddress ia = (InetAddress) ias.nextElement();
        String s = ia.getHostAddress();
        System.out.println("    [Possible callback IP address: " + s + "]");
      }
    }

    InetAddress ia = InetAddress.getLocalHost();
    return ia.getHostAddress();
  }

  private final int CLUSTER_ERROR_JOB_COMPLETED_TOO_EARLY = 5;
  private final int CLUSTER_ERROR_TIMEOUT = 3;

  private int waitForClusterToComeUp() throws Exception {
    final long startMillis = System.currentTimeMillis();
    while (true) {
      DBG("clusterFailedToComeUp=", clusterFailedToComeUp, ";clusterIsUp=", clusterIsUp);

      if (clusterFailedToComeUp) {
        System.out.println("ERROR: At least one node failed to come up during cluster formation");
        killJobAndWait(job);
        return 4;
      }

      DBG("Checking if the job already completed");
      if (job.isComplete()) {
        return CLUSTER_ERROR_JOB_COMPLETED_TOO_EARLY;
      }

      if (clusterIsUp) {
        break;
      }

      long nowMillis = System.currentTimeMillis();
      long deltaMillis = nowMillis - startMillis;
      DBG("Cluster is not yet up, waiting for ", deltaMillis, "ms.");
      if (cloudFormationTimeoutSeconds > 0) {
        if (deltaMillis > (cloudFormationTimeoutSeconds * 1000)) {
          System.out.println("ERROR: Timed out waiting for H2O cluster to come up (" + cloudFormationTimeoutSeconds + " seconds)");
          System.out.println("ERROR: (Try specifying the -timeout option to increase the waiting time limit)");
          if (clusterHasNodeWithLocalhostIp) {
            System.out.println("");
            System.out.println("NOTE: One of the nodes chose 127.0.0.1 as its IP address, which is probably wrong.");
            System.out.println("NOTE: You may want to specify the -network option, which lets you specify the network interface the mappers bind to.");
            System.out.println("NOTE: Typical usage is:  -network a.b.c.d/24");
          }
          killJobAndWait(job);
          return CLUSTER_ERROR_TIMEOUT;
        }
      }

      final int ONE_SECOND_MILLIS = 1000;
      Thread.sleep (ONE_SECOND_MILLIS);
    }

    return 0;
  }

  private void waitForClusterToShutdown() throws Exception {
    while (true) {
      if (job.isComplete()) {
        break;
      }

      final int ONE_SECOND_MILLIS = 1000;
      Thread.sleep (ONE_SECOND_MILLIS);
    }
  }

  /*
   * Clean up driver-side resources after the hadoop job has finished.
   *
   * This method was added so that it can be called from inside
   * Spring Hadoop and the driver can be created and then deleted from inside
   * a single process.
   */
  private void cleanUpDriverResources() {
    ctrlc.setComplete();
    try {
      Runtime.getRuntime().removeShutdownHook(ctrlc);
    }
    catch (IllegalStateException ignore) {
      // If "Shutdown in progress" exception would be thrown, just ignore and don't bother to remove the hook.
    }
    ctrlc = null;

    try {
      setShutdownRequested();
      driverCallbackSocket.close();
      driverCallbackSocket = null;
    }
    catch (Exception e) {
      System.out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
      e.printStackTrace();
    }

    try {
      if (! job.isComplete()) {
        System.out.println("ERROR: Job not complete after cleanUpDriverResources()");
      }
    }
    catch (Exception e) {
      System.out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
      e.printStackTrace();
    }

    // At this point, resources are released.
    // The hadoop job has completed (job.isComplete() is true),
    // so the cluster memory and cpus are freed.
    // The driverCallbackSocket has been closed so a new one can be made.

    // The callbackManager itself may or may not have finished, but it doesn't
    // matter since the server socket has been closed.
  }

  private String calcHadoopVersion() {
    try {
      Process p = new ProcessBuilder("hadoop", "version").start();
      p.waitFor();
      BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = br.readLine();
      if (line == null) {
        line = "(unknown)";
      }
      return line;
    }
    catch (Exception e) {
      return "(unknown)";
    }
  }

  private int mapperArgsLength = 0;
  private int mapperConfLength = 0;

  private void addMapperArg(Configuration conf, String name) {
    conf.set(h2omapper.H2O_MAPPER_ARGS_BASE + Integer.toString(mapperArgsLength), name);
    mapperArgsLength++;
  }

  private void addMapperArg(Configuration conf, String name, String value) {
    addMapperArg(conf, name);
    addMapperArg(conf, value);
  }

  private void addMapperConf(Configuration conf, String name, String value, String payloadFileName) {
    try {
      byte[] payloadData = readBinaryFile(payloadFileName);
      addMapperConf(conf, name, value, payloadData);
    }
    catch (Exception e) {
      StringBuilder sb = new StringBuilder();
      sb.append("Failed to read config file (").append(payloadFileName).append(")");
      if (e.getLocalizedMessage() != null) {
        sb.append(": ");
        sb.append(e.getLocalizedMessage());
      }

      error(sb.toString());
    }
  }

  private void addMapperConf(Configuration conf, String name, String value, byte[] payloadData) {
    String payload = convertByteArrToString(payloadData);

    conf.set(h2omapper.H2O_MAPPER_CONF_ARG_BASE + Integer.toString(mapperConfLength), name);
    conf.set(h2omapper.H2O_MAPPER_CONF_BASENAME_BASE + Integer.toString(mapperConfLength), value);
    conf.set(h2omapper.H2O_MAPPER_CONF_PAYLOAD_BASE + Integer.toString(mapperConfLength), payload);
    mapperConfLength++;
  }

  private static ServerSocket bindCallbackSocket() throws IOException {
    Exception ex = null;
    int permissionExceptionCount = 0; // try to detect likely unintended range specifications
    // eg.: running with non-root user & setting range 100-1100 (the effective range would be 1024-1100 = not what user wanted)
    ServerSocket result = null;
    for (int p = driverCallbackPortRange.from; (result == null) && (p <= driverCallbackPortRange.to); p++) {
      ServerSocket ss = new ServerSocket();
      ss.setReuseAddress(true);
      InetSocketAddress sa = new InetSocketAddress(driverCallbackIp, p);
      try {
        int backlog = Math.max(50, numNodes * 3); // minimum 50 (bind's default) or numNodes * 3 (safety constant, arbitrary)
        ss.bind(sa, backlog);
        result = ss;
      } catch (BindException e) {
        if ("Permission denied".equals(e.getMessage()))
          permissionExceptionCount++;
        ex = e;
      } catch (SecurityException e) {
        permissionExceptionCount++;
        ex = e;
      } catch (IOException e) {
        ex = e;
      } catch (RuntimeException e) {
        ex = e;
      }
    }
    if ((permissionExceptionCount > 0) && (! driverCallbackPortRange.isSinglePort()))
      warning("Some ports (count=" + permissionExceptionCount + ") of the specified port range are not available" +
              " due to process restrictions (range: " + driverCallbackPortRange + ").");
    if (result == null)
      if (ex instanceof IOException)
        throw (IOException) ex;
      else
        throw (RuntimeException) ex;
    return result;
  }

  private int run2(String[] args) throws Exception {
    // Arguments that get their default value set based on runtime info.
    // -----------------------------------------------------------------

    // PermSize
    // Java 7 and below need a larger PermSize for H2O.
    // Java 8 no longer has PermSize, but rather MetaSpace, which does not need to be set at all.
    if (JAVA_VERSION.getMajor() <= 7) {
      mapperPermSize = "256m";
    }

    // Parse arguments.
    // ----------------
    args = ArrayUtils.append(args, getSystemArgs()); // append "system-level" args to user specified args
    String[] otherArgs = parseArgs(args);
    validateArgs();

    // Set up callback address and port.
    // ---------------------------------
    if (driverCallbackIp == null) {
      driverCallbackIp = calcMyIp();
    }
    if (driverCallbackPortRange == null) {
      driverCallbackPortRange = new PortRange(driverCallbackPort, driverCallbackPort);
    }
    driverCallbackSocket = bindCallbackSocket();
    int actualDriverCallbackPort = driverCallbackSocket.getLocalPort();
    CallbackManager cm = new CallbackManager();
    cm.setServerSocket(driverCallbackSocket);
    cm.start();
    System.out.println("Using mapper->driver callback IP address and port: " + driverCallbackIp + ":" + actualDriverCallbackPort);
    System.out.println("(You can override these with -driverif and -driverport/-driverportrange.)");

    // Set up configuration.
    // ---------------------
    Configuration conf = getConf();

    // Run impersonation options
    if (principal != null && keytabPath != null) {
      UserGroupInformation.setConfiguration(conf);
      UserGroupInformation.loginUserFromKeytab(principal, keytabPath);
      // performs user impersonation (will only work if core-site.xml has hadoop.proxyuser.*.* props set on name node
      if (runAsUser != null) {
        System.out.println("Attempting to securely impersonate user, " + runAsUser);
        UserGroupInformation currentEffUser = UserGroupInformation.getLoginUser();
        UserGroupInformation proxyUser = UserGroupInformation.createProxyUser(runAsUser, currentEffUser);
        UserGroupInformation.setLoginUser(proxyUser);
      }
    } else if (runAsUser != null) {
      UserGroupInformation.setConfiguration(conf);
      UserGroupInformation.setLoginUser(UserGroupInformation.createRemoteUser(runAsUser));
    }


    // Set memory parameters.
    long processTotalPhysicalMemoryMegabytes;
    {
      Pattern p = Pattern.compile("([1-9][0-9]*)([mgMG])");
      Matcher m = p.matcher(mapperXmx);
      boolean b = m.matches();
      if (!b) {
        System.out.println("(Could not parse mapperXmx.");
        System.out.println("INTERNAL FAILURE.  PLEASE CONTACT TECHNICAL SUPPORT.");
        System.exit(1);
      }
      assert (m.groupCount() == 2);
      String number = m.group(1);
      String units = m.group(2);
      long megabytes = Long.parseLong(number);
      if (units.equals("g") || units.equals("G")) {
        megabytes = megabytes * 1024;
      }

      // YARN container must be sized greater than Xmx.
      // YARN will kill the application if the RSS of the process is larger than
      // mapreduce.map.memory.mb.
      long jvmInternalMemoryMegabytes = (long) ((double) megabytes * ((double) extraMemPercent) / 100.0);
      processTotalPhysicalMemoryMegabytes = megabytes + jvmInternalMemoryMegabytes;
      conf.set("mapreduce.job.ubertask.enable", "false");
      String mapreduceMapMemoryMb = Long.toString(processTotalPhysicalMemoryMegabytes);
      conf.set("mapreduce.map.memory.mb", mapreduceMapMemoryMb);

      // MRv1 standard options, but also required for YARN.
      StringBuilder sb = new StringBuilder()
              .append("-Xms").append(mapperXmx)
              .append(" -Xmx").append(mapperXmx)
              .append(((mapperPermSize != null) && (mapperPermSize.length() > 0)) ? (" -XX:PermSize=" + mapperPermSize) : "")
              .append((enableExceptions ? " -ea" : ""))
              .append((enableVerboseGC && !JAVA_VERSION.useUnifiedLogging() ? " -verbose:gc" : ""))
              .append(enableVerboseGC && JAVA_VERSION.useUnifiedLogging() ? "-Xlog:gc=info" : "")
              .append((enablePrintGCDetails ? " -XX:+PrintGCDetails" : ""))
              .append((enablePrintGCTimeStamps ? " -XX:+PrintGCTimeStamps" : ""))
              .append((enableVerboseClass ? " -verbose:class" : ""))
              .append((enablePrintCompilation ? " -XX:+PrintCompilation" : ""))
              .append((enableExcludeMethods ? " -XX:CompileCommand=exclude,water/fvec/NewChunk.append2slowd" : ""))
              .append((enableLog4jDefaultInitOverride ? " -Dlog4j.defaultInitOverride=true" : ""))
              .append((enableDebug ? " -agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (enableSuspend ? "y" : "n") + ",address=" + debugPort : ""));
      for (String s : extraJvmArguments) {
        sb.append(" ").append(s);
      }

      String mapChildJavaOpts = sb.toString();

      conf.set("mapreduce.map.java.opts", mapChildJavaOpts);
      if (!usingYarn()) {
        conf.set("mapred.child.java.opts", mapChildJavaOpts);
        conf.set("mapred.map.child.java.opts", mapChildJavaOpts);       // MapR 2.x requires this.
      }

      System.out.println("Memory Settings:");
      System.out.println("    mapreduce.map.java.opts:     " + mapChildJavaOpts);
      System.out.println("    Extra memory percent:        " + extraMemPercent);
      System.out.println("    mapreduce.map.memory.mb:     " + mapreduceMapMemoryMb);
    }

    conf.set("mapreduce.client.genericoptionsparser.used", "true");
    if (!usingYarn()) {
      conf.set("mapred.used.genericoptionsparser", "true");
    }

    conf.set("mapreduce.map.speculative", "false");
    if (!usingYarn()) {
      conf.set("mapred.map.tasks.speculative.execution", "false");
    }

    conf.set("mapreduce.map.maxattempts", "1");
    if (!usingYarn()) {
      conf.set("mapred.map.max.attempts", "1");
    }

    conf.set("mapreduce.job.jvm.numtasks", "1");
    if (!usingYarn()) {
      conf.set("mapred.job.reuse.jvm.num.tasks", "1");
    }

    conf.set(h2omapper.H2O_DRIVER_IP_KEY, driverCallbackIp);
    conf.set(h2omapper.H2O_DRIVER_PORT_KEY, Integer.toString(actualDriverCallbackPort));

    // Arguments.
    addMapperArg(conf, "-name", jobtrackerName);
    if (network.length() > 0) {
      addMapperArg(conf, "-network", network);
    }
    if (nthreads >= 0) {
      addMapperArg(conf, "-nthreads", Integer.toString(nthreads));
    }
    if (contextPath != null) {
      addMapperArg(conf, "-context_path", contextPath);
    }
    if (basePort >= 0) {
      addMapperArg(conf, "-baseport", Integer.toString(basePort));
    }
    if (portOffset >= 1) {
      addMapperArg(conf, "-port_offset", Integer.toString(portOffset));
    }
    if (beta) {
      addMapperArg(conf, "-beta");
    }
    if (enableRandomUdpDrop) {
      addMapperArg(conf, "-random_udp_drop");
    }
    if (flowDir != null) {
      addMapperArg(conf, "-flow_dir", flowDir);
    }
    if ((new File(".h2o_no_collect")).exists() || (new File(System.getProperty("user.home") + "/.h2o_no_collect")).exists()) {
      addMapperArg(conf, "-ga_opt_out");
    }
    String hadoopVersion = calcHadoopVersion();
    addMapperArg(conf, "-ga_hadoop_ver", hadoopVersion);
    if (jksPass != null) {
      addMapperArg(conf, "-jks_pass", jksPass);
    }
    if (hashLogin) {
      addMapperArg(conf, "-hash_login");
    }
    if (ldapLogin) {
      addMapperArg(conf, "-ldap_login");
    }
    if (kerberosLogin) {
      addMapperArg(conf, "-kerberos_login");
    }
    if (pamLogin) {
      addMapperArg(conf, "-pam_login");
    }
    if (formAuth) {
      addMapperArg(conf, "-form_auth");
    }
    if (sessionTimeout != null) {
      addMapperArg(conf, "-session_timeout", sessionTimeout);
    }
    addMapperArg(conf, "-user_name", userName);

    for (String s : extraArguments) {
      addMapperArg(conf, s);
    }

    if (client) {
      addMapperArg(conf, "-md5skip");
      addMapperArg(conf, "-disable_web");
    }

    // Proxy
    final Credentials proxyCredentials = proxy ? Credentials.make(userName) : null;
    if (proxyCredentials != null) {
      final byte[] hashFileData = StringUtils.bytesOf(proxyCredentials.toHashFileEntry());
      addMapperArg(conf, "-hash_login");
      addMapperConf(conf, "-login_conf", "login.conf", hashFileData);
    }

    conf.set(h2omapper.H2O_MAPPER_ARGS_LENGTH, Integer.toString(mapperArgsLength));

    // Config files.
    if (jksFileName != null) {
      addMapperConf(conf, "-jks", "h2o.jks", jksFileName);
    }
    if (loginConfFileName != null) {
      addMapperConf(conf, "-login_conf", "login.conf", loginConfFileName);
    } else if (kerberosLogin) {
      // Use default Kerberos configuration file
      final byte[] krbConfData = StringUtils.bytesOf(
              "krb5loginmodule {\n" +
              "     com.sun.security.auth.module.Krb5LoginModule required;\n" +
              "};"
      );
      addMapperConf(conf, "-login_conf", "login.conf", krbConfData);
    } else if (pamLogin) {
      // Use default PAM configuration file
      final byte[] pamConfData = StringUtils.bytesOf(
              "pamloginmodule {\n" +
                      "     de.codedo.jaas.PamLoginModule required\n" +
                      "     service = h2o;\n" +
                      "};"
      );
      addMapperConf(conf, "-login_conf", "login.conf", pamConfData);
    }

    // SSL
    if (null != securityConf && !securityConf.isEmpty()) {
      addMapperConf(conf, "-internal_security_conf", "security.config", securityConf);
    } else if(internal_secure_connections) {
      SecurityUtils.SSLCredentials credentials = SecurityUtils.generateSSLPair();
      securityConf = SecurityUtils.generateSSLConfig(credentials);
      addMapperConf(conf, "", credentials.jks.name, credentials.jks.getLocation());
      addMapperConf(conf, "-internal_security_conf", "default-security.config", securityConf);
    }

    conf.set(h2omapper.H2O_MAPPER_CONF_LENGTH, Integer.toString(mapperConfLength));

    // Run job.  We are running a zero combiner and zero reducer configuration.
    // ------------------------------------------------------------------------
    job = submitJob(conf);

    System.out.println("Job name '" + jobtrackerName + "' submitted");
    System.out.println("JobTracker job ID is '" + job.getJobID() + "'");
    hadoopJobId = job.getJobID();
    applicationId = hadoopJobId.replace("job_", "application_");
    maybePrintYarnLogsMessage(false);

    // Register ctrl-c handler to try to clean up job when possible.
    ctrlc = new CtrlCHandler();
    Runtime.getRuntime().addShutdownHook(ctrlc);

    System.out.println("Waiting for H2O cluster to come up...");
    int rv = waitForClusterToComeUp();

    if ((rv == CLUSTER_ERROR_TIMEOUT) ||
        (rv == CLUSTER_ERROR_JOB_COMPLETED_TOO_EARLY)) {
      // Try to print YARN diagnostics.
      try {
        // Wait a short time before trying to print diagnostics.
        // Try to give the Resource Manager time to clear out this job itself from it's state.
        Thread.sleep(3000);

        Class clazz = Class.forName("water.hadoop.H2OYarnDiagnostic");
        if (clazz != null) {
          @SuppressWarnings("all")
          Method method = clazz.getMethod("diagnose", String.class, String.class, int.class, int.class, int.class);
          String queueName;
          queueName = conf.get("mapreduce.job.queuename");
          if (queueName == null) {
            queueName = conf.get("mapred.job.queue.name");
          }
          if (queueName == null) {
            queueName = "default";
          }
          method.invoke(null, applicationId, queueName, numNodes, (int)processTotalPhysicalMemoryMegabytes, numNodesStarted.get());
        }

        return rv;
      }
      catch (Exception e) {
        if (System.getenv("H2O_DEBUG_HADOOP") != null) {
          System.out.println();
          e.printStackTrace();
          System.out.println();
        }
      }

      System.out.println("ERROR: H2O cluster failed to come up");
      return rv;
    }
    else if (rv != 0) {
      System.out.println("ERROR: H2O cluster failed to come up");
      return rv;
    }

    if (job.isComplete()) {
      System.out.println("ERROR: H2O cluster failed to come up");
      return 2;
    }

    System.out.printf("H2O cluster (%d nodes) is up\n", numNodes);
    if (disown) {
      // Do a short sleep here just to make sure all of the cloud
      // status stuff in H2O has settled down.
      Thread.sleep(CLOUD_FORMATION_SETTLE_DOWN_SECONDS);

      System.out.println("Open H2O Flow in your web browser: " + getPublicUrl());
      System.out.println("Disowning cluster and exiting.");
      Runtime.getRuntime().removeShutdownHook(ctrlc);
      return 0;
    }

    if (client) {
      if (flatfileContent == null)
        throw new IllegalStateException("ERROR: flatfile should have been created by now.");

      final File flatfile = File.createTempFile("h2o", "txt");
      flatfile.deleteOnExit();

      Writer w = new BufferedWriter(new FileWriter(flatfile));
      boolean flatfileCreated = false;
      try {
        w.write(flatfileContent);
        w.close();
        flatfileCreated = true;
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        try {
          w.close();
        } catch (IOException suppressed) { /* ignore */ }
      }

      if (!flatfileCreated) {
        System.out.println("ERROR: Failed to write flatfile.");
        System.exit(1);
      }

      String[] generatedClientArgs = new String[]{
              "-client",
              "-flatfile", flatfile.getAbsolutePath(),
              "-md5skip",
              "-user_name", userName,
              "-name", jobtrackerName
      };
      if (securityConf != null)
        generatedClientArgs = ArrayUtils.append(generatedClientArgs, new String[]{"-internal_security_conf", securityConf});
      generatedClientArgs = ArrayUtils.append(generatedClientArgs, otherArgs);

      H2OStarter.start(generatedClientArgs, true);
      reportClientReady(H2O.SELF_ADDRESS.getHostAddress(), H2O.API_PORT);
    }

    if (proxy) {
      proxyUrl = ProxyStarter.start(otherArgs, proxyCredentials, getClusterUrl(), reportHostname);
      reportProxyReady(proxyUrl);
    }

    if (! (client || proxy))
      System.out.println("(Note: Use the -disown option to exit the driver after cluster formation)");
    System.out.println("");
    System.out.println("Open H2O Flow in your web browser: " + getPublicUrl());
    System.out.println("");
    System.out.println("(Press Ctrl-C to kill the cluster)");
    System.out.println("Blocking until the H2O cluster shuts down...");
    waitForClusterToShutdown();
    cleanUpDriverResources();

    boolean success = job.isSuccessful();
    int exitStatus;
    exitStatus = success ? 0 : 1;
    System.out.println((success ? "" : "ERROR: ") + "Job was" + (success ? " " : " not ") + "successful");
    if (success) {
      System.out.println("Exiting with status 0");
    }
    else {
      System.out.println("Exiting with nonzero exit status");
    }
    return exitStatus;
  }

  private String[] getSystemArgs() {
    String[] args = new String[0];
    String config = getConf().get(ARGS_CONFIG_PROP, DEFAULT_ARGS_CONFIG);
    File argsConfig = new File(String.format(ARGS_CONFIG_FILE_PATTERN, config));
    if (! argsConfig.exists()) {
      File defaultArgsConfig = new File(String.format(ARGS_CONFIG_FILE_PATTERN, DEFAULT_ARGS_CONFIG));
      if (defaultArgsConfig.exists()) {
        System.out.println("ERROR: There is no arguments file for configuration '" + config + "', however, " +
                "the arguments file exists for the default configuration.\n       " +
                "Please create an arguments file also for configuration '" + config + "' and store it in '" +
                argsConfig.getAbsolutePath() + "' (the file can be empty).");
        System.exit(1);
      }
      return args;
    }

    try (BufferedReader r = new BufferedReader(new FileReader(argsConfig))) {
      String arg;
      while ((arg = r.readLine()) != null) {
        args = ArrayUtils.append(args, arg.trim());
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("ERROR: System level H2O arguments cannot be read from file " + argsConfig.getAbsolutePath() + "; "
              + (e.getMessage() != null ? e.getMessage() : "(null)"));
      System.exit(1);
    }

    return args;
  }

  private JobWrapper submitJob(Configuration conf) throws Exception {
    // Set up job stuff.
    // -----------------
    final Job j = new Job(conf, jobtrackerName);
    j.setJarByClass(getClass());
    j.setInputFormatClass(H2OInputFormat.class);
    j.setMapperClass(h2omapper.class);
    j.setNumReduceTasks(0);
    j.setOutputKeyClass(Text.class);
    j.setOutputValueClass(Text.class);

    if (outputPath != null)
      FileOutputFormat.setOutputPath(j, new Path(outputPath));
    else
      j.setOutputFormatClass(NullOutputFormat.class);

    DBG("Submitting job");
    j.submit();
    JobWrapper jw = JobWrapper.wrap(j);
    DBG("Job submitted, id=", jw.getJobID());

    return jw;
  }

  /**
   * The run method called by ToolRunner.
   * @param args Arguments after ToolRunner arguments have been removed.
   * @return Exit value of program.
   */
  @Override
  public int run(String[] args) {
    int rv = -1;

    try {
      rv = run2(args);
    }
    catch (org.apache.hadoop.mapred.FileAlreadyExistsException e) {
      if (ctrlc != null) { ctrlc.setComplete(); }
      System.out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
      System.exit(1);
    }
    catch (Exception e) {
      System.out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : "(null)"));
      e.printStackTrace();
      System.exit(1);
    }

    return rv;
  }

  private static abstract class JobWrapper {
    final String _jobId;
    final Job _job;

    JobWrapper(Job job) {
      _job = job;
      _jobId = _job.getJobID().toString();
    }

    String getJobID() {
      return _jobId;
    }

    abstract boolean isComplete() throws IOException;
    abstract void killJob() throws IOException;
    abstract boolean isSuccessful() throws IOException;

    static JobWrapper wrap(Job job) {
      if (driverDebug) {
        int timeoutSeconds = job.getConfiguration().getInt(DRIVER_JOB_CALL_TIMEOUT_SEC, 10);
        DBG("Timeout for Hadoop calls set to ", timeoutSeconds, "s");
        return new AsyncExecutingJobWrapper(job, timeoutSeconds);
      } else
        return new DelegatingJobWrapper(job);
    }
  }

  private static class DelegatingJobWrapper extends JobWrapper {
    DelegatingJobWrapper(Job job) {
      super(job);
    }

    @Override
    boolean isComplete() throws IOException {
      return _job.isComplete();
    }

    @Override
    boolean isSuccessful() throws IOException {
      return _job.isSuccessful();
    }

    @Override
    void killJob() throws IOException {
      _job.killJob();
    }
  }

  private static class AsyncExecutingJobWrapper extends JobWrapper {
    private final ExecutorService _es;
    private final int _timeoutSeconds;
    AsyncExecutingJobWrapper(Job job, int timeoutSeconds) {
      super(job);
      _es = Executors.newCachedThreadPool();
      _timeoutSeconds = timeoutSeconds;
    }

    @Override
    boolean isComplete() throws IOException {
      return runAsync("isComplete", new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return _job.isComplete();
        }
      });
    }

    @Override
    boolean isSuccessful() throws IOException {
      return runAsync("isSuccessful", new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          return _job.isSuccessful();
        }
      });
    }

    @Override
    void killJob() throws IOException {
      runAsync("killJob", new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          _job.killJob();
          return null;
        }
      });
    }

    private <T> T runAsync(String taskName, Callable<T> task) throws IOException {
      Future<T> future = _es.submit(task);
      try {
        long start = System.currentTimeMillis();
        DBG("Executing job.", taskName, "(); id=", _jobId);
        T result = future.get(_timeoutSeconds, TimeUnit.SECONDS);
        DBG("Operation job.", taskName, "() took ", (System.currentTimeMillis() - start), "ms");
        return result;
      } catch (TimeoutException ex) {
        throw new RuntimeException("Operation " + taskName + " was not able to complete in " + _timeoutSeconds + "s.", ex);
      } catch (Exception e) {
        throw new IOException("Operation " + taskName + " failed", e);
      }
    }
  }

  private static void DBG(Object... objs) {
    if (! driverDebug) return;
    StringBuilder sb = new StringBuilder("DBG: ");
    for( Object o : objs ) sb.append(o);
    System.out.println(sb.toString());
  }

  private static void quickTest() throws Exception {
    byte[] byteArr = readBinaryFile("/Users/tomk/h2o.jks");
    String payload = convertByteArrToString(byteArr);
    byte[] byteArr2 = convertStringToByteArr(payload);

    assert (byteArr.length == byteArr2.length);
    for (int i = 0; i < byteArr.length; i++) {
      assert byteArr[i] == byteArr2[i];
    }

    writeBinaryFile("/Users/tomk/test.jks", byteArr2);
    System.exit(0);
  }

  /**
   * Main entry point
   * @param args Full program args, including those that go to ToolRunner.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    // quickTest();

    int exitCode = ToolRunner.run(new h2odriver(), args);
    maybePrintYarnLogsMessage();
    System.exit(exitCode);
  }
}
