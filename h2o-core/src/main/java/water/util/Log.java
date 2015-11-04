package water.util;

import java.io.File;
import java.util.ArrayList;

import org.apache.log4j.H2OPropertyConfigurator;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import water.H2O;
import water.persist.PersistManager;

/** Log for H2O. 
 *
 *  OOME: when the VM is low on memory, OutOfMemoryError can be thrown in the
 *  logging framework while it is trying to print a message. In this case the
 *  first message that fails is recorded for later printout, and a number of
 *  messages can be discarded. The framework will attempt to print the recorded
 *  message later, and report the number of dropped messages, but this done in
 *  a best effort and lossy manner. Basically when an OOME occurs during
 *  logging, no guarantees are made about the messages.
 **/
abstract public class Log {

  private static org.apache.log4j.Logger _logger = null;

  static String LOG_DIR = null;

  static final int FATAL= 0;
  static final int ERRR = 1;
  static final int WARN = 2;
  static final int INFO = 3;
  static final int DEBUG= 4;
  static final int TRACE= 5;
  static final String[] LVLS = { "FATAL", "ERRR", "WARN", "INFO", "DEBUG", "TRACE" };
  static int _level=INFO;
  static boolean _quiet = false;

  // Common pre-header
  private static String _preHeader;

  public static void init( String slvl, boolean quiet ) {
    if( slvl != null ) {
      slvl = slvl.toLowerCase();
      if( slvl.startsWith("fatal") ) _level = FATAL;
      if( slvl.startsWith("err"  ) ) _level = ERRR;
      if( slvl.startsWith("warn" ) ) _level = WARN;
      if( slvl.startsWith("info" ) ) _level = INFO;
      if( slvl.startsWith("debug") ) _level = DEBUG;
      if( slvl.startsWith("trace") ) _level = TRACE;
    }
    _quiet = quiet;
  }
  
  public static void trace( Object... objs ) { if( _level >= TRACE ) write(TRACE,objs); }
  public static void debug( Object... objs ) { if( _level >= DEBUG ) write(DEBUG,objs); }
  public static void info ( Object... objs ) { if( _level >= INFO  ) write(INFO ,objs); }
  public static void warn ( Object... objs ) { if( _level >= WARN  ) write(WARN, objs); }
  public static void err  ( Object... objs ) { if( _level >= ERRR  ) write(ERRR, objs); }
  public static void fatal( Object... objs ) { if( _level >= FATAL ) write(FATAL, objs); }

  public static void httpd( String msg ) {
    // This is never called anymore.
    throw H2O.fail();
  }

  public static void httpd( String method, String uri, int status, long deltaMillis ) {
    org.apache.log4j.Logger l = LogManager.getLogger(water.api.RequestServer.class);
    String s = String.format("  %-6s  %3d  %6d ms  %s", method, status, deltaMillis, uri);
    l.info(s);
  }

  public static void info( String s, boolean stdout ) { if( _level >= INFO ) write0(INFO, stdout, s); }

