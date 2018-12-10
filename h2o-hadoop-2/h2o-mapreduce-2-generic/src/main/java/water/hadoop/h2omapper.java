package water.hadoop;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Mapper;
import water.H2O;

import water.util.Log;


/**
 * Interesting Configuration properties:
 * mapper	mapred.local.dir=/tmp/hadoop-tomk/mapred/local/taskTracker/tomk/jobcache/job_local1117903517_0001/attempt_local1117903517_0001_m_000000_0
 */
public class h2omapper extends Mapper<Text, Text, Text, Text> {
  final static public String H2O_DRIVER_IP_KEY = "h2o.driver.ip";
  final static public String H2O_DRIVER_PORT_KEY = "h2o.driver.port";

  final static public String H2O_MAPPER_ARGS_BASE = "h2o.mapper.args.";
  final static public String H2O_MAPPER_ARGS_LENGTH = "h2o.mapper.args.length";

  final static public String H2O_MAPPER_CONF_ARG_BASE = "h2o.mapper.conf.arg.";
  final static public String H2O_MAPPER_CONF_BASENAME_BASE = "h2o.mapper.conf.basename.";
  final static public String H2O_MAPPER_CONF_PAYLOAD_BASE = "h2o.mapper.conf.payload.";
  final static public String H2O_MAPPER_CONF_LENGTH = "h2o.mapper.conf.length";

  static EmbeddedH2OConfig _embeddedH2OConfig;

  private static class EmbeddedH2OConfig extends water.init.AbstractEmbeddedH2OConfig {
    volatile String _driverCallbackIp;
    volatile int _driverCallbackPort = -1;
    volatile int _mapperCallbackPort = -1;
    volatile String _embeddedWebServerIp = "(Unknown)";
    volatile int _embeddedWebServerPort = -1;

    void setDriverCallbackIp(String value) {
      _driverCallbackIp = value;
    }

    void setDriverCallbackPort(int value) {
      _driverCallbackPort = value;
    }

    void setMapperCallbackPort(int value) {
      _mapperCallbackPort = value;
    }

    private class BackgroundWriterThread extends Thread {
      MapperToDriverMessage _m;

      void setMessage (MapperToDriverMessage value) {
        _m = value;
      }

      public void run() {
        try {
          Socket s = new Socket(_m.getDriverCallbackIp(), _m.getDriverCallbackPort());
          _m.write(s);
          s.close();
        }
        catch (java.net.ConnectException e) {
          System.out.println("EmbeddedH2OConfig: BackgroundWriterThread could not connect to driver at " + _driverCallbackIp + ":" + _driverCallbackPort);
          System.out.println("(This is normal when the driver disowns the hadoop job and exits.)");
        }
        catch (Exception e) {
          System.out.println("EmbeddedH2OConfig: BackgroundWriterThread caught an Exception");
          e.printStackTrace();
        }
      }
    }

    @Override
    public void notifyAboutEmbeddedWebServerIpPort (InetAddress ip, int port) {
      _embeddedWebServerIp = ip.getHostAddress();
      _embeddedWebServerPort = port;

      try {
        MapperToDriverMessage msg = new MapperToDriverMessage();
        msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
        msg.setMessageEmbeddedWebServerIpPort(ip.getHostAddress(), port);
        BackgroundWriterThread bwt = new BackgroundWriterThread();
        System.out.printf("EmbeddedH2OConfig: notifyAboutEmbeddedWebServerIpPort called (%s, %d)\n", ip.getHostAddress(), port);
        bwt.setMessage(msg);
        bwt.start();
      }
      catch (Exception e) {
        System.out.println("EmbeddedH2OConfig: notifyAboutEmbeddedWebServerIpPort caught an Exception");
        e.printStackTrace();
      }
    }

    @Override
    public boolean providesFlatfile() {
      return true;
    }

    @Override
    public String fetchFlatfile() throws Exception {
      System.out.printf("EmbeddedH2OConfig: fetchFlatfile called\n");
      MapperToDriverMessage msg = new MapperToDriverMessage();
      msg.setMessageFetchFlatfile(_embeddedWebServerIp, _embeddedWebServerPort);
      Socket s = new Socket(_driverCallbackIp, _driverCallbackPort);
      msg.write(s);
      DriverToMapperMessage msg2 = new DriverToMapperMessage();
      msg2.read(s);
      char type = msg2.getType();
      if (type != DriverToMapperMessage.TYPE_FETCH_FLATFILE_RESPONSE) {
        int typeAsInt = (int)type & 0xff;
        String str = "DriverToMapperMessage type unrecognized (" + typeAsInt + ")";
        Log.err(str);
        throw new Exception (str);
      }
      s.close();
      String flatfile = msg2.getFlatfile();
      System.out.printf("EmbeddedH2OConfig: fetchFlatfile returned\n");
      System.out.println("------------------------------------------------------------");
      System.out.println(flatfile);
      System.out.println("------------------------------------------------------------");
      return flatfile;
    }

