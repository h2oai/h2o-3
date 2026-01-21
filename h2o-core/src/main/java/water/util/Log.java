package water.util;

import org.apache.log4j.Logger;
import water.H2O;
import water.persist.PersistManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static water.util.LoggerBackend.L4J_LVLS;
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
  private static final String PROP_MAX_PID_LENGTH = H2O.OptArgs.SYSTEM_PROP_PREFIX + "log.max.pid.length";

  private static int _level = INFO;
  private static boolean _quiet = false;
  private static Logger _logger = null;
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
    _logger = null;
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
      _logger = null;
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
    Logger log = (_logger != null ? _logger : createLog4j());
    if (s.contains("\n")) {
      for (String line : s.split("\n")) {
        log.log(L4J_LVLS[lvl], line);
      }
      if (t != null) {
        log.log(L4J_LVLS[lvl], t);
      }
    } else {
      log.log(L4J_LVLS[lvl], s, t);
    }
  }

  private static List<BufferedMsg> consumeBufferedMessages() {
    List<BufferedMsg> buff = null;
    if (INIT_MSGS != null) {
      buff = INIT_MSGS;
      INIT_MSGS = null;
    }
    return buff;
  }

  public static void flushBufferedMessages() {
    List<BufferedMsg> buff = consumeBufferedMessages();
    if (buff != null) 
      for (BufferedMsg m : buff)
        write0(m.lvl, m.msg, m.t);
  }

  public static void flushBufferedMessagesToStdout() {
    List<BufferedMsg> buff = consumeBufferedMessages();
    if (buff != null)
      for (BufferedMsg m : buff) {
        System.out.println(m.msg);
        if (m.t != null)
          m.t.printStackTrace();
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
  public static String getLogDir() {
    if (_logDir == null) {
      throw new RuntimeException("LOG_DIR not yet defined");
    }
    return _logDir;
  }

  private static String getLogFileNamePrefix() {
    if (H2O.SELF_ADDRESS == null) {
      throw new RuntimeException("H2O.SELF_ADDRESS not yet defined");
    }
    if (H2O.H2O_PORT == 0) {
      throw new RuntimeException("H2O.H2O_PORT is not yet determined");
    }
    String ip = H2O.SELF_ADDRESS.getHostAddress();
    int port = H2O.API_PORT;
    String portString = Integer.toString(port);
    return "h2o_" + ip + "_" + portString;
  }

  private static File determineLogDir() {
    File dir;
    if (H2O.ARGS.log_dir != null) {
      dir = new File(H2O.ARGS.log_dir);
    } else {
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
    return dir;
  }
  
  /**
   * Get log file name without the path for particular log level.
   */
  public static String getLogFileName(String level) {
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
  public static String getLogFilePath(String level) {
    return getLogDir() + File.separator + getLogFileName(level);
  }

  private static String getHostPortPid() {
    String host = H2O.SELF_ADDRESS.getHostAddress();
    return fixedLength(host + ":" + H2O.API_PORT + " ", 22) + fixedLength(H2O.PID + " ", maximumPidLength() + 2);
  }

  // set sys.ai.h2o.log.max.pid.length to avoid h2o-3 trimming PID in the logs
  private static int maximumPidLength() {
    String maxPidPropertyValue = System.getProperty(PROP_MAX_PID_LENGTH);
    return maxPidPropertyValue != null
            ? Integer.parseInt(maxPidPropertyValue)
            : 4;
  }

  private static synchronized Logger createLog4j() {
    if (_logger == null) { // Test again under lock
      _logDir = determineLogDir().toString();

      LoggerBackend lb = new LoggerBackend();
      lb._launchedWithHadoopJar = H2O.ARGS.launchedWithHadoopJar();
      lb._haveInheritedLog4jConfiguration = H2O.haveInheritedLog4jConfiguration();
      lb._prefix = getHostPortPid();
      lb._maxLogFileSize = _maxLogFileSize;
      lb._level = _level;
      lb._getLogFilePath = Log::getLogFilePath;

      Logger logger = lb.createLog4j();
      if (logger == null) {
        H2O.exit(1);
        throw new IllegalStateException("Shouldn't reach this - exit should exit the application");
      }
      _logger = logger;
    }
    return _logger;
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
    for (StackTraceElement el : els) {
      POST(n, el.toString());
    }
  }

}
