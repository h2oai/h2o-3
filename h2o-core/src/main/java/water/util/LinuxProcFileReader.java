package water.util;

import java.io.*;
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
public abstract class LinuxProcFileReader {
  private static final char[] buffer = new char[8 * 1024]; // MUST be first in file, or else clinit ordering problems
  // Read all of a file, throwing IOE on buffer-full or returning a String
  private static String readFile(File f) throws IOException {
    int bytesRead = 0;
    try(FileReader fr = new FileReader(f)) {
        synchronized(buffer) {
          while (true) {
            int n = fr.read(buffer, bytesRead, buffer.length - bytesRead);
            if( n < 0 ) return new String(buffer, 0, bytesRead);
            bytesRead += n;
            if( bytesRead >= buffer.length ) throw new IOException("LinuxProcFileReader readFile unexpected buffer full");
          }
        }
    }
  }

  // --------- Process ID --------------
  /** @return process id for this node  */
  public static int getProcessID() { return PID; }
  private static final int PID;
  static { PID = getProcessIdFromJMX(); }
  private static int getProcessIdFromJMX() {
    // Note: may fail in some JVM implementations therefore fallback has to be provided.
    // Something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
    final String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
    final int index = jvmName.indexOf('@');
    return index < 1 ? -1 // part before '@' empty (index = 0) / '@' not found (index = -1)
      : Integer.parseInt(jvmName.substring(0, index));
  }

  // --------- /proc/pid/status --- Number of available processors
  /** @return number of CPUs allowed by this process. */
  public static char getProcessCpusAllowed() { return PROCESS_CPUS_ALLOWED; }
  private static final char PROCESS_CPUS_ALLOWED;
  static { PROCESS_CPUS_ALLOWED = parseProcessStatusFile(readProcessStatusFile(PID)); }
  private static String readProcessStatusFile(int pid) {
    try { return pid== -1 ? null : readFile(new File("/proc/"+pid+"/status")); }
    catch( IOException ignore ) { return null; }
  }
  private static char parseProcessStatusFile(String s) {
    char cpus = (char) Runtime.getRuntime().availableProcessors();
    if(s == null) return cpus;
    try {
      Matcher m = Pattern.compile("Cpus_allowed:\\s+([A-Fa-f0-9,]+)").matcher(s);
      return m.find() ? numSetBitsHex(m.group(1)) : cpus;
    }
    catch( Exception ignore ) { return cpus; }
  }
  /** @return number of set bits in hexadecimal string (chars must be 0-F) */
  private static char numSetBitsHex(String s) {
    // Look-up table for num set bits in 4-bit char
    final int[] bits_set = {0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4};
    char nset = 0;
    for(int i = 0; i < s.length(); i++) {
      Character ch = s.charAt(i);
      if (ch == ',') continue;
      int x = Integer.parseInt(ch.toString(), 16);
      nset += bits_set[x];
    }
    return nset;
  }


  // --------- /proc/pid/proc --- process RSS and TotalTicks ----
  /** @return ticks this process was running. */
  public static long getProcessTotalTicks() { return _processTotalTicks; }
  private static long _processTotalTicks = -1;

  /** @return resident set size (RSS) of this process. */
  public static long getProcessRss() { return _processRss; }
  private static long _processRss = -1;

  private static String readProcessProcFile(int pid) {
    try { return pid == -1 ? null : readFile(new File("/proc/"+pid+"/stat")); }
    catch( IOException ignore ) { return null; }
  }
  private static final Pattern procPattern = Pattern.compile(
    "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
    "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
    "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
    "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + "\\s+" +
    "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" + ".*");
  private static void parseProcessProcFile(String s) {
    if (s == null) return;
    try {
      BufferedReader reader = new BufferedReader(new StringReader(s));
      Matcher m = procPattern.matcher(reader.readLine());
      if(!m.matches() ) return;
      long processUserTicks   = Long.parseLong(m.group(14));
      long processSystemTicks = Long.parseLong(m.group(15));
      _processTotalTicks = processUserTicks + processSystemTicks;
      _processRss = Long.parseLong(m.group(24));
    }
    catch (Exception ignore) {}
  }