    @Override
    public void notifyAboutCloudSize (InetAddress ip, int port, InetAddress leaderIp, int leaderPort, int size) {
      _embeddedWebServerIp = ip.getHostAddress();
      _embeddedWebServerPort = port;

      try {
        MapperToDriverMessage msg = new MapperToDriverMessage();
        msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
        msg.setMessageCloudSize(ip.getHostAddress(), port, leaderIp.getHostAddress(), leaderPort, size);
        BackgroundWriterThread bwt = new BackgroundWriterThread();
        System.out.printf("EmbeddedH2OConfig: notifyAboutCloudSize called (%s, %d, %d)\n", ip.getHostAddress(), port, size);
        bwt.setMessage(msg);
        bwt.start();
      }
      catch (Exception e) {
        System.out.println("EmbeddedH2OConfig: notifyAboutCloudSize caught an Exception");
        e.printStackTrace();
      }
    }

    @Override
    public void exit(int status) {
      try {
        MapperToDriverMessage msg = new MapperToDriverMessage();
        msg.setDriverCallbackIpPort(_driverCallbackIp, _driverCallbackPort);
        msg.setMessageExit(_embeddedWebServerIp, _embeddedWebServerPort, status);
        System.out.printf("EmbeddedH2OConfig: exit called (%d)\n", status);
        BackgroundWriterThread bwt = new BackgroundWriterThread();
        bwt.setMessage(msg);
        bwt.start();
        System.out.println("EmbeddedH2OConfig: after bwt.start()");
      }
      catch (Exception e) {
        System.out.println("EmbeddedH2OConfig: exit caught an exception 1");
        e.printStackTrace();
      }

      try {
        // Wait one second to deliver the message before exiting.
        Thread.sleep (1000);
        Socket s = new Socket("127.0.0.1", _mapperCallbackPort);
        byte[] b = new byte[1];
        b[0] = (byte)status;
        OutputStream os = s.getOutputStream();
        os.write(b);
        os.flush();
        s.close();
        System.out.println("EmbeddedH2OConfig: after write to mapperCallbackPort");

        Thread.sleep(60 * 1000);
        // Should never make it this far!
      }
      catch (Exception e) {
        System.out.println("EmbeddedH2OConfig: exit caught an exception 2");
        e.printStackTrace();
      }

      System.exit(111);
    }

    @Override
    public void print() {
      System.out.println("EmbeddedH2OConfig print()");
      System.out.println("    Driver callback IP: " + ((_driverCallbackIp != null) ? _driverCallbackIp : "(null)"));
      System.out.println("    Driver callback port: " + _driverCallbackPort);
      System.out.println("    Embedded webserver IP: " + ((_embeddedWebServerIp != null) ? _embeddedWebServerIp : "(null)"));
      System.out.println("    Embedded webserver port: " + _embeddedWebServerPort);
    }
  }

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

  private int run2(Context context) throws IOException, InterruptedException {
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
      String arg = conf.get(H2O_MAPPER_ARGS_BASE + Integer.toString(i));
      argsList.add(arg);
    }

    // Config files passed by the driver.
    int confLength = Integer.parseInt(conf.get(H2O_MAPPER_CONF_LENGTH));
    for (int i = 0; i < confLength; i++) {
      String arg = conf.get(H2O_MAPPER_CONF_ARG_BASE + Integer.toString(i));
      // For files which are not passed as args (i.e. SSL certs)
      if(null != arg && !arg.isEmpty()) {
        argsList.add(arg);
      }

      String basename = conf.get(H2O_MAPPER_CONF_BASENAME_BASE + Integer.toString(i));
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
      String payload = conf.get(H2O_MAPPER_CONF_PAYLOAD_BASE + Integer.toString(i));
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

    String[] args = argsList.toArray(new String[argsList.size()]);
    try {
      _embeddedH2OConfig = new EmbeddedH2OConfig();
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
