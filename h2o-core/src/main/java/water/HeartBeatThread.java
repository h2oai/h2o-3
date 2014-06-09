package water;

import java.lang.management.ManagementFactory;
import javax.management.*;
import water.persist.Persist;
import water.init.LinuxProcFileReader;
import water.util.Log;

/**
 * Starts a thread publishing multicast HeartBeats to the local subnet: the
 * Leader of this Cloud.
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
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

  // My Histogram. Called from any thread calling into the MM.
  // Singleton, allocated now so I do not allocate during an OOM event.
  static private final Cleaner.Histo myHisto = new Cleaner.Histo();

  // uniquely number heartbeats for better timelines
  static private int HB_VERSION;

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
    while( true ) {
      // Once per second, for the entire cloud a Node will multi-cast publish
      // itself, so other unrelated Clouds discover each other and form up.
      try { Thread.sleep(SLEEP); } // Only once-sec per entire Cloud
      catch( InterruptedException ignore ) { }

      // Update the interesting health self-info for publication also
      H2O cloud = H2O.CLOUD;
      HeartBeat hb = H2O.SELF._heartbeat;
      hb._hb_version = HB_VERSION++;
      hb._jvm_boot_msec= TimeLine.JVM_BOOT_MSEC;
      final Runtime run = Runtime.getRuntime();
      hb.set_free_mem  (run. freeMemory());
      hb.set_max_mem   (run.  maxMemory());
      hb.set_tot_mem   (run.totalMemory());
      hb._keys       = (H2O.STORE.size ());
      hb.set_mvalsz    (myHisto.histo(false)._cached);
      hb.set_tvalsz    (myHisto.histo(false)._total );
      hb._num_cpus   = (char)run.availableProcessors();
      Object load = null;
      try {
        load = mbs.getAttribute(os, "SystemLoadAverage");
        if( (double)load == -1 ) // SystemLoadAverage not available on windows
          load = mbs.getAttribute(os, "SystemCpuLoad");
      } catch( Exception e ) {
        // Ignore, data probably not available on this VM
      }
      hb._system_load_average = load instanceof Double ? ((Double) load).floatValue() : 0;
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
      hb.set_free_disk(Persist.getIce().getUsableSpace());
      hb.set_max_disk(Persist.getIce().getTotalSpace());

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
      hb._pid = lpfr.getProcessID();

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
    }
  }
}
