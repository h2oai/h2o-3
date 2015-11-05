package water;

import com.brsanthu.googleanalytics.DefaultRequest;
import com.brsanthu.googleanalytics.GoogleAnalytics;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinWorkerThread;

import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.reflections.Reflections;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import hex.ModelBuilder;
import water.UDPRebooted.ShutdownTsk;
import water.api.ModelCacheManager;
import water.api.RequestServer;
import water.exceptions.H2OFailException;
import water.exceptions.H2OIllegalArgumentException;
import water.init.AbstractBuildVersion;
import water.init.AbstractEmbeddedH2OConfig;
import water.init.JarHash;
import water.init.NetworkInit;
import water.init.NodePersistentStorage;
import water.nbhm.NonBlockingHashMap;
import water.persist.PersistManager;
import water.util.GAUtils;
import water.util.Log;
import water.util.OSUtils;
import water.util.PrettyPrint;

/**
* Start point for creating or joining an <code>H2O</code> Cloud.
*
* @author <a href="mailto:cliffc@h2o.ai"></a>
* @version 1.0
*/
final public class H2O {
  //-------------------------------------------------------------------------------------------------------------------
  // Command-line argument parsing and help
  //-------------------------------------------------------------------------------------------------------------------


  /**
   * Print help about command line arguments.
   */
  public static void printHelp() {
    String defaultFlowDirMessage;
    if (DEFAULT_FLOW_DIR() == null) {
      // If you start h2o on Hadoop, you must set -flow_dir.
      // H2O doesn't know how to guess a good one.
      // user.home doesn't make sense.
      defaultFlowDirMessage =
      "          (The default is none; saving flows not available.)\n";
    }
    else {
      defaultFlowDirMessage =
      "          (The default is '" + DEFAULT_FLOW_DIR() + "'.)\n";
    }

    String s =
            "\n" +
            "Usage:  java [-Xmx<size>] -jar h2o.jar [options]\n" +
            "        (Note that every option has a default and is optional.)\n" +
            "\n" +
            "    -h | -help\n" +
            "          Print this help.\n" +
            "\n" +
            "    -version\n" +
            "          Print version info and exit.\n" +
            "\n" +
            "    -name <h2oCloudName>\n" +
            "          Cloud name used for discovery of other nodes.\n" +
            "          Nodes with the same cloud name will form an H2O cloud\n" +
            "          (also known as an H2O cluster).\n" +
            "\n" +
            "    -flatfile <flatFileName>\n" +
            "          Configuration file explicitly listing H2O cloud node members.\n" +
            "\n" +
            "    -ip <ipAddressOfNode>\n" +
            "          IP address of this node.\n" +
            "\n" +
            "    -port <port>\n" +
            "          Port number for this node (note: port+1 is also used).\n" +
            "          (The default port is " + ARGS.port + ".)\n" +
            "\n" +
            "    -network <IPv4network1Specification>[,<IPv4network2Specification> ...]\n" +
            "          The IP address discovery code will bind to the first interface\n" +
            "          that matches one of the networks in the comma-separated list.\n" +
            "          Use instead of -ip when a broad range of addresses is legal.\n" +
            "          (Example network specification: '10.1.2.0/24' allows 256 legal\n" +
            "          possibilities.)\n" +
            "\n" +
            "    -ice_root <fileSystemPath>\n" +
            "          The directory where H2O spills temporary data to disk.\n" +
            "\n" +
            "    -log_dir <fileSystemPath>\n" +
            "          The directory where H2O writes logs to disk.\n" +
            "          (This usually has a good default that you need not change.)\n" +
            "\n" +
            "    -log_level <TRACE,DEBUG,INFO,WARN,ERRR,FATAL>\n" +
            "          Write messages at this logging level, or above.  Default is INFO." +
            "\n" +
            "\n" +
            "    -flow_dir <server side directory or HDFS directory>\n" +
            "          The directory where H2O stores saved flows.\n" +
            defaultFlowDirMessage +
            "\n" +
            "    -nthreads <#threads>\n" +
            "          Maximum number of threads in the low priority batch-work queue.\n" +
            "          (The default is 99.)\n" +
            "\n" +
            "    -client\n" +
            "          Launch H2O node in client mode.\n" +
            "\n" +
            "Cloud formation behavior:\n" +
            "\n" +
            "    New H2O nodes join together to form a cloud at startup time.\n" +
            "    Once a cloud is given work to perform, it locks out new members\n" +
            "    from joining.\n" +
            "\n" +
            "Examples:\n" +
            "\n" +
            "    Start an H2O node with 4GB of memory and a default cloud name:\n" +
            "        $ java -Xmx4g -jar h2o.jar\n" +
            "\n" +
            "    Start an H2O node with 6GB of memory and a specify the cloud name:\n" +
            "        $ java -Xmx6g -jar h2o.jar -name MyCloud\n" +
            "\n" +
            "    Start an H2O cloud with three 2GB nodes and a default cloud name:\n" +
            "        $ java -Xmx2g -jar h2o.jar &\n" +
            "        $ java -Xmx2g -jar h2o.jar &\n" +
            "        $ java -Xmx2g -jar h2o.jar &\n" +
            "\n";

    System.out.print(s);

    for (AbstractH2OExtension e : H2O.getExtensions()) {
      e.printHelp();
    }
  }

  /**
   * Singleton ARGS instance that contains the processed arguments.
   */
  public static final OptArgs ARGS = new OptArgs();

  /**
   * A class containing all of the arguments for H2O.
   */
  public static class
    OptArgs {
    //-----------------------------------------------------------------------------------
    // Help and info
    //-----------------------------------------------------------------------------------
    /** -help, -help=true; print help and exit*/
    public boolean help = false;

    /** -version, -version=true; print version and exit */
    public boolean version = false;

    //-----------------------------------------------------------------------------------
    // Clouding
    //-----------------------------------------------------------------------------------
    /** -name=name; Set cloud name */
    public String name = System.getProperty("user.name"); // Cloud name

    /** -flatfile=flatfile; Specify a list of cluster IP addresses */
    public String flatfile;

    /** -port=####; Specific Browser/API/HTML port */
    public int port;

    /** -baseport=####; Port to start upward searching from. */
    public int baseport = 54321;

    /** -port=ip4_or_ip6; Named IP4/IP6 address instead of the default */
    public String ip;

    /** -network=network; Network specification for acceptable interfaces to bind to */
    public String network;

    /** -client, -client=true; Client-only; no work; no homing of Keys (but can cache) */
    public boolean client;

    /** -user_name=user_name; Set user name */
    public String user_name = System.getProperty("user.name");

    //-----------------------------------------------------------------------------------
    // Node configuration
    //-----------------------------------------------------------------------------------
    /** -ice_root=ice_root; ice root directory; where temp files go */
    public String ice_root;

    /** -nthreads=nthreads; Max number of F/J threads in the low-priority batch queue */
    public int nthreads=Runtime.getRuntime().availableProcessors();

    /** -log_dir=/path/to/dir; directory to save logs in */
    public String log_dir;

    /** -flow_dir=/path/to/dir; directory to save flows in */
    public String flow_dir;

    /** -disable_web; disable web API port (used by Sparkling Water) */
    public boolean disable_web = false;

    //-----------------------------------------------------------------------------------
    // HDFS & AWS
    //-----------------------------------------------------------------------------------
    /** -hdfs_config=hdfs_config; configuration file of the HDFS */
    public String hdfs_config = null;

    /** -hdfs_skip=hdfs_skip; used by Hadoop driver to not unpack and load any HDFS jar file at runtime. */
    public boolean hdfs_skip = false;

    /** -aws_credentials=aws_credentials; properties file for aws credentials */
    public String aws_credentials = null;

    /** --ga_hadoop_ver=ga_hadoop_ver; Version string for Hadoop */
    public String ga_hadoop_ver = null;

    /** --ga_opt_out; Turns off usage reporting to Google Analytics  */
    public boolean ga_opt_out = false;

    //-----------------------------------------------------------------------------------
    // Debugging
    //-----------------------------------------------------------------------------------
    /** -log_level=log_level; One of DEBUG, INFO, WARN, ERRR.  Default is INFO. */
    public String log_level;

    /** -random_udp_drop, -random_udp_drop=true; test only, randomly drop udp incoming */
    public boolean random_udp_drop;

    /** -md5skip, -md5skip=true; test-only; Skip the MD5 Jar checksum; allows jars from different builds to mingle in the same cloud */
    public boolean md5skip = false;

    /** -quiet Enable quiet mode and avoid any prints to console, useful for client embedding */
    public boolean quiet = false;

    /** -beta, -experimental */
    public ModelBuilder.BuilderVisibility model_builders_visibility = ModelBuilder.BuilderVisibility.Stable;
    public boolean useUDP = false;

    @Override public String toString() {
      StringBuilder result = new StringBuilder();

      //determine fields declared in this class only (no fields of superclass)
      Field[] fields = this.getClass().getDeclaredFields();

      //print field names paired with their values
      result.append("[ ");
      for (Field field : fields) {
        try {
          result.append(field.getName());
          result.append(": ");
          //requires access to private field:
          result.append(field.get(this));
          result.append(", ");
        }
        catch (IllegalAccessException ex) {
          Log.err(ex);
        }
      }
      result.deleteCharAt(result.length() - 2);
      result.deleteCharAt(result.length() - 1);
      result.append(" ]");

      return result.toString();
    }
  }

