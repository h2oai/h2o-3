package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Mapper;
import water.H2O;
import water.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * Interesting Configuration properties:
 * mapper	mapred.local.dir=/tmp/hadoop-tomk/mapred/local/taskTracker/tomk/jobcache/job_local1117903517_0001/attempt_local1117903517_0001_m_000000_0
 */
public class h2omapper extends Mapper<Text, Text, Text, Text> {

  public static final String H2O_DRIVER_IP_KEY = "h2o.driver.ip";
  public static final String H2O_DRIVER_PORT_KEY = "h2o.driver.port";

  public static final String H2O_AUTH_PRINCIPAL = "h2o.auth.principal";
  public static final String H2O_HIVE_HOST = "h2o.hive.host";
  public static final String H2O_HIVE_PRINCIPAL = "h2o.hive.principal";
  
  public static final String H2O_MAPPER_ARGS_BASE = "h2o.mapper.args.";
  public static final String H2O_MAPPER_ARGS_LENGTH = "h2o.mapper.args.length";

  public static final String H2O_MAPPER_CONF_ARG_BASE = "h2o.mapper.conf.arg.";
  public static final String H2O_MAPPER_CONF_BASENAME_BASE = "h2o.mapper.conf.basename.";
  public static final String H2O_MAPPER_CONF_PAYLOAD_BASE = "h2o.mapper.conf.payload.";
  public static final String H2O_MAPPER_CONF_LENGTH = "h2o.mapper.conf.length";

  /**
   * Identify hadoop mapper counter
   */
  public enum H2O_MAPPER_COUNTER {
    HADOOP_COUNTER_HEARTBEAT
  }

  /**
   * Hadoop heartbeat keepalive thread.  Periodically update a counter so that
   * jobtracker knows not to kill the job.
   *
   * Default jobtracker timeout is 10 minutes, so this should be sufficiently
   * under that.
   */
  public class CounterThread extends Thread {
    Context _context;
    Counter _counter;
    final int SIXTY_SECONDS_MILLIS = 60 * 1000;

    CounterThread (Context context, Counter counter) {
      _context = context;
      _counter = counter;
    }

    @Override
    @SuppressWarnings("all")
    public void run() {
      while (true) {
        _context.progress();
        _counter.increment(1);
        try {
          Thread.sleep (SIXTY_SECONDS_MILLIS);
        }
        catch (Exception ignore) {}
      }
    }
  }

  /**
   * Under unusual debugging circumstances, it can be helpful to print out the command line arguments in this format.
   * @param arr Array of command-line arguments.
   */
  private static void printArgs(String[] arr) {
    Log.info("");
    Log.info("----- printArgs -----");
    for (int i = 0; i < arr.length; i++) {
      String s = arr[i];
      Log.info(i);
      if (s == null) {
        Log.info("null");
      }
      else {
        Log.info(s);
      }
    }
    Log.info("----------");
  }

  /**
   * This shouldn't be necessary, but is.  In one really weird Hadoop environment, we saw an argument coming across
   * from the driver as null.  This shouldn't be possible but it happened.  So repair it here, by forcing a null
   * to really be the empty string.
   *
   * @param args Array of command line arguments
   */
  private static void repairNullArgsAndWarnIfNecessary(String[] args) {
    boolean haveANullArg = false;
    for (String s : args) {
      if (s == null) {
        haveANullArg = true;
        break;
      }
    }

    if (haveANullArg) {
      Log.warn("Found a null command-line argument; printing all command-line arguments out now");
      printArgs(args);
    }

    for (int i = 0; i < args.length; i++) {
      String s = args[i];
      args[i] = (s == null) ? "" : s;
    }
  }
  