  // --------- /proc/pid/stat --- System and Process cpu ticks -----------
  /**  Array of ticks:  [cpu number][tick type]
   *
   * tick types are:
   * [0] user ticks
   * [1] system ticks
   * [2] other ticks (i/o)
   * [3] idle ticks
   *
   * @return ticks array for each cpu of the system.
   */
  public static long[][] getCpuTicks() { return CPUTICKS; }
  private static final long[][] CPUTICKS = new long[PROCESS_CPUS_ALLOWED][4/*{user,sys,io,idle}*/];
  /** @return ticks the system was idle.  in general:  idle + busy == 100% */
  public static long getSystemIdleTicks() { return _systemIdleTicks; }
  private static long _systemIdleTicks = -1;
  /** @return ticks the system was up. */
  public static long getSystemTotalTicks() { return _systemTotalTicks; }
  private static long _systemTotalTicks = -1;
  // Read from various /proc files, returning a String or null if not available
  private static String readSystemProcFile() {
    try { return readFile(new File("/proc/stat")); }
    catch( IOException ignore ) { return null; }
  }
  private static final Pattern sysTicksPattern = Pattern.compile("cpu\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+).*");
  private static final Pattern cpuTicksPattern = Pattern.compile("cpu(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+).*");
  /** @param s String containing contents of proc file. */
  private static void parseSystemProcFile(String s) {
    if (s == null) return;
    BufferedReader reader = new BufferedReader(new StringReader(s));
    try { 
      // Read aggregate cpu values
      String line = reader.readLine();
      Matcher m = sysTicksPattern.matcher(line);
      if( !m.matches() ) return;
  
      int matchGroupBase = 0;
      long systemUserTicks = 0;
      long systemSystemTicks = 0;
      long systemOtherTicks = 0;
      systemUserTicks    += Long.parseLong(m.group(1 + matchGroupBase));
      systemOtherTicks   += Long.parseLong(m.group(2 + matchGroupBase));
      systemSystemTicks  += Long.parseLong(m.group(3 + matchGroupBase));
      _systemIdleTicks    = Long.parseLong(m.group(4 + matchGroupBase));
      systemOtherTicks   += Long.parseLong(m.group(5 + matchGroupBase));
      systemSystemTicks  += Long.parseLong(m.group(6 + matchGroupBase));
      systemSystemTicks  += Long.parseLong(m.group(7 + matchGroupBase));
      _systemTotalTicks   = systemUserTicks + systemOtherTicks + systemSystemTicks + _systemIdleTicks;
  
      // Read individual cpu values
      int cpu = 0;
      line = reader.readLine();
      while (line != null) {
        m = cpuTicksPattern.matcher(line);
        if( !m.matches() ) break;
        // Copying algorithm from http://gee.cs.oswego.edu/dl/code/
        // See perfbar.c in gtk_perfbar package.
        // int cpuNum = Integer.parseInt(m.group(1));
        matchGroupBase = 1;
        long cpuUserTicks = 0;
        long cpuSystemTicks = 0;
        long cpuOtherTicks = 0;
        long cpuIdleTicks = 0;
        cpuUserTicks    += Long.parseLong(m.group(1 + matchGroupBase));
        cpuOtherTicks   += Long.parseLong(m.group(2 + matchGroupBase));
        cpuSystemTicks  += Long.parseLong(m.group(3 + matchGroupBase));
        cpuIdleTicks    += Long.parseLong(m.group(4 + matchGroupBase));
        cpuOtherTicks   += Long.parseLong(m.group(5 + matchGroupBase));
        cpuSystemTicks  += Long.parseLong(m.group(6 + matchGroupBase));
        cpuSystemTicks  += Long.parseLong(m.group(7 + matchGroupBase));
        CPUTICKS[cpu][0] = cpuUserTicks;
        CPUTICKS[cpu][1] = cpuSystemTicks;
        CPUTICKS[cpu][2] = cpuOtherTicks;
        CPUTICKS[cpu][3] = cpuIdleTicks;
        cpu++;
        line = reader.readLine();
      }
    } catch( IOException ignore ) {/**/}
  }

  // --------- /proc/pid/stat --- System and Process cpu ticks -----------
  /** @return number of currently open fds of this process.  */
  public static int getProcessNumOpenFds() { return _processNumOpenFds; }
  private static int _processNumOpenFds = -1;
  private static void readProcessNumOpenFds(int pid) {
    if( pid == -1 ) return;
    String[] arr = new File("/proc/" + pid + "/fd").list();
    _processNumOpenFds = arr == null ? 0 : arr.length;
  }


  /** Read and parse data from /proc/stat and /proc/pid/stat.
   *  If this doesn't work for some reason, the values will be -1.  */
  public static boolean refresh() {
    parseSystemProcFile(readSystemProcFile());
    parseProcessProcFile(readProcessProcFile(PID));
    readProcessNumOpenFds(PID);
    return valid();
  }

  /** @return true if all the values are ok to use; false otherwise. */
  private static boolean valid() {
    return _systemIdleTicks >= 0 && _systemTotalTicks >= 0 && _processTotalTicks >= 0 && _processNumOpenFds >= 0;
  }


