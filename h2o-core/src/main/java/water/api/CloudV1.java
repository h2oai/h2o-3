package water.api;

import water.*;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

class CloudV1 extends Schema<CloudHandler,CloudV1> {
  // This Schema has no inputs

  // Output fields
  @API(help="version")
  private String version;
  
  @API(help="cloud_name")
  private String cloud_name;
  
  @API(help="cloud_size")
  private int cloud_size;

  @API(help="cloud_uptime_millis")
  private long cloud_uptime_millis;

  @API(help="Nodes reporting unhealthy")
  private int bad_nodes;

  @API(help="Cloud voting is stable")
  private boolean consensus;

  @API(help="Cloud is accepting new members or not")
  private boolean locked;

  @API(help="nodes")
  private Node[] nodes;
  
  // Output fields one-per-JVM
  private static class Node extends Iced {
    @API(help="IP")
    final H2ONode h2o;

    @API(help="(now-last_ping)<HeartbeatThread.TIMEOUT")
    final boolean healthy;

    @API(help="Time (in msec) of last ping")
    final long last_ping;

    @API(help="System load; average #runnables/#cores")
    final float sys_load;       // Average #runnables/#cores
         
    @API(help="Data on Node (memory or disk)")
    final long total_value_size;

    @API(help="Data on Node (memory only)")
    final long mem_value_size;

    @API(help="#local keys")
    final int num_keys;

    @API(help="Free heap")
    final long free_mem;
    @API(help="Total heap")
    final long tot_mem;
    @API(help="Max heap")
    final long max_mem;

    @API(help="Free disk")
    final long free_disk;
    @API(help="Max disk")
    final long max_disk;

    @API(help="Active Remote Procedure Calls")
    final int rpcs_active;

    @API(help="F/J Thread count, by priority")
    final short fjthrds[];

    @API(help="F/J Task count, by priority")
    final short fjqueue[];

    @API(help="Open TCP connections")
    final int tcps_active;

    @API(help="Open File Descripters")
    final int open_fds;

    @API(help="num_cpus")
    final int num_cpus;

    @API(help="PID")
    final String pid;

    Node( H2ONode h2o ) {
      HeartBeat hb = h2o._heartbeat;

      // Basic system health
      this.h2o = h2o;
      healthy = (System.currentTimeMillis()-h2o._last_heard_from)<HeartBeatThread.TIMEOUT;
      last_ping = h2o._last_heard_from;
      sys_load = hb._system_load_average;

      // Memory being used
      total_value_size = hb.get_tvalsz();
      mem_value_size = hb.get_mvalsz();
      num_keys = hb._keys;
      // GC health
      free_mem = hb.get_free_mem();
      tot_mem = hb.get_tot_mem();
      max_mem = hb.get_max_mem();
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
      pid = hb._pid;
    }
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override protected CloudV1 fillInto( CloudHandler h ) {
    return this;                // No inputs
  }

  // Version&Schema-specific filling from the handler
  @Override protected CloudV1 fillFrom( CloudHandler h ) {
    version = h._version;
    cloud_name = h._cloud_name;
    cloud_size = h._members.length;
    cloud_uptime_millis = h._uptime_ms;
    consensus = h._consensus;
    locked = h._locked;
    nodes = new Node[h._members.length];
    for( int i=0; i<h._members.length; i++ ) {
      nodes[i] = new Node(h._members[i]);
      if( !nodes[i].healthy ) bad_nodes++;
    }
    return this;
  }

  // Pretty-print the status in HTML
  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.bodyHead();
    ab.title(cloud_name);

    // Status string
    String statstr = "<div>Ready</div>";
    if( !locked     ) statstr = "<div>Accepting new members</div>";
    if( !consensus  ) statstr = "<div class='alert alert-warn'>Adding new members</div>";
    if( bad_nodes!=0) statstr = "<div class='alert alert-error'>"+bad_nodes+" nodes are unhealthy</div>";
    ab.putStr("Status", statstr);

    ab.putStr("Uptime",PrettyPrint.msecs(cloud_uptime_millis,true));

    // Node status display
    ab.arrayHead(new String[]{"IP",
                              "ping","Load",
                              "Data (cached%)","Keys",
                              "GC free / total / max",
                              "Disk (free%)",
                              "CPU (rpcs, threads, tasks)",
                              "TCPs & FDs", "Cores", "PID"});

