package water.api;

import water.*;

import java.util.concurrent.ConcurrentHashMap;

public class CloudV3 extends SchemaV3<Iced, CloudV3> {
  /**
   * Data structure to store last tick counts from a given node.
   */
  private static class LastTicksEntry {
    final public long _system_idle_ticks;
    final public long _system_total_ticks;
    final public long _process_total_ticks;

    LastTicksEntry(HeartBeat hb) {
      _system_idle_ticks   = hb._system_idle_ticks;
      _system_total_ticks  = hb._system_total_ticks;
      _process_total_ticks = hb._process_total_ticks;
    }
  }

  /**
   * Store last tick counts for each node.
   *
   * This is local to a node and doesn't need to be Iced, so make it transient.
   * Access this each time the Cloud status page is called on this node.
   *
   * The window of tick aggregation is between calls to this page (which might come from the browser or from REST
   * API clients).
   *
   * Note there is no attempt to distinguish between REST API sessions.  Every call updates the last tick count info.
   */
  private static transient ConcurrentHashMap<String,LastTicksEntry> ticksHashMap = new ConcurrentHashMap<>();

  public CloudV3() {}

  // This Schema has no inputs
  @API(help="skip_ticks", direction=API.Direction.INPUT)
  public boolean skip_ticks = false;

  // Output fields
  @API(help="version", direction=API.Direction.OUTPUT)
  public String version;

  @API(help="branch_name", direction=API.Direction.OUTPUT)
  public String branch_name;

  @API(help="build_number", direction=API.Direction.OUTPUT)
  public String build_number;

  @API(help="Node index number cloud status is collected from (zero-based)", direction=API.Direction.OUTPUT)
  public int node_idx;

  @API(help="cloud_name", direction=API.Direction.OUTPUT)
  public String cloud_name;

  @API(help="cloud_size", direction=API.Direction.OUTPUT)
  public int cloud_size;

  @API(help="cloud_uptime_millis", direction=API.Direction.OUTPUT)
  public long cloud_uptime_millis;

  @API(help="cloud_healthy", direction=API.Direction.OUTPUT)
  public boolean cloud_healthy;

  @API(help="Nodes reporting unhealthy", direction=API.Direction.OUTPUT)
  public int bad_nodes;

  @API(help="Cloud voting is stable", direction=API.Direction.OUTPUT)
  public boolean consensus;

  @API(help="Cloud is accepting new members or not", direction=API.Direction.OUTPUT)
  public boolean locked;

  @API(help="Cloud is in client mode.", direction=API.Direction.OUTPUT)
  public boolean is_client;

  @API(help="nodes", direction=API.Direction.OUTPUT)
  public NodeV3[] nodes;

  // Output fields one-per-JVM
  public static class NodeV3 extends SchemaV3<Iced, NodeV3> {
    public NodeV3() {}

    @API(help="IP", direction=API.Direction.OUTPUT)
    public String h2o;

    @API(help="IP address and port in the form a.b.c.d:e", direction=API.Direction.OUTPUT)
    public String ip_port;

    @API(help="(now-last_ping)<HeartbeatThread.TIMEOUT", direction=API.Direction.OUTPUT)
    public boolean healthy;

    @API(help="Time (in msec) of last ping", direction=API.Direction.OUTPUT)
    public long last_ping;

    @API(help="PID", direction=API.Direction.OUTPUT)
    public int pid;

    @API(help="num_cpus", direction=API.Direction.OUTPUT)
    public int num_cpus;

    @API(help="cpus_allowed", direction=API.Direction.OUTPUT)
    public int cpus_allowed;

    @API(help="nthreads", direction=API.Direction.OUTPUT)
    public int nthreads;

    @API(help="System load; average #runnables/#cores", direction=API.Direction.OUTPUT)
    public float sys_load;       // Average #runnables/#cores

    @API(help="System CPU percentage used by this H2O process in last interval", direction=API.Direction.OUTPUT)
    public int my_cpu_pct;

    @API(help="System CPU percentage used by everything in last interval", direction=API.Direction.OUTPUT)
    public int sys_cpu_pct;

    @API(help="Data on Node memory", direction=API.Direction.OUTPUT)
    public long mem_value_size;
    @API(help="Temp (non Data) memory", direction=API.Direction.OUTPUT)
    public long pojo_mem;
    @API(help="Free heap", direction=API.Direction.OUTPUT)
    public long free_mem;
    @API(help="Maximum memory size for node", direction=API.Direction.OUTPUT)
    public long max_mem;
    @API(help="Size of data on node's disk", direction=API.Direction.OUTPUT)
    public long swap_mem;