  private int run2(Context context) throws IOException {
    Configuration conf = context.getConfiguration();

    Counter counter = context.getCounter(H2O_MAPPER_COUNTER.HADOOP_COUNTER_HEARTBEAT);
    Thread counterThread = new CounterThread(context, counter);
    counterThread.start();

    String mapredLocalDir = conf.get("mapred.local.dir");
    String ice_root;
    if (mapredLocalDir.contains(",")) {
      ice_root = mapredLocalDir.split(",")[0];
    }
    else {
      ice_root = mapredLocalDir;
    }

    String driverIp = conf.get(H2O_DRIVER_IP_KEY);
    String driverPortString = conf.get(H2O_DRIVER_PORT_KEY);
    int driverPort = Integer.parseInt(driverPortString);

    ServerSocket ss = new ServerSocket();
    InetSocketAddress sa = new InetSocketAddress("127.0.0.1", 0);
    ss.bind(sa);
    int localPort = ss.getLocalPort();

    List<String> argsList = new ArrayList<String>();

    // Arguments set inside the mapper.
    argsList.add("-ice_root");
    argsList.add(ice_root);
    argsList.add("-hdfs_skip");

    // Arguments passed by the driver.
    int argsLength = Integer.parseInt(conf.get(H2O_MAPPER_ARGS_LENGTH));
    for (int i = 0; i < argsLength; i++) {
      String arg = conf.get(H2O_MAPPER_ARGS_BASE + i);
      argsList.add(arg);
    }

    // Config files passed by the driver.
    int confLength = Integer.parseInt(conf.get(H2O_MAPPER_CONF_LENGTH));
    for (int i = 0; i < confLength; i++) {
      String arg = conf.get(H2O_MAPPER_CONF_ARG_BASE + i);
      // For files which are not passed as args (i.e. SSL certs)
      if(null != arg && !arg.isEmpty()) {
        argsList.add(arg);
      }

      String basename = conf.get(H2O_MAPPER_CONF_BASENAME_BASE + i);
      File f = new File(ice_root);
      boolean b = f.exists();
      if (! b) {
        boolean success = f.mkdirs();
        if (! success) {
          Log.POST(103, "mkdirs(" + f.toString() + ") failed");
          return -1;
        }
        Log.POST(104, "after mkdirs()");
      }
      String fileName = ice_root + File.separator + basename;
      String payload = conf.get(H2O_MAPPER_CONF_PAYLOAD_BASE + i);
      byte[] byteArr = h2odriver.convertStringToByteArr(payload);
      h2odriver.writeBinaryFile(fileName, byteArr);
      if(null != arg && !arg.isEmpty()) {
        argsList.add(fileName);
      }

      // Need to modify this config here as we don't know the destination dir for keys when generating it
      if("default-security.config".equals(basename)) {
        modifyKeyPath(fileName, ice_root);
      }
    }

    DelegationTokenRefresher.setup(conf);
    String[] args = argsList.toArray(new String[0]);
    try {
      EmbeddedH2OConfig _embeddedH2OConfig = new EmbeddedH2OConfig();
      _embeddedH2OConfig.setDriverCallbackIp(driverIp);
      _embeddedH2OConfig.setDriverCallbackPort(driverPort);
      _embeddedH2OConfig.setMapperCallbackPort(localPort);
      H2O.setEmbeddedH2OConfig(_embeddedH2OConfig);
      Log.POST(11, "After setEmbeddedH2OConfig");
      //-------------------------------------------------------------
      repairNullArgsAndWarnIfNecessary(args);
      water.H2OApp.main(args);
      //-------------------------------------------------------------
      Log.POST(12, "After main");
    }
    catch (Exception e) {
      Log.POST(13, "Exception in main");
      Log.POST(13, e.toString());
      e.printStackTrace();
    }

    Log.POST(14, "Waiting for exit");
    // EmbeddedH2OConfig will send a one-byte exit status to this socket.
    Socket sock = ss.accept();
    System.out.println("Wait for exit woke up from accept");
    byte[] b = new byte[1];
    InputStream is = sock.getInputStream();
    int expectedBytes = 1;
    int receivedBytes = 0;
    while (receivedBytes < expectedBytes) {
      int n = is.read(b, receivedBytes, expectedBytes-receivedBytes);
      System.out.println("is.read returned " + n);
      if (n < 0) {
        System.exit(112);
      }
      receivedBytes += n;
    }

    int exitStatus = (int)b[0];
    System.out.println("Received exitStatus " + exitStatus);
    return exitStatus;
  }

  //==============================================================================
  //                        SSL RELATED METHODS
  //==============================================================================
  private void modifyKeyPath(String fileName, String ice_root) throws IOException {
    FileInputStream in = null;
    Properties sslProps;
    try {
      in = new FileInputStream(fileName);
      sslProps = new Properties();
      sslProps.load(in);
    } finally {
      if (in != null) {
        in.close();
      }
    }

    subPath("h2o_ssl_jks_internal", sslProps, ice_root);
    subPath("h2o_ssl_jts", sslProps, ice_root);

    FileOutputStream out = null;
    try {
      out = new FileOutputStream(fileName);
      sslProps.store(out, null);
    } finally {
      if (out != null) {
        out.close();
      }
    }
  }

  //==============================================================================
  //==============================================================================

  private void subPath(String prop, Properties sslProps, String ice_root) {
    String path = sslProps.getProperty(prop);
    // Change only auto generated path. Don't allow the user to use "h2o-internal.jks" as path
    if(null != path && "h2o-internal.jks".equals(path)) {
      sslProps.setProperty(prop, ice_root + File.separator + path);
    }
  }

  @Override
  public void run(Context context) throws IOException, InterruptedException {
    try {
      Log.POST(0, "Entered run");

      setup(context);
      int exitStatus = run2(context);
      cleanup(context);

      Log.POST(1000, "Leaving run");
      System.out.println("Exiting with status " + exitStatus);
      System.out.flush();
      if (exitStatus != 0) {
        System.exit(exitStatus);
      }
    }
    catch (Exception e) {
      Log.POST(999, e);
      System.exit(100);
    }

    System.out.println("Exiting mapper run method");
    System.out.flush();
  }

  /**
   * For debugging only.
   */
  public static void main (String[] args) {
    try {
      h2omapper m = new h2omapper();
      m.run(null);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