  /**
   * Main is purely for command-line testing.
   */
  public static void main(String[] args) {
    final String sysTestData =
            "cpu  43559117 24094 1632164 1033740407 245624 29 200080 0 0 0\n"+
                    "cpu0 1630761 1762 62861 31960072 40486 15 10614 0 0 0\n"+
                    "cpu1 1531923 86 62987 32118372 13190 0 6806 0 0 0\n"+
                    "cpu2 1436788 332 66513 32210723 10867 0 6772 0 0 0\n"+
                    "cpu3 1428700 1001 64574 32223156 8751 0 6811 0 0 0\n"+
                    "cpu4 1424410 152 62649 32232602 6552 0 6836 0 0 0\n"+
                    "cpu5 1427172 1478 58744 32233938 5471 0 6708 0 0 0\n"+
                    "cpu6 1418433 348 60957 32241807 5301 0 6639 0 0 0\n"+
                    "cpu7 1404882 182 60640 32258150 3847 0 6632 0 0 0\n"+
                    "cpu8 1485698 3593 67154 32101739 38387 0 9016 0 0 0\n"+
                    "cpu9 1422404 1601 66489 32193865 15133 0 8800 0 0 0\n"+
                    "cpu10 1383939 3386 69151 32233567 11219 0 8719 0 0 0\n"+
                    "cpu11 1376904 3051 65256 32246197 8307 0 8519 0 0 0\n"+
                    "cpu12 1381437 1496 68003 32237894 6966 0 8676 0 0 0\n"+
                    "cpu13 1376250 1527 66598 32247951 7020 0 8554 0 0 0\n"+
                    "cpu14 1364352 1573 65520 32262764 5093 0 8531 0 0 0\n"+
                    "cpu15 1359076 1176 64380 32269336 5219 0 8593 0 0 0\n"+
                    "cpu16 1363844 6 29612 32344252 4830 2 4366 0 0 0\n"+
                    "cpu17 1477797 1019 70211 32190189 6278 0 3731 0 0 0\n"+
                    "cpu18 1285849 30 29219 32428612 3549 0 3557 0 0 0\n"+
                    "cpu19 1272308 0 27306 32445340 2089 0 3541 0 0 0\n"+
                    "cpu20 1326369 5 29152 32386824 2458 0 4416 0 0 0\n"+
                    "cpu21 1320883 28 31886 32384709 2327 1 4869 0 0 0\n"+
                    "cpu22 1259498 1 26954 32458931 2247 0 3511 0 0 0\n"+
                    "cpu23 1279464 0 26694 32439550 1914 0 3571 0 0 0\n"+
                    "cpu24 1229977 19 32308 32471217 4191 0 4732 0 0 0\n"+
                    "cpu25 1329079 92 79253 32324092 5267 0 4821 0 0 0\n"+
                    "cpu26 1225922 30 34837 32475220 4000 0 4711 0 0 0\n"+
                    "cpu27 1261848 56 43928 32397341 3552 0 5625 0 0 0\n"+
                    "cpu28 1226707 20 36281 32463498 3935 4 5943 0 0 0\n"+
                    "cpu29 1379751 19 35593 32317723 2872 4 5913 0 0 0\n"+
                    "cpu30 1247661 0 32636 32455845 2033 0 4775 0 0 0\n"+
                    "cpu31 1219016 10 33804 32484916 2254 0 4756 0 0 0\n"+
                    "intr 840450413 1194 0 0 0 0 0 0 0 1 0 0 0 0 0 0 0 55 0 0 0 0 0 0 45 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 593665 88058 57766 41441 62426 61320 39848 39787 522984 116724 99144 95021 113975 99093 78676 78144 0 168858 168858 168858 162 2986764 4720950 3610168 5059579 3251008 2765017 3 3 3 3 3 3 3 3 3 3 3 3 3 3 3 3 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0  0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 00 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n"+
                    "ctxt 1506565570\n"+
                    "btime 1385196580\n"+
                    "processes 1226464\n"+
                    "procs_running 21\n"+
                    "procs_blocked 0\n"+
                    "softirq 793917930 0 156954983 77578 492842649 1992553 0 7758971 51856558 228040 82206598\n";

    final String procTestData = "16790 (java) S 1 16789 16789 0 -1 4202496 6714145 0 0 0 4773058 5391 0 0 20 0 110 0 33573283 64362651648 6467228 18446744073709551615 1073741824 1073778376 140734614041280 140734614032416 140242897981768 0 0 3 16800972 18446744073709551615 0 0 17 27 0 0 0 0 0\n";

    parseSystemProcFile(sysTestData);
    parseProcessProcFile(procTestData);
    System.out.println("System idle ticks: " + getSystemIdleTicks());
    System.out.println("System total ticks: " + getSystemTotalTicks());
    System.out.println("Process total ticks: " + getProcessTotalTicks());
    System.out.println("Process RSS: " + getProcessRss());
    System.out.println("Number of cpus: " + getCpuTicks().length);
  }
}