  public static void parseFailed(String message) {
    System.out.println("");
    System.out.println("ERROR: " + message);
    System.out.println("");
    printHelp();
    H2O.exit(1);
  }

  public static class OptString {
    String _s;
    String _lastMatchedFor;

    public OptString(String s) {
      _s = s;
    }

    public boolean matches(String s) {
      boolean result = false;

      _lastMatchedFor = s;

      String candidate;
      candidate = "-" + s;
      if (_s.equals(candidate)) {
        result = true;
      }

      candidate = "--" + s;
      if (_s.equals(candidate)) {
        result = true;
      }

      return result;
    }

    public int incrementAndCheck(int i, String[] args) {
      i = i + 1;
      if (i >= args.length) {
        parseFailed(_lastMatchedFor + " not specified");
      }
      return i;
    }

    public int parseInt(String a) {
      int result = 0;

      try {
        result = Integer.parseInt(a);
      }
      catch (Exception e) {
        parseFailed("Argument " + _lastMatchedFor + " must be an integer (was given '" + a + "')" );
      }

      return result;
    }

    public String toString() { return _s; }
  }


  /**
   * Dead stupid argument parser.
   */
  private static void parseArguments(String[] args) {
    for (AbstractH2OExtension e : H2O.getExtensions()) {
      args = e.parseArguments(args);
    }

    for (int i = 0; i < args.length; i++) {
      OptString s = new OptString(args[i]);
      if (s.matches("h") || s.matches("help")) {
        ARGS.help = true;
      }
      else if (s.matches("version")) {
        ARGS.version = true;
      }
      else if (s.matches("name")) {
        i = s.incrementAndCheck(i, args);
        ARGS.name = args[i];
      }
      else if (s.matches("flatfile")) {
        i = s.incrementAndCheck(i, args);
        ARGS.flatfile = args[i];
      }
      else if (s.matches("port")) {
        i = s.incrementAndCheck(i, args);
        ARGS.port = s.parseInt(args[i]);
      }
      else if (s.matches("baseport")) {
        i = s.incrementAndCheck(i, args);
        ARGS.baseport = s.parseInt(args[i]);
      }
      else if (s.matches("ip")) {
        i = s.incrementAndCheck(i, args);
        ARGS.ip = args[i];
      }
      else if (s.matches("network")) {
        i = s.incrementAndCheck(i, args);
        ARGS.network = args[i];
      }
      else if (s.matches("client")) {
        ARGS.client = true;
      }
      else if (s.matches("user_name")) {
        i = s.incrementAndCheck(i, args);
        ARGS.user_name = args[i];
      }
      else if (s.matches("ice_root")) {
        i = s.incrementAndCheck(i, args);
        ARGS.ice_root = args[i];
      }
      else if (s.matches("log_dir")) {
        i = s.incrementAndCheck(i, args);
        ARGS.log_dir = args[i];
      }
      else if (s.matches("flow_dir")) {
        i = s.incrementAndCheck(i, args);
        ARGS.flow_dir = args[i];
      }
      else if (s.matches("disable_web")) {
        ARGS.disable_web = true;
      }
      else if (s.matches("nthreads")) {
        i = s.incrementAndCheck(i, args);
        ARGS.nthreads = s.parseInt(args[i]);
      }
      else if (s.matches("hdfs_config")) {
        i = s.incrementAndCheck(i, args);
        ARGS.hdfs_config = args[i];
      }
      else if (s.matches("hdfs_skip")) {
        ARGS.hdfs_skip = true;
      }
      else if (s.matches("aws_credentials")) {
        i = s.incrementAndCheck(i, args);
        ARGS.aws_credentials = args[i];
      }
      else if (s.matches("ga_hadoop_ver")) {
        i = s.incrementAndCheck(i, args);
        ARGS.ga_hadoop_ver = args[i];
      }
      else if (s.matches("ga_opt_out")) {
        // JUnits pass this as a system property, but it usually a flag without an arg
        if (i+1 < args.length && args[i+1].equals("yes")) i++;
        ARGS.ga_opt_out = true;
      }
      else if (s.matches("log_level")) {
        i = s.incrementAndCheck(i, args);
        ARGS.log_level = args[i];
      }
      else if (s.matches("random_udp_drop")) {
        ARGS.random_udp_drop = true;
      }
      else if (s.matches("md5skip")) {
        ARGS.md5skip = true;
      }
      else if (s.matches("quiet")) {
        ARGS.quiet = true;
      }
      else if (s.matches("beta")) {
        ARGS.model_builders_visibility = ModelBuilder.BuilderVisibility.Beta;
      }
      else if (s.matches("experimental")) {
        ARGS.model_builders_visibility = ModelBuilder.BuilderVisibility.Experimental;
      } else if(s.matches("useUDP")) {
          ARGS.useUDP = true;
      } else {
        parseFailed("Unknown argument (" + s + ")");
      }
    }
  }

  // Model cache manager
  public static ModelCacheManager getMCM() { return new ModelCacheManager(); }

  // Google analytics performance measurement
  public static GoogleAnalytics GA;
  public static int CLIENT_TYPE_GA_CUST_DIM = 1;
  public static int CLIENT_ID_GA_CUST_DIM = 2;

  //-------------------------------------------------------------------------------------------------------------------
  // Embedded configuration for a full H2O node to be implanted in another
  // piece of software (e.g. Hadoop mapper task).
  //-------------------------------------------------------------------------------------------------------------------

  public static volatile AbstractEmbeddedH2OConfig embeddedH2OConfig;

  /**
   * Register embedded H2O configuration object with H2O instance.
   */
  public static void setEmbeddedH2OConfig(AbstractEmbeddedH2OConfig c) { embeddedH2OConfig = c; }
  public static AbstractEmbeddedH2OConfig getEmbeddedH2OConfig() { return embeddedH2OConfig; }

  /**
   * Tell the embedding software that this H2O instance belongs to
   * a cloud of a certain size.
   * This may be non-blocking.
   *
   * @param ip IP address this H2O can be reached at.
   * @param port Port this H2O can be reached at (for REST API and browser).
   * @param size Number of H2O instances in the cloud.
   */
  public static void notifyAboutCloudSize(InetAddress ip, int port, int size) {
    if (embeddedH2OConfig == null) { return; }
    embeddedH2OConfig.notifyAboutCloudSize(ip, port, size);
  }


  public static void closeAll() {
    try { NetworkInit._udpSocket.close(); } catch( IOException ignore ) { }
    try { H2O.getJetty().stop(); } catch( Exception ignore ) { }
    try { NetworkInit._tcpSocketBig.close(); } catch( IOException ignore ) { }
    if(!H2O.ARGS.useUDP)
      try { NetworkInit._tcpSocketSmall.close(); } catch( IOException ignore ) { }
    PersistManager PM = H2O.getPM();
    if( PM != null ) PM.getIce().cleanUp();
  }


