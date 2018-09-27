package water.util;

import org.apache.log4j.H2OPropertyConfigurator;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import water.H2O;
import water.persist.PersistManager;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

  public static final byte FATAL= 0;
  public static final byte ERRR = 1;
  public static final byte WARN = 2;
  public static final byte INFO = 3;
  public static final byte DEBUG= 4;
  public static final byte TRACE= 5;
  public static final String[] LVLS = { "FATAL", "ERRR", "WARN", "INFO", "DEBUG", "TRACE" };
  static String LOG_DIR = null;
  static int _level=INFO;
  static boolean _quiet = false;
  private static org.apache.log4j.Logger _logger = null;
  // Common pre-header
  private static String _preHeader;
  // A little bit of startup buffering
  private static ArrayList<String> INIT_MSGS = new ArrayList<>();
  
  public static byte valueOf( String slvl ) {
    if( slvl == null ) return -1;
    slvl = slvl.toLowerCase();
    if( slvl.startsWith("fatal") ) return FATAL;
    if( slvl.startsWith("err"  ) ) return ERRR;
    if( slvl.startsWith("warn" ) ) return WARN;
    if( slvl.startsWith("info" ) ) return INFO;
    if( slvl.startsWith("debug") ) return DEBUG;
    if( slvl.startsWith("trace") ) return TRACE;
    return -1;
  }
  
  public static void init(String sLvl, boolean quiet) {
    int lvl = valueOf(sLvl);
    if( lvl != -1 ) _level = lvl;
    _quiet = quiet;
  }
  
  public static void setLogLevel(String sLvl, boolean quiet) {
    init(sLvl, quiet);
  }
  
  public static void setLogLevel(String sLvl) {
    setLogLevel(sLvl, true);
  }

  public static void trace( Object... objs ) { log(TRACE,objs); }

  public static void debug( Object... objs ) { log(DEBUG,objs); }
  
  public static void info ( Object... objs ) { log(INFO ,objs); }
  
  public static void warn ( Object... objs ) { log(WARN ,objs); }
  
  public static void err  ( Object... objs ) { log(ERRR ,objs); }
  
  public static void err(Throwable ex) {
    StringWriter sw = new StringWriter();
    ex.printStackTrace(new PrintWriter(sw));
    err(sw.toString());
  }
  
  public static void fatal( Object... objs ) { log(FATAL,objs); }
  
  public static void log  ( int level, Object... objs ) { if( _level >= level ) write(level, objs); }

  public static void httpd( String msg ) {
    // This is never called anymore.
    throw H2O.fail();
  }

  public static void httpd( String method, String uri, int status, long deltaMillis ) {
    org.apache.log4j.Logger l = LogManager.getLogger(water.api.RequestServer.class);
    String s = String.format("  %-6s  %3d  %6d ms  %s", method, status, deltaMillis, uri);
    l.info(s);
  }

  public static void info( String s, boolean stdout ) { if( _level >= INFO ) write0(INFO, stdout, new String[]{s}); }

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
      if (bufmsgs != null) for( String s : bufmsgs ) write0(INFO, true, s);
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

  public static void flushStdout() {
    if (INIT_MSGS != null) {
      for (String s : INIT_MSGS) {
        System.out.println(s);
      }

      INIT_MSGS.clear();
    }
  }

  public static int getLogLevel(){
    return _level;
  }

  public static boolean isLoggingFor(String strLevel){
    int level = valueOf(strLevel);
    if(level == -1){ // in case of invalid log level return false
      return false;
    }
    return _level >= level;
  }

  /**
   * Get the directory where the logs are stored.
   */
  public static String getLogDir() throws Exception {
    if (LOG_DIR == null) {
      throw new Exception("LOG_DIR not yet defined");
    }
    return LOG_DIR;
  }

  private static String getLogFileNamePrefix() throws Exception {
    if (H2O.SELF_ADDRESS == null) {
      throw new Exception("H2O.SELF_ADDRESS not yet defined");
    }
    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    return "h2o_" + ip + "_" + portString;
  }

  /**
   * Get log file name without the path for particular log level.
   */
  public static String getLogFileName(String level) throws Exception {
    return getLogFileNamePrefix() + getLogFileNameSuffix(level);
  }

  /** Get suffix of the log file name specific to particular log level */
  private static String getLogFileNameSuffix(String level){
    switch (level) {
      case "trace": return "-1-trace.log";
      case "debug": return "-2-debug.log";
      case "info": return "-3-info.log";
      case "warn": return "-4-warn.log";
      case "error": return "-5-error.log";
      case "fatal": return "-6-fatal.log";
      case "httpd": return "-httpd.log";
      default:
        throw new RuntimeException("Unknown level " + level);
    }
  }

  /** Get full path to a specific log file*/
  public static String getLogFilePath(String level) throws Exception {
    return getLogDir() + File.separator + getLogFileName(level);
  }
  
  
  private static void setLog4jProperties(String logDir, java.util.Properties p) throws Exception {
    LOG_DIR = logDir;

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
    p.setProperty("log4j.appender.R1.File",                     getLogFilePath("trace"));
    p.setProperty("log4j.appender.R1.MaxFileSize",              "1MB");
    p.setProperty("log4j.appender.R1.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R1.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R2",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R2.Threshold",                "DEBUG");
    p.setProperty("log4j.appender.R2.File",                     getLogFilePath("debug"));
    p.setProperty("log4j.appender.R2.MaxFileSize",              "3MB");
    p.setProperty("log4j.appender.R2.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R2.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R2.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R3",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R3.Threshold",                "INFO");
    p.setProperty("log4j.appender.R3.File",                     getLogFilePath("info"));
    p.setProperty("log4j.appender.R3.MaxFileSize",              "2MB");
    p.setProperty("log4j.appender.R3.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R3.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R3.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R4",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R4.Threshold",                "WARN");
    p.setProperty("log4j.appender.R4.File",                     getLogFilePath("warn"));
    p.setProperty("log4j.appender.R4.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R4.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R4.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R4.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R5",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R5.Threshold",                "ERROR");
    p.setProperty("log4j.appender.R5.File",                     getLogFilePath("error"));
    p.setProperty("log4j.appender.R5.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R5.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R5.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R5.layout.ConversionPattern", "%m%n");

    p.setProperty("log4j.appender.R6",                          "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.R6.Threshold",                "FATAL");
    p.setProperty("log4j.appender.R6.File",                     getLogFilePath("fatal"));
    p.setProperty("log4j.appender.R6.MaxFileSize",              "256KB");
    p.setProperty("log4j.appender.R6.MaxBackupIndex",           "3");
    p.setProperty("log4j.appender.R6.layout",                   "org.apache.log4j.PatternLayout");
    p.setProperty("log4j.appender.R6.layout.ConversionPattern", "%m%n");

    // HTTPD logging
    p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, HTTPD");
    p.setProperty("log4j.additivity.water.api.RequestServer",   "false");

    p.setProperty("log4j.appender.HTTPD",                       "org.apache.log4j.RollingFileAppender");
    p.setProperty("log4j.appender.HTTPD.Threshold",             "TRACE");
    p.setProperty("log4j.appender.HTTPD.File",                  getLogFilePath("httpd"));
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

    // Turn down the logging for external libraries that Orc parser depends on
    p.setProperty("log4j.logger.org.apache.hadoop.util.NativeCodeLoader", "ERROR");

    // See the following document for information about the pattern layout.
    // http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html
    //
    //  Uncomment this line to find the source of unwanted messages.
    //     p.setProperty("log4j.appender.R1.layout.ConversionPattern", "%p %C %m%n");
  }

  private static synchronized org.apache.log4j.Logger createLog4j() {
    if( _logger != null ) return _logger; // Test again under lock

    boolean launchedWithHadoopJar = H2O.ARGS.launchedWithHadoopJar();
    String h2oLog4jConfiguration = System.getProperty ("h2o.log4j.configuration");

    if (h2oLog4jConfiguration != null) {
      // Try to configure via a file on local filesystem
      if (new File(h2oLog4jConfiguration).exists()) {
        PropertyConfigurator.configure(h2oLog4jConfiguration);
      } else {
        // Try to load file via classloader resource (e.g., from classpath)
        URL confUrl = Log.class.getClassLoader().getResource(h2oLog4jConfiguration);
        if (confUrl != null) {
          PropertyConfigurator.configure(confUrl);
        }
      }
    } else {
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

  public static boolean getQuiet() { return _quiet; }
  
  public static void setQuiet(boolean q) {
    _quiet = q;
  }
}
