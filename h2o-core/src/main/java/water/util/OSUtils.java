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
}
