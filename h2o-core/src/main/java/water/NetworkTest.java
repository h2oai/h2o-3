package water;

import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.Timer;

import java.util.Random;


public class NetworkTest {
//  static final int API_WEAVER=1; // This file has auto-gen'd doc & json fields
//  static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.
//
//  @API(help = "Message sizes", filter = Default.class, json=true)
  public int[] msg_sizes = new int[]{1,1<<10,1<<20}; //INPUT

//  @API(help = "Repeats", filter = Default.class, json=true)
  public int repeats = 10; //INPUT

//  @API(help = "Do collective test", filter = Default.class, json=true)
  public boolean collective = true;

//  @API(help = "Do serial test", filter = Default.class, json=true)
  public boolean serial = true;

//  @API(help = "Collective broadcast/reduce times in microseconds (for each message size)", json=true)
  public double[] microseconds_collective; //OUTPUT

//  @API(help = "Collective bandwidths in Bytes/sec (for each message size, for each node)", json=true)
  public double[] bandwidths_collective; //OUTPUT

//  @API(help = "Round-trip times in microseconds (for each message size, for each node)", json=true)
  public double[][] microseconds; //OUTPUT

//  @API(help = "Bi-directional bandwidths in Bytes/sec (for each message size, for each node)", json=true)
  public double[][] bandwidths; //OUTPUT

//  @API(help = "Nodes", json=true)
  public String[] nodes; //OUTPUT

  public void execImpl() {
    microseconds = new double[msg_sizes.length][];
    microseconds_collective = new double[msg_sizes.length];
    NetworkTester nt = new NetworkTester(msg_sizes, microseconds, microseconds_collective, repeats, serial, collective);
    H2O.submitTask(nt);
    nt.join();

    // compute bandwidths from timing results
    bandwidths = new double[msg_sizes.length][];
    for (int i=0; i<bandwidths.length; ++i) {
      bandwidths[i] = new double[microseconds[i].length];
      for (int j=0; j< microseconds[i].length; ++j) {
        //send and receive the same message -> 2x
        bandwidths[i][j] = ( 2*msg_sizes[i] /*Bytes*/) / (microseconds[i][j] / 1e6 /*Seconds*/) ;
      }
    }

    bandwidths_collective = new double[msg_sizes.length];
    for (int i=0; i<bandwidths_collective.length; ++i) {
      //broadcast and reduce the message to all nodes -> 2 x nodes
      bandwidths_collective[i] = ( 2*H2O.CLOUD.size()*msg_sizes[i] /*Bytes*/) / (microseconds_collective[i] / 1e6 /*Seconds*/) ;
    }

    // populate node names
    nodes = new String[H2O.CLOUD.size()];
    for (int i=0; i<nodes.length; ++i)
      nodes[i] = H2O.CLOUD._memary[i]._key.toString();
    StringBuilder sb = new StringBuilder();
    toASCII(sb);
    Log.info(sb);
  }

  // Helper class to run the actual test
  public static class NetworkTester extends H2O.H2OCountedCompleter {
    double[][] microseconds;
    double[] microseconds_collective;
    int[] msg_sizes;
    public int repeats = 10;
    boolean serial;
    boolean collective;

    public NetworkTester(int[] msg, double[][] res, double[] res_collective, int rep, boolean serial, boolean collective) {
      microseconds = res;
      microseconds_collective = res_collective;
      msg_sizes = msg;
      repeats = rep;
      this.serial = serial;
      this.collective = collective;
    }
    @Override
    public void compute2() {
      // serial comm
      if (serial) {
        for (int i = 0; i < microseconds.length; ++i) {
          microseconds[i] = send_recv_all(msg_sizes[i], repeats);
          ArrayUtils.div(microseconds[i], 1e3f); //microseconds
        }
      }
      // collective comm
      if (collective) {
        for (int i = 0; i < microseconds_collective.length; ++i) {
          microseconds_collective[i] = send_recv_collective(msg_sizes[i], repeats);
        }
        ArrayUtils.div(microseconds_collective, 1e3f); //microseconds
      }
      tryComplete();
    }
  }

  /**
   * Helper class that contains a payload and has an empty compute2().
   * If it is remotely executed, it will just send the payload over the wire.
   */
  private static class PingPongTask extends DTask<PingPongTask> {
    private final byte[] _payload;

    public PingPongTask(byte[] payload) {
      _payload = payload;
    }
    @Override public void compute2(){tryComplete();}
    @Override public byte priority() {
      return H2O.MIN_HI_PRIORITY;
    }
  }