    @API(help="#local keys", direction=API.Direction.OUTPUT)
    public int num_keys;

    @API(help="Free disk", direction=API.Direction.OUTPUT)
    public long free_disk;
    @API(help="Max disk", direction=API.Direction.OUTPUT)
    public long max_disk;

    @API(help="Active Remote Procedure Calls", direction=API.Direction.OUTPUT)
    public int rpcs_active;

    @API(help="F/J Thread count, by priority", direction=API.Direction.OUTPUT)
    public short fjthrds[];

    @API(help="F/J Task count, by priority", direction=API.Direction.OUTPUT)
    public short fjqueue[];

    @API(help="Open TCP connections", direction=API.Direction.OUTPUT)
    public int tcps_active;

    @API(help="Open File Descripters", direction=API.Direction.OUTPUT)
    public int open_fds;

    @API(help="Linpack GFlops", direction=API.Direction.OUTPUT)
    public double gflops;

    @API(help="Memory Bandwidth", direction=API.Direction.OUTPUT)
    public double mem_bw;

    NodeV3(H2ONode h2o, boolean skip_ticks) {
      HeartBeat hb = h2o._heartbeat;

      // Basic system health
      this.h2o = h2o.toString();
      ip_port = h2o.getIpPortString();
      healthy = (System.currentTimeMillis()-h2o._last_heard_from)<HeartBeatThread.TIMEOUT;
      last_ping = h2o._last_heard_from;
      sys_load = hb._system_load_average;
      gflops = hb._gflops;
      mem_bw = hb._membw;

      // Memory being used
      mem_value_size = hb.get_kv_mem();
      pojo_mem = hb.get_pojo_mem();
      free_mem = hb.get_free_mem();
      swap_mem = hb.get_swap_mem();
      max_mem = pojo_mem + free_mem + mem_value_size;
      num_keys = hb._keys;

      // Disk health
      free_disk = hb.get_free_disk();
      max_disk  = hb.get_max_disk();

      // Fork/Join Activity
      rpcs_active = hb._rpcs;
      fjthrds = hb._fjthrds;
      fjqueue = hb._fjqueue;

      // System properties & I/O Status
      tcps_active = hb._tcps_active;
      open_fds = hb._process_num_open_fds; // -1 if not available
      num_cpus = hb._num_cpus;
      cpus_allowed = hb._cpus_allowed;
      nthreads = hb._nthreads;
      pid = hb._pid;

      // Use tick information to calculate CPU usage percentage for the entire system and
      // for the specific H2O node.
      //
      // Note that 100% here means "the entire box".  This is different from 'top' 100%,
      // which usually means one core.
      my_cpu_pct = -1;
      sys_cpu_pct = -1;
      if (!skip_ticks) {
        LastTicksEntry lte = ticksHashMap.get(h2o.toString());
        if (lte != null) {
          long system_total_ticks_delta = hb._system_total_ticks - lte._system_total_ticks;

          // Avoid divide by 0 errors.
          if (system_total_ticks_delta > 0) {
            long system_idle_ticks_delta = hb._system_idle_ticks - lte._system_idle_ticks;
            double sys_cpu_frac_double = 1 - ((double)(system_idle_ticks_delta) / (double)system_total_ticks_delta);
            if (sys_cpu_frac_double < 0) sys_cpu_frac_double = 0;               // Clamp at 0.
            else if (sys_cpu_frac_double > 1) sys_cpu_frac_double = 1;          // Clamp at 1.
            sys_cpu_pct = (int)(sys_cpu_frac_double * 100);

            long process_total_ticks_delta = hb._process_total_ticks - lte._process_total_ticks;
            double process_cpu_frac_double = ((double)(process_total_ticks_delta) / (double)system_total_ticks_delta);
            // Saturate at 0 and 1.
            if (process_cpu_frac_double < 0) process_cpu_frac_double = 0;       // Clamp at 0.
            else if (process_cpu_frac_double > 1) process_cpu_frac_double = 1;  // Clamp at 1.
            my_cpu_pct = (int)(process_cpu_frac_double * 100);
          }
        }
        LastTicksEntry newLte = new LastTicksEntry(hb);
        ticksHashMap.put(h2o.toString(), newLte);
      }
    }
  }
}
