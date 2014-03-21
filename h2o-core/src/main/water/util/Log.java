package water.util;

import java.io.File;
import java.util.ArrayList;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import water.H2O;
import water.persist.Persist;

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
  public static String getLogDir() { return LOG_DIR == null ? "unknown-log-dir" : LOG_DIR; }

  static final int ERRR = 0;
  static final int WARN = 1;
  static final int INFO = 2;
  static final int DEBUG= 3;
  static final String[] LVLS = { "ERRR", "WARN", "INFO", "DEBUG" };
  static int _level=INFO;

  // Common pre-header
  private static String _preHeader;

  public static void init( String slvl ) {
    if( slvl != null ) {
      slvl = slvl.toLowerCase();
      if( slvl.startsWith("err"  ) ) _level = ERRR;
      if( slvl.startsWith("warn" ) ) _level = WARN;
      if( slvl.startsWith("info" ) ) _level = INFO;
      if( slvl.startsWith("debug") ) _level = DEBUG;
    }
  }
  
  public static void debug( Object... objs ) { write(DEBUG,objs); }
  public static void info ( Object... objs ) { write(INFO ,objs); }
  public static void warn ( Object... objs ) { write(WARN ,objs); }
  public static void err  ( Object... objs ) { write(ERRR ,objs); }

  public static RuntimeException throwErr( Throwable e ) {
    err(e);                     // Log it
    throw e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e); // Throw it
  }

  private static void write( int lvl, Object objs[] ) { if( lvl <= _level ) write0(lvl,objs); }

  private static void write0( int lvl, Object objs[] ) {
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
      for( String s : bufmsgs ) write0(INFO,s);
    }
    write0(lvl,res);
  }

  private static void write0( int lvl, String s ) {
    StringBuilder sb = new StringBuilder();
    String hdr = header(lvl);   // Common header for all lines
    write0(sb,hdr,s);

    // log something here
    org.apache.log4j.Logger l4j = _logger != null ? _logger : createLog4j();

    System.out.println(sb);
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
    return Timer.nowAsString()+" "+_preHeader+" "+
      fixedLength(Thread.currentThread().getName() + " ", 10)+
      LVLS[lvl]+": ";
  }

  // A little bit of startup buffering
  private static ArrayList<String> INIT_MSGS = new ArrayList<String>();


  private static synchronized org.apache.log4j.Logger createLog4j() {
    if( _logger != null ) return _logger; // Test again under lock

    // Use ice folder if local, or default
    File dir = new File( H2O.ICE_ROOT.getScheme() == null || Persist.Schemes.FILE.equals(H2O.ICE_ROOT.getScheme())
                         ? H2O.ICE_ROOT.getPath()
                         : H2O.DEFAULT_ICE_ROOT());
      
    // If a log4j properties file was specified on the command-line, use it.
    // Otherwise, create some default properties on the fly.
    String log4jProperties = System.getProperty ("log4j.properties");
    if (log4jProperties != null) {
      PropertyConfigurator.configure(log4jProperties);
    } else {
      LOG_DIR = dir.toString() + File.separator + "h2ologs";
      String ip = H2O.SELF_ADDRESS.getHostAddress();
      // Somehow, the above process for producing an IP address has a slash
      // in it, which is mystifying.  Remove it.
      int port = H2O.H2O_PORT-1;
      String portString = Integer.toString(port);
      String logPathFileName = LOG_DIR + File.separator + "h2o_" + ip + "_" + portString + ".log";

      java.util.Properties p = new java.util.Properties();
      
      p.setProperty("log4j.rootLogger", "INFO, R");
      p.setProperty("log4j.appender.R", "org.apache.log4j.RollingFileAppender");
      p.setProperty("log4j.appender.R.File", logPathFileName);
      p.setProperty("log4j.appender.R.MaxFileSize", "256KB");
      p.setProperty("log4j.appender.R.MaxBackupIndex", "5");
      p.setProperty("log4j.appender.R.layout", "org.apache.log4j.PatternLayout");
      
      // Turn down the logging for some class hierarchies.
      p.setProperty("log4j.logger.org.apache.http", "WARN");
      p.setProperty("log4j.logger.com.amazonaws", "WARN");
      p.setProperty("log4j.logger.org.apache.hadoop", "WARN");
      p.setProperty("log4j.logger.org.jets3t.service", "WARN");
      
      // See the following document for information about the pattern layout.
      // http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html
      //
      //  Uncomment this line to find the source of unwanted messages.
      //     p.setProperty("log4j.appender.R.layout.ConversionPattern", "%p %C %m%n");
      p.setProperty("log4j.appender.R.layout.ConversionPattern", "%m%n");
      
      PropertyConfigurator.configure(p);
    }
    
    return (_logger = LogManager.getLogger(Log.class.getName()));
  }

  static String fixedLength(String s, int length) {
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
  
}