  /**
   * Send a message from this node to all nodes in serial (including self), and receive it back
   * @param msg_size message size in bytes
   * @return Time in nanoseconds that it took to send and receive the message (one per node)
   */
  private static double[] send_recv_all(int msg_size, int repeats) {
    byte[] payload = new byte[msg_size];
    new Random().nextBytes(payload);
    final int siz = H2O.CLOUD.size();
    double[] times = new double[siz];
    for (int i = 0; i < siz; ++i) { //loop over compute nodes
      H2ONode node = H2O.CLOUD._memary[i];
      Timer t = new Timer();
      for (int l = 0; l < repeats; ++l) {
        PingPongTask ppt = new PingPongTask(payload); //same payload for all nodes
        new RPC<>(node, ppt).call().get(); //blocking send
      }
      times[i] = (double) t.nanos() / repeats;
    }
    return times;
  }


  /**
   * Helper class that contains a payload and has an empty map/reduce.
   * If it is remotely executed, it will just send the payload over the wire.
   */
  private static class CollectiveTask extends MRTask<CollectiveTask> {
    private final byte[] _payload; //will be sent over the wire (broadcast/reduce)

    public CollectiveTask(byte[] payload){
      _payload = payload;
    }
  }

  /**
   * Broadcast a message from this node to all nodes and reduce it back
   * @param msg_size message size in bytes
   * @return Time in nanoseconds that it took
   */
  private static double send_recv_collective(int msg_size, int repeats) {
    byte[] payload = new byte[msg_size];
    new Random().nextBytes(payload);
    Vec v = Vec.makeConSeq(0., 1); //trivial Vec: 1 element with value 0.

    Timer t = new Timer();
    for (int l = 0; l < repeats; ++l) {
      new CollectiveTask(payload).doAll(v); //same payload for all nodes
    }
    v.remove(new Futures()).blockForPending();
    return (double) t.nanos() / repeats;
  }

  public boolean toHTML(StringBuilder sb) {
    try {
      sb.append("Origin: " + H2O.SELF._key);

      sb.append("<table cellpadding='10'>");
      sb.append("<tr>");
      sb.append("<th>Destination / Message Size</th>");
      for (int msg_size : msg_sizes) {
        sb.append("<th>");
        sb.append(PrettyPrint.bytes(msg_size));
        sb.append("</th>");
      }
      sb.append("</tr>");

      sb.append("<tr>");
      sb.append("<td>");
      sb.append("All (broadcast & reduce)");
      sb.append("</td>");
      for (int m = 0; m < msg_sizes.length; ++m) {
        sb.append("<td>");
        sb.append(PrettyPrint.usecs((long) microseconds_collective[m])).append(", ").
                append(PrettyPrint.bytesPerSecond((long)bandwidths_collective[m]));
        sb.append("</td>");
      }
      sb.append("</tr>");

      for (int n = 0; n < H2O.CLOUD._memary.length; ++n) {
        sb.append("</tr>");

        sb.append("<tr>");
        sb.append("<td>");
        sb.append(H2O.CLOUD._memary[n]._key);
        sb.append("</td>");
        for (int m = 0; m < msg_sizes.length; ++m) {
          sb.append("<td>");
          sb.append(PrettyPrint.usecs((long) microseconds[m][n])).append(", ").
                  append(PrettyPrint.bytesPerSecond((long)bandwidths[m][n]));
          sb.append("</td>");
        }
      }
      sb.append("</tr>");
      sb.append("</table>");
    } catch (Throwable t) {
      return false;
    }
    return true;
  }

  public boolean toASCII(StringBuilder sb) {
    try {
      sb.append("Origin: " + H2O.SELF._key);

      sb.append("\n");
      sb.append("Destination / Message Size\t");
      for (int msg_size : msg_sizes) {
        sb.append("        ").append(PrettyPrint.bytes(msg_size)).append("             ");
      }

      sb.append("\n");
      sb.append("All (broadcast & reduce)");
      sb.append("\t");
      for (int m = 0; m < msg_sizes.length; ++m) {
        sb.append("    ").append(PrettyPrint.usecs((long) microseconds_collective[m])).append(", ").
                append(PrettyPrint.bytesPerSecond((long) bandwidths_collective[m])).append("    ");
        sb.append("\t");
      }

      for (int n = 0; n < H2O.CLOUD._memary.length; ++n) {

        sb.append("\n");
        sb.append(H2O.CLOUD._memary[n]._key);
        sb.append("    \t");
        for (int m = 0; m < msg_sizes.length; ++m) {
          sb.append("    ").append(PrettyPrint.usecs((long) microseconds[m][n])).append(", ").
                  append(PrettyPrint.bytesPerSecond((long) bandwidths[m][n])).append("   ");
          sb.append("\t");
        }
      }
    } catch (Throwable t) {
      return false;
    }
    return true;
  }
}
