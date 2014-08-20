package water;

import water.util.ArrayUtils;
import water.util.PrettyPrint;
import water.util.Timer;

import java.util.Random;


public class NetworkTest extends H2O.H2OCountedCompleter<NetworkTest> {
//  static final int API_WEAVER=1; // This file has auto-gen'd doc & json fields
//  static public DocGen.FieldDoc[] DOC_FIELDS; // Initialized from Auto-Gen code.

//  @API(help = "Message sizes", filter = Default.class, json=true)
  public int[] msg_sizes = new int[]{1,1<<10,1<<20}; //INPUT

//  @API(help = "Repeats", filter = Default.class, json=true)
  public int repeats = 10; //INPUT

//  @API(help = "Round-trip times in microseconds (for each message size, for each node)", json=true)
  public double[][] microseconds; //OUTPUT

//  @API(help = "Bi-directional bandwidths in Bytes/sec (for each message size, for each node)", json=true)
  public double[][] bandwidths; //OUTPUT

//  @API(help = "Nodes", json=true)
  public String[] nodes; //OUTPUT

  @Override protected void compute2() {
    microseconds = new double[msg_sizes.length][];
    bandwidths = new double[msg_sizes.length][];
    for (int i=0; i<msg_sizes.length; ++i) {
      microseconds[i] = send_recv_all(msg_sizes[i], repeats); //nanoseconds
      ArrayUtils.div(microseconds[i], 1e3f); //microseconds
      bandwidths[i] = new double[microseconds[i].length];
      for (int j=0; j< microseconds[i].length; ++j) {
        //send and receive the same message -> 2x
        bandwidths[i][j] = ( 2*msg_sizes[i] /*Bytes*/) / (microseconds[i][j] / 1e6 /*Seconds*/) ;
      }
    }
    nodes = new String[H2O.CLOUD.size()];
    for (int i=0; i<nodes.length; ++i)
      nodes[i] = H2O.CLOUD._memary[i]._key.toString();
  }

  /**
   * Helper class that contains a payload and has an empty compute2().
   * If it is remotely executed, it will just send the payload over the wire.
   */
  private static class PingPongTask extends DTask<PingPongTask> {
    private final byte[] _payload;

    public PingPongTask(int msg_size){
      _payload = new byte[msg_size];
      new Random().nextBytes(_payload);
    }
    @Override public void compute2() {
      tryComplete();
    }
  }

  /**
   * Send a message from this node to all nodes in serial (including self), and receive it back
   * @param msg_size message size in bytes
   * @return Time in nanoseconds that it took to send and receive the message (one per node)
   */
  private static double[] send_recv_all(int msg_size, int repeats) {
    PingPongTask ppt = new PingPongTask(msg_size); //same payload for all nodes
    final int siz = H2O.CLOUD.size();
    double[] times = new double[siz];
    for (int i = 0; i < siz; ++i) { //loop over compute nodes
      H2ONode node = H2O.CLOUD._memary[i];
      Timer t = new Timer();
      for (int l = 0; l < repeats; ++l) {
        new RPC<>(node, ppt).call().get(); //blocking send
      }
      times[i] = (double) t.nanos() / repeats;
    }
    return times;
  }

  public boolean toHTML(StringBuilder sb) {
    try {
      sb.append("Origin: ").append(H2O.SELF._key);

      sb.append("<table cellpadding='10'>");
      sb.append("<tr>");
      sb.append("<th>Destination / Message Size</th>");
      for (int msg_size : msg_sizes) {
        sb.append("<th>");
        sb.append(PrettyPrint.bytes(msg_size));
        sb.append("</th>");
      }
      for (int n = 0; n < microseconds[0].length; ++n) {
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
}
