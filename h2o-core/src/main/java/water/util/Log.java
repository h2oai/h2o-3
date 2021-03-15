package water.util;

import water.H2O;
import water.persist.PersistManager;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

import static water.util.StringUtils.fixedLength;

/** 
 * Log for H2O. 
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

  private static int _level = INFO;
  private static boolean _quiet = false;
  private static boolean _bufferMessages = true;
  private static String _logDir = null;
  private static String _maxLogFileSize = "3MB";

  // A little bit of startup buffering
  private static class BufferedMsg { 
    private final int lvl; private final String msg; private final Throwable t;
    BufferedMsg(int l, String m, Throwable t) { this.lvl = l; this.msg = m; this.t = t; }
  }
  private static ArrayList<BufferedMsg> INIT_MSGS = new ArrayList<>();
  
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
  
  public static void init(String sLvl, boolean quiet, String maxLogFileSize) {
    int lvl = valueOf(sLvl);
    if( lvl != -1 ) _level = lvl;
    _quiet = quiet;
    //_logger = null;
    if (maxLogFileSize != null) {
      _maxLogFileSize = maxLogFileSize;
    }
  }
  
  public static void notifyAboutNetworkingInitialized() {
    _bufferMessages = false; // at this point we can create the log files and use a correct prefix ip:port for each log message
    assert H2O.SELF_ADDRESS != null && H2O.H2O_PORT != 0;
  }
  
  public static void notifyAboutProcessExiting() {
    // make sure we write out whatever we have right now 
    Log.flushBufferedMessages();

    // if there are any other log messages after this call, we want to preserve them as well
    if (_quiet) {
      _quiet = false;
      //_logger = null;
    }
    INIT_MSGS = null;
  }
  
  public static void setLogLevel(String level, boolean quiet) {
    init(level, quiet, null);
  }
  
  public static void setLogLevel(String level) {
    setLogLevel(level, true);
  }

  public static void trace( Object... objs ) { log(TRACE,objs); }

  public static void debug( Object... objs ) { log(DEBUG,objs); }
  
  public static void info ( Object... objs ) { log(INFO ,objs); }
  
  public static void warn ( Object... objs ) { log(WARN ,objs); }
  
  public static void err  ( Object... objs ) { log(ERRR ,objs); }
  
  public static void fatal( Object... objs ) { log(FATAL,objs); }
  
  public static void log  ( int level, Object... objs ) { write(level, objs); }

  // This call *throws* an unchecked exception and never returns (after logging).
  public static RuntimeException throwErr( Throwable e ) {
    err(e);                     // Log it
    throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e); // Throw it
  }

  private static void write(int lvl, Object[] objs) {
    write0(lvl, objs);
  }

  private static void write0(int lvl, Object[] objs) {
    StringBuilder msgBuff = new StringBuilder();
    Throwable t = null;
    for (int i = 0; i < objs.length - 1; i++) msgBuff.append(objs[i]);
    if (objs.length > 0 && objs[objs.length - 1] instanceof Throwable) {
      t = (Throwable) objs[objs.length-1];
    } else if (objs.length > 0) {
      msgBuff.append(objs[objs.length-1]);
    }
    String msg = msgBuff.toString();
    if (_bufferMessages) { // Oops, need to buffer until we can do a proper header
      INIT_MSGS.add(new BufferedMsg(lvl, msg, t));
      return;
    }
    flushBufferedMessages();
    write0(lvl, msg, t);
  }

  private static void write0(int lvl, String s, Throwable t) {
    //Logger log = (_logger != null ? _logger : createLog4j());
    //if (s.contains("\n")) {
    //  for (String line : s.split("\n")) {
    //    log.log(L4J_LVLS[lvl], line);
    //  }
    //  if (t != null) {
    //    log.log(L4J_LVLS[lvl], t);
    //  }
    //} else {
    //  log.log(L4J_LVLS[lvl], s, t);
    //}
  }

  public static void flushBufferedMessages() {
    if (INIT_MSGS != null) {
      ArrayList<BufferedMsg> buff = INIT_MSGS;
      INIT_MSGS = null;
      if (buff != null) for (BufferedMsg m : buff) write0(m.lvl, m.msg, m.t);
    }
  }

  public static int getLogLevel(){
    return _level;
  }

  public static boolean isLoggingFor(int level) {
    if (level == -1) { // in case of invalid log level return false
      return false;
    }
    return _level >= level;
  }

  public static boolean isLoggingFor(String strLevel){
    int level = valueOf(strLevel);
    return isLoggingFor(level);
  }

  /**
   * Get the directory where the logs are stored.
   */
  public static String getLogDir() throws Exception {
    if (_logDir == null) {
      throw new Exception("LOG_DIR not yet defined");
    }
    return _logDir;
  }

  private static String getLogFileNamePrefix() throws Exception {
    if (H2O.SELF_ADDRESS == null) {
      throw new Exception("H2O.SELF_ADDRESS not yet defined");
    }
    if (H2O.H2O_PORT == 0) {
      throw new Exception("H2O.H2O_PORT is not yet determined");
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
  
  private static String getHostPortPid() {
    String host = H2O.SELF_ADDRESS.getHostAddress();
    return fixedLength(host + ":" + H2O.API_PORT + " ", 22) + fixedLength(H2O.PID + " ", 6);
  }
  
  private static void setLog4jProperties(String logDir, Properties p) throws Exception {
    _logDir = logDir;

    String patternTail = getHostPortPid() + " %10.10t %5.5p %c: %m%n";
    String pattern = "%d{MM-dd HH:mm:ss.SSS} " + patternTail;

    //p.setProperty("log4j.rootLogger", L4J_LVLS[_level] + ", console");
    //
    //// H2O-wide logging
    //String appenders = L4J_LVLS[_level] + ", R1, R2, R3, R4, R5, R6";
    //for (String packageName : new String[] {"water", "ai.h2o", "hex"}) {
    //  p.setProperty("log4j.logger." + packageName, appenders);
    //  p.setProperty("log4j.logger.additivity." + packageName, "false");
    //}
    //
    //p.setProperty("log4j.appender.console",                     "org.apache.log4j.ConsoleAppender");
    //p.setProperty("log4j.appender.console.Threshold",           L4J_LVLS[_level].toString());
    //p.setProperty("log4j.appender.console.layout",              "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.console.layout.ConversionPattern", pattern);
    //
    //p.setProperty("log4j.appender.R1",                          "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.R1.Threshold",                "TRACE");
    //p.setProperty("log4j.appender.R1.File",                     getLogFilePath("trace"));
    //p.setProperty("log4j.appender.R1.MaxFileSize",              "1MB");
    //p.setProperty("log4j.appender.R1.MaxBackupIndex",           "3");
    //p.setProperty("log4j.appender.R1.layout",                   "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.R1.layout.ConversionPattern", pattern);
    //
    //p.setProperty("log4j.appender.R2",                          "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.R2.Threshold",                "DEBUG");
    //p.setProperty("log4j.appender.R2.File",                     getLogFilePath("debug"));
    //p.setProperty("log4j.appender.R2.MaxFileSize",              _maxLogFileSize);
    //p.setProperty("log4j.appender.R2.MaxBackupIndex",           "3");
    //p.setProperty("log4j.appender.R2.layout",                   "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.R2.layout.ConversionPattern", pattern);
    //
    //p.setProperty("log4j.appender.R3",                          "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.R3.Threshold",                "INFO");
    //p.setProperty("log4j.appender.R3.File",                     getLogFilePath("info"));
    //p.setProperty("log4j.appender.R3.MaxFileSize",              _maxLogFileSize);
    //p.setProperty("log4j.appender.R3.MaxBackupIndex",           "3");
    //p.setProperty("log4j.appender.R3.layout",                   "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.R3.layout.ConversionPattern", pattern);
    //
    //p.setProperty("log4j.appender.R4",                          "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.R4.Threshold",                "WARN");
    //p.setProperty("log4j.appender.R4.File",                     getLogFilePath("warn"));
    //p.setProperty("log4j.appender.R4.MaxFileSize",              "256KB");
    //p.setProperty("log4j.appender.R4.MaxBackupIndex",           "3");
    //p.setProperty("log4j.appender.R4.layout",                   "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.R4.layout.ConversionPattern", pattern);
    //
    //p.setProperty("log4j.appender.R5",                          "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.R5.Threshold",                "ERROR");
    //p.setProperty("log4j.appender.R5.File",                     getLogFilePath("error"));
    //p.setProperty("log4j.appender.R5.MaxFileSize",              "256KB");
    //p.setProperty("log4j.appender.R5.MaxBackupIndex",           "3");
    //p.setProperty("log4j.appender.R5.layout",                   "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.R5.layout.ConversionPattern", pattern);
    //
    //p.setProperty("log4j.appender.R6",                          "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.R6.Threshold",                "FATAL");
    //p.setProperty("log4j.appender.R6.File",                     getLogFilePath("fatal"));
    //p.setProperty("log4j.appender.R6.MaxFileSize",              "256KB");
    //p.setProperty("log4j.appender.R6.MaxBackupIndex",           "3");
    //p.setProperty("log4j.appender.R6.layout",                   "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.R6.layout.ConversionPattern", pattern);
    //
    //// HTTPD logging
    //p.setProperty("log4j.logger.water.api.RequestServer",       "TRACE, HTTPD");
    //p.setProperty("log4j.additivity.water.api.RequestServer",   "false");
    //
    //p.setProperty("log4j.appender.HTTPD",                       "org.apache.log4j.RollingFileAppender");
    //p.setProperty("log4j.appender.HTTPD.Threshold",             "TRACE");
    //p.setProperty("log4j.appender.HTTPD.File",                  getLogFilePath("httpd"));
    //p.setProperty("log4j.appender.HTTPD.MaxFileSize",           "1MB");
    //p.setProperty("log4j.appender.HTTPD.MaxBackupIndex",        "3");
    //p.setProperty("log4j.appender.HTTPD.layout",                "org.apache.log4j.PatternLayout");
    //p.setProperty("log4j.appender.HTTPD.layout.ConversionPattern", "%d{ISO8601} " + patternTail);
    //
    //// Turn down the logging for some class hierarchies.
    //p.setProperty("log4j.logger.org.apache.http",               "WARN");
    //p.setProperty("log4j.logger.com.amazonaws",                 "WARN");
    //p.setProperty("log4j.logger.org.apache.hadoop",             "WARN");
    //p.setProperty("log4j.logger.org.jets3t.service",            "WARN");
    //p.setProperty("log4j.logger.org.reflections.Reflections",   "ERROR");
    //p.setProperty("log4j.logger.com.brsanthu.googleanalytics",  "ERROR");
    //
    //// Turn down the logging for external libraries that Orc parser depends on
    //p.setProperty("log4j.logger.org.apache.hadoop.util.NativeCodeLoader", "ERROR");
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
  public static void POST(int n, String s) {
    System.out.println("POST " + n + ": " + s);
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

}