  // This call *throws* an unchecked exception and never returns (after logging).
  public static RuntimeException throwErr( Throwable e ) {
    err(e);                     // Log it
    throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e); // Throw it
  }

  private static void write( int lvl, Object objs[] ) {
    boolean writeToStdout = (lvl <= _level);
    write0(lvl, writeToStdout, objs);
  }

  private static void write0( int lvl, boolean stdout, Object objs[] ) {
    StringBuilder sb = new StringBuilder();
    for( Object o : objs ) sb.append(o);
    String res = sb.toString();
    if( H2O.SELF_ADDRESS == null ) { // Oops, need to buffer until we can do a proper header
      INIT_MSGS.add(res);
      return;
    }
    if( INIT_MSGS != null ) {   // Ahh, dump any initial buffering
      String host = H2O.SELF_ADDRESS.getHostAddress();
      _preHeader = fixedLength(host + ":" + H2O.API_PORT + " ", 22) + fixedLength(H2O.PID + " ", 6);
      ArrayList<String> bufmsgs = INIT_MSGS;  INIT_MSGS = null;
      for( String s : bufmsgs ) write0(INFO, true, s);
    }
    write0(lvl, stdout, res);
  }

  private static void write0( int lvl, boolean stdout, String s ) {
    StringBuilder sb = new StringBuilder();
    String hdr = header(lvl);   // Common header for all lines
    write0(sb, hdr, s);

    // stdout first - in case log4j dies failing to init or write something
    if(stdout && !_quiet) System.out.println(sb);

    // log something here
    org.apache.log4j.Logger l4j = _logger != null ? _logger : createLog4j();
    switch( lvl ) {
    case FATAL:l4j.fatal(sb); break;
    case ERRR: l4j.error(sb); break;
    case WARN: l4j.warn (sb); break;
    case INFO: l4j.info (sb); break;
    case DEBUG:l4j.debug(sb); break;
    case TRACE:l4j.trace(sb); break;
    default:
      l4j.error("Invalid log level requested");
      l4j.error(s);
    }
  }

  private static void write0( StringBuilder sb, String hdr, String s ) {
    if( s.contains("\n") ) {
      for( String s2 : s.split("\n") ) { write0(sb,hdr,s2); sb.append("\n"); }
      sb.setLength(sb.length()-1);
    } else {
      sb.append(hdr).append(s);
    }
  }

  // Build a header for all lines in a single message
  private static String header( int lvl ) {
    String nowString = Timer.nowAsLogString();
    String s = nowString +" "+_preHeader+" "+
      fixedLength(Thread.currentThread().getName() + " ", 10)+
      LVLS[lvl]+": ";
    return s;
  }

  // A little bit of startup buffering
  private static ArrayList<String> INIT_MSGS = new ArrayList<String>();

  public static void flushStdout() {
    for (String s : INIT_MSGS) {
      System.out.println(s);
    }

    INIT_MSGS.clear();
  }

  /**
   * @return This is what should be used when doing Download All Logs.
   */
  public static String getLogDir() throws Exception {
    if (LOG_DIR == null) {
      throw new Exception("LOG_DIR not yet defined");
    }

    return LOG_DIR;
  }

  private static String getLogFileNameStem() throws Exception {
    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    return "h2o_" + ip + "_" + portString;
  }

  /**
   * @return The common prefix for all of the different log files for this process.
   */
  public static String getLogPathFileNameStem() throws Exception {
    if (H2O.SELF_ADDRESS == null) {
      throw new Exception("H2O.SELF_ADDRESS not yet defined");
    }

    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    String logFileName = getLogDir() + File.separator + getLogFileNameStem();
    return logFileName;
  }

  /**
   * @return This is what shows up in the Web UI when clicking on show log file.  File name only.
   */
  public static String getLogFileName(String level) throws Exception {
    String f;
    switch (level) {
      case "trace": f = "-1-trace.log"; break;
      case "debug": f = "-2-debug.log"; break;
      case "info":  f = "-3-info.log"; break;
      case "warn":  f = "-4-warn.log"; break;
      case "error": f = "-5-error.log"; break;
      case "fatal": f = "-6-fatal.log"; break;
      case "httpd": f = "-httpd.log"; break;
      default:
        throw new Exception("Unknown level");
    }

    return getLogFileNameStem() + f;
  }

  private static void setLog4jProperties(String logDir, java.util.Properties p) throws Exception {
    LOG_DIR = logDir;
    String logPathFileName = getLogPathFileNameStem();

    // H2O-wide logging
    String appenders = new String[]{
      "TRACE, R6",
      "TRACE, R5, R6",
      "TRACE, R4, R5, R6",
      "TRACE, R3, R4, R5, R6",
      "TRACE, R2, R3, R4, R5, R6",
      "TRACE, R1, R2, R3, R4, R5, R6",
    }[_level];
    p.setProperty("log4j.logger.water.default", appenders);
    p.setProperty("log4j.additivity.water.default",   "false");

    p.setProperty("log4j.appender.R1",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R1.Threshold",                "TRACE");
    p.setProperty("log4j.appender.R1.File",                     logPathFileName + "-1-trace.log");
    p.setProperty("log4j.appender.R1.MaxFileSize",              "1MB");
    p.setProperty("log4j.appender.R1.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R1.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R2",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R2.Threshold",                "DEBUG");
    p.setProperty("log4j.appender.R2.File",                     logPathFileName + "-2-debug.log");
    p.setProperty("log4j.appender.R2.MaxFileSize",              "3MB");
    p.setProperty("log4j.appender.R2.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R2.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R2.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R3",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R3.Threshold",                "INFO");
    p.setProperty("log4j.appender.R3.File",                     logPathFileName + "-3-info.log");
    p.setProperty("log4j.appender.R3.MaxFileSize",              "2MB");
    p.setProperty("log4j.appender.R3.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R3.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R3.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R4",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R4.Threshold",                "WARN");
    p.setProperty("log4j.appender.R4.File",                     logPathFileName + "-4-warn.log");
    p.setProperty("log4j.appender.R4.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R4.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R4.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R4.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R5",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R5.Threshold",                "ERROR");
    p.setProperty("log4j.appender.R5.File",                     logPathFileName + "-5-error.log");
    p.setProperty("log4j.appender.R5.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R5.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R5.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R5.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R6",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R6.Threshold",                "FATAL");
    p.setProperty("log4j.appender.R6.File",                     logPathFileName + "-6-fatal.log");
    p.setProperty("log4j.appender.R6.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R6.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R6.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R6.layout.ConversionPattern", "%m%n");

    // HTTPD logging
    p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, HTTPD");
    p.setProperty("log4j.additivity.water.api.RequestServer",   "false");

    p.setProperty("log4j.appender.HTTPD",                       "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.HTTPD.Threshold",             "TRACE");
    p.setProperty("log4j.appender.HTTPD.File",                  logPathFileName + "-httpd.log");
    p.setProperty("log4j.appender.HTTPD.MaxFileSize",           "1MB");
    p.setProperty("log4j.appender.HTTPD.MaxBackupIndex",        "3");
    p.setProperty("log4j.appender.HTTPD.layout",                "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.HTTPD.layout.ConversionPattern", "%d{ISO8601} %m%n");

    // Turn down the logging for some class hierarchies.
    p.setProperty("log4j.logger.org.apache.http",               "WARN");
    p.setProperty("log4j.logger.com.amazonaws",                 "WARN");
    p.setProperty("log4j.logger.org.apache.hadoop",             "WARN");
    p.setProperty("log4j.logger.org.jets3t.service",            "WARN");
    p.setProperty("log4j.logger.org.reflections.Reflections",   "ERROR");
    p.setProperty("log4j.logger.com.brsanthu.googleanalytics",  "ERROR");

    // See the following document for information about the pattern layout.
    // http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html
    //
    //  Uncomment this line to find the source of unwanted messages.
    //     p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%p %C %m%n");
  }

  private static synchronized org.apache.log4j.Logger createLog4j() {
    if( _logger != null ) return _logger; // Test again under lock

    boolean launchedWithHadoopJar = H2O.ARGS.hdfs_skip;
    String log4jConfiguration = System.getProperty ("h2o.log4j.configuration");
    boolean log4jConfigurationProvided = log4jConfiguration != null;

    if (log4jConfigurationProvided) {
      PropertyConfigurator.configure(log4jConfiguration);
    }
    else {
      // Create some default properties on the fly if we aren't using a provided configuration.
      // H2O creates the log setup itself on the fly in code.
      java.util.Properties p = new java.util.Properties();
      try {
        File dir;
        if (H2O.ARGS.log_dir != null) {
          dir = new File(H2O.ARGS.log_dir);
        }
        else {
          boolean windowsPath = H2O.ICE_ROOT.toString().matches("^[a-zA-Z]:.*");

          // Use ice folder if local, or default
          if (windowsPath)
            dir = new File(H2O.ICE_ROOT.toString());
          else if (H2O.ICE_ROOT.getScheme() == null || PersistManager.Schemes.FILE.equals(H2O.ICE_ROOT.getScheme()))
            dir = new File(H2O.ICE_ROOT.getPath());
          else
            dir = new File(H2O.DEFAULT_ICE_ROOT());

          dir = new File(dir, "h2ologs");
        }

        setLog4jProperties(dir.toString(), p);
      }
      catch (Exception e) {
        System.err.println("ERROR: failed in createLog4j, exiting now.");
        e.printStackTrace();
        H2O.exit(1);
      }

      // For the Hadoop case, force H2O to specify the logging setup since we don't care
      // about any hadoop log setup, anyway.
      //
      // For the Sparkling Water case, we will have inherited the log4j configuration,
      // so append to it rather than whack it.
      if (!launchedWithHadoopJar && H2O.haveInheritedLog4jConfiguration()) {
        // Use a modified log4j property configurator to append rather than create a new log4j configuration.
        H2OPropertyConfigurator.configure(p);
      }
      else {
        PropertyConfigurator.configure(p);
      }
    }
    
    return (_logger = LogManager.getLogger("water.default"));
  }

  public static String fixedLength(String s, int length) {
    String r = padRight(s, length);
    if( r.length() > length ) {
      int a = Math.max(r.length() - length + 1, 0);
      int b = Math.max(a, r.length());
      r = "#" + r.substring(a, b);
    }
    return r;
  }
  
  static String padRight(String stringToPad, int size) {
    StringBuilder strb = new StringBuilder(stringToPad);
    while( strb.length() < size )
      if( strb.length() < size ) strb.append(' ');
    return strb.toString();
  }

  public static void ignore(Throwable e) {
    ignore(e,"[h2o] Problem ignored: ");
  }
  public static void ignore(Throwable e, String msg) {
    ignore(e, msg, true);
  }
  public static void ignore(Throwable e, String msg, boolean printException) {
    debug(msg + (printException? e.toString() : ""));
  }

  //-----------------------------------------------------------------
  // POST support for debugging embedded configurations.
  //-----------------------------------------------------------------

  /**
   * POST stands for "Power on self test".
   * Stamp a POST code to /tmp.
   * This is for bringup, when no logging or stdout I/O is reliable.
   * (Especially when embedded, such as in hadoop mapreduce, for example.)
   *
   * @param n POST code.
   * @param s String to emit.
   */
//  private static final Object postLock = new Object();
  public static void POST(int n, String s) {
    // DO NOTHING UNLESS ENABLED BY REMOVING THIS RETURN!
    System.out.println("POST " + n + ": " + s);
    return;

//      synchronized (postLock) {
//          File f = new File ("/tmp/h2o.POST");
//          if (! f.exists()) {
//              boolean success = f.mkdirs();
//              if (! success) {
//                  try { System.err.print ("Exiting from POST now!"); } catch (Exception _) {}
//                  H2O.exit (0);
//              }
//          }
//
//          f = new File ("/tmp/h2o.POST/" + n);
//          try {
//              f.createNewFile();
//              FileWriter fstream = new FileWriter(f.getAbsolutePath(), true);
//              BufferedWriter out = new BufferedWriter(fstream);
//              out.write(s + "\n");
//              out.close();
//          }
//          catch (Exception e) {
//              try { System.err.print ("Exiting from POST now!"); } catch (Exception _) {}
//              H2O.exit (0);
//          }
//      }
  }
  public static void POST(int n, Exception e) {
    if (e.getMessage() != null) {
      POST(n, e.getMessage());
    }
    POST(n, e.toString());
    StackTraceElement[] els = e.getStackTrace();
    for (int i = 0; i < els.length; i++) {
      POST(n, els[i].toString());
    }
  }

  public static void setQuiet(boolean q) { _quiet = q; }
  public static boolean getQuiet() { return _quiet; }
}
