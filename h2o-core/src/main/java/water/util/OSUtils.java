package water.util;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

public class OSUtils {

 /** Safe call to obtain size of total physical memory.
  *
  * <p>It is platform dependent and returns size of machine physical
  * memory in bytes</p>
  *
  * @return total size of machine physical memory in bytes or -1 if the attribute is not available.
  */
 public static long getTotalPhysicalMemory() {
   long memory = -1;
   try {
     MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
     Object attribute = mBeanServer.getAttribute(new ObjectName("java.lang","type","OperatingSystem"), "TotalPhysicalMemorySize");
     return (Long) attribute;
   } catch (Throwable e) { e.printStackTrace(); }
   return memory;
 }

  public static Long getLongProperty(String name) {
    return getLongProperty(name, 10);
  }

  public static Long getLongProperty(String name, int radix) {
    String value = System.getProperty(name);
    try {
      return value == null ? null : longValueOf(value, radix);
    } catch (NumberFormatException nfe) {
      return null;
    }
  }

  public static long longValueOf(String value, int radix) {
    if (radix == 16 && value.startsWith("0x")) {
      return Long.valueOf(value.substring(2), radix);
    } else {
      return Long.valueOf(value, radix);
    }
  }

  public static String getOsName() {
    return System.getProperty("os.name");
  }

  public static boolean isLinux() {
    return getOsName().toLowerCase().startsWith("linux");
  }

  public static boolean isWindows() {
    return getOsName().toLowerCase().startsWith("windows");
  }

  public static boolean isWsl() {
    LinuxProcFileReader lpfr = new LinuxProcFileReader();
    return lpfr.isWsl();
  }
}