  /** Notify embedding software instance H2O wants to exit.  Shuts down a single Node.
   *  @param status H2O's requested process exit value.
   */
  public static void exit(int status) {
    // Embedded H2O path (e.g. inside Hadoop mapper task).
    if( embeddedH2OConfig != null )
      embeddedH2OConfig.exit(status);

    // Standalone H2O path,p or if the embedded config does not exit
    System.exit(status);
  }

  /** Cluster shutdown itself by sending a shutdown UDP packet. */
  public static void shutdown(int status) {
    if(status == 0) H2O.orderlyShutdown();
    UDPRebooted.T.error.send(H2O.SELF);
    H2O.exit(status);
  }

  public static int orderlyShutdown() {
    return orderlyShutdown(-1);
  }
  public static int orderlyShutdown(int timeout) {
    boolean [] confirmations = new boolean[H2O.CLOUD.size()];
    if (H2O.SELF.index() >= 0) { // Do not wait for clients to shutdown
      confirmations[H2O.SELF.index()] = true;
    }
    Futures fs = new Futures();
    for(H2ONode n:H2O.CLOUD._memary) {
      if(n != H2O.SELF)
        fs.add(new RPC(n, new ShutdownTsk(H2O.SELF,n.index(), 1000, confirmations)).call());
    }
    if(timeout > 0)
      try { Thread.sleep(timeout); }
      catch (Exception ignore) {}
    else fs.blockForPending(); // todo, should really have block for pending with a timeout

    int failedToShutdown = 0;
    // shutdown failed
    for(boolean b:confirmations)
      if(!b) failedToShutdown++;
    return failedToShutdown;
  }
  private static volatile boolean _shutdownRequested = false;

  public static void requestShutdown() {
    _shutdownRequested = true;
  }

  public static boolean getShutdownRequested() {
    return _shutdownRequested;
  }

  //-------------------------------------------------------------------------------------------------------------------

  public static final AbstractBuildVersion ABV;
  static {
    AbstractBuildVersion abv = AbstractBuildVersion.UNKNOWN_VERSION;
    try {
      Class klass = Class.forName("water.init.BuildVersion");
      java.lang.reflect.Constructor constructor = klass.getConstructor();
      abv = (AbstractBuildVersion) constructor.newInstance();
    } catch (Exception ignore) { }
    ABV = abv;
  }

  //-------------------------------------------------------------------------------------------------------------------

  private static boolean _haveInheritedLog4jConfiguration = false;
  public static boolean haveInheritedLog4jConfiguration() {
    return _haveInheritedLog4jConfiguration;
  }

  public static void configureLogging() {
    if (LogManager.getCurrentLoggers().hasMoreElements()) {
      _haveInheritedLog4jConfiguration = true;
      return;
    }

    // Disable logging from a few specific classes at startup.
    // (These classes may (or may not) be re-enabled later on.)
    //
    // The full logger initialization is done by setLog4jProperties() in class water.util.Log.
    // The trick is the output path / file isn't known until the H2O API PORT is chosen,
    // so real logger initialization has to happen somewhat late in the startup lifecycle.
    java.util.Properties p = new java.util.Properties();
    p.setProperty("log4j.logger.org.reflections.Reflections", "WARN");
    p.setProperty("log4j.logger.org.eclipse.jetty", "WARN");
    PropertyConfigurator.configure(p);
    System.setProperty("org.eclipse.jetty.LEVEL", "WARN");

    // Log jetty stuff to stdout for now.
    // TODO:  figure out how to wire this into log4j.
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StrErrLog");
  }

  //-------------------------------------------------------------------------------------------------------------------

  // Be paranoid and check that this doesn't happen twice.
  private static boolean extensionsRegistered = false;
  private static long registerExtensionsMillis = 0;

  /**
   * Register H2O extensions.
   * <p/>
   * Use reflection to find all classes that inherit from water.AbstractH2OExtension
   * and call H2O.addExtension() for each.
   */
  public static void registerExtensions() {
    if (extensionsRegistered) {
      throw H2O.fail("Extensions already registered");
    }

    long before = System.currentTimeMillis();

    // Disallow schemas whose parent is in another package because it takes ~4s to do the getSubTypesOf call.
    String[] packages = new String[]{"water", "hex"};

    for (String pkg : packages) {
      Reflections reflections = new Reflections(pkg);
      for (Class registerClass : reflections.getSubTypesOf(water.AbstractH2OExtension.class)) {
        if (!Modifier.isAbstract(registerClass.getModifiers())) {
          try {
            Object instance = registerClass.newInstance();
            water.AbstractH2OExtension e = (water.AbstractH2OExtension) instance;
            H2O.addExtension(e);
          } catch (Exception e) {
            throw H2O.fail(e.toString());
          }
        }
      }
    }

    for (AbstractH2OExtension e : H2O.getExtensions()) {
      e.init();
    }

    extensionsRegistered = true;

    registerExtensionsMillis = System.currentTimeMillis() - before;
  }

  private static ArrayList<AbstractH2OExtension> extensions = new ArrayList<>();

  public static void addExtension(AbstractH2OExtension e) {
    extensions.add(e);
  }

  public static ArrayList<AbstractH2OExtension> getExtensions() {
    return extensions;
  }

  //-------------------------------------------------------------------------------------------------------------------

  // Be paranoid and check that this doesn't happen twice.
  private static boolean apisRegistered = false;

  /**
   * Register REST API routes.
   *
   * Use reflection to find all classes that inherit from water.api.AbstractRegister
   * and call the register() method for each.
   *
   * @param relativeResourcePath Relative path from running process working dir to find web resources.
   */
  public static void registerRestApis(String relativeResourcePath) {
    if (apisRegistered) {
      throw H2O.fail("APIs already registered");
    }

    // Log extension registrations here so the message is grouped in the right spot.
    for (AbstractH2OExtension e : H2O.getExtensions()) {
      e.printInitialized();
    }
    Log.info("Registered " + H2O.getExtensions().size() + " extensions in: " + registerExtensionsMillis + "mS");

    long before = System.currentTimeMillis();

    // Disallow schemas whose parent is in another package because it takes ~4s to do the getSubTypesOf call.
    String[] packages = new String[] { "water", "hex" };

    for (String pkg : packages) {
      Reflections reflections = new Reflections(pkg);
      Log.debug("Registering REST APIs for package: " + pkg);
      for (Class registerClass : reflections.getSubTypesOf(water.api.AbstractRegister.class)) {
        if (!Modifier.isAbstract(registerClass.getModifiers())) {
          try {
            Log.debug("Found REST API registration for class: " + registerClass.getName());
            Object instance = registerClass.newInstance();
            water.api.AbstractRegister r = (water.api.AbstractRegister) instance;
            r.register(relativeResourcePath);
          }
          catch (Exception e) {
            throw H2O.fail(e.toString());
          }
        }
      }
    }

    apisRegistered = true;

    long registerApisMillis = System.currentTimeMillis() - before;
    Log.info("Registered: " + RequestServer.numRoutes() + " REST APIs in: " + registerApisMillis + "mS");
  }

  //-------------------------------------------------------------------------------------------------------------------

  public static class AboutEntry {
    private String name;
    private String value;

    public String getName() { return name; }
    public String getValue() { return value; }

    AboutEntry(String n, String v) {
      name = n;
      value = v;
    }
  }

  private static ArrayList<AboutEntry> aboutEntries = new ArrayList<>();

  @SuppressWarnings("unused")
  public static void addAboutEntry(String name, String value) {
    AboutEntry e = new AboutEntry(name, value);
    aboutEntries.add(e);
  }

  @SuppressWarnings("unused")
  public static ArrayList<AboutEntry> getAboutEntries() {
    return aboutEntries;
  }

  //-------------------------------------------------------------------------------------------------------------------

  private static AtomicLong nextModelNum = new AtomicLong(0);

