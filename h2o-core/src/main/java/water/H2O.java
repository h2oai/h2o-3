package water;

import hex.ModelBuilder;
import jsr166y.CountedCompleter;
import jsr166y.ForkJoinPool;
import jsr166y.ForkJoinWorkerThread;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import water.UDPRebooted.ShutdownTsk;
import water.api.LogsHandler;
import water.api.RequestServer;
import water.exceptions.H2OFailException;
import water.exceptions.H2OIllegalArgumentException;
import water.init.*;
import water.nbhm.NonBlockingHashMap;
import water.parser.DecryptionTool;
import water.parser.ParserService;
import water.persist.PersistManager;
import water.server.ServletUtils;
import water.util.*;
import water.webserver.iface.WebServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
* Start point for creating or joining an <code>H2O</code> Cloud.
*
* @author <a href="mailto:cliffc@h2o.ai"></a>
* @version 1.0
*/
final public class H2O {
  public static final String DEFAULT_JKS_PASS = "h2oh2o";
  public static final int H2O_DEFAULT_PORT = 54321;
  public static final Map<Integer, Integer> GITHUB_DISCUSSIONS = createMap();
  
  static Map<Integer, Integer> createMap() {
    Integer[] GHDiscussion = new Integer[]{15512, 15513, 15514, 15515, 15516, 15517, 15518, 15519, 15520, 15521, 15522,
            15523, 15524, 15525};
    Integer[] techNoteNumber = new Integer[]{1,2,3,4,5,7,9,10,11,12,13,14,15,16};
    Map<Integer, Integer> mapTNToGH = new HashMap<>();
    int mapLen = GHDiscussion.length;
    for (int index=0; index<mapLen; index++)
      mapTNToGH.put(techNoteNumber[index], GHDiscussion[index]);
    return mapTNToGH;
  }
  
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
            "          Port number for this node (note: port+1 is also used by default).\n" +
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
            "          Write messages at this logging level, or above.  Default is INFO.\n" +
            "\n" + 
            "    -max_log_file_size\n" +
            "          Maximum size of INFO and DEBUG log files. The file is rolled over after a specified size has been reached.\n" +
            "          (The default is 3MB. Minimum is 1MB and maximum is 99999MB)\n" +
            "\n" +
            "    -flow_dir <server side directory or HDFS directory>\n" +
            "          The directory where H2O stores saved flows.\n" +
            defaultFlowDirMessage +
            "\n" +
            "    -nthreads <#threads>\n" +
            "          Maximum number of threads in the low priority batch-work queue.\n" +
            "          (The default is " + (char)H2ORuntime.availableProcessors() + ".)\n" +
            "\n" +
            "    -client\n" +
            "          Launch H2O node in client mode.\n" +
            "\n" +
            "    -notify_local <fileSystemPath>\n" +
            "          Specifies a file to write when the node is up. The file contains one line with the IP and\n" +
            "          port of the embedded web server. e.g. 192.168.1.100:54321\n" +
            "\n" +
            "    -context_path <context_path>\n" +
            "          The context path for jetty.\n" +
            "\n" +
            "Authentication options:\n" +
            "\n" +
            "    -jks <filename>\n" +
            "          Java keystore file\n" +
            "\n" +
            "    -jks_pass <password>\n" +
            "          (Default is '" + DEFAULT_JKS_PASS + "')\n" +
            "\n" +
            "    -jks_alias <alias>\n" +
            "          (Optional, use if the keystore has multiple certificates and you want to use a specific one.)\n" +
            "\n" +
            "    -hostname_as_jks_alias\n" +
            "          (Optional, use if you want to use the machine hostname as your certificate alias.)\n" +
            "\n" +
            "    -hash_login\n" +
            "          Use Jetty HashLoginService\n" +
            "\n" +
            "    -ldap_login\n" +
            "          Use Jetty Ldap login module\n" +
            "\n" +
            "    -kerberos_login\n" +
            "          Use Jetty Kerberos login module\n" +
            "\n" +
            "    -spnego_login\n" +
            "          Use Jetty SPNEGO login service\n" +
            "\n" +
            "    -pam_login\n" +
            "          Use Jetty PAM login module\n" +
            "\n" +
            "    -login_conf <filename>\n" +
            "          LoginService configuration file\n" +
            "\n" +
            "    -spnego_properties <filename>\n" +
            "          SPNEGO login module configuration file\n" +
            "\n" +
            "    -form_auth\n" +
            "          Enables Form-based authentication for Flow (default is Basic authentication)\n" +
            "\n" +
            "    -session_timeout <minutes>\n" +
            "          Specifies the number of minutes that a session can remain idle before the server invalidates\n" +
            "          the session and requests a new login. Requires '-form_auth'. Default is no timeout\n" +
            "\n" +
            "    -internal_security_conf <filename>\n" +
            "          Path (absolute or relative) to a file containing all internal security related configurations\n" +
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

