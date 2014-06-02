package water.schemas;

import water.*;
import water.api.Cloud;
import water.api.Handler;
import water.util.DocGen.HTML;
import water.util.PrettyPrint;

public class CloudV1 extends Schema<Cloud,CloudV1> {
  // This Schema has no inputs

  // Output fields
  @API(help="version")
  public String version;
  
  @API(help="cloud_name")
  public String cloud_name;
  
  @API(help="cloud_size")
  public int cloud_size;

  @API(help="cloud_uptime_millis")
  public long cloud_uptime_millis;

  @API(help="Nodes reporting unhealthy")
  public int bad_nodes;

  @API(help="Cloud voting is stable")
  public boolean consensus;

  @API(help="Cloud is accepting new members or not")
  public boolean locked;

  @API(help="nodes")
  public Node[] nodes;
  
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
  @Override public CloudV1 fillInto( Cloud h ) {
    return this;                // No inputs
  }

  // Version&Schema-specific filling from the handler
  @Override public CloudV1 fillFrom( Cloud h ) {
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
    ab.arrayHead(new String[]{"IP","ping","Load","Data (cached)"});

    // Totals line
    long now = System.currentTimeMillis();
    long max_ping=0;
    float load=0f;
    long total_data=0, cached_data=0;
    for( Node node : nodes ) {
      max_ping = Math.max(max_ping,(now-node.last_ping));
      load += node.sys_load;
      total_data  += node.total_value_size;
      cached_data += node.  mem_value_size;
    }
    float avg_load = load/nodes.length;
    formatRow(ab,"",ab.bold("Summary"),max_ping,avg_load,total_data,cached_data);

    // All Node lines
    for( Node n : nodes ) {
      formatRow(ab, n.healthy?"":"class=\"error\"", 
                n.h2o.toString(), now-n.last_ping, n.sys_load,
                n.total_value_size, n.mem_value_size);
    }

    ab.arrayTail();

    return ab.bodyTail();
  }

  private HTML formatRow( HTML ab, String color, 
                          String name, long ping, float load, 
                          long total_data, long mem_data ) {
    ab.p("<tr").p(color).p(">");
    // Basic node health
    ab.cell(name).cell(PrettyPrint.msecs(ping,true)).cell(String.format("%4.3f",load));
    // Data footprint
    int data_perc = total_data==0?100:(int)(mem_data*100/total_data);
    ab.cell(PrettyPrint.bytes(total_data)+(total_data==0?"":" ("+data_perc+"%)"));

    return ab.p("</tr>");
  }

}
