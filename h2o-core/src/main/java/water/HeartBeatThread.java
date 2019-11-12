package water;

import java.lang.management.ManagementFactory;
import javax.management.*;
import water.util.LinuxProcFileReader;
import water.util.Log;
import water.init.*;

/**
 * Starts a thread publishing multicast HeartBeats to the local subnet: the
 * Leader of this Cloud.
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */
public class HeartBeatThread extends Thread {
  public HeartBeatThread() {
    super("Heartbeat");
    setDaemon(true);
  }

  // Time between heartbeats.  Strictly several iterations less than the
  // timeout.
  static final int SLEEP = 1000;

  // Timeout in msec before we decide to not include a Node in the next round
  // of Paxos Cloud Membership voting.
  static public final int TIMEOUT = 60000;

  // Timeout in msec before we decide a Node is suspect, and call for a vote
  // to remove him.  This must be strictly greater than the TIMEOUT.
  static final int SUSPECT = TIMEOUT+500;

  // uniquely number heartbeats for better timelines
  static private char HB_VERSION;

  // Timeout in msec for all kinds of I/O operations on unresponsive clients.
  // Endlessly retry until this timeout, and then declare the client "dead", 
  // and toss out all in-flight client ops
  static public final int CLIENT_TIMEOUT=1000;

  // The Run Method.
  // Started by main() on a single thread, this code publishes Cloud membership
  // to the Cloud once a second (across all members).  If anybody disagrees
  // with the membership Heartbeat, they will start a round of Paxos group
  // discovery.
  public void run() {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    ObjectName os;
    try {
      os = new ObjectName("java.lang:type=OperatingSystem");
    } catch( MalformedObjectNameException e ) {
      throw Log.throwErr(e);
    }
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    int counter = 0;
    //noinspection InfiniteLoopStatement
    while( true ) {
      // Update the interesting health self-info for publication also
      H2O cloud = H2O.CLOUD;
      HeartBeat hb = H2O.SELF._heartbeat;
      hb._hb_version = HB_VERSION++;
      hb._jvm_boot_msec= TimeLine.JVM_BOOT_MSEC;


      // Memory utilization as of last FullGC
      long kv_gc = Cleaner.KV_USED_AT_LAST_GC;
      long heap_gc = Cleaner.HEAP_USED_AT_LAST_GC;
      long pojo_gc = Math.max(heap_gc - kv_gc,0);
      long kv_mem = Cleaner.Histo.cached(); // More current than last FullGC numbers; can skyrocket
      // Since last FullGC, assuming POJO remains constant and KV changed: new free memory
      long free_mem = Math.max(MemoryManager.MEM_MAX-kv_mem-pojo_gc,0);
      long pojo_mem = MemoryManager.MEM_MAX-kv_mem-free_mem;
      hb.set_kv_mem(kv_mem);
      hb.set_pojo_mem(pojo_mem);
      hb.set_free_mem(free_mem);
      hb.set_swap_mem(Cleaner.Histo.swapped());
      hb._keys = H2O.STORE.size();

      try {
        hb._system_load_average = ((Double)mbs.getAttribute(os, "SystemLoadAverage")).floatValue();
        if( hb._system_load_average == -1 )  // SystemLoadAverage not available on windows
          hb._system_load_average = ((Double)mbs.getAttribute(os, "SystemCpuLoad")).floatValue();
      } catch( Exception e ) {/*Ignore, data probably not available on this VM*/ }

      int rpcs = 0;
      for( H2ONode h2o : cloud._memary )
        rpcs += h2o.taskSize();
      hb._rpcs       = (char)rpcs;
      // Scrape F/J pool counts
      hb._fjthrds = new short[H2O.MAX_PRIORITY+1];
      hb._fjqueue = new short[H2O.MAX_PRIORITY+1];
      for( int i=0; i<hb._fjthrds.length; i++ ) {
        hb._fjthrds[i] = (short)H2O.getWrkThrPoolSize(i);
        hb._fjqueue[i] = (short)H2O.getWrkQueueSize(i);
      }
      hb._tcps_active= (char)H2ONode.TCPS.get();

      // get the usable and total disk storage for the partition where the
      // persistent KV pairs are stored
      hb.set_free_disk(H2O.getPM().getIce().getUsableSpace());
      hb.set_max_disk (H2O.getPM().getIce().getTotalSpace() );

      // get cpu utilization for the system and for this process.  (linux only.)
      LinuxProcFileReader lpfr = new LinuxProcFileReader();
      lpfr.read();
      if (lpfr.valid()) {
        hb._system_idle_ticks = lpfr.getSystemIdleTicks();
        hb._system_total_ticks = lpfr.getSystemTotalTicks();
        hb._process_total_ticks = lpfr.getProcessTotalTicks();
        hb._process_num_open_fds = lpfr.getProcessNumOpenFds();
      }
      else {
        hb._system_idle_ticks = -1;
        hb._system_total_ticks = -1;
        hb._process_total_ticks = -1;
        hb._process_num_open_fds = -1;
      }
      hb._num_cpus = (short)Runtime.getRuntime().availableProcessors();
      hb._cpus_allowed = (short) lpfr.getProcessCpusAllowed();
      if (H2O.ARGS.nthreads < hb._cpus_allowed) {
        hb._cpus_allowed = H2O.ARGS.nthreads;
      }
      hb._nthreads = H2O.ARGS.nthreads;
      try {
        hb._pid = Integer.parseInt(lpfr.getProcessID());
      }
      catch (Exception ignore) {}

      // Announce what Cloud we think we are in.
      // Publish our health as well.
      UDPHeartbeat.build_and_multicast(cloud, hb);

      // If we have no internet connection, then the multicast goes
      // nowhere and we never receive a heartbeat from ourselves!
      // Fake it now.
      long now = System.currentTimeMillis();
      H2O.SELF._last_heard_from = now;

      // Look for napping Nodes & propose removing from Cloud
      for( H2ONode h2o : cloud._memary ) {
        long delta = now - h2o._last_heard_from;
        if( delta > SUSPECT ) {// We suspect this Node has taken a dirt nap
          if( !h2o._announcedLostContact ) {
            Paxos.print("hart: announce suspect node",cloud._memary,h2o.toString());
            h2o._announcedLostContact = true;
          }
        } else if( h2o._announcedLostContact ) {
          Paxos.print("hart: regained contact with node",cloud._memary,h2o.toString());
          h2o._announcedLostContact = false;
        }
      }

      // Run mini-benchmark every 5 mins.  However, on startup - do not have
      // all JVMs immediately launch a all-core benchmark - they will fight
      // with each other.  Stagger them using the hashcode.
      // Run this benchmark *before* testing the heap or GC, so the GC numbers
      // are current as of the send time.
      if( (counter+Math.abs(H2O.SELF.hashCode()*0xDECAF /*spread wider than 1 apart*/)) % (300/(Float.isNaN(hb._gflops)?10:1)) == 0) {
        hb._gflops   = (float)Linpack.run(hb._cpus_allowed);
        hb._membw    = (float)MemoryBandwidth.run(hb._cpus_allowed);
      }
      counter++;

      // Once per second, for the entire cloud a Node will multi-cast publish
      // itself, so other unrelated Clouds discover each other and form up.
      try { Thread.sleep(SLEEP); } // Only once-sec per entire Cloud
      catch( IllegalMonitorStateException ignore ) { }
      catch( InterruptedException ignore ) { }
    }
  }
}