    for (AbstractH2OExtension e : extManager.getCoreExtensions()) {
      e.printHelp();
    }
  }

  /**
   * Singleton ARGS instance that contains the processed arguments.
   */
  public static final OptArgs ARGS = new OptArgs();

  /**
   * A class containing all of the authentication arguments for H2O.
   */
  public static class
    BaseArgs {

    //-----------------------------------------------------------------------------------
    // Authentication & Security
    //-----------------------------------------------------------------------------------
    /** -jks is Java KeyStore file on local filesystem */
    public String jks = null;

    /** -jks_pass is Java KeyStore password; default is 'h2oh2o' */
    public String jks_pass = DEFAULT_JKS_PASS;

    /** -jks_alias if the keystore has multiple certificates and you want to use a specific one */
    public String jks_alias = null;

    /** -hostname_as_jks_alias if you want to use the machine hostname as your certificate alias */
    public boolean hostname_as_jks_alias = false;    

    /** -hash_login enables HashLoginService */
    public boolean hash_login = false;

    /** -ldap_login enables ldaploginmodule */
    public boolean ldap_login = false;

    /** -kerberos_login enables krb5loginmodule */
    public boolean kerberos_login = false;

    /** -kerberos_login enables SpnegoLoginService */
    public boolean spnego_login = false;

    /** -pam_login enables pamloginmodule */
    public boolean pam_login = false;

    /** -login_conf is login configuration service file on local filesystem */
    public String login_conf = null;

    /** -spnego_properties is SPNEGO configuration file on local filesystem */
    public String spnego_properties = null;

    /** -form_auth enables Form-based authentication */
    public boolean form_auth = false;

    /** -session_timeout maximum duration of session inactivity in minutes **/
    String session_timeout_spec = null; // raw value specified by the user
    public int session_timeout = 0; // parsed value (in minutes)

    /** -user_name=user_name; Set user name */
    public String user_name = System.getProperty("user.name");

    /** -internal_security_conf path (absolute or relative) to a file containing all internal security related configurations */
    public String internal_security_conf = null;

    /** -internal_security_conf_rel_paths interpret paths of internal_security_conf relative to the main config file */
    public boolean internal_security_conf_rel_paths = false;

    /** -internal_security_enabled is a boolean that indicates if internal communication paths are secured*/
    public boolean internal_security_enabled = false;

    /** -allow_insecure_xgboost is a boolean that allows xgboost to run in a secured cluster */
    public boolean allow_insecure_xgboost = false;

    /** -use_external_xgboost; invoke XGBoost on external cluster started by Steam */
    public boolean use_external_xgboost = false;

    /** -decrypt_tool specifies the DKV key where a default decrypt tool will be installed*/
    public String decrypt_tool = null;

    //-----------------------------------------------------------------------------------
    // Kerberos
    //-----------------------------------------------------------------------------------

    public String principal = null;
    public String keytab_path = null;
    public String hdfs_token_refresh_interval = null;

    //-----------------------------------------------------------------------------------
    // Networking
    //-----------------------------------------------------------------------------------
    /** -port=####; Specific Browser/API/HTML port */
    public int port;

    /** -baseport=####; Port to start upward searching from. */
    public int baseport = H2O_DEFAULT_PORT;

    /** -port_offset=####; Offset between the API(=web) port and the internal communication port; api_port + port_offset = h2o_port */
    public int port_offset = 1;

    /** -web_ip=ip4_or_ip6; IP used for web server. By default it listen to all interfaces. */
    public String web_ip = null;

    /** -ip=ip4_or_ip6; Named IP4/IP6 address instead of the default */
    public String ip;

    /** -network=network; Network specification for acceptable interfaces to bind to */
    public String network;

    /** -context_path=jetty_context_path; the context path for jetty */
    public String context_path = "";

    public KeyValueArg[] extra_headers = new KeyValueArg[0];

    public PathMatcher file_deny_glob = FileSystems.getDefault().getPathMatcher("glob:{/bin/*,/etc/*,/var/*,/usr/*,/proc/*,**/.**}");

  }

  public static class KeyValueArg {
    public final String _key;
    public final String _value;
    private KeyValueArg(String key, String value) {
      _key = key;
      _value = value;
    }
  } 
  
  /**
   * A class containing all of the arguments for H2O.
   */
  public static class
    OptArgs extends BaseArgs {
    // Prefix of hidden system properties
    public static final String SYSTEM_PROP_PREFIX = "sys.ai.h2o.";
    public static final String SYSTEM_DEBUG_CORS = H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.cors";
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

    //-----------------------------------------------------------------------------------
    // Node configuration
    //-----------------------------------------------------------------------------------
    /** -ice_root=ice_root; ice root directory; where temp files go */
    public String ice_root;

    /** -cleaner; enable user-mode spilling of big data to disk in ice_root */
    public boolean cleaner = false;

    /** -nthreads=nthreads; Max number of F/J threads in the low-priority batch queue */
    public short nthreads= (short)H2ORuntime.availableProcessors();

    /** -log_dir=/path/to/dir; directory to save logs in */
    public String log_dir;

    /** -flow_dir=/path/to/dir; directory to save flows in */
    public String flow_dir;

    /** -disable_web; disable Jetty and REST API interface */
    public boolean disable_web = false;

    /** -disable_net; do not listen to incoming traffic and do not try to discover other nodes, for single node deployments */
    public boolean disable_net = false;

    /** -disable_flow; disable access to H2O Flow, keep REST API interface available to clients */
    public boolean disable_flow = false;
    
    /** -client, -client=true; Client-only; no work; no homing of Keys (but can cache) */
    public boolean client;

    /** -allow_clients, -allow_clients=true; Enable clients to connect to this H2O node - disabled by default */
    public boolean allow_clients = false;

    public boolean allow_unsupported_java = false;

    /** If this timeout is set to non 0 value, stop the cluster if there hasn't been any rest api request to leader
     * node after the given timeout. Unit is milliseconds.
     */
    public int rest_api_ping_timeout = 0;
    
    /** specifies a file to write when the node is up */
    public String notify_local;

    /** what the is ratio of available off-heap memory to maximum JVM heap memory */
    public double off_heap_memory_ratio = 0;

    //-----------------------------------------------------------------------------------
    // HDFS & AWS
    //-----------------------------------------------------------------------------------
    /** -hdfs_config=hdfs_config; configuration file of the HDFS */
    public String[] hdfs_config = null;

    /** -hdfs_skip=hdfs_skip; used by Hadoop driver to not unpack and load any HDFS jar file at runtime. */
    public boolean hdfs_skip = false;

    /** -aws_credentials=aws_credentials; properties file for aws credentials */
    public String aws_credentials = null;

    /** -configure_s3_using_s3a; use S3A(FileSystem) to configure S3 client */
    public boolean configure_s3_using_s3a = false;
    
    /** --ga_hadoop_ver=ga_hadoop_ver; Version string for Hadoop */
    public String ga_hadoop_ver = null;

    /** -Hkey=value; additional configuration to merge into the Hadoop Configuration */
    public final Properties hadoop_properties = new Properties();

    //-----------------------------------------------------------------------------------
    // Recovery
    //-----------------------------------------------------------------------------------
    /** -auto_recovery_dir=hdfs://path/to/recovery; Where to store {@link hex.faulttolerance.Recoverable} job data */
    public String auto_recovery_dir;

    //-----------------------------------------------------------------------------------
    // Debugging
    //-----------------------------------------------------------------------------------
    /** -log_level=log_level; One of DEBUG, INFO, WARN, ERRR.  Default is INFO. */
    public String log_level;

    /** -max_log_file_size=max_log_file_size; Maximum size of log file. The file is rolled over after a specified size has been reached.*/
    public String max_log_file_size;

    /** -random_udp_drop, -random_udp_drop=true; test only, randomly drop udp incoming */
    public boolean random_udp_drop;

    /** -md5skip, -md5skip=true; test-only; Skip the MD5 Jar checksum; allows jars from different builds to mingle in the same cloud */
    public boolean md5skip = false;

    /** -quiet Enable quiet mode and avoid any prints to console, useful for client embedding */
    public boolean quiet = false;

    /** Timeout specifying how long to wait before we check if the client has disconnected from this node */
    public long clientDisconnectTimeout = HeartBeatThread.CLIENT_TIMEOUT * 20;

    /** -embedded; when running embedded into another application (eg. Sparkling Water) - enforce all threads to be daemon threads */
    public boolean embedded = false;

    /**
     * Optionally disable algorithms marked as beta or experimental.
     * Everything is on by default.
     */
    public ModelBuilder.BuilderVisibility features_level = ModelBuilder.BuilderVisibility.Experimental;

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
          Log.err(ex, ex);
        }
      }
      result.deleteCharAt(result.length() - 2);
      result.deleteCharAt(result.length() - 1);
      result.append(" ]");

      return result.toString();
    }

    /**
     * Whether this H2O instance was launched on hadoop (using 'hadoop jar h2odriver.jar') or not.
     */
    public boolean launchedWithHadoopJar() {
      return hdfs_skip;
    }

  }

  public static void parseFailed(String message) {
    System.out.println("");
    System.out.println("ERROR: " + message);
    System.out.println("");
    printHelp();
    H2O.exitQuietly(1); // argument parsing failed -> we might have inconsistent ARGS and not be able to initialize logging 
  }

  /**
   * Use when given arguments are incompatible for cluster to run.
   * Log is flushed into stdout to show important debugging information
   */
  public static void clusterInitializationFailed() {
    Log.flushBufferedMessagesToStdout();
    H2O.exitQuietly(1);
  }

  public static class OptString {
    final String _s;
    String _lastMatchedFor;
    public OptString(String s) {
      _s = s;
    }
    public boolean matches(String s) {
      _lastMatchedFor = s;
      if (_s.equals("-"  + s)) return true;
      if (_s.equals("--" + s)) return true;
      return false;
    }

    public int incrementAndCheck(int i, String[] args) {
      i = i + 1;
      if (i >= args.length) parseFailed(_lastMatchedFor + " not specified");
      return i;
    }

    public int parseInt(String a) {
      try { return Integer.parseInt(a); }
      catch (Exception e) { }
      parseFailed("Argument " + _lastMatchedFor + " must be an integer (was given '" + a + "')" );
      return 0;
    }

    public int parsePort(String portString){
      int portNum = parseInt(portString);
      if(portNum < 0 || portNum > 65535){
        parseFailed("Argument " + _lastMatchedFor + " must be an integer between 0 and 65535");
        return 0;
      }else{
        return portNum;
      }
    }
    
    public String checkFileSize(String fileSizeString){
      int length = fileSizeString.length();
      if(length > 2 && length < 8 && fileSizeString.substring(length-2, length).equals("MB")){
        try {
          Integer.parseInt(fileSizeString.substring(0, length-2));
          return fileSizeString;
        } catch (NumberFormatException ex){
          parseFailed("Argument " + _lastMatchedFor + " must be String value from 1MB to 99999MB.");
          return null;
        }
      } 
      parseFailed("Argument " + _lastMatchedFor + " must be String value from 1MB to 99999MB.");
      return null;
    }

    @Override public String toString() { return _s; }
  }

  /**
   * Dead stupid argument parser.
   */
  static void parseArguments(String[] args) {
    for (AbstractH2OExtension e : extManager.getCoreExtensions()) {
      args = e.parseArguments(args);
    }
    parseH2OArgumentsTo(args, ARGS);
  }

  public static OptArgs parseH2OArgumentsTo(String[] args, OptArgs trgt) {
    for (int i = 0; i < args.length; i++) {
      OptString s = new OptString(args[i]);
      if (s.matches("h") || s.matches("help")) {
        trgt.help = true;
      }
      else if (s.matches("version")) {
        trgt.version = true;
      }
      else if (s.matches("name")) {
        i = s.incrementAndCheck(i, args);
        trgt.name = args[i];
      }
      else if (s.matches("flatfile")) {
        i = s.incrementAndCheck(i, args);
        trgt.flatfile = args[i];
      }
      else if (s.matches("port")) {
        i = s.incrementAndCheck(i, args);
        trgt.port = s.parsePort(args[i]);
      }
      else if (s.matches("baseport")) {
        i = s.incrementAndCheck(i, args);
        trgt.baseport = s.parsePort(args[i]);
      }
      else if (s.matches("port_offset")) {
        i = s.incrementAndCheck(i, args);
        trgt.port_offset = s.parsePort(args[i]); // port offset has the same properties as a port, we don't allow negative offsets
      }
      else if (s.matches("ip")) {
        i = s.incrementAndCheck(i, args);
        trgt.ip = args[i];
      }
      else if (s.matches("web_ip")) {
        i = s.incrementAndCheck(i, args);
        trgt.web_ip = args[i];
      }
      else if (s.matches("network")) {
        i = s.incrementAndCheck(i, args);
        trgt.network = args[i];
      }
      else if (s.matches("client")) {
        trgt.client = true;
      }
      else if (s.matches("allow_clients")) {
        trgt.allow_clients = true;
      }
      else if (s.matches("allow_unsupported_java")) {
        trgt.allow_unsupported_java = true;
      } 
      else if (s.matches("rest_api_ping_timeout")) {
        i = s.incrementAndCheck(i, args);
        trgt.rest_api_ping_timeout = s.parseInt(args[i]);
      }
      else if (s.matches("notify_local")) {
        i = s.incrementAndCheck(i, args);
        trgt.notify_local = args[i];
      }
      else if (s.matches("off_heap_memory_ratio")) {
        i = s.incrementAndCheck(i, args);
        trgt.off_heap_memory_ratio = Double.parseDouble(args[i]);
      }
      else if (s.matches("user_name")) {
        i = s.incrementAndCheck(i, args);
        trgt.user_name = args[i];
      }
      else if (s.matches("ice_root")) {
        i = s.incrementAndCheck(i, args);
        trgt.ice_root = args[i];
      }
      else if (s.matches("log_dir")) {
        i = s.incrementAndCheck(i, args);
        trgt.log_dir = args[i];
      }
      else if (s.matches("flow_dir")) {
        i = s.incrementAndCheck(i, args);
        trgt.flow_dir = args[i];
      }
      else if (s.matches("disable_web")) {
        trgt.disable_web = true;
      }
      else if (s.matches("disable_net")) {
        trgt.disable_net = true;
      }
      else if (s.matches("disable_flow")) {
        trgt.disable_flow = true;
      }
      else if (s.matches("context_path")) {
        i = s.incrementAndCheck(i, args);
        String value = args[i];
        trgt.context_path = value.startsWith("/")
                            ? value.trim().length() == 1
                              ? "" : value
                            : "/" + value;
      }
      else if (s.matches("nthreads")) {
        i = s.incrementAndCheck(i, args);
        int nthreads = s.parseInt(args[i]);
        if (nthreads >= 1) { //otherwise keep default (all cores)
          if (nthreads > Short.MAX_VALUE)
            throw H2O.unimpl("Can't handle more than " + Short.MAX_VALUE + " threads.");
          trgt.nthreads = (short) nthreads;
        }
      }
      else if (s.matches("hdfs_config")) {
        i = s.incrementAndCheck(i, args);
        trgt.hdfs_config = ArrayUtils.append(trgt.hdfs_config, args[i]);
      }
      else if (s.matches("hdfs_skip")) {
        trgt.hdfs_skip = true;
      }
      else if (s.matches("H")) {
        i = s.incrementAndCheck(i, args);
        String key = args[i];
        i = s.incrementAndCheck(i, args);
        String value = args[i];
        trgt.hadoop_properties.setProperty(key, value);
      }
      else if (s.matches("aws_credentials")) {
        i = s.incrementAndCheck(i, args);
        trgt.aws_credentials = args[i];
      }
      else if (s.matches("configure_s3_using_s3a")) {
        trgt.configure_s3_using_s3a = true;
      }
      else if (s.matches("ga_hadoop_ver")) {
        i = s.incrementAndCheck(i, args);
        trgt.ga_hadoop_ver = args[i];
      }
      else if (s.matches("ga_opt_out")) {
        // JUnits pass this as a system property, but it usually a flag without an arg
        if (i+1 < args.length && args[i+1].equals("yes")) i++;
      }
      else if (s.matches("auto_recovery_dir")) {
        i = s.incrementAndCheck(i, args);
        trgt.auto_recovery_dir = args[i];
      }
      else if (s.matches("log_level")) {
        i = s.incrementAndCheck(i, args);
        trgt.log_level = args[i];
      }
      else if (s.matches("max_log_file_size")) {
        i = s.incrementAndCheck(i, args);
        trgt.max_log_file_size = s.checkFileSize(args[i]);
      }
      else if (s.matches("random_udp_drop")) {
        trgt.random_udp_drop = true;
      }
      else if (s.matches("md5skip")) {
        trgt.md5skip = true;
      }
      else if (s.matches("quiet")) {
        trgt.quiet = true;
      }
      else if(s.matches("cleaner")) {
        trgt.cleaner = true;
      }
      else if (s.matches("jks")) {
        i = s.incrementAndCheck(i, args);
        trgt.jks = args[i];
      }
      else if (s.matches("jks_pass")) {
        i = s.incrementAndCheck(i, args);
        trgt.jks_pass = args[i];
      }
      else if (s.matches("jks_alias")) {
        i = s.incrementAndCheck(i, args);
        trgt.jks_alias = args[i];
      }
      else if (s.matches("hostname_as_jks_alias")) {
        trgt.hostname_as_jks_alias = true;
      }
      else if (s.matches("hash_login")) {
        trgt.hash_login = true;
      }
      else if (s.matches("ldap_login")) {
        trgt.ldap_login = true;
      }
      else if (s.matches("kerberos_login")) {
        trgt.kerberos_login = true;
      }
      else if (s.matches("spnego_login")) {
        trgt.spnego_login = true;
      }
      else if (s.matches("pam_login")) {
        trgt.pam_login = true;
      }
      else if (s.matches("login_conf")) {
        i = s.incrementAndCheck(i, args);
        trgt.login_conf = args[i];
      }
      else if (s.matches("spnego_properties")) {
        i = s.incrementAndCheck(i, args);
        trgt.spnego_properties = args[i];
      }
      else if (s.matches("form_auth")) {
        trgt.form_auth = true;
      }
      else if (s.matches("session_timeout")) {
        i = s.incrementAndCheck(i, args);
        trgt.session_timeout_spec = args[i];
        try { trgt.session_timeout = Integer.parseInt(args[i]); } catch (Exception e) { /* ignored */ }
      }
      else if (s.matches("internal_security_conf")) {
        i = s.incrementAndCheck(i, args);
        trgt.internal_security_conf = args[i];
      }
      else if (s.matches("internal_security_conf_rel_paths")) {
        trgt.internal_security_conf_rel_paths = true;
      }
      else if (s.matches("allow_insecure_xgboost")) {
        trgt.allow_insecure_xgboost = true;
      }
      else if (s.matches("use_external_xgboost")) {
        trgt.use_external_xgboost = true;
      }
      else if (s.matches("decrypt_tool")) {
        i = s.incrementAndCheck(i, args);
        trgt.decrypt_tool = args[i];
      }
      else if (s.matches("principal")) {
        i = s.incrementAndCheck(i, args);
        trgt.principal = args[i];
      }
      else if (s.matches("keytab")) {
        i = s.incrementAndCheck(i, args);
        trgt.keytab_path = args[i];
      }
      else if (s.matches("hdfs_token_refresh_interval")) {
        i = s.incrementAndCheck(i, args);
        trgt.hdfs_token_refresh_interval = args[i];
      }
      else if (s.matches("no_latest_check")) {
        // ignored
        Log.trace("Invoked with 'no_latest_check' option (NOOP in current release).");
      }
      else if(s.matches(("client_disconnect_timeout"))){
        i = s.incrementAndCheck(i, args);
        int clientDisconnectTimeout = s.parseInt(args[i]);
        if (clientDisconnectTimeout <= 0) {
          throw new IllegalArgumentException("Interval for checking if client is disconnected has to be positive (milliseconds).");
        }
        trgt.clientDisconnectTimeout = clientDisconnectTimeout;
      } else if (s.matches("useUDP")) {
        Log.warn("Support for UDP communication was removed from H2O, using TCP.");
      } else if (s.matches("watchdog_client_retry_timeout")) {
        warnWatchdogRemoved("watchdog_client_retry_timeout");
      } else if (s.matches("watchdog_client")) {
        warnWatchdogRemoved("watchdog_client");
      } else if (s.matches("watchdog_client_connect_timeout")) {
        warnWatchdogRemoved("watchdog_client_connect_timeout");
      } else if (s.matches("watchdog_stop_without_client")) {
        warnWatchdogRemoved("watchdog_stop_without_client");
      } else if (s.matches("features")) {
        i = s.incrementAndCheck(i, args);
        trgt.features_level = ModelBuilder.BuilderVisibility.valueOfIgnoreCase(args[i]);
        Log.info(String.format("Limiting algorithms available to level: %s", trgt.features_level.name()));
      } else if (s.matches("add_http_header")) {
        i = s.incrementAndCheck(i, args);
        String key = args[i];
        i = s.incrementAndCheck(i, args);
        String value = args[i];
        trgt.extra_headers = ArrayUtils.append(trgt.extra_headers, new KeyValueArg(key, value));
      } else if (s.matches("file_deny_glob")) {
        i = s.incrementAndCheck(i, args);
        String key = args[i];
        try {
          trgt.file_deny_glob = FileSystems.getDefault().getPathMatcher("glob:" + key);
        }
        catch (Exception e) {
          throw new IllegalArgumentException("Error parsing file_deny_glob parameter");
        }
      }
      else if(s.matches("embedded")) {
        trgt.embedded = true;
      } else {
        parseFailed("Unknown argument (" + s + ")");
      }
    }
    return trgt;
  }

  private static void warnWatchdogRemoved(String param) {
    Log.warn("Support for watchdog client communication was removed and '" + param + "' argument has no longer any effect. " +
            "It will be removed in the next major release 3.30.");
  }
  
  private static void validateArguments() {
    if (ARGS.jks != null) {
      if (! new File(ARGS.jks).exists()) {
        parseFailed("File does not exist: " + ARGS.jks);
      }
    }

    if (ARGS.jks_alias != null && ARGS.hostname_as_jks_alias) {
      parseFailed("Options -jks_alias and -hostname_as_jks_alias are mutually exclusive, specify only one of them");
    }
    
    if (ARGS.login_conf != null) {
      if (! new File(ARGS.login_conf).exists()) {
        parseFailed("File does not exist: " + ARGS.login_conf);
      }
    }

    int login_arg_count = 0;
    if (ARGS.hash_login) login_arg_count++;
    if (ARGS.ldap_login) login_arg_count++;
    if (ARGS.kerberos_login) login_arg_count++;
    if (ARGS.spnego_login) login_arg_count++;
    if (ARGS.pam_login) login_arg_count++;
    if (login_arg_count > 1) {
      parseFailed("Can only specify one of -hash_login, -ldap_login, -kerberos_login, -spnego_login and -pam_login");
    }

    if (ARGS.hash_login || ARGS.ldap_login || ARGS.kerberos_login || ARGS.pam_login || ARGS.spnego_login) {
      if (H2O.ARGS.login_conf == null) {
        parseFailed("Must specify -login_conf argument");
      }
    } else {
      if (H2O.ARGS.form_auth) {
        parseFailed("No login method was specified. Form-based authentication can only be used in conjunction with of a LoginService.\n" +
                "Pick a LoginService by specifying '-<method>_login' option.");
      }
    }

    if (ARGS.spnego_login) {
      if (H2O.ARGS.spnego_properties == null) {
        parseFailed("Must specify -spnego_properties argument");
      }
      if (H2O.ARGS.form_auth) {
        parseFailed("Form-based authentication not supported when SPNEGO login is enabled.");
      }
    }

    if (ARGS.session_timeout_spec != null) {
      if (! ARGS.form_auth) {
        parseFailed("Session timeout can only be enabled for Form based authentication (use -form_auth)");
      }
      if (ARGS.session_timeout <= 0)
        parseFailed("Invalid session timeout specification (" + ARGS.session_timeout + ")");
    }
    
    if (ARGS.rest_api_ping_timeout < 0) {
      parseFailed(String.format("rest_api_ping_timeout needs to be 0 or higher, was (%d)", ARGS.rest_api_ping_timeout));
    }

    // Validate extension arguments
    for (AbstractH2OExtension e : extManager.getCoreExtensions()) {
      e.validateArguments();
    }
  }

  //-------------------------------------------------------------------------------------------------------------------
  // Embedded configuration for a full H2O node to be implanted in another
  // piece of software (e.g. Hadoop mapper task).
  //-------------------------------------------------------------------------------------------------------------------

  public static volatile AbstractEmbeddedH2OConfig embeddedH2OConfig;

  /**
   * Register embedded H2O configuration object with H2O instance.
   */
  public static void setEmbeddedH2OConfig(AbstractEmbeddedH2OConfig c) {
    embeddedH2OConfig = c;
  }

  /**
   * Returns an instance of {@link AbstractEmbeddedH2OConfig}. The origin of the embedded config might be either
   * from directly setting the embeddedH2OConfig field via setEmbeddedH2OConfig setter, or dynamically provided via
   * service loader. Directly set {@link AbstractEmbeddedH2OConfig} is always prioritized. ServiceLoader lookup is only
   * performed if no config is previously set.
   * <p>
   * Result of first ServiceLoader lookup is also considered final - once a service is found, dynamic lookup is not
   * performed any further.
   *
   * @return An instance of {@link AbstractEmbeddedH2OConfig}, if set or dynamically provided. Otherwise null
   * @author Michal Kurka
   */
  public static AbstractEmbeddedH2OConfig getEmbeddedH2OConfig() {
    if (embeddedH2OConfig != null) {
      return embeddedH2OConfig;
    }

    embeddedH2OConfig = discoverEmbeddedConfigProvider()
            .map(embeddedConfigProvider -> {
              Log.info(String.format("Dynamically loaded '%s' as AbstractEmbeddedH2OConfigProvider.", embeddedConfigProvider.getName()));
              return embeddedConfigProvider.getConfig();
            }).orElse(null);

    return embeddedH2OConfig;
  }

  /**
   * Uses {@link ServiceLoader} to discover active instances of {@link EmbeddedConfigProvider}. Only one provider
   * may be active at a time. If more providers are detected, {@link IllegalStateException} is thrown.
   *
   * @return An {@link Optional} of {@link EmbeddedConfigProvider}, if a single active provider is found. Otherwise
   * an empty optional.
   * @throws IllegalStateException When there are multiple active instances {@link EmbeddedConfigProvider} discovered.
   */
  private static Optional<EmbeddedConfigProvider> discoverEmbeddedConfigProvider() throws IllegalStateException {
    final ServiceLoader<EmbeddedConfigProvider> configProviders = ServiceLoader.load(EmbeddedConfigProvider.class);
    EmbeddedConfigProvider provider = null;
    for (final EmbeddedConfigProvider candidateProvider : configProviders) {
      candidateProvider.init();
      if (!candidateProvider.isActive())
        continue;
      if (provider != null) {
        throw new IllegalStateException("Multiple active EmbeddedH2OConfig providers: " + provider.getName() +
                " and " + candidateProvider.getName() + " (possibly other as well).");
      }
      provider = candidateProvider;
    }

    return Optional.ofNullable(provider);
  }

  /**
   * Tell the embedding software that this H2O instance belongs to
   * a cloud of a certain size.
   * This may be non-blocking.
   *
   * @param ip IP address this H2O can be reached at.
   * @param port Port this H2O can be reached at (for REST API and browser).
   * @param size Number of H2O instances in the cloud.
   */
  public static void notifyAboutCloudSize(InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
    if (ARGS.notify_local != null && !ARGS.notify_local.trim().isEmpty()) {
      final File notifyFile = new File(ARGS.notify_local);
      final File parentDir = notifyFile.getParentFile();
      if (parentDir != null && !parentDir.isDirectory()) {
        if (!parentDir.mkdirs()) {
          Log.err("Cannot make parent dir for notify file.");
          H2O.exit(-1);
        }
      }
      try(BufferedWriter output = new BufferedWriter(new FileWriter(notifyFile))) {
        output.write(SELF_ADDRESS.getHostAddress());
        output.write(':');
        output.write(Integer.toString(API_PORT));
        output.flush();
      } catch (IOException e) {
        Log.err("Unable to write notify file.");
        H2O.exit(-1);
      }
    }
    if (embeddedH2OConfig != null) {
      embeddedH2OConfig.notifyAboutCloudSize(ip, port, leaderIp, leaderPort, size);
    }
  }


  public static void closeAll() {
    try { H2O.getWebServer().stop(); } catch( Exception ignore ) { }
    try { NetworkInit.close(); } catch( IOException ignore ) { }
    PersistManager PM = H2O.getPM();
    if( PM != null ) PM.getIce().cleanUp();
  }


  /** Notify embedding software instance H2O wants to exit.  Shuts down a single Node.
   *  @param status H2O's requested process exit value.
   */
  public static void exit(int status) {
    // Log subsystem might be still caching message, let it know to flush the cache and start logging even if we don't have SELF yet
    Log.notifyAboutProcessExiting();

    exitQuietly(status);
  }

  /**
   * Notify embedding software instance H2O wants to exit.  Shuts down a single Node.
   * Exit without logging any buffered messages, invoked when H2O arguments are not correctly parsed
   * and we might thus not be able to successfully initialize the logging subsystem.
   * 
   * @param status H2O's requested process exit value.
   */
  private static void exitQuietly(int status) {
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

  /** Orderly shutdown with infinite timeout for confirmations from the nodes in the cluster */
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
        fs.add(new RPC(n, new ShutdownTsk(H2O.SELF,n.index(), 1000, confirmations, 0)).call());
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

  public static final AbstractBuildVersion ABV = AbstractBuildVersion.getBuildVersion();

  //-------------------------------------------------------------------------------------------------------------------

  private static boolean _haveInheritedLog4jConfiguration = false;
  public static boolean haveInheritedLog4jConfiguration() {
    return _haveInheritedLog4jConfiguration;
  }

  public static void configureLogging() {
    if (LogManager.getCurrentLoggers().hasMoreElements()) {
      _haveInheritedLog4jConfiguration = true;
      return;
    } else if (System.getProperty("log4j.configuration") != null) {
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
    p.setProperty("log4j.rootCategory", "WARN, console");
    p.setProperty("log4j.logger.org.eclipse.jetty", "WARN");

    p.setProperty("log4j.appender.console", "org.apache.log4j.ConsoleAppender");
    p.setProperty("log4j.appender.console.layout", "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.console.layout.ConversionPattern", "%m%n");

    PropertyConfigurator.configure(p);
    System.setProperty("org.eclipse.jetty.LEVEL", "WARN");

    // Log jetty stuff to stdout for now.
    // TODO:  figure out how to wire this into log4j.
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
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

  private static final AtomicLong nextModelNum = new AtomicLong(0);
  
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
  public static String calcNextUniqueModelId(String desc) {
    return calcNextUniqueObjectId("model", nextModelNum, desc);
  }
  synchronized public static String calcNextUniqueObjectId(String type, AtomicLong sequenceSource, String desc) {
    StringBuilder sb = new StringBuilder();
    sb.append(desc).append('_').append(type).append('_');

    // Append user agent string if we can figure it out.
    String source = ServletUtils.getUserAgent();
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
    long n = sequenceSource.addAndGet(1);
    sb.append(CLUSTER_ID).append("_").append(n);

    return sb.toString();
  }

  //-------------------------------------------------------------------------------------------------------------------

  // This piece of state is queried by Steam.
  // It's used to inform the Admin user the last time each H2O instance did something.
  // Admins can take this information and decide whether to kill idle clusters to reclaim tied up resources.

  private static volatile long lastTimeSomethingHappenedMillis = System.currentTimeMillis();

  private static volatile AtomicInteger activeRapidsExecs = new AtomicInteger();

  /**
   * Get the number of milliseconds the H2O cluster has been idle.
   * @return milliseconds since the last interesting thing happened.
   */
  public static long getIdleTimeMillis() {
    long latestEndTimeMillis = -1;

    // If there are any running rapids queries, consider that not idle.
    if (activeRapidsExecs.get() > 0) {
      updateNotIdle();
    }
    else {
      // If there are any running jobs, consider that not idle.
      // Remember the latest job ending time as well.
      Job[] jobs = Job.jobs();
      for (int i = jobs.length - 1; i >= 0; i--) {
        Job j = jobs[i];
        if (j.isRunning()) {
          updateNotIdle();
          break;
        }

        if (j.end_time() > latestEndTimeMillis) {
          latestEndTimeMillis = j.end_time();
        }
      }
    }

    long latestTimeMillis = Math.max(latestEndTimeMillis, lastTimeSomethingHappenedMillis);

    // Calculate milliseconds and clamp at zero.
    long now = System.currentTimeMillis();
    long deltaMillis = now - latestTimeMillis;
    if (deltaMillis < 0) {
      deltaMillis = 0;
    }
    return deltaMillis;
  }

  /**
   * Update the last time that something happened to reset the idle timer.
   * This is meant to be callable safely from almost anywhere.
   */
  public static void updateNotIdle() {
    lastTimeSomethingHappenedMillis = System.currentTimeMillis();
  }

  /**
   * Increment the current number of active Rapids exec calls.
   */
  public static void incrementActiveRapidsCounter() {
    updateNotIdle();
    activeRapidsExecs.incrementAndGet();
  }

  /**
   * Decrement the current number of active Rapids exec calls.
   */
  public static void decrementActiveRapidsCounter() {
    updateNotIdle();
    activeRapidsExecs.decrementAndGet();
  }

  //-------------------------------------------------------------------------------------------------------------------

  // Atomically set once during startup.  Guards against repeated startups.
  public static final AtomicLong START_TIME_MILLIS = new AtomicLong(); // When did main() run

  // Used to gate default worker threadpool sizes
  public static final int NUMCPUS = H2ORuntime.availableProcessors();

  // Best-guess process ID
  public static final long PID;
  static {
    PID = getCurrentPID();
  }

  // Extension Manager instance
  private static final ExtensionManager extManager = ExtensionManager.getInstance();

  /**
   * Retrieves a value of an H2O system property.
   * 
   * H2O system properties have {@link OptArgs#SYSTEM_PROP_PREFIX} prefix.
   * 
   * @param name property name
   * @param def default value
   * @return value of the system property or default value if property was not defined
   */
  public static String getSysProperty(String name, String def) {
    return System.getProperty(H2O.OptArgs.SYSTEM_PROP_PREFIX + name, def);
  }
  
  /**
   * Retrieves a boolean value of an H2O system property.
   *
   * H2O system properties have {@link OptArgs#SYSTEM_PROP_PREFIX} prefix.
   *
   * @param name property name
   * @param def default value
   * @return value of the system property as boolean or default value if property was not defined. False returned if 
   *    the system property value is set but it is not "true" or any upper/lower case variant of it.
   */
  public static boolean getSysBoolProperty(String name, boolean def) {
    return Boolean.parseBoolean(getSysProperty(name, String.valueOf(def)));
  }

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
   * there's a typing error on your schema; your switch statement didn't cover all the AstRoot
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
    Log.fatal(new Exception(msg));

    // H2O fail() exists because of coding errors - but what if usage of fail() was itself a coding error?
    // Property "suppress.shutdown.on.failure" can be used in the case when someone is seeing shutdowns on production
    // because a developer incorrectly used fail() instead of just throwing a (recoverable) exception
    boolean suppressShutdown = getSysBoolProperty("suppress.shutdown.on.failure", false);
    if (! suppressShutdown) {
      H2O.shutdown(-1);
    } else {
      throw new IllegalStateException("Suppressed shutdown for failure: " + msg, cause);
    }

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
    return message + "\n\n" +
        "For more information visit:\n" +
        "  https://github.com/h2oai/h2o-3/discussions/" + GITHUB_DISCUSSIONS.get(number);
  }

  /**
   * Return an error message with an accompanying list of URLs to help the user get more detailed information.
   *
   * @param numbers H2O tech note numbers.
   * @param message Message to present to the user.
   * @return A longer message including a list of URLs.
   */
  public static String technote(int[] numbers, String message) {
    StringBuilder sb = new StringBuilder()
            .append(message)
            .append("\n")
            .append("\n")
            .append("For more information visit:\n");

    for (int number : numbers) {
      sb.append("  https://github.com/h2oai/h2o-3/discussions/").append(GITHUB_DISCUSSIONS.get(number)).append("\n");
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
  public static final byte    ACK_ACK_PRIORITY = MAX_PRIORITY;   //126
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
            p>=MIN_HI_PRIORITY /* low priority FJQs should use the default FJ settings to use LIFO order of thread private queues. */);
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

  // For testing purposes (verifying API work exceeds grunt model-build work)
  // capture the class of any submitted job lower than this priority;
  static public int LOW_PRIORITY_API_WORK;
  static public String LOW_PRIORITY_API_WORK_CLASS;

  // Submit to the correct priority queue
  public static <T extends H2OCountedCompleter> T submitTask( T task ) {
    int priority = task.priority();
    if( priority < LOW_PRIORITY_API_WORK )
      LOW_PRIORITY_API_WORK_CLASS = task.getClass().toString();
    assert MIN_PRIORITY <= priority && priority <= MAX_PRIORITY:"priority " + priority + " is out of range, expected range is < " + MIN_PRIORITY + "," + MAX_PRIORITY + ">";
    if( FJPS[priority]==null )
      synchronized( H2O.class ) { if( FJPS[priority] == null ) FJPS[priority] = new PrioritizedForkJoinPool(priority,-1); }
    FJPS[priority].submit(task);
    return task;
  }

  /**
   * Executes a runnable on a regular H2O Node (= not on a client).
   * If the current H2O Node is a regular node, the runnable will be executed directly (RemoteRunnable#run will be invoked).
   * If the current H2O Node is a client node, the runnable will be send to a leader node of the cluster and executed there.
   * The caller shouldn't make any assumptions on where the code will be run.
   * @param runnable code to be executed
   * @param <T> RemoteRunnable
   * @return executed runnable (will be a different instance if executed remotely).
   */
  public static <T extends RemoteRunnable> T runOnH2ONode(T runnable) {
    H2ONode node = H2O.ARGS.client ? H2O.CLOUD.leader() : H2O.SELF;
    return runOnH2ONode(node, runnable);
  }

  public static <T extends RemoteRunnable> T runOnLeaderNode(T runnable) {
    return runOnH2ONode(H2O.CLOUD.leader(), runnable);
  }

  // package-private for unit tests
  static <T extends RemoteRunnable> T runOnH2ONode(H2ONode node, T runnable) {
    if (node == H2O.SELF) {
      // run directly
      runnable.run();
      return runnable;
    } else {
      RunnableWrapperTask<T> task = new RunnableWrapperTask<>(runnable);
      try {
        return new RPC<>(node, task).call().get()._runnable;
      } catch (DistributedException e) {
        Log.trace("Exception in calling runnable on a remote node",  e);
        Throwable cause = e.getCause();
        throw cause instanceof RuntimeException ? (RuntimeException) cause : e;
      }
    }
  }

  private static class RunnableWrapperTask<T extends RemoteRunnable> extends DTask<RunnableWrapperTask<T>> {
    private final T _runnable;
    private RunnableWrapperTask(T runnable) {
      _runnable = runnable;
    }
    @Override
    public void compute2() {
      _runnable.setupOnRemote();
      _runnable.run();
      tryComplete();
    }
  }

  public abstract static class RemoteRunnable<T extends RemoteRunnable> extends Iced<T> {
    public void setupOnRemote() {}
    public abstract void run();
  }

  /** Simple wrapper over F/J {@link CountedCompleter} to support priority
   *  queues.  F/J queues are simple unordered (and extremely light weight)
   *  queues.  However, we frequently need priorities to avoid deadlock and to
   *  promote efficient throughput (e.g. failure to respond quickly to {@link
   *  TaskGetKey} can block an entire node for lack of some small piece of
   *  data).  So each attempt to do lower-priority F/J work starts with an
   *  attempt to work and drain the higher-priority queues. */
  public static abstract class H2OCountedCompleter<T extends H2OCountedCompleter>
      extends CountedCompleter
      implements Cloneable, Freezable<T> {

    @Override
    public byte [] asBytes(){return new AutoBuffer().put(this).buf();}

    @Override
    public T reloadFromBytes(byte [] ary){ return read(new AutoBuffer(ary));}

    private /*final*/ byte _priority;
    // Without a completer, we expect this task will be blocked on - so the
    // blocking thread is not available in the current thread pool, so the
    // launched task needs to run at a higher priority.
    public H2OCountedCompleter( ) { this(null); }
    // With a completer, this task will NOT be blocked on and the the current
    // thread is available for executing it... so the priority can remain at
    // the current level.
    static private byte computePriority( H2OCountedCompleter completer ) {
      int currThrPrior = currThrPriority();
      // If there's no completer, then current thread will block on this task
      // at the current priority, possibly filling up the current-priority
      // thread pool - so the task has to run at the next higher priority.
      if( completer == null ) return (byte)(currThrPrior+1);
      // With a completer - no thread blocks on this task, so no thread pool
      // gets filled-up with blocked threads.  We can run at the current
      // priority (or the completer's priority if it's higher).
      return (byte)Math.max(currThrPrior,completer.priority());
    }
    protected H2OCountedCompleter(H2OCountedCompleter completer) { this(completer,computePriority(completer));  }
    // Special for picking GUI priorities
    protected H2OCountedCompleter( byte prior ) { this(null,prior); }

    protected H2OCountedCompleter(H2OCountedCompleter completer, byte prior) {
      super(completer);
      _priority = prior;
    }

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
        else { ex.printStackTrace(); throw ex; }
      } finally {
        t._priority = pp;
        if( pp == MIN_PRIORITY && set_t_prior ) t.setPriority(Thread.NORM_PRIORITY-1);
      }
      // Now run the task as planned
      if( this instanceof DTask ) icer().compute1(this);
      else compute2();
    }

    public void compute1() { compute2(); }

    /** Override compute3() with actual work without having to worry about tryComplete() */
    public void compute2() {}

    // In order to prevent deadlock, threads that block waiting for a reply
    // from a remote node, need the remote task to run at a higher priority
    // than themselves.  This field tracks the required priority.
    protected final byte priority() { return _priority; }
    @Override public final T clone(){
      try { return (T)super.clone(); }
      catch( CloneNotSupportedException e ) { throw Log.throwErr(e); }
    }

    /** If this is a F/J thread, return it's priority - used to lift the
     *  priority of a blocking remote call, so the remote node runs it at a
     *  higher priority - so we don't deadlock when we burn the local
     *  thread. */
    protected static byte currThrPriority() {
      Thread cThr = Thread.currentThread();
      return (byte)((cThr instanceof FJWThr) ? ((FJWThr)cThr)._priority : MIN_PRIORITY);
    }

    // The serialization flavor / delegate.  Lazily set on first use.
    private short _ice_id;

    /** Find the serialization delegate for a subclass of this class */
    protected Icer<T> icer() {
      int id = _ice_id;
      if(id != 0) {
        int tyid;
        if (id != 0)
          assert id == (tyid = TypeMap.onIce(this)) : "incorrectly cashed id " + id + ", typemap has " + tyid + ", type = " + getClass().getName();
      }
      return TypeMap.getIcer(id!=0 ? id : (_ice_id=(short)TypeMap.onIce(this)),this);
    }
    @Override final public AutoBuffer write    (AutoBuffer ab) { return icer().write    (ab,(T)this); }
    @Override final public AutoBuffer writeJSON(AutoBuffer ab) { return icer().writeJSON(ab,(T)this); }
    @Override final public T read    (AutoBuffer ab) { return icer().read    (ab,(T)this); }
    @Override final public T readJSON(AutoBuffer ab) { return icer().readJSON(ab,(T)this); }
    @Override final public int frozenType() { return icer().frozenType();   }
  }


  public static abstract class H2OCallback<T extends H2OCountedCompleter> extends H2OCountedCompleter{
    public H2OCallback(){}
    public H2OCallback(H2OCountedCompleter cc){super(cc);}
    @Override
    public void compute2(){throw H2O.fail();}
    @Override public    void onCompletion(CountedCompleter caller){callback((T) caller);}
    public abstract void callback(T t);
  }

  public static int H2O_PORT; // H2O TCP Port
  public static int API_PORT; // RequestServer and the API HTTP port

  /**
   * @return String of the form ipaddress:port
   */
  public static String getIpPortString() {
    return H2O.ARGS.disable_web? "" : H2O.SELF_ADDRESS.getHostAddress() + ":" + H2O.API_PORT;
  }

  public static String getURL(String schema) {
    return getURL(schema, H2O.SELF_ADDRESS, H2O.API_PORT, H2O.ARGS.context_path);
  }

  public static String getURL(String schema, InetAddress address, int port, String contextPath) {
    return String.format(address instanceof Inet6Address
                    ? "%s://[%s]:%d%s" : "%s://%s:%d%s",
            schema, address.getHostAddress(), port, contextPath);
  }

  public static String getURL(String schema, String hostname, int port, String contextPath) {
    return String.format("%s://%s:%d%s", schema, hostname, port, contextPath);
  }

  // The multicast discovery port
  public static MulticastSocket  CLOUD_MULTICAST_SOCKET;
  public static NetworkInterface CLOUD_MULTICAST_IF;
  public static InetAddress      CLOUD_MULTICAST_GROUP;
  public static int              CLOUD_MULTICAST_PORT ;

  /** Myself, as a Node in the Cloud */
  public static H2ONode SELF = null;
  /** IP address of this node used for communication
   * with other nodes.
   */
  public static InetAddress SELF_ADDRESS;

  /* Global flag to mark this specific cloud instance IPv6 only.
   * Right now, users have to force IPv6 stack by specifying the following
   * JVM options:
   *  -Djava.net.preferIPv6Addresses=true
   *  -Djava.net.preferIPv6Addresses=false
   */
  static final boolean IS_IPV6 = NetworkUtils.isIPv6Preferred() && !NetworkUtils.isIPv4Preferred();

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

  /* A static list of acceptable Cloud members passed via -flatfile option.
   * It is updated also when a new client appears. */
  private static Set<H2ONode> STATIC_H2OS = null;
  
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

  private static WebServer webServer;
  public static void setWebServer(WebServer value) {
    webServer = value;
  }
  public static WebServer getWebServer() {
    return webServer;
  }

  /** If logging has not been setup yet, then Log.info will only print to
   *  stdout.  This allows for early processing of the '-version' option
   *  without unpacking the jar file and other startup stuff.  */
  private static void printAndLogVersion(String[] arguments) {
    Log.init(ARGS.log_level, ARGS.quiet, ARGS.max_log_file_size);
    Log.info("----- H2O started " + (ARGS.client?"(client)":"") + " -----");
    Log.info("Build git branch: " + ABV.branchName());
    Log.info("Build git hash: " + ABV.lastCommitHash());
    Log.info("Build git describe: " + ABV.describe());
    Log.info("Build project version: " + ABV.projectVersion());
    Log.info("Build age: " + PrettyPrint.toAge(ABV.compiledOnDate(), new Date()));
    Log.info("Built by: '" + ABV.compiledBy() + "'");
    Log.info("Built on: '" + ABV.compiledOn() + "'");

    if (ABV.isTooOld()) {
      Log.warn("\n*** Your H2O version is over 100 days old. Please download the latest version from: https://h2o-release.s3.amazonaws.com/h2o/latest_stable.html ***");
      Log.warn("");
    }

    Log.info("Found H2O Core extensions: " + extManager.getCoreExtensions());
    Log.info("Processed H2O arguments: ", Arrays.toString(arguments));

    Runtime runtime = Runtime.getRuntime();
    Log.info("Java availableProcessors: " + H2ORuntime.availableProcessors());
    Log.info("Java heap totalMemory: " + PrettyPrint.bytes(runtime.totalMemory()));
    Log.info("Java heap maxMemory: " + PrettyPrint.bytes(runtime.maxMemory()));
    Log.info("Java version: Java "+System.getProperty("java.version")+" (from "+System.getProperty("java.vendor")+")");
    List<String> launchStrings = ManagementFactory.getRuntimeMXBean().getInputArguments();
    Log.info("JVM launch parameters: "+launchStrings);
    Log.info("JVM process id: " + ManagementFactory.getRuntimeMXBean().getName());
    Log.info("OS version: "+System.getProperty("os.name")+" "+System.getProperty("os.version")+" ("+System.getProperty("os.arch")+")");
    long totalMemory = OSUtils.getTotalPhysicalMemory();
    Log.info ("Machine physical memory: " + (totalMemory==-1 ? "NA" : PrettyPrint.bytes(totalMemory)));
    Log.info("Machine locale: " + Locale.getDefault());
  }

  /** Initializes the local node and the local cloud with itself as the only member. */
  private static void startLocalNode() {
    // Figure self out; this is surprisingly hard
    NetworkInit.initializeNetworkSockets();

    // Do not forget to put SELF into the static configuration (to simulate proper multicast behavior)
    if ( !ARGS.client && H2O.isFlatfileEnabled() && !H2O.isNodeInFlatfile(SELF)) {
      Log.warn("Flatfile configuration does not include self: " + SELF + ", but contains " + H2O.getFlatfile());
      H2O.addNodeToFlatfile(SELF);
    }

    if (!H2O.ARGS.disable_net) {
      Log.info("H2O cloud name: '" + ARGS.name + "' on " + SELF +
              (H2O.isFlatfileEnabled()
                      ? ", static configuration based on -flatfile " + ARGS.flatfile
                      : (", discovery address " + CLOUD_MULTICAST_GROUP + ":" + CLOUD_MULTICAST_PORT)));
    }

    if (!H2O.ARGS.disable_web) {
      Log.info("If you have trouble connecting, try SSH tunneling from your local machine (e.g., via port 55555):\n" +
              "  1. Open a terminal and run 'ssh -L 55555:localhost:"
              + API_PORT + " " + System.getProperty("user.name") + "@" + SELF_ADDRESS.getHostAddress() + "'\n" +
              "  2. Point your browser to " + NetworkInit.h2oHttpView.getScheme() + "://localhost:55555");
    }

    if (H2O.ARGS.rest_api_ping_timeout > 0) {
      Log.info(String.format("Registering REST API Check Thread. If 3/Ping endpoint is not" +
          " accessed during %d ms, the cluster will be terminated.", H2O.ARGS.rest_api_ping_timeout));
      new RestApiPingCheckThread().start();
    }
    // Create the starter Cloud with 1 member
    SELF._heartbeat._jar_md5 = JarHash.JARHASH;
    SELF._heartbeat._client = ARGS.client;
    SELF._heartbeat._cloud_name_hash = ARGS.name.hashCode();
  }

  /** Starts the worker threads, receiver threads, heartbeats and all other
   *  network related services.  */
  private static void startNetworkServices() {
    // Start the Persistent meta-data cleaner thread, which updates the K/V
    // mappings periodically to disk. There should be only 1 of these, and it
    // never shuts down.  Needs to start BEFORE the HeartBeatThread to build
    // an initial histogram state.
    Cleaner.THE_CLEANER.start();

    if (H2O.ARGS.disable_net)
      return;

    // We've rebooted the JVM recently. Tell other Nodes they can ignore task
    // prior tasks by us. Do this before we receive any packets
    UDPRebooted.T.reboot.broadcast();

    // Start the MultiReceiverThread, to listen for multi-cast requests from
    // other Cloud Nodes. There should be only 1 of these, and it never shuts
    // down. Started soon, so we can start parsing multi-cast UDP packets
    new MultiReceiverThread().start();

    // Start the TCPReceiverThread, to listen for TCP requests from other Cloud
    // Nodes. There should be only 1 of these, and it never shuts down.
    NetworkInit.makeReceiverThread().start();
  }

  @Deprecated
  static public void register(
          String method_url, Class<? extends water.api.Handler> hclass, String method, String apiName, String summary
  ) {
    Log.warn("The H2O.register method is deprecated and will be removed in the next major release." +
            "Please register REST API endpoints as part of their corresponding REST API extensions!");
    RequestServer.registerEndpoint(apiName, method_url, hclass, method, summary);
  }

  public static void registerResourceRoot(File f) {
    JarHash.registerResourceRoot(f);
  }

  /** Start the web service; disallow future URL registration.
   *  Blocks until the server is up.
   *
   *  @deprecated use starServingRestApi
   */
  @Deprecated
  static public void finalizeRegistration() {
    startServingRestApi();
  }

  /**
   * This switch Jetty into accepting mode.
   */
  public static void startServingRestApi() {
    if (!H2O.ARGS.disable_web) {
      NetworkInit.h2oHttpView.acceptRequests();
    }
  }

  // --------------------------------------------------------------------------
  // The Current Cloud. A list of all the Nodes in the Cloud. Changes if we
  // decide to change Clouds via atomic Cloud update.
  public static volatile H2O CLOUD = new H2O(new H2ONode[0],0,0);

  // ---
  // A dense array indexing all Cloud members. Fast reversal from "member#" to
  // Node.  No holes.  Cloud size is _members.length.
  public final H2ONode[] _memary;

  // mapping from a node ip to node index
  private HashMap<String, Integer> _node_ip_to_index;
  final int _hash;

  public H2ONode getNodeByIpPort(String ipPort) {
    if(_node_ip_to_index != null) {
      Integer index = _node_ip_to_index.get(ipPort);
      if (index != null) {
        if(index == -1){
          return H2O.SELF;
        } else if(index <= -1 || index >= _memary.length){
          // index -1 should not happen anymore as well
          throw new RuntimeException("Mapping from node id to node index contains: " + index + ", however this node" +
                  "does not exist!");
        }
        return _memary[index];
      } else {
        // no node with such ip:port
        return null;
      }
    } else {
      // mapping is null, no cloud ready yet
      return null;
    }
  }

  // A dense integer identifier that rolls over rarely. Rollover limits the
  // number of simultaneous nested Clouds we are operating on in-parallel.
  // Really capped to 1 byte, under the assumption we won't have 256 nested
  // Clouds. Capped at 1 byte so it can be part of an atomically-assigned
  // 'long' holding info specific to this Cloud.
  final char _idx; // no unsigned byte, so unsigned char instead

  // Construct a new H2O Cloud from the member list
  H2O( H2ONode[] h2os, int hash, int idx ) {
    this(h2os, false, hash, idx);
  }

  H2O( H2ONode[] h2os, boolean presorted, int hash, int idx ) {
    _memary = h2os;
    if (!presorted)
      java.util.Arrays.sort(_memary); // ... sorted!
    _hash = hash;                     // And record hash for cloud rollover
    _idx = (char)(idx&0x0ff);         // Roll-over at 256
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

    H2O.CLOUD._node_ip_to_index = new HashMap<>();
    for(H2ONode node: H2O.CLOUD._memary){
      H2O.CLOUD._node_ip_to_index.put(node.getIpPortString(), node.index());
    }
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
  public boolean isSingleNode() { return size() == 1; }
  public final H2ONode leader() {
    return _memary[0];
  }
  public final H2ONode leaderOrNull() {
    return _memary.length > 0 ? _memary[0] : null;
  }

  // Find the node index for this H2ONode, or a negative number on a miss
  int nidx( H2ONode h2o ) { return java.util.Arrays.binarySearch(_memary,h2o); }
  boolean contains( H2ONode h2o ) { return nidx(h2o) >= 0; }
  @Override public String toString() {
    return java.util.Arrays.toString(_memary);
  }
  public H2ONode[] members() { return _memary; }

  // Cluster free memory
  public long free_mem() {
    long memsz = 0;
    for( H2ONode h2o : CLOUD._memary )
      memsz += h2o._heartbeat.get_free_mem();
    return memsz;
  }

  // Quick health check; no reason given for bad health
  public boolean healthy() {
    long now = System.currentTimeMillis();
    for (H2ONode node : H2O.CLOUD.members())
      if (!node.isHealthy(now))
        return false;
    return true;
  }

  public static void waitForCloudSize(int x, long ms) {
    long start = System.currentTimeMillis();
    if(!cloudIsReady(x)) 
      Log.info("Waiting for clouding to finish. Current number of nodes " + CLOUD.size() + ". Target number of nodes: " + x);
    while (System.currentTimeMillis() - start < ms) {
      if (cloudIsReady(x))
        break;
      try { Thread.sleep(100); } catch (InterruptedException ignore) {}
    }
    if (CLOUD.size() < x)
      throw new RuntimeException("Cloud size " + CLOUD.size() + " under " + x + ". Consider to increase `DEFAULT_TIME_FOR_CLOUDING`.");
  }

  private static boolean cloudIsReady(int x) {
    return CLOUD.size() >= x && Paxos._commonKnowledge;
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
  }

  // --------------------------------------------------------------------------
  // The (local) set of Key/Value mappings.
  public static final NonBlockingHashMap<Key,Value> STORE = new NonBlockingHashMap<>();

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
      if( old==null ) Scope.track_internal(key); // New Key - start tracking
    }
    return old; // Return success
  }

  // Get the value from the store
  public static void raw_remove(Key key) {
    Value v = STORE.remove(key);
    if( v != null ) v.removePersist();
  }
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
      public GCTask() {super(GUI_PRIORITY);}
      @Override public void compute2() {
        Log.info("Calling System.gc() now...");
        System.gc();
        Log.info("System.gc() finished");
        tryComplete();
      }
    }

    for (H2ONode node : H2O.CLOUD._memary) {
      GCTask t = new GCTask();
      new RPC<>(node, t).call().get();
    }
  }

  private static boolean JAVA_CHECK_PASSED = false;

  /**
   * Check if the Java version is not supported
   *
   * @return true if not supported
   */
  public static boolean checkUnsupportedJava(String[] args) {
    if (JAVA_CHECK_PASSED)
      return false;
    if (Boolean.getBoolean(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.noJavaVersionCheck")) {
      return false;
    } 
    boolean unsupported = runCheckUnsupportedJava(args);
    if (!unsupported) {
      JAVA_CHECK_PASSED = true;
    }
    return unsupported;
  }

  static boolean runCheckUnsupportedJava(String[] args) {
    if (!JavaVersionSupport.runningOnSupportedVersion()) {
      Throwable error = null;
      boolean allowUnsupported = ArrayUtils.contains(args, "-allow_unsupported_java");
      if (allowUnsupported) {
        boolean checkPassed = false;
        try {
          checkPassed = dynamicallyInvokeJavaSelfCheck();
        } catch (Throwable t) {
          error = t;
        }
        if (checkPassed) {
          Log.warn("H2O is running on a version of Java (" + System.getProperty("java.version") + ") that was not certified at the time of the H2O release. " + 
                  "For production use please use a certified Java version (versions " + JavaVersionSupport.describeSupportedVersions() + " are officially supported).");
          return false;
        }
      }
      System.err.printf("Only Java versions %s are supported, system version is %s%n",
              JavaVersionSupport.describeSupportedVersions(),
              System.getProperty("java.version"));
      if (ARGS.allow_unsupported_java) {
        System.err.println("H2O was invoked with flag -allow_unsupported_java, however, " +
                "we found out that your Java version doesn't meet the requirements to run H2O. Please use a supported Java version.");
      }
      if (error != null)
        error.printStackTrace(System.err);
      return true;
    }
    String vmName = System.getProperty("java.vm.name");
    if (vmName != null && vmName.equals("GNU libgcj")) {
      System.err.println("GNU gcj is not supported");
      return true;
    }
    return false;
  }

  /**
   * Dynamically invoke water.JavaSelfCheck#checkCompatibility. The call is dynamic in order to prevent
   * classloading issues to even load this class.
   *
   * @return true if Java-compatibility self-check passes successfully
   * @throws ClassNotFoundException
   * @throws NoSuchMethodException
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  static boolean dynamicallyInvokeJavaSelfCheck() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Class<?> cls = Class.forName("water.JavaSelfCheck");
    Method m = cls.getDeclaredMethod("checkCompatibility");
    return (Boolean) m.invoke(null);
  }

  /**
   * Any system property starting with `ai.h2o.` and containing any more `.` does not match
   * this pattern and is therefore ignored. This is mostly to prevent system properties
   * serving as configuration for H2O's dependencies (e.g. `ai.h2o.org.eclipse.jetty.LEVEL` ).
   */
  static boolean isArgProperty(String name) {
    final String prefix = "ai.h2o.";
    if (!name.startsWith(prefix))
      return false;
    return name.lastIndexOf('.') < prefix.length(); 
  }

  // --------------------------------------------------------------------------
  public static void main( String[] args ) {
   H2O.configureLogging();
   extManager.registerCoreExtensions();
   extManager.registerListenerExtensions();
   extManager.registerAuthExtensions();

   long time0 = System.currentTimeMillis();

   if (checkUnsupportedJava(args))
     throw new RuntimeException("Unsupported Java version");

    // Record system start-time.
    if( !START_TIME_MILLIS.compareAndSet(0L, System.currentTimeMillis()) )
      return;                   // Already started

    // Copy all ai.h2o.* system properties to the tail of the command line,
    // effectively overwriting the earlier args.
    ArrayList<String> args2 = new ArrayList<>(Arrays.asList(args));
    for( Object p : System.getProperties().keySet() ) {
      String s = (String) p;
      if(isArgProperty(s)) {
        args2.add("-" + s.substring(7));
        // hack: Junits expect properties, throw out dummy prop for ga_opt_out
        if (!s.substring(7).equals("ga_opt_out") && !System.getProperty(s).isEmpty())
          args2.add(System.getProperty(s));
      }
    }

    // Parse args
    String[] arguments = args2.toArray(args);
    parseArguments(arguments);

    // Get ice path before loading Log or Persist class
    long time1 = System.currentTimeMillis();
    String ice = DEFAULT_ICE_ROOT();
    if( ARGS.ice_root != null ) ice = ARGS.ice_root.replace("\\", "/");
    try {
      ICE_ROOT = new URI(ice);
    } catch(URISyntaxException ex) {
      throw new RuntimeException("Invalid ice_root: " + ice + ", " + ex.getMessage());
    }

    // Always print version, whether asked-for or not!
    long time2 = System.currentTimeMillis();
    printAndLogVersion(arguments);
    if( ARGS.version ) {
      Log.flushBufferedMessagesToStdout();
      exit(0);
    }

    // Print help & exit
    if (ARGS.help) {
      printHelp();
      exit(0);
    }

    // Validate arguments
    validateArguments();

    Log.info("X-h2o-cluster-id: " + H2O.CLUSTER_ID);
    Log.info("User name: '" + H2O.ARGS.user_name + "'");

    // Epic Hunt for the correct self InetAddress
    long time4 = System.currentTimeMillis();
    Log.info("IPv6 stack selected: " + IS_IPV6);
    SELF_ADDRESS = NetworkInit.findInetAddressForSelf();
    // Right now the global preference is to use IPv4 stack
    // To select IPv6 stack user has to explicitly pass JVM flags
    // to enable IPv6 preference.
    if (!IS_IPV6 && SELF_ADDRESS instanceof Inet6Address) {
      Log.err("IPv4 network stack specified but IPv6 address found: " + SELF_ADDRESS + "\n"
              + "Please specify JVM flags -Djava.net.preferIPv6Addresses=true and -Djava.net.preferIPv4Addresses=false to select IPv6 stack");
      H2O.exit(-1);
    }
    if (IS_IPV6 && SELF_ADDRESS instanceof Inet4Address) {
      Log.err("IPv6 network stack specified but IPv4 address found: " + SELF_ADDRESS);
      H2O.exit(-1);
    }

    // Start the local node.  Needed before starting logging.
    long time5 = System.currentTimeMillis();
    startLocalNode();

    // Allow core extensions to perform initialization that requires the network.
    long time6 = System.currentTimeMillis();
    for (AbstractH2OExtension ext: extManager.getCoreExtensions()) {
      ext.onLocalNodeStarted();
    }

    try {
      String logDir = Log.getLogDir();
      Log.info("Log dir: '" + logDir + "'");
    }
    catch (Exception e) {
      Log.info("Log dir: (Log4j configuration inherited)");
    }

    Log.info("Cur dir: '" + System.getProperty("user.dir") + "'");

    //Print extra debug info now that logs are setup
    long time7 = System.currentTimeMillis();
    RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
    Log.debug("H2O launch parameters: "+ARGS.toString());
    if (rtBean.isBootClassPathSupported()) {
      Log.debug("Boot class path: " + rtBean.getBootClassPath());
    }
    Log.debug("Java class path: "+ rtBean.getClassPath());
    Log.debug("Java library path: "+ rtBean.getLibraryPath());

    // Load up from disk and initialize the persistence layer
    long time8 = System.currentTimeMillis();
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
    long time9 = System.currentTimeMillis();
    startNetworkServices();   // start server services
    Log.trace("Network services started");

    // The "Cloud of size N formed" message printed out by doHeartbeat is the trigger
    // for users of H2O to know that it's OK to start sending REST API requests.
    long time10 = System.currentTimeMillis();
    Paxos.doHeartbeat(SELF);
    assert SELF._heartbeat._cloud_hash != 0 || ARGS.client;

    // Start the heartbeat thread, to publish the Clouds' existence to other
    // Clouds. This will typically trigger a round of Paxos voting so we can
    // join an existing Cloud.
    new HeartBeatThread().start();

    long time11 = System.currentTimeMillis();

    // Log registered parsers
    Log.info("Registered parsers: " + Arrays.toString(ParserService.INSTANCE.getAllProviderNames(true)));

    // Start thread checking client disconnections
    if (Boolean.getBoolean(H2O.OptArgs.SYSTEM_PROP_PREFIX + "debug.clientDisconnectAttack")) {
      // for development only!
      Log.warn("Client Random Disconnect attack is enabled - use only for debugging! More warnings to follow ;)");
      new ClientRandomDisconnectThread().start();
    } else {
      // regular mode of operation
      new ClientDisconnectCheckThread().start();
    }

    if (isGCLoggingEnabled()) {
      Log.info(H2O.technote(16,
              "GC logging is enabled, you might see messages containing \"GC (Allocation Failure)\". " +
                      "Please note that this is a normal part of GC operations and occurrence of such messages doesn't directly indicate an issue."));
    }
    
    long time12 = System.currentTimeMillis();
    Log.debug("Timing within H2O.main():");
    Log.debug("    Args parsing & validation: " + (time1 - time0) + "ms");
    Log.debug("    Get ICE root: " + (time2 - time1) + "ms");
    Log.debug("    Print log version: " + (time4 - time2) + "ms");
    Log.debug("    Detect network address: " + (time5 - time4) + "ms");
    Log.debug("    Start local node: " + (time6 - time5) + "ms");
    Log.debug("    Extensions onLocalNodeStarted(): " + (time7 - time6) + "ms");
    Log.debug("    RuntimeMxBean: " + (time8 - time7) + "ms");
    Log.debug("    Initialize persistence layer: " + (time9 - time8) + "ms");
    Log.debug("    Start network services: " + (time10 - time9) + "ms");
    Log.debug("    Cloud up: " + (time11 - time10) + "ms");
    Log.debug("    Start GA: " + (time12 - time11) + "ms");
  }

  private static boolean isGCLoggingEnabled() {
    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    List<String> jvmArgs = runtimeMXBean.getInputArguments();
    for (String arg : jvmArgs) {
      if (arg.startsWith("-XX:+PrintGC") || 
              arg.equals("-verbose:gc") || 
              (arg.startsWith("-Xlog:") && arg.contains("gc")))
        return true;
    }
    return false;
  }
  
  /** Find PID of the current process, use -1 if we can't find the value. */
  private static long getCurrentPID() {
    try {
      String n = ManagementFactory.getRuntimeMXBean().getName();
      int i = n.indexOf('@');
      if(i != -1) {
        return Long.parseLong(n.substring(0, i));
      } else {
        return -1L;
      }
    } catch(Throwable ignore) {
      return -1L;
    }
  }

  // Die horribly
  public static void die(String s) {
    Log.fatal(s);
    H2O.exit(-1);
  }

  /**
   * Add node to a manual multicast list.
   * Note: the method is valid only if -flatfile option was specified on commandline
   *
   * @param node H2O node
   * @return true if the node was already in the multicast list.
   */
  public static boolean addNodeToFlatfile(H2ONode node) {
    assert isFlatfileEnabled() : "Trying to use flatfile, but flatfile is not enabled!";
    return STATIC_H2OS.add(node);
  }

  /**
   * Remove node from a manual multicast list.
   * Note: the method is valid only if -flatfile option was specified on commandline
   *
   * @param node H2O node
   * @return true if the node was in the multicast list.
   */
  public static boolean removeNodeFromFlatfile(H2ONode node) {
    assert isFlatfileEnabled() : "Trying to use flatfile, but flatfile is not enabled!";
    return STATIC_H2OS.remove(node);
  }

  /**
   * Check if a node is included in a manual multicast list.
   * Note: the method is valid only if -flatfile option was specified on commandline
   *
   * @param node H2O node
   * @return true if the node is in the multicast list.
   */
  public static boolean isNodeInFlatfile(H2ONode node) {
    assert isFlatfileEnabled() : "Trying to use flatfile, but flatfile is not enabled!";
    return STATIC_H2OS.contains(node);
  }

  /**
   * Check if manual multicast is enabled.
   *
   * @return true if -flatfile option was specified on commandline
   */
  public static boolean isFlatfileEnabled() {
    return STATIC_H2OS != null;
  }

  /**
   * Setup a set of nodes which should be contacted during manual multicast
   *
   * @param nodes set of H2O nodes
   */
  public static void setFlatfile(Set<H2ONode> nodes) {
    if (nodes == null) {
      STATIC_H2OS = null;
    } else {
      STATIC_H2OS = Collections.newSetFromMap(new ConcurrentHashMap<H2ONode, Boolean>());
      STATIC_H2OS.addAll(nodes);
    }
  }

  /**
   * Returns a set of nodes which are contacted during manual multi-cast.
   *
   * @return set of H2O nodes
   */
  public static Set<H2ONode> getFlatfile() {
    return new HashSet<>(STATIC_H2OS);
  }

  /**
   * Forgets H2O client
   */
  static boolean removeClient(H2ONode client){
    return client.removeClient();
  }

  public static H2ONode[] getClients(){
    return H2ONode.getClients();
  }

  public static H2ONode getClientByIPPort(String ipPort){
    return H2ONode.getClientByIPPort(ipPort);
  }

  public static Key<DecryptionTool> defaultDecryptionTool() {
    return H2O.ARGS.decrypt_tool != null ? Key.<DecryptionTool>make(H2O.ARGS.decrypt_tool) : null;
  }

  public static URI downloadLogs(URI destinationDir, LogArchiveContainer logContainer) {
    return LogsHandler.downloadLogs(destinationDir.toString(), logContainer);
  }

  public static URI downloadLogs(URI destinationDir, String logContainer) {
    return LogsHandler.downloadLogs(destinationDir.toString(), LogArchiveContainer.valueOf(logContainer));
  }

  public static URI downloadLogs(String destinationDir, LogArchiveContainer logContainer) {
    return LogsHandler.downloadLogs(destinationDir, logContainer);
  }
  
  public static URI downloadLogs(String destinationDir, String logContainer) {
    return LogsHandler.downloadLogs(destinationDir, LogArchiveContainer.valueOf(logContainer));
  }

  /**
   * Is this H2O cluster running in our continuous integration environment?
   * 
   * This information can be used to enable extended error output or force shutdown in case
   * an error is encountered. Use responsibly.
   * 
   * @return true, if running in CI
   */
  static boolean isCI() {
    return AbstractBuildVersion.getBuildVersion().isDevVersion() 
            && "jenkins".equals(System.getProperty("user.name"));
  }

}
