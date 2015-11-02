package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

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
  static {
    String javaVersionString = System.getProperty("java.version");
    Pattern p = Pattern.compile("1\\.([0-9]*)(.*)");
    Matcher m = p.matcher(javaVersionString);
    boolean b = m.matches();
    if (! b) {
      System.out.println("Could not parse java version: " + javaVersionString);
      System.exit(1);
    }
    javaMajorVersion = Integer.parseInt(m.group(1));
  }

  final static int DEFAULT_CLOUD_FORMATION_TIMEOUT_SECONDS = 120;
  final static int CLOUD_FORMATION_SETTLE_DOWN_SECONDS = 2;
  final static int DEFAULT_EXTRA_MEM_PERCENT = 10;

  // Options that are parsed by the main thread before other threads are created.
  static final int javaMajorVersion;
  static String jobtrackerName = null;
  static int numNodes = -1;
  static String outputPath = null;
  static String mapperXmx = null;
  static int extraMemPercent = -1;            // Between 0 and 10, typically.  Cannot be negative.
  static String mapperPermSize = null;
  static String driverCallbackIp = null;
  static int driverCallbackPort = 0;          // By default, let the system pick the port.
  static String network = null;
  static boolean disown = false;
  static String clusterReadyFileName = null;
  static String hadoopJobId = "";
  static String applicationId = "";
  static int cloudFormationTimeoutSeconds = DEFAULT_CLOUD_FORMATION_TIMEOUT_SECONDS;
  static int nthreads = -1;
  static int basePort = -1;
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
  static boolean hashLogin = false;
  static boolean ldapLogin = false;
  static String loginConfFileName = null;
  static String userName = System.getProperty("user.name");

  // Runtime state that might be touched by different threads.
  volatile ServerSocket driverCallbackSocket = null;
  volatile Job job = null;
  volatile CtrlCHandler ctrlc = null;
  volatile boolean clusterIsUp = false;
  volatile boolean clusterFailedToComeUp = false;
  volatile boolean clusterHasNodeWithLocalhostIp = false;
  volatile boolean shutdownRequested = false;
  volatile AtomicInteger numNodesStarted = new AtomicInteger();
  volatile AtomicInteger numNodesReportingFullCloudSize = new AtomicInteger();
  volatile String clusterIp = null;
  volatile int clusterPort = -1;

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

  public String getClusterUrl() {
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

  public static class H2ORecordReader extends RecordReader<Text, Text> {
    H2ORecordReader() {
    }

    public void initialize(InputSplit split, TaskAttemptContext context) {
    }

    public boolean nextKeyValue() throws IOException {
      return false;
    }

    public Text getCurrentKey() { return null; }
    public Text getCurrentValue() { return null; }
    public void close() throws IOException { }
    public float getProgress() throws IOException { return 0; }
  }

  public static class EmptySplit extends InputSplit implements Writable {
    public void write(DataOutput out) throws IOException { }
    public void readFields(DataInput in) throws IOException { }
    public long getLength() { return 0L; }
    public String[] getLocations() { return new String[0]; }
  }

  public static class H2OInputFormat extends InputFormat<Text, Text> {
    H2OInputFormat() {
    }

    public List<InputSplit> getSplits(JobContext context) throws IOException, InterruptedException {
      List<InputSplit> ret = new ArrayList<InputSplit>();
      int numSplits = numNodes;
      for (int i = 0; i < numSplits; ++i) {
        ret.add(new EmptySplit());
      }
      return ret;
    }

    public RecordReader<Text, Text> createRecordReader(
            InputSplit ignored, TaskAttemptContext taskContext)
            throws IOException {
      return(new H2ORecordReader());
    }
  }

  public static void killJobAndWait(Job job) {
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

  /**
   * Read and handle one Mapper->Driver Callback message.
   */
  class CallbackHandlerThread extends Thread {
    private Socket _s;
    private CallbackManager _cm;

    private void createClusterReadyFile(String ip, int port) throws Exception {
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
          System.out.println("H2O node " + msg.getEmbeddedWebServerIp() + ":" + msg.getEmbeddedWebServerPort() + " reports H2O cluster size " + msg.getCloudSize());
          if (msg.getCloudSize() == numNodes) {
            // Do this under a synchronized block to avoid getting multiple cluster ready notification files.
            synchronized (h2odriver.class) {
              if (! clusterIsUp) {
                int n = numNodesReportingFullCloudSize.incrementAndGet();
                if (n == numNodes) {
                  if (clusterReadyFileName != null) {
                    createClusterReadyFile(msg.getEmbeddedWebServerIp(), msg.getEmbeddedWebServerPort());
                    System.out.println("Cluster notification file (" + clusterReadyFileName + ") created.");
                  }
                  setClusterIpPort(msg.getEmbeddedWebServerIp(), msg.getEmbeddedWebServerPort());
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
                    "          [-h | -help]\n" +
                    "          [-jobname <name of job in jobtracker (defaults to: 'H2O_nnnnn')>]\n" +
                    "              (Note nnnnn is chosen randomly to produce a unique name)\n" +
                    "          [-driverif <ip address of mapper->driver callback interface>]\n" +
                    "          [-driverport <port of mapper->driver callback interface>]\n" +
                    "          [-network <IPv4network1Specification>[,<IPv4network2Specification> ...]\n" +
                    "          [-timeout <seconds>]\n" +
                    "          [-disown]\n" +
                    "          [-notify <notification file name>]\n" +
                    "          -mapperXmx <per mapper Java Xmx heap size>\n" +
                    "          [-extramempercent <0 to 20>]\n" +
                    "          -n | -nodes <number of H2O nodes (i.e. mappers) to create>\n" +
                    "          [-nthreads <maximum typical worker threads, i.e. cpus to use>]\n" +
                    "          [-baseport <starting HTTP port for H2O nodes; default is 54321>]\n" +
                    "          [-flow_dir <server side directory or hdfs directory>]\n " +
                    "          [-ea]\n" +
                    "          [-verbose:gc]\n" +
                    "          [-XX:+PrintGCDetails]\n" +
                    "          [-license <license file name (local filesystem, not hdfs)>]\n" +
                    "          -o | -output <hdfs output dir>\n" +
                    "\n" +
                    "Notes:\n" +
                    "          o  Each H2O node runs as a mapper.\n" +
                    "          o  Only one mapper may be run per host.\n" +
                    "          o  There are no combiners or reducers.\n" +
                    "          o  Each H2O cluster should have a unique jobname.\n" +
                    "          o  -mapperXmx, -nodes and -output are required.\n" +
                    "\n" +
                    "          o  -mapperXmx is set to both Xms and Xmx of the mapper to reserve\n" +
                    "             memory up front.\n" +
                    "          o  -extramempercent is a percentage of mapperXmx.  (Default: " + DEFAULT_EXTRA_MEM_PERCENT + ")\n" +
                    "             Extra memory for internal JVM use outside of Java heap.\n" +
                    "                 mapreduce.map.memory.mb = mapperXmx * (1 + extramempercent/100)\n" +
                    "          o  -libjars with an h2o.jar is required.\n" +
                    "          o  -driverif and -driverport let the user optionally specify the\n" +
                    "             network interface and port (on the driver host) for callback\n" +
                    "             messages from the mapper to the driver.\n" +
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
                    "          o  All mappers must start before the H2O cloud is considered up.\n" +
                    "\n" +
                    "Examples:\n" +
                    "          hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g -output hdfsOutputDir\n" +
                    "          hadoop jar h2odriver.jar -nodes 1 -mapperXmx 6g -notify notify.txt -disown -output hdfsOutputDir\n" +
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
  void parseArgs(String[] args) {
    int i = 0;
    while (true) {
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
      else if (s.equals("-baseport")) {
        i++; if (i >= args.length) { usage(); }
        basePort = Integer.parseInt(args[i]);
        if ((basePort < 0) || (basePort > 65535)) {
            error("Base port must be between 1 and 65535");
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
      else if (s.equals("-verbose:gc")) {
        enableVerboseGC = true;
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
      }
      else if (s.equals("-XX:+PrintGCDetails")) {
        enablePrintGCDetails = true;
      }
      else if (s.equals("-XX:+PrintGCTimeStamps")) {
        enablePrintGCTimeStamps = true;
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
      else if (s.equals("-hash_login")) {
        hashLogin = true;
      }
      else if (s.equals("-ldap_login")) {
        ldapLogin = true;
      }
      else if (s.equals("-login_conf")) {
        i++; if (i >= args.length) { usage(); }
        loginConfFileName = args[i];
      }
      else if (s.equals("-user_name")) {
        i++; if (i >= args.length) { usage(); }
        userName = args[i];
      }
      else {
        error("Unrecognized option " + s);
      }

      i++;
    }
  }

  void validateArgs() {
    // Check for mandatory arguments.
    if (numNodes < 1) {
      error("Number of H2O nodes must be greater than 0 (must specify -n)");
    }
    if (outputPath == null) {
      error("Missing required option -output");
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
    long startMillis = System.currentTimeMillis();
    while (true) {
      if (clusterFailedToComeUp) {
        System.out.println("ERROR: At least one node failed to come up during cluster formation");
        killJobAndWait(job);
        return 4;
      }

      if (job.isComplete()) {
        return CLUSTER_ERROR_JOB_COMPLETED_TOO_EARLY;
      }

      if (clusterIsUp) {
        break;
      }

      long nowMillis = System.currentTimeMillis();
      long deltaMillis = nowMillis - startMillis;
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
    String payload = "";
    try {
      byte[] byteArr = readBinaryFile(payloadFileName);
      payload = convertByteArrToString(byteArr);
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

    conf.set(h2omapper.H2O_MAPPER_CONF_ARG_BASE + Integer.toString(mapperConfLength), name);
    conf.set(h2omapper.H2O_MAPPER_CONF_BASENAME_BASE + Integer.toString(mapperConfLength), value);
    conf.set(h2omapper.H2O_MAPPER_CONF_PAYLOAD_BASE + Integer.toString(mapperConfLength), payload);
    mapperConfLength++;
  }

  private int run2(String[] args) throws Exception {
    // Arguments that get their default value set based on runtime info.
    // -----------------------------------------------------------------

    // PermSize
    // Java 7 and below need a larger PermSize for H2O.
    // Java 8 no longer has PermSize, but rather MetaSpace, which does not need to be set at all.
    if (javaMajorVersion <= 7) {
      mapperPermSize = "256m";
    }

    // Parse arguments.
    // ----------------
    parseArgs (args);
    validateArgs();

    // Set up callback address and port.
    // ---------------------------------
    if (driverCallbackIp == null) {
      driverCallbackIp = calcMyIp();
    }
    driverCallbackSocket = new ServerSocket();
    driverCallbackSocket.setReuseAddress(true);
    InetSocketAddress sa = new InetSocketAddress(driverCallbackIp, driverCallbackPort);
    driverCallbackSocket.bind(sa, driverCallbackPort);
    int actualDriverCallbackPort = driverCallbackSocket.getLocalPort();
    CallbackManager cm = new CallbackManager();
    cm.setServerSocket(driverCallbackSocket);
    cm.start();
    System.out.println("Using mapper->driver callback IP address and port: " + driverCallbackIp + ":" + actualDriverCallbackPort);
    System.out.println("(You can override these with -driverif and -driverport.)");

    // Set up configuration.
    // ---------------------
    Configuration conf = getConf();

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
      long jvmInternalMemoryMegabytes = (long) ((double)megabytes * ((double)extraMemPercent)/100.0);
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
              .append((enableVerboseGC ? " -verbose:gc" : ""))
              .append((enablePrintGCDetails ? " -XX:+PrintGCDetails" : ""))
              .append((enablePrintGCTimeStamps ? " -XX:+PrintGCTimeStamps" : ""))
              .append((enableVerboseClass ? " -verbose:class" : ""))
              .append((enablePrintCompilation ? " -XX:+PrintCompilation" : ""))
              .append((enableExcludeMethods ? " -XX:CompileCommand=exclude,water/fvec/NewChunk.append2slowd" : ""))
              .append((enableLog4jDefaultInitOverride ? " -Dlog4j.defaultInitOverride=true" : ""))
              .append((enableDebug ? " -agentlib:jdwp=transport=dt_socket,server=y,suspend=" + (enableSuspend ? "y" : "n") + ",address=" + debugPort : ""))
              ;
      for (String s : extraJvmArguments) {
        sb.append(" ").append(s);
      }

      String mapChildJavaOpts = sb.toString();

      conf.set("mapreduce.map.java.opts", mapChildJavaOpts);
      if (! usingYarn()) {
        conf.set("mapred.child.java.opts", mapChildJavaOpts);
        conf.set("mapred.map.child.java.opts", mapChildJavaOpts);       // MapR 2.x requires this.
      }

      System.out.println("Memory Settings:");
      System.out.println("    mapreduce.map.java.opts:     " + mapChildJavaOpts);
      System.out.println("    Extra memory percent:        " + extraMemPercent);
      System.out.println("    mapreduce.map.memory.mb:     " + mapreduceMapMemoryMb);
    }

    conf.set("mapreduce.client.genericoptionsparser.used", "true");
    if (! usingYarn()) {
      conf.set("mapred.used.genericoptionsparser", "true");
    }

    conf.set("mapreduce.map.speculative", "false");
    if (! usingYarn()) {
      conf.set("mapred.map.tasks.speculative.execution", "false");
    }

    conf.set("mapreduce.map.maxattempts", "1");
    if (! usingYarn()) {
      conf.set("mapred.map.max.attempts", "1");
    }

    conf.set("mapreduce.job.jvm.numtasks", "1");
    if (! usingYarn()) {
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
    if (basePort >= 0) {
      addMapperArg(conf, "-baseport", Integer.toString(basePort));
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
    if((new File(".h2o_no_collect")).exists() || (new File(System.getProperty("user.home")+"/.h2o_no_collect")).exists()) {
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
    addMapperArg(conf, "-user_name", userName);

    for (String s : extraArguments) {
      addMapperArg(conf, s);
    }

    conf.set(h2omapper.H2O_MAPPER_ARGS_LENGTH, Integer.toString(mapperArgsLength));

    // Config files.
    if (jksFileName != null) {
      addMapperConf(conf, "-jks", "h2o.jks", jksFileName);
    }
    if (loginConfFileName != null) {
      addMapperConf(conf, "-login_conf", "login.conf", loginConfFileName);
    }

    conf.set(h2omapper.H2O_MAPPER_CONF_LENGTH, Integer.toString(mapperConfLength));

    // Set up job stuff.
    // -----------------
    job = new Job(conf, jobtrackerName);
    job.setJarByClass(getClass());
    job.setInputFormatClass(H2OInputFormat.class);
    job.setMapperClass(h2omapper.class);
    job.setNumReduceTasks(0);
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(Text.class);

    FileInputFormat.addInputPath(job, new Path("ignored"));
    if (outputPath != null) {
      FileOutputFormat.setOutputPath(job, new Path(outputPath));
    }

    // Run job.  We are running a zero combiner and zero reducer configuration.
    // ------------------------------------------------------------------------
    job.submit();
    System.out.println("Job name '" + jobtrackerName + "' submitted");
    System.out.println("JobTracker job ID is '" + job.getJobID() + "'");
    hadoopJobId = job.getJobID().toString();
    applicationId = hadoopJobId.replace("job_", "application_");
    maybePrintYarnLogsMessage(false);

    // Register ctrl-c handler to try to clean up job when possible.
    ctrlc = new CtrlCHandler();
    Runtime.getRuntime().addShutdownHook(ctrlc);

    System.out.printf("Waiting for H2O cluster to come up...\n");
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

      System.out.println("Open H2O Flow in your web browser: " + getClusterUrl());
      System.out.println("Disowning cluster and exiting.");
      Runtime.getRuntime().removeShutdownHook(ctrlc);
      return 0;
    }

    System.out.println("(Note: Use the -disown option to exit the driver after cluster formation)");
    System.out.println("");
    System.out.println("Open H2O Flow in your web browser: " + getClusterUrl());
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