  /**
   * Calculate a unique model id that includes User-Agent info (if it can be discovered).
   * For the user agent info to be discovered, this needs to be called from a Jetty thread.
   *
   * This lets us distinguish models created from R vs. other front-ends, for example.
   * At some future point, it could make sense to include a sessionId here.
   *
   * The algorithm is:
   *   descModel_[userAgentPrefixIfKnown_]cloudId_monotonicallyIncreasingInteger
   *
   * Right now because of the way the REST API works, a bunch of numbers are created and
   * thrown away.  So the values are monotonically increasing but not contiguous.
   *
   * @param desc Model description.
   * @return The suffix.
   */
  synchronized public static String calcNextUniqueModelId(String desc) {
    StringBuilder sb = new StringBuilder();
    sb.append(desc).append("_model_");

    // Append user agent string if we can figure it out.
    String source = JettyHTTPD.getUserAgent();
    if (source != null) {
      StringBuilder ua = new StringBuilder();

      if (source.contains("Safari")) {
        ua.append("safari");
      }
      else if (source.contains("Python")) {
        ua.append("python");
      }
      else {
        for (int i = 0; i < source.length(); i++) {
          char c = source.charAt(i);
          if (c >= 'a' && c <= 'z') {
            ua.append(c);
            continue;
          } else if (c >= 'A' && c <= 'Z') {
            ua.append(c);
            continue;
          }
          break;
        }
      }

      if (ua.toString().length() > 0) {
        sb.append(ua.toString()).append("_");
      }
    }

    // REST API needs some refactoring to avoid burning lots of extra numbers.
    //
    // I actually tried only doing the addAndGet only for POST requests (and junk UUID otherwise),
    // but that didn't eliminate the gaps.
    long n = nextModelNum.addAndGet(1);
    sb.append(Long.toString(CLUSTER_ID)).append("_").append(Long.toString(n));

    return sb.toString();
  }

  //-------------------------------------------------------------------------------------------------------------------

  // Atomically set once during startup.  Guards against repeated startups.
  public static final AtomicLong START_TIME_MILLIS = new AtomicLong(); // When did main() run

  // Used to gate default worker threadpool sizes
  public static final int NUMCPUS = Runtime.getRuntime().availableProcessors();
  // Best-guess process ID
  public static long PID = -1L;


  /**
   * Throw an exception that will cause the request to fail, but the cluster to continue.
   * @see #fail(String, Throwable)
   * @return never returns
   */
  public static H2OIllegalArgumentException unimpl() { return new H2OIllegalArgumentException("unimplemented"); }

  /**
   * Throw an exception that will cause the request to fail, but the cluster to continue.
   * @see #unimpl(String)
   * @see #fail(String, Throwable)
   * @return never returns
   */
  public static H2OIllegalArgumentException unimpl(String msg) { return new H2OIllegalArgumentException("unimplemented: " + msg); }

  /**
   * H2O.fail is intended to be used in code where something should never happen, and if
   * it does it's a coding error that needs to be addressed immediately.  Examples are:
   * AutoBuffer serialization for an object you're trying to serialize isn't available;
   * there's a typing error on your schema; your switch statement didn't cover all the AST
   * subclasses available in Rapids.
   * <p>
   * It should *not* be used when only the single request should fail, it should *only* be
   * used if the error means that someone needs to go add some code right away.
   *
   * @param msg Message to Log.fatal()
   * @param cause Optional cause exception to Log.fatal()
   * @return never returns; calls System.exit(-1)
   */
  public static H2OFailException fail(String msg, Throwable cause) {
    Log.fatal(msg);
    if (null != cause) Log.fatal(cause);
    Log.fatal("Stacktrace: ");
    Log.fatal(Arrays.toString(Thread.currentThread().getStackTrace()));

    H2O.shutdown(-1);

    // unreachable
    return new H2OFailException(msg);
  }

  /**
   * @see #fail(String, Throwable)
   * @return never returns
   */
  public static H2OFailException fail() { return H2O.fail("Unknown code failure"); }

  /**
   * @see #fail(String, Throwable)
   * @return never returns
   */
  public static H2OFailException fail(String msg) { return H2O.fail(msg, null); }

  /**
   * Return an error message with an accompanying URL to help the user get more detailed information.
   *
   * @param number H2O tech note number.
   * @param message Message to present to the user.
   * @return A longer message including a URL.
   */
  public static String technote(int number, String message) {
    StringBuffer sb = new StringBuffer()
            .append(message)
            .append("\n")
            .append("\n")
            .append("For more information visit:\n")
            .append("  http://jira.h2o.ai/browse/TN-").append(Integer.toString(number));

    return sb.toString();
  }

  /**
   * Return an error message with an accompanying list of URLs to help the user get more detailed information.
   *
   * @param numbers H2O tech note numbers.
   * @param message Message to present to the user.
   * @return A longer message including a list of URLs.
   */
  public static String technote(int[] numbers, String message) {
    StringBuffer sb = new StringBuffer()
            .append(message)
            .append("\n")
            .append("\n")
            .append("For more information visit:\n");

    for (int number : numbers) {
      sb.append("  http://jira.h2o.ai/browse/TN-").append(Integer.toString(number)).append("\n");
    }

    return sb.toString();
  }


  // --------------------------------------------------------------------------
  // The worker pools - F/J pools with different priorities.

  // These priorities are carefully ordered and asserted for... modify with
  // care.  The real problem here is that we can get into cyclic deadlock
  // unless we spawn a thread of priority "X+1" in order to allow progress
  // on a queue which might be flooded with a large number of "<=X" tasks.
  //
  // Example of deadlock: suppose TaskPutKey and the Invalidate ran at the same
  // priority on a 2-node cluster.  Both nodes flood their own queues with
  // writes to unique keys, which require invalidates to run on the other node.
  // Suppose the flooding depth exceeds the thread-limit (e.g. 99); then each
  // node might have all 99 worker threads blocked in TaskPutKey, awaiting
  // remote invalidates - but the other nodes' threads are also all blocked
  // awaiting invalidates!
  //
  // We fix this by being willing to always spawn a thread working on jobs at
  // priority X+1, and guaranteeing there are no jobs above MAX_PRIORITY -
  // i.e., jobs running at MAX_PRIORITY cannot block, and when those jobs are
  // done, the next lower level jobs get unblocked, etc.
  public static final byte        MAX_PRIORITY = Byte.MAX_VALUE-1;
  public static final byte    ACK_ACK_PRIORITY = MAX_PRIORITY-0; //126
  public static final byte  FETCH_ACK_PRIORITY = MAX_PRIORITY-1; //125
  public static final byte        ACK_PRIORITY = MAX_PRIORITY-2; //124
  public static final byte   DESERIAL_PRIORITY = MAX_PRIORITY-3; //123
  public static final byte INVALIDATE_PRIORITY = MAX_PRIORITY-3; //123
  public static final byte    GET_KEY_PRIORITY = MAX_PRIORITY-4; //122
  public static final byte    PUT_KEY_PRIORITY = MAX_PRIORITY-5; //121
  public static final byte     ATOMIC_PRIORITY = MAX_PRIORITY-6; //120
  public static final byte        GUI_PRIORITY = MAX_PRIORITY-7; //119
  public static final byte     MIN_HI_PRIORITY = MAX_PRIORITY-7; //119
  public static final byte        MIN_PRIORITY = 0;

