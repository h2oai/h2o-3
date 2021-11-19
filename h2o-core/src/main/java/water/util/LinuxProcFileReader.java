package water.util;

import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linux /proc file reader.
 *
 * Read tick information for the system and the current process in order to provide
 * stats on the cloud page about CPU utilization.
 *
 * Tick counts are monotonically increasing since boot.
 *
 * Find definitions of /proc file info here.
 * http://man7.org/linux/man-pages/man5/proc.5.html
 */
public class LinuxProcFileReader {
  private String _systemData;
  private String _processData;
  private String _processStatus;
  private String _pid;

  private long _systemIdleTicks = -1;
  private long _systemTotalTicks = -1;
  private long _processTotalTicks = -1;

  private long _processRss = -1;
  private int _processCpusAllowed = -1;

  private int _processNumOpenFds = -1;

  private ArrayList<long[]> _cpuTicks = null;

  /**
   * Constructor.
   */
  public LinuxProcFileReader() {
  }

  /**
   * @return whether this java process is running in Windows Subsystem for Linux environment.
   */
  public boolean isWsl() {
    try {
      if (! new File("/proc/version").exists()) {
        return false;
      }

      String s = readFile(new File("/proc/version"));
      if (! s.contains("Microsoft")) {
        return false;
      }

      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  /**
   * @return ticks the system was idle.  in general:  idle + busy == 100%
   */
  public long getSystemIdleTicks()   { assert _systemIdleTicks > 0;    return _systemIdleTicks; }

  /**
   * @return ticks the system was up.
   */
  public long getSystemTotalTicks()  { assert _systemTotalTicks > 0;   return _systemTotalTicks; }

  /**
   * @return ticks this process was running.
   */
  public long getProcessTotalTicks() { assert _processTotalTicks > 0;  return _processTotalTicks; }

  /**
   * Array of ticks.
   * [cpu number][tick type]
   *
   * tick types are:
   *
   * [0] user ticks
   * [1] system ticks
   * [2] other ticks (i/o)
   * [3] idle ticks
   *
   * @return ticks array for each cpu of the system.
   */
  public long[][] getCpuTicks()      { assert _cpuTicks != null;       return _cpuTicks.toArray(new long[0][0]); }

  /**
   * @return resident set size (RSS) of this process.
   */
  public long getProcessRss()        { assert _processRss > 0;         return _processRss; }

  static private boolean isOSNameMatch(final String osName, final String osNamePrefix) {
    if (osName == null) {
      return false;
    }

    return osName.startsWith(osNamePrefix);
  }

  private static boolean getOSMatchesName(final String osNamePrefix) {
    String osName = System.getProperty("os.name");
    return isOSNameMatch(osName, osNamePrefix);
  }

  private static boolean IS_OS_LINUX() {
    return getOSMatchesName("Linux") || getOSMatchesName("LINUX");
  }

  /**
   * @return number of CPUs allowed by this process.
   */
  public int getProcessCpusAllowed() {
    return getProcessCpusAllowed(IS_OS_LINUX());
  }

  int getProcessCpusAllowed(boolean isLinux) {
    if (! isLinux) {
      return getProcessCpusAllowedFallback();
    }

    // _processCpusAllowed is not available on CentOS 5 and earlier.
    // In this case, just return availableProcessors.
    if (_processCpusAllowed < 0) {
      return getProcessCpusAllowedFallback();
    }

    return _processCpusAllowed;
  }

  int getProcessCpusAllowedFallback() {
    // Note: We use H2ORuntime#availableProcessors everywhere else - here we report the actual #cpus JVM is allowed to see
    return Runtime.getRuntime().availableProcessors();
  }

  /**
   * @return number of currently open fds of this process.
   */
  public int getProcessNumOpenFds() { assert _processNumOpenFds > 0;  return _processNumOpenFds; }


  /**
   * @return process id for this node as a String.
   */
  public String getProcessID() { return _pid; }
  /**
   * Read and parse data from /proc/stat and /proc/&lt;pid&gt;/stat.
   * If this doesn't work for some reason, the values will be -1.
   */
  public void read() {
    String pid = "-1";
    try {
      pid = getProcessId();
      _pid = pid;
    }
    catch (Exception ignore) {}

    File f = new File ("/proc/stat");
    if (! f.exists()) {
      return;
    }

    try {
      readSystemProcFile();
      readProcessProcFile(pid);
      readProcessNumOpenFds(pid);
      readProcessStatusFile(pid);
      parseSystemProcFile(_systemData);
      parseProcessProcFile(_processData);
      parseProcessStatusFile(_processStatus);
    }
    catch (Exception ignore) {}
  }

  /**
   * @return true if all the values are ok to use; false otherwise.
   */
  public boolean valid() {
    return ((_systemIdleTicks >= 0) && (_systemTotalTicks >= 0) && (_processTotalTicks >= 0) &&
            (_processNumOpenFds >= 0));
  }

  /**
   * @return number of set bits in hexadecimal string (chars must be 0-F)
   */
  public static int numSetBitsHex(String s) {
    // Look-up table for num set bits in 4-bit char
    final int[] bits_set = {0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4};

    int nset = 0;
    for(int i = 0; i < s.length(); i++) {
      Character ch = s.charAt(i);
      if (ch == ',') {
        continue;
      }
      int x = Integer.parseInt(ch.toString(), 16);
      nset += bits_set[x];
    }
    return nset;
  }

  private static String getProcessId() throws Exception {
    // Note: may fail in some JVM implementations
    // therefore fallback has to be provided

    // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
    final String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    final int index = jvmName.indexOf('@');

    if (index < 1) {
      // part before '@' empty (index = 0) / '@' not found (index = -1)
      throw new Exception ("Can't get process Id");
    }

    return Long.toString(Long.parseLong(jvmName.substring(0, index)));
  }

  private String readFile(File f) throws Exception {
    char[] buffer = new char[16 * 1024];
    FileReader fr = new FileReader(f);
    int bytesRead = 0;
    while (true) {
      int n = fr.read(buffer, bytesRead, buffer.length - bytesRead);
      if (n < 0) {
        fr.close();
        return new String (buffer, 0, bytesRead);
      }
      else if (n == 0) {
        // This is weird.
        fr.close();
        throw new Exception("LinuxProcFileReader readFile read 0 bytes");
      }

      bytesRead += n;

      if (bytesRead >= buffer.length) {
        fr.close();
        throw new Exception("LinuxProcFileReader readFile unexpected buffer full");
      }
    }
  }

  private void readSystemProcFile() {
    try {
      _systemData = readFile(new File("/proc/stat"));
    }
    catch (Exception ignore) {}
  }

  /**
   * @param s String containing contents of proc file.
   */
  void parseSystemProcFile(String s) {
    if (s == null) return;

    try {
      BufferedReader reader = new BufferedReader(new StringReader(s));
      String line = reader.readLine();

      // Read aggregate cpu values
      {
        Pattern p = Pattern.compile("cpu\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+).*");
        Matcher m = p.matcher(line);
        boolean b = m.matches();
        if (!b) {
          return;
        }

        long systemUserTicks = Long.parseLong(m.group(1));
        long systemNiceTicks = Long.parseLong(m.group(2));
        long systemSystemTicks = Long.parseLong(m.group(3));
        _systemIdleTicks = Long.parseLong(m.group(4));
        _systemTotalTicks = systemUserTicks + systemNiceTicks + systemSystemTicks + _systemIdleTicks;
      }

      // Read individual cpu values
      _cpuTicks = new ArrayList<long[]>();
      line = reader.readLine();
      while (line != null) {
        Pattern p = Pattern.compile("cpu(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+).*");
        Matcher m = p.matcher(line);
        boolean b = m.matches();
        if (! b) {
          break;
        }

        // Copying algorithm from http://gee.cs.oswego.edu/dl/code/
        // See perfbar.c in gtk_perfbar package.
        // int cpuNum = Integer.parseInt(m.group(1));
        long cpuUserTicks = 0;
        long cpuSystemTicks = 0;
        long cpuOtherTicks = 0;
        long cpuIdleTicks = 0;
        cpuUserTicks    += Long.parseLong(m.group(2));
        cpuOtherTicks   += Long.parseLong(m.group(3));
        cpuSystemTicks  += Long.parseLong(m.group(4));
        cpuIdleTicks    += Long.parseLong(m.group(5));
        cpuOtherTicks   += Long.parseLong(m.group(6));
        cpuSystemTicks  += Long.parseLong(m.group(7));
        cpuSystemTicks  += Long.parseLong(m.group(8));
        long[] oneCpuTicks = {cpuUserTicks, cpuSystemTicks, cpuOtherTicks, cpuIdleTicks};
        _cpuTicks.add(oneCpuTicks);

        line = reader.readLine();
      }
    }
    catch (Exception ignore) {}
  }

  private void readProcessProcFile(String pid) {
    try {
      String s = "/proc/" + pid + "/stat";
      _processData = readFile(new File(s));
    }
    catch (Exception ignore) {}
  }

  void parseProcessProcFile(String s) {
    if (s == null) return;

    try {
      BufferedReader reader = new BufferedReader(new StringReader(s));
      String line = reader.readLine();

      Pattern p = Pattern.compile(
              "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
              "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
              "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
              "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
              "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + ".*");
      Matcher m = p.matcher(line);
      boolean b = m.matches();
      if (! b) {
        return;
      }

      long processUserTicks   = Long.parseLong(m.group(14));
      long processSystemTicks   = Long.parseLong(m.group(15));
      _processTotalTicks = processUserTicks + processSystemTicks;
      _processRss = Long.parseLong(m.group(24));
    }
    catch (Exception ignore) {}
  }

  private void readProcessNumOpenFds(String pid) {
    try {
      String s = "/proc/" + pid + "/fd";
      File f = new File(s);
      String[] arr = f.list();
      if (arr != null) {
        _processNumOpenFds = arr.length;
      }
    }
    catch (Exception ignore) {}
  }

  private void readProcessStatusFile(String pid) {
    try {
      String s = "/proc/" + pid + "/status";
      _processStatus = readFile(new File(s));
    }
    catch (Exception ignore) {}
  }

  void parseProcessStatusFile(String s) {
    if(s == null) return;
    try {
      Pattern p = Pattern.compile("Cpus_allowed:\\s+([A-Fa-f0-9,]+)");
      Matcher m = p.matcher(s);
      boolean b = m.find();
      if (! b) {
        return;
      }
      _processCpusAllowed = numSetBitsHex(m.group(1));
    }
    catch (Exception ignore) {}
  }

}