    // Totals line
    long now = System.currentTimeMillis();
    long max_ping=0;
    float load=0f;
    long data_tot=0, data_cached=0, data_keys=0;
    long gc_free=0, gc_tot=0, gc_max=0;
    long disk_free=0, disk_max=0;
    int cpu_rpcs=0;
    short fjthrds[] = new short[H2O.MAX_PRIORITY+1];  java.util.Arrays.fill(fjthrds,(short)-1);
    short fjqueue[] = new short[H2O.MAX_PRIORITY+1];  java.util.Arrays.fill(fjqueue,(short)-1);
    int tcps=0, fds=0;
    int cores=0;
    for( Node n : nodes ) {
      max_ping = Math.max(max_ping,(now-n.last_ping));
      load       += n.sys_load;         // Sys health
      data_tot   += n.total_value_size; // Data
      data_cached+= n.  mem_value_size;
      data_keys  += n.num_keys;
      gc_free    += n.free_mem; // GC
      gc_tot     += n. tot_mem;
      gc_max     += n. max_mem;
      disk_free  += n.free_disk; // Disk
      disk_max   += n. max_disk;
      cpu_rpcs   += n.rpcs_active; // Work
      tcps       += n.tcps_active; // I/O
      fds        += n.open_fds;
      cores      += n.num_cpus; // CPUs
      for( int i=0; i<fjthrds.length; i++ ) { // Work
        fjadd(fjthrds,i,n.fjthrds[i]);
        fjadd(fjqueue,i,n.fjqueue[i]);
      }
    }
    float avg_load = load/nodes.length;
    formatRow(ab,"",
              ab.bold("Summary"),max_ping,avg_load,
              data_tot,data_cached,data_keys,
              gc_free,gc_tot,gc_max,
              disk_free,disk_max,
              cpu_rpcs,fjthrds,fjqueue,
              tcps, fds, cores, ""
              );

    // All Node lines
    for( Node n : nodes )
      formatRow(ab, n.healthy?"":"class=\"error\"", 
                n.h2o.toString(), now-n.last_ping, n.sys_load,
                n.total_value_size, n.mem_value_size,n.num_keys,
                n.free_mem,n.tot_mem,n.max_mem,
                n.free_disk,n.max_disk,
                n.rpcs_active,n.fjthrds,n.fjqueue,
                n.tcps_active,n.open_fds,n.num_cpus,n.pid
                );

    ab.arrayTail();

    return ab.bodyTail();
  }

  private HTML formatRow( HTML ab, String color, 
                          String name, long ping, float load, 
                          long total_data, long mem_data, long num_keys,
                          long free_mem, long tot_mem, long max_mem,
                          long free_disk, long max_disk,
                          int rpcs, short fjthrds[], short fjqueue[],
                          int tpcs, int fds, int cores, String pid
                          ) {
    ab.p("<tr").p(color).p(">");
    // Basic node health
    ab.cell(name).cell(PrettyPrint.msecs(ping,true)).cell(String.format("%4.3f",load));
    // Data footprint
    int data_perc = total_data==0?100:(int)(mem_data*100/total_data);
    ab.cell(PrettyPrint.bytes(total_data)+(total_data==0?"":" ("+data_perc+"%)"));
    ab.cell(num_keys);
    // GC health
    ab.cell(PrettyPrint.bytes(free_mem)+"<br>"+PrettyPrint.bytes(tot_mem)+"<br>"+PrettyPrint.bytes(max_mem));
    // Disk health
    int disk_perc = max_disk==0?100:(int)(free_disk*100/max_disk);
    ab.cell(PrettyPrint.bytes(max_disk)+(max_disk==0?"":" ("+disk_perc+"%)"));
    // CPU Fork/Join Activity
    ab.p("<td nowrap>").p(Integer.toString(rpcs)+fjq(fjthrds)+fjq(fjqueue)).p("</td>");
    // File Descripters and System
    ab.cell(Integer.toString(tpcs)+" / "+(fds < 0 ? "-" : Integer.toString(fds)));
    ab.cell(cores);
    ab.cell(pid);

    return ab.p("</tr>");
  }

  private void fjadd( short[] fjs, int x, short fj ) {
    if( fj==-1 ) return;
    fjs[x] = (short)((fjs[x] == -1 ? 0 : fjs[x]) + fj);
  }

  private String fjq( short[] fjs ) {
    int max_lo;
    for( max_lo=H2O.MIN_HI_PRIORITY; max_lo>0; max_lo-- )
      if( fjs[max_lo-1]!= -1 ) break;
    String s = "<br>[";
    for( int i=0; i<max_lo; i++ ) s += Math.max(fjs[i],0)+"/";
    s += ".../";
    for( int i=H2O.MIN_HI_PRIORITY; i<fjs.length-1; i++ ) s += fjs[i]+"/";
    s += fjs[fjs.length-1];
    return s+"]";
  }
}