  // F/J threads that remember the priority of the last task they started
  // working on.
  // made public for ddply
  public static class FJWThr extends ForkJoinWorkerThread {
    public int _priority;
    FJWThr(ForkJoinPool pool) {
      super(pool);
      _priority = ((PrioritizedForkJoinPool)pool)._priority;
      setPriority( _priority == Thread.MIN_PRIORITY
                   ? Thread.NORM_PRIORITY-1
                   : Thread. MAX_PRIORITY-1 );
      setName("FJ-"+_priority+"-"+getPoolIndex());
    }
  }
  // Factory for F/J threads, with cap's that vary with priority.
  static class FJWThrFact implements ForkJoinPool.ForkJoinWorkerThreadFactory {
    private final int _cap;
    FJWThrFact( int cap ) { _cap = cap; }
    @Override public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
      int cap = _cap==-1 ? 4 * NUMCPUS : _cap;
      return pool.getPoolSize() <= cap ? new FJWThr(pool) : null;
    }
  }

  // A standard FJ Pool, with an expected priority level.
  private static class PrioritizedForkJoinPool extends ForkJoinPool {
    final int _priority;
    private PrioritizedForkJoinPool(int p, int cap) {
      super((ARGS.nthreads <= 0) ? NUMCPUS : ARGS.nthreads,
            new FJWThrFact(cap),
            null,
            p<MIN_HI_PRIORITY);
      _priority = p;
    }
    private H2OCountedCompleter poll2() { return (H2OCountedCompleter)pollSubmission(); }
  }

  // Hi-priority work, sorted into individual queues per-priority.
  // Capped at a small number of threads per pool.
  private static final PrioritizedForkJoinPool FJPS[] = new PrioritizedForkJoinPool[MAX_PRIORITY+1];
  static {
    // Only need 1 thread for the AckAck work, as it cannot block
    FJPS[ACK_ACK_PRIORITY] = new PrioritizedForkJoinPool(ACK_ACK_PRIORITY,1);
    for( int i=MIN_HI_PRIORITY+1; i<MAX_PRIORITY; i++ )
      FJPS[i] = new PrioritizedForkJoinPool(i,4); // All CPUs, but no more for blocking purposes
    FJPS[GUI_PRIORITY] = new PrioritizedForkJoinPool(GUI_PRIORITY,2);
  }

  // Easy peeks at the FJ queues
  static int getWrkQueueSize  (int i) { return FJPS[i]==null ? -1 : FJPS[i].getQueuedSubmissionCount();}
  static int getWrkThrPoolSize(int i) { return FJPS[i]==null ? -1 : FJPS[i].getPoolSize();             }

  // Submit to the correct priority queue
  public static <T extends H2OCountedCompleter> T submitTask( T task ) {
    int priority = task.priority();
    assert MIN_PRIORITY <= priority && priority <= MAX_PRIORITY:"priority " + priority + " is out of range, expected range is < " + MIN_PRIORITY + "," + MAX_PRIORITY + ">";
    if( FJPS[priority]==null )
      synchronized( H2O.class ) { if( FJPS[priority] == null ) FJPS[priority] = new PrioritizedForkJoinPool(priority,-1); }
    FJPS[priority].submit(task);
    return task;
  }

  public static abstract class H2OFuture<T> implements Future<T> {
    public final T getResult(){
      try {
        return get();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Simple wrapper over F/J {@link CountedCompleter} to support priority
   *  queues.  F/J queues are simple unordered (and extremely light weight)
   *  queues.  However, we frequently need priorities to avoid deadlock and to
   *  promote efficient throughput (e.g. failure to respond quickly to {@link
   *  TaskGetKey} can block an entire node for lack of some small piece of
   *  data).  So each attempt to do lower-priority F/J work starts with an
   *  attempt to work and drain the higher-priority queues. */
  public static abstract class H2OCountedCompleter<T extends H2OCountedCompleter> extends CountedCompleter implements Cloneable, Freezable<T> {
    public final byte _priority;
    public H2OCountedCompleter( ) { this(false); }
    public H2OCountedCompleter( boolean bumpPriority ) {
      _priority = bumpPriority && (Thread.currentThread() instanceof FJWThr) ? nextThrPriority() : MIN_PRIORITY;
    }
    protected H2OCountedCompleter(H2OCountedCompleter completer){ super(completer); _priority = MIN_PRIORITY; }

    /** Used by the F/J framework internally to do work.  Once per F/J task,
     *  drain the high priority queue before doing any low priority work.
     *  Calls {@link #compute2} which contains actual work. */
    @Override public final void compute() {
      FJWThr t = (FJWThr)Thread.currentThread();
      int pp = ((PrioritizedForkJoinPool)t.getPool())._priority;
      // Drain the high priority queues before the normal F/J queue
      H2OCountedCompleter h2o = null;
      boolean set_t_prior = false;
      try {
        assert  priority() == pp:" wrong priority for task " + getClass().getSimpleName() + ", expected " + priority() + ", but got " + pp; // Job went to the correct queue?
        assert t._priority <= pp; // Thread attempting the job is only a low-priority?
        final int p2 = Math.max(pp,MIN_HI_PRIORITY);
        for( int p = MAX_PRIORITY; p > p2; p-- ) {
          if( FJPS[p] == null ) continue;
          h2o = FJPS[p].poll2();
          if( h2o != null ) {     // Got a hi-priority job?
            t._priority = p;      // Set & do it now!
            t.setPriority(Thread.MAX_PRIORITY-1);
            set_t_prior = true;
            h2o.compute2();       // Do it ahead of normal F/J work
            p++;                  // Check again the same queue
          }
        }
      } catch( Throwable ex ) {
        // If the higher priority job popped an exception, complete it
        // exceptionally...  but then carry on and do the lower priority job.
        if( h2o != null ) h2o.completeExceptionally(ex);
        else ex.printStackTrace();
      } finally {
        t._priority = pp;
        if( pp == MIN_PRIORITY && set_t_prior ) t.setPriority(Thread.NORM_PRIORITY-1);
      }
      // Now run the task as planned
      compute2();
    }

    /** Override to specify actual work to do */
    protected abstract void compute2();

    /** Exceptional completion path; mostly does printing if the exception was
     *  not handled earlier in the stack.  */
    @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      if( this.getCompleter() == null ) { // nobody else to handle this exception, so print it out
        System.err.println("onExCompletion for "+this);
        ex.printStackTrace();
      }
      return true;
    }
    // In order to prevent deadlock, threads that block waiting for a reply
    // from a remote node, need the remote task to run at a higher priority
    // than themselves.  This field tracks the required priority.
    protected byte priority() { return _priority; }
    @Override public final T clone(){
      try { return (T)super.clone(); }
      catch( CloneNotSupportedException e ) { throw Log.throwErr(e); }
    }

    /** If this is a F/J thread, return it's priority+1 - used to lift the
     *  priority of a blocking remote call, so the remote node runs it at a
     *  higher priority - so we don't deadlock when we burn the local
     *  thread. */
    protected final byte nextThrPriority() {
      Thread cThr = Thread.currentThread();
      return (byte)((cThr instanceof FJWThr) ? ((FJWThr)cThr)._priority+1 : priority());
    }

    // The serialization flavor / delegate.  Lazily set on first use.
    private transient short _ice_id;

    /** Find the serialization delegate for a subclass of this class */
    protected Icer<T> icer() {
      int id = _ice_id;
      return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this);
    }
    @Override final public AutoBuffer write    (AutoBuffer ab) { return icer().write    (ab,(T)this); }
    @Override final public AutoBuffer writeJSON(AutoBuffer ab) { return icer().writeJSON(ab,(T)this); }
    @Override final public T read    (AutoBuffer ab) { return icer().read    (ab,(T)this); }
    @Override final public T readJSON(AutoBuffer ab) { return icer().readJSON(ab,(T)this); }
    @Override final public int frozenType() { return icer().frozenType();   }
    @Override       public AutoBuffer write_impl( AutoBuffer ab ) { return ab; }
    @Override       public T read_impl( AutoBuffer ab ) { return (T)this; }
    @Override       public AutoBuffer writeJSON_impl( AutoBuffer ab ) { return ab; }
    @Override       public T readJSON_impl( AutoBuffer ab ) { return (T)this; }
  }


  public static abstract class H2OCallback<T extends H2OCountedCompleter> extends H2OCountedCompleter{
    public H2OCallback(){}
    public H2OCallback(H2OCountedCompleter cc){super(cc);}
    @Override protected void compute2(){throw H2O.fail();}
    @Override public    void onCompletion(CountedCompleter caller){callback((T) caller);}
    public abstract void callback(T t);
  }

  public static int H2O_PORT; // Both TCP & UDP cluster ports
  public static int API_PORT; // RequestServer and the API HTTP port

  /**
   * @return String of the form ipaddress:port
   */
  public static String getIpPortString() {
    return H2O.SELF_ADDRESS.getHostAddress() + ":" + H2O.API_PORT;
  }

  // The multicast discovery port
  public static MulticastSocket  CLOUD_MULTICAST_SOCKET;
  public static NetworkInterface CLOUD_MULTICAST_IF;
  public static InetAddress      CLOUD_MULTICAST_GROUP;
  public static int              CLOUD_MULTICAST_PORT ;

  // Myself, as a Node in the Cloud
  public static H2ONode SELF = null;
  public static InetAddress SELF_ADDRESS;

  // Place to store temp/swap files
  public static URI ICE_ROOT;
  public static String DEFAULT_ICE_ROOT() {
    String username = System.getProperty("user.name");
    if (username == null) username = "";
    String u2 = username.replaceAll(" ", "_");
    if (u2.length() == 0) u2 = "unknown";
    return "/tmp/h2o-" + u2;
  }

  // Place to store flows
  public static String DEFAULT_FLOW_DIR() {
    String flow_dir = null;

    try {
      if (ARGS.ga_hadoop_ver != null) {
        PersistManager pm = getPM();
        if (pm != null) {
          String s = pm.getHdfsHomeDirectory();
          if (pm.exists(s)) {
            flow_dir = s;
          }
        }
        if (flow_dir != null) {
          flow_dir = flow_dir + "/h2oflows";
        }
      } else {
        flow_dir = System.getProperty("user.home") + File.separator + "h2oflows";
      }
    }
    catch (Exception ignore) {
      // Never want this to fail, as it will kill program startup.
      // Returning null is fine if it fails for whatever reason.
    }

    return flow_dir;
  }

  /* Static list of acceptable Cloud members passed via -flatfile option.
   * It is updated also when a new client appears. */
  public static HashSet<H2ONode> STATIC_H2OS = null;

  // Reverse cloud index to a cloud; limit of 256 old clouds.
  static private final H2O[] CLOUDS = new H2O[256];

  // Enables debug features like more logging and multiple instances per JVM
  static final String DEBUG_ARG = "h2o.debug";
  static final boolean DEBUG = System.getProperty(DEBUG_ARG) != null;

  // Returned in REST API responses as X-h2o-cluster-id.
  //
  // Currently this is unique per node.  Might make sense to distribute this
  // as part of joining the cluster so all nodes have the same value.
  public static final long CLUSTER_ID = System.currentTimeMillis();

  private static JettyHTTPD jetty;
  public static void setJetty(JettyHTTPD value) {
    jetty = value;
  }
  public static JettyHTTPD getJetty() {
    return jetty;
  }

  /** If logging has not been setup yet, then Log.info will only print to
   *  stdout.  This allows for early processing of the '-version' option
   *  without unpacking the jar file and other startup stuff.  */
  static void printAndLogVersion() {
    Log.init(ARGS.log_level, ARGS.quiet);
    Log.info("----- H2O started " + (ARGS.client?"(client)":"") + " -----");
    Log.info("Build git branch: " + ABV.branchName());
    Log.info("Build git hash: " + ABV.lastCommitHash());
    Log.info("Build git describe: " + ABV.describe());
    Log.info("Build project version: " + ABV.projectVersion());
    Log.info("Built by: '" + ABV.compiledBy() + "'");
    Log.info("Built on: '" + ABV.compiledOn() + "'");

    for (AbstractH2OExtension e : H2O.getExtensions()) {
      String n = e.getExtensionName() + " ";
      AbstractBuildVersion abv = e.getBuildVersion();
      Log.info(n + "Build git branch: ", abv.branchName());
      Log.info(n + "Build git hash: ", abv.lastCommitHash());
      Log.info(n + "Build git describe: ", abv.describe());
      Log.info(n + "Build project version: ", abv.projectVersion());
      Log.info(n + "Built by: ", abv.compiledBy());
      Log.info(n + "Built on: ", abv.compiledOn());
    }

    Runtime runtime = Runtime.getRuntime();
    Log.info("Java availableProcessors: " + runtime.availableProcessors());
    Log.info("Java heap totalMemory: " + PrettyPrint.bytes(runtime.totalMemory()));
    Log.info("Java heap maxMemory: " + PrettyPrint.bytes(runtime.maxMemory()));
    Log.info("Java version: Java "+System.getProperty("java.version")+" (from "+System.getProperty("java.vendor")+")");
    List<String> launchStrings = ManagementFactory.getRuntimeMXBean().getInputArguments();
    Log.info("JVM launch parameters: "+launchStrings);
    Log.info("OS version: "+System.getProperty("os.name")+" "+System.getProperty("os.version")+" ("+System.getProperty("os.arch")+")");
    long totalMemory = OSUtils.getTotalPhysicalMemory();
    Log.info ("Machine physical memory: " + (totalMemory==-1 ? "NA" : PrettyPrint.bytes(totalMemory)));
  }

  private static void startGAStartupReport() {
    new GAStartupReportThread().start();
  }

  /** Initializes the local node and the local cloud with itself as the only member. */
  private static void startLocalNode() {
    PID = -1L;
    try {
      String n = ManagementFactory.getRuntimeMXBean().getName();
      int i = n.indexOf('@');
      if( i != -1 ) PID = Long.parseLong(n.substring(0, i));
    } catch( Throwable ignore ) { }

    // Figure self out; this is surprisingly hard
    NetworkInit.initializeNetworkSockets();
    // Do not forget to put SELF into the static configuration (to simulate
    // proper multicast behavior)
    if( !ARGS.client && STATIC_H2OS != null && !STATIC_H2OS.contains(SELF)) {
      Log.warn("Flatfile configuration does not include self: " + SELF+ " but contains " + STATIC_H2OS);
      STATIC_H2OS.add(SELF);
    }

    Log.info ("H2O cloud name: '" + ARGS.name + "' on " + SELF+
              (ARGS.flatfile==null
               ? (", discovery address "+CLOUD_MULTICAST_GROUP+":"+CLOUD_MULTICAST_PORT)
               : ", static configuration based on -flatfile "+ARGS.flatfile));

    Log.info("If you have trouble connecting, try SSH tunneling from your local machine (e.g., via port 55555):\n" +
            "  1. Open a terminal and run 'ssh -L 55555:localhost:"
            + API_PORT + " " + System.getProperty("user.name") + "@" + SELF_ADDRESS.getHostAddress() + "'\n" +
            "  2. Point your browser to " + jetty.getScheme() + "://localhost:55555");


    // Create the starter Cloud with 1 member
    SELF._heartbeat._jar_md5 = JarHash.JARHASH;
    SELF._heartbeat._client = ARGS.client;
  }

  /** Starts the worker threads, receiver threads, heartbeats and all other
   *  network related services.  */
  private static void startNetworkServices() {
    // We've rebooted the JVM recently. Tell other Nodes they can ignore task
    // prior tasks by us. Do this before we receive any packets
    UDPRebooted.T.reboot.broadcast();

    // Start the UDPReceiverThread, to listen for requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    // Started first, so we can start parsing UDP packets
    if(H2O.ARGS.useUDP)
      new UDPReceiverThread().start();

    // Start the MultiReceiverThread, to listen for multi-cast requests from
    // other Cloud Nodes. There should be only 1 of these, and it never shuts
    // down. Started soon, so we can start parsing multi-cast UDP packets
    new MultiReceiverThread().start();

    // Start the Persistent meta-data cleaner thread, which updates the K/V
    // mappings periodically to disk. There should be only 1 of these, and it
    // never shuts down.  Needs to start BEFORE the HeartBeatThread to build
    // an initial histogram state.
    Cleaner.THE_CLEANER.start();

    // Start a UDP timeout worker thread. This guy only handles requests for
    // which we have not received a timely response and probably need to
    // arrange for a re-send to cover a dropped UDP packet.
    new UDPTimeOutThread().start();
    new H2ONode.AckAckTimeOutThread().start();

    // Start the TCPReceiverThread, to listen for TCP requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    new TCPReceiverThread(NetworkInit._tcpSocketBig).start();
    // Register the default Requests
    Object x = water.api.RequestServer.class;
  }

  // Callbacks to add new Requests & menu items
  static private volatile boolean _doneRequests;

  static public void registerGET( String url_pattern, Class hclass, String hmeth, String summary ) {
    registerGET(url_pattern, hclass, hmeth, null, summary);
  }

  static public void registerGET( String url_pattern, Class hclass, String hmeth, String doc_method, String summary ) {
    if( _doneRequests ) throw new IllegalArgumentException("Cannot add more Requests once the list is finalized");
    RequestServer.register(url_pattern,"GET", hclass, hmeth, doc_method, summary);
  }

  static public void registerPOST( String url_pattern, Class hclass, String hmeth, String summary ) {
    if( _doneRequests ) throw new IllegalArgumentException("Cannot add more Requests once the list is finalized");
    RequestServer.register(url_pattern,"POST",hclass,hmeth,null,summary);
  }

  public static void registerResourceRoot(File f) {
    JarHash.registerResourceRoot(f);
  }

  /** Start the web service; disallow future URL registration.
   *  Blocks until the server is up.  */
  static public void finalizeRegistration() {
    if (_doneRequests) return;
    _doneRequests = true;
    water.api.RequestServer.finalizeRegistration();
  }

  // --------------------------------------------------------------------------
  // The Current Cloud. A list of all the Nodes in the Cloud. Changes if we
  // decide to change Clouds via atomic Cloud update.
  public static volatile H2O CLOUD = new H2O(new H2ONode[0],0,0);

  // ---
  // A dense array indexing all Cloud members. Fast reversal from "member#" to
  // Node.  No holes.  Cloud size is _members.length.
  public final H2ONode[] _memary;
  final int _hash;

  // A dense integer identifier that rolls over rarely. Rollover limits the
  // number of simultaneous nested Clouds we are operating on in-parallel.
  // Really capped to 1 byte, under the assumption we won't have 256 nested
  // Clouds. Capped at 1 byte so it can be part of an atomically-assigned
  // 'long' holding info specific to this Cloud.
  final char _idx; // no unsigned byte, so unsigned char instead

  // Construct a new H2O Cloud from the member list
  H2O( H2ONode[] h2os, int hash, int idx ) {
    _memary = h2os;             // Need to clone?
    java.util.Arrays.sort(_memary);       // ... sorted!
    _hash = hash;               // And record hash for cloud rollover
    _idx = (char)(idx&0x0ff);   // Roll-over at 256
  }

  // One-shot atomic setting of the next Cloud, with an empty K/V store.
  // Called single-threaded from Paxos. Constructs the new H2O Cloud from a
  // member list.
  void set_next_Cloud( H2ONode[] h2os, int hash ) {
    synchronized(this) {
      int idx = _idx+1; // Unique 1-byte Cloud index
      if( idx == 256 ) idx=1; // wrap, avoiding zero
      CLOUDS[idx] = CLOUD = new H2O(h2os,hash,idx);
    }
    SELF._heartbeat._cloud_size=(char)CLOUD.size();
  }

  // Is nnn larger than old (counting for wrap around)? Gets confused if we
  // start seeing a mix of more than 128 unique clouds at the same time. Used
  // to tell the order of Clouds appearing.
  static boolean larger( int nnn, int old ) {
    assert (0 <= nnn && nnn <= 255);
    assert (0 <= old && old <= 255);
    return ((nnn-old)&0xFF) < 64;
  }

  public final int size() { return _memary.length; }
  final H2ONode leader() { return _memary[0]; }

  // Find the node index for this H2ONode, or a negative number on a miss
  int nidx( H2ONode h2o ) { return java.util.Arrays.binarySearch(_memary,h2o); }
  boolean contains( H2ONode h2o ) { return nidx(h2o) >= 0; }
  @Override public String toString() {
    return java.util.Arrays.toString(_memary);
  }
  public H2ONode[] members() { return _memary; }

  // Cluster memory
  public long memsz() {
    long memsz = 0;
    for( H2ONode h2o : CLOUD._memary )
      memsz += h2o.get_max_mem();
    return memsz;
  }

  public static void waitForCloudSize(int x, long ms) {
    long start = System.currentTimeMillis();
    while( System.currentTimeMillis() - start < ms ) {
      if( CLOUD.size() >= x && Paxos._commonKnowledge )
        break;
      try { Thread.sleep(100); } catch( InterruptedException ignore ) { }
    }
    if( H2O.CLOUD.size() < x )
      throw new RuntimeException("Cloud size under " + x);
  }

  public static int getCloudSize() {
    if (! Paxos._commonKnowledge) return -1;
    return CLOUD.size();
  }

  // - Wait for at least HeartBeatThread.SLEEP msecs and
  //   try to join others, if any. Try 2x just in case.
  // - Assume that we get introduced to everybody else
  //   in one Paxos update, if at all (i.e, rest of
  //   the cloud was already formed and stable by now)
  // - If nobody else is found, not an error.
  public static void joinOthers() {
    long start = System.currentTimeMillis();
    while( System.currentTimeMillis() - start < 2000 ) {
      if( CLOUD.size() > 1 && Paxos._commonKnowledge )
        break;
      try { Thread.sleep(100); } catch( InterruptedException ignore ) { }
    }
  }

  // --------------------------------------------------------------------------
  static void initializePersistence() {
    _PM = new PersistManager(ICE_ROOT);

    if( ARGS.aws_credentials != null ) {
      try { water.persist.PersistS3.getClient(); }
      catch( IllegalArgumentException e ) { Log.err(e); }
    }
  }

  // --------------------------------------------------------------------------
  // The (local) set of Key/Value mappings.
  static final NonBlockingHashMap<Key,Value> STORE = new NonBlockingHashMap<>();

  // PutIfMatch
  // - Atomically update the STORE, returning the old Value on success
  // - Kick the persistence engine as needed
  // - Return existing Value on fail, no change.
  //
  // Keys are interned here: I always keep the existing Key, if any. The
  // existing Key is blind jammed into the Value prior to atomically inserting
  // it into the STORE and interning.
  //
  // Because of the blind jam, there is a narrow unusual race where the Key
  // might exist but be stale (deleted, mapped to a TOMBSTONE), a fresh put()
  // can find it and jam it into the Value, then the Key can be deleted
  // completely (e.g. via an invalidate), the table can resize flushing the
  // stale Key, an unrelated weak-put can re-insert a matching Key (but as a
  // new Java object), and delete it, and then the original thread can do a
  // successful put_if_later over the missing Key and blow the invariant that a
  // stored Value always points to the physically equal Key that maps to it
  // from the STORE. If this happens, some of replication management bits in
  // the Key will be set in the wrong Key copy... leading to extra rounds of
  // replication.

  public static Value putIfMatch( Key key, Value val, Value old ) {
    if( old != null ) // Have an old value?
      key = old._key; // Use prior key
    if( val != null ) {
      assert val._key.equals(key);
      if( val._key != key ) val._key = key; // Attempt to uniquify keys
    }

    // Insert into the K/V store
    Value res = STORE.putIfMatchUnlocked(key,val,old);
    if( res != old ) return res; // Return the failure cause
    // Persistence-tickle.
    // If the K/V mapping is going away, remove the old guy.
    // If the K/V mapping is changing, let the store cleaner just overwrite.
    // If the K/V mapping is new, let the store cleaner just create
    if( old != null && val == null ) old.removePersist(); // Remove the old guy
    if( val != null ) {
      Cleaner.dirty_store(); // Start storing the new guy
      if( old==null ) Scope.track(key); // New Key - start tracking
    }
    return old; // Return success
  }
  public static String foo( Value v ) {
    if( v==null ) return null;
    if( v.isNull() ) return "Null";
    return Integer.toString(((water.fvec.Vec.ESPC)(v.get()))._espcs.length);
  }

  // Get the value from the store
  public static Value     get(Key key) { return STORE.get(key); }
  public static Value raw_get(Key key) { return STORE.get(key); }
  public static void raw_remove(Key key) { STORE.remove(key); }
  public static void raw_clear() { STORE.clear(); }
  public static boolean containsKey( Key key ) { return STORE.get(key) != null; }
  static Key getk( Key key ) { return STORE.getk(key); }
  public static Set<Key> localKeySet( ) { return STORE.keySet(); }
  static Collection<Value> values( ) { return STORE.values(); }
  static public int store_size() { return STORE.size(); }

  // Nice local-STORE only debugging summary
  public static String STOREtoString() {
    int[] cnts = new int[1];
    Object[] kvs = H2O.STORE.raw_array();
    // Start the walk at slot 2, because slots 0,1 hold meta-data
    for( int i=2; i<kvs.length; i += 2 ) {
      // In the raw backing array, Keys and Values alternate in slots
      Object ov = kvs[i+1];
      if( !(ov instanceof Value) ) continue; // Ignore tombstones and Primes and null's
      Value val = (Value)ov;
      if( val.isNull() ) { Value.STORE_get(val._key); continue; } // Another variant of NULL
      int t = val.type();
      while( t >= cnts.length ) cnts = Arrays.copyOf(cnts,cnts.length<<1);
      cnts[t]++;
    }
    StringBuilder sb = new StringBuilder();
    for( int t=0; t<cnts.length; t++ )
      if( cnts[t] != 0 )
        sb.append(String.format("-%30s %5d\n",TypeMap.CLAZZES[t],cnts[t]));
    return sb.toString();
  }

  // Persistence manager
  private static PersistManager _PM;
  public static PersistManager getPM() { return _PM; }

  // Node persistent storage
  private static NodePersistentStorage NPS;
  public static NodePersistentStorage getNPS() { return NPS; }

  /**
   * Run System.gc() on every node in the H2O cluster.
   *
   * Having to call this manually from user code is a sign that something is wrong and a better
   * heuristic is needed internally.
   */
  public static void gc() {
    class GCTask extends DTask<GCTask> {
      public GCTask() {}

      @Override public void compute2() {
        Log.info("Calling System.gc() now...");
        System.gc();
        Log.info("System.gc() finished");
        tryComplete();
      }

      @Override public byte priority() { return H2O.MIN_HI_PRIORITY; }
    }

    for (H2ONode node : H2O.CLOUD._memary) {
      GCTask t = new GCTask();
      new RPC<>(node, t).call().get();
    }
  }

  // --------------------------------------------------------------------------
  public static void main( String[] args ) {

    // Record system start-time.
    if( !START_TIME_MILLIS.compareAndSet(0L, System.currentTimeMillis()) )
      return;                   // Already started

    // Copy all ai.h2o.* system properties to the tail of the command line,
    // effectively overwriting the earlier args.
    ArrayList<String> args2 = new ArrayList<>(Arrays.asList(args));
    for( Object p : System.getProperties().keySet() ) {
      String s = (String)p;
      if( s.startsWith("ai.h2o.") ) {
        args2.add("-" + s.substring(7));
        // hack: Junits expect properties, throw out dummy prop for ga_opt_out
        if (!s.substring(7).equals("ga_opt_out"))
          args2.add(System.getProperty(s));
      }
    }

    // Parse args
    parseArguments(args2.toArray(args));

    // Get ice path before loading Log or Persist class
    String ice = DEFAULT_ICE_ROOT();
    if( ARGS.ice_root != null ) ice = ARGS.ice_root.replace("\\", "/");
    try {
      ICE_ROOT = new URI(ice);
    } catch(URISyntaxException ex) {
      throw new RuntimeException("Invalid ice_root: " + ice + ", " + ex.getMessage());
    }

    // Always print version, whether asked-for or not!
    printAndLogVersion();
    if( ARGS.version ) {
      Log.flushStdout();
      exit(0);
    }

    // Print help & exit
    if( ARGS.help ) { printHelp(); exit(0); }

    // Validate extension arguments
    for (AbstractH2OExtension e : H2O.getExtensions()) {
      e.validateArguments();
    }

    Log.info("X-h2o-cluster-id: " + H2O.CLUSTER_ID);
    Log.info("User name: '" + H2O.ARGS.user_name + "'");

    // Register with GA or not
    List<String> gaidList = JarHash.getResourcesList("gaid");
    if((new File(".h2o_no_collect")).exists()
            || (new File(System.getProperty("user.home")+File.separator+".h2o_no_collect")).exists()
            || ARGS.ga_opt_out
            || gaidList.contains("CRAN")) {
      GA = null;
      Log.info("Opted out of sending usage metrics.");
    } else {
      try {
        GA = new GoogleAnalytics("UA-56665317-1", "H2O", ABV.projectVersion());
        DefaultRequest defReq = GA.getDefaultRequest();
        String gaid = null;
        if (gaidList.size() > 0) {
          if (gaidList.size() > 1) Log.debug("More than once resource seen in gaid dir.");
          for (String str : gaidList) {
            if (str.matches("........-....-....-....-............")
                && !str.equals("XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX")) {
              gaid = str;
              break;
            }
          }
        }
        if (gaid == null) { // No UUID, create one
          gaid = defReq.clientId();
          gaid = gaid.replaceFirst("........-","ANONYMOU-");
        }
        defReq.customDimension(CLIENT_ID_GA_CUST_DIM, gaid);
        GA.setDefaultRequest(defReq);
      } catch(Throwable t) {
        Log.POST(11, t.toString());
        StackTraceElement[] stes = t.getStackTrace();
        for(int i =0; i < stes.length; i++) Log.POST(11, stes[i].toString());
      }
    }

    // Epic Hunt for the correct self InetAddress
    NetworkInit.findInetAddressForSelf();

    // Start the local node.  Needed before starting logging.
    startLocalNode();

    try {
      String logDir = Log.getLogDir();
      Log.info("Log dir: '" + logDir + "'");
    }
    catch (Exception e) {
      Log.info("Log dir: (Log4j configuration inherited)");
    }

    Log.info("Cur dir: '" + System.getProperty("user.dir") + "'");

    //Print extra debug info now that logs are setup
    RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
    Log.debug("H2O launch parameters: "+ARGS.toString());
    Log.debug("Boot class path: "+ rtBean.getBootClassPath());
    Log.debug("Java class path: "+ rtBean.getClassPath());
    Log.debug("Java library path: "+ rtBean.getLibraryPath());

    // Load up from disk and initialize the persistence layer
    initializePersistence();

    // Initialize NPS
    {
      String flow_dir;

      if (ARGS.flow_dir != null) {
        flow_dir = ARGS.flow_dir;
      }
      else {
        flow_dir = DEFAULT_FLOW_DIR();
      }

      if (flow_dir != null) {
        flow_dir = flow_dir.replace("\\", "/");
        Log.info("Flow dir: '" + flow_dir + "'");
      }
      else {
        Log.info("Flow dir is undefined; saving flows not available");
      }

      NPS = new NodePersistentStorage(flow_dir);
    }

    // Start network services, including heartbeats
    startNetworkServices();   // start server services
    Log.trace("Network services started");

    // The "Cloud of size N formed" message printed out by doHeartbeat is the trigger
    // for users of H2O to know that it's OK to start sending REST API requests.
    Paxos.doHeartbeat(SELF);
    assert SELF._heartbeat._cloud_hash != 0 || ARGS.client;

    // Start the heartbeat thread, to publish the Clouds' existence to other
    // Clouds. This will typically trigger a round of Paxos voting so we can
    // join an existing Cloud.
    new HeartBeatThread().start();

    if (GA != null)
      startGAStartupReport();
  }

  // Die horribly
  public static void die(String s) {
    Log.fatal(s);
    H2O.shutdown(-1);
  }

  public static class GAStartupReportThread extends Thread {
    final private int sleepMillis = 150 * 1000; //2.5 min

    // Constructor.
    public GAStartupReportThread() {
      super("GAStartupReport");        // Only 9 characters get printed in the log.
      setDaemon(true);
      setPriority(MAX_PRIORITY - 2);
    }

    // Class main thread.
    @Override
    public void run() {
      try {
        Thread.sleep (sleepMillis);
      }
      catch (Exception ignore) {};
      GAUtils.logStartup();
    }
  }
}
