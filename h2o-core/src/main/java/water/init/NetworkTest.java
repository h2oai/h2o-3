package water.init;

import water.fvec.Vec;
import water.*;
import water.util.*;

import java.util.Random;


public class NetworkTest extends Iced {
  public int[] msg_sizes = new int[]{1, 1 << 10, 1 << 20}; //INPUT // Message sizes
  public int repeats = 10; //INPUT // Repeats
  public boolean collective = true; // Do collective test
  public boolean serial = true; // Do serial test
  public double[] microseconds_collective; //OUTPUT // Collective broadcast/reduce times in microseconds (for each message size)
  public double[] bandwidths_collective; //OUTPUT // Collective bandwidths in Bytes/sec (for each message size, for each node)
  public double[][] microseconds; //OUTPUT // Round-trip times in microseconds (for each message size, for each node)
  public double[][] bandwidths; //OUTPUT // Bi-directional bandwidths in Bytes/sec (for each message size, for each node)
  public String[] nodes; //OUTPUT // Nodes
  public TwoDimTable table; //OUTPUT

  public NetworkTest execImpl() {
    microseconds = new double[msg_sizes.length][];
    microseconds_collective = new double[msg_sizes.length];
    NetworkTester nt = new NetworkTester(msg_sizes, microseconds, microseconds_collective, repeats, serial, collective);
    H2O.submitTask(nt);
    nt.join();

    // compute bandwidths from timing results
    bandwidths = new double[msg_sizes.length][];
    for (int i = 0; i < bandwidths.length; ++i) {
      bandwidths[i] = new double[microseconds[i].length];
      for (int j = 0; j < microseconds[i].length; ++j) {
        //send and receive the same message -> 2x
        bandwidths[i][j] = (2 * msg_sizes[i] /*Bytes*/) / (microseconds[i][j] / 1e6 /*Seconds*/);
      }
    }

    bandwidths_collective = new double[msg_sizes.length];
    for (int i = 0; i < bandwidths_collective.length; ++i) {
      //broadcast and reduce the message to all nodes -> 2 x nodes
      bandwidths_collective[i] = (2 * H2O.CLOUD.size() * msg_sizes[i] /*Bytes*/) / (microseconds_collective[i] / 1e6 /*Seconds*/);
    }

    // populate node names
    nodes = new String[H2O.CLOUD.size()];
    for (int i = 0; i < nodes.length; ++i)
      nodes[i] = H2O.CLOUD._memary[i].toString();
    fillTable();
    Log.info(table.toString());
    return this;
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
      super((byte)(H2O.MIN_HI_PRIORITY-1));
      microseconds = res;
      microseconds_collective = res_collective;
      msg_sizes = msg;
      repeats = rep;
      this.serial = serial;
      this.collective = collective;
    }

    @Override public void compute2() {
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
    public PingPongTask(byte[] payload) { _payload = payload; }
    @Override public void compute2() { tryComplete(); }
  }

  /**
   * Send a message from this node to all nodes in serial (including self), and receive it back
   *
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

    public CollectiveTask(byte[] payload) {
      _payload = payload;
    }
  }

  /**
   * Broadcast a message from this node to all nodes and reduce it back
   *
   * @param msg_size message size in bytes
   * @return Time in nanoseconds that it took
   */
  private static double send_recv_collective(int msg_size, int repeats) {
    byte[] payload = new byte[msg_size];
    new Random().nextBytes(payload);
    Vec v = Vec.makeZero(1); //trivial Vec: 1 element with value 0.

    Timer t = new Timer();
    for (int l = 0; l < repeats; ++l) {
      new CollectiveTask(payload).doAll(v); //same payload for all nodes
    }
    v.remove(new Futures()).blockForPending();
    return (double) t.nanos() / repeats;
  }

  public void fillTable() {
    String tableHeader = "Network Test";
    String tableDescription = "Launched from " + H2O.SELF._key;
    String[] rowHeaders = new String[H2O.CLOUD.size()+1];
    rowHeaders[0] = "all - collective bcast/reduce";
    for (int i = 0; i < H2O.CLOUD.size(); ++i) {
      rowHeaders[1+i] =
              ((H2O.SELF.equals(H2O.CLOUD._memary[i]) ? "self" : "remote") + " " + H2O.CLOUD._memary[i].toString());
    }
    String[] colHeaders = new String[msg_sizes.length];
    for (int i = 0; i < colHeaders.length; ++i) {
      colHeaders[i] = msg_sizes[i] + " bytes";
    }
    String[] colTypes = new String[msg_sizes.length];
    for (int i = 0; i < colTypes.length; ++i) {
      colTypes[i] = "string";
    }
    String[] colFormats = new String[msg_sizes.length];
    for (int i = 0; i < colTypes.length; ++i) {
      colFormats[i] = "%s";
    }
    String colHeaderForRowHeaders = "Destination";

    table = new TwoDimTable(tableHeader, tableDescription, rowHeaders, colHeaders, colTypes, colFormats, colHeaderForRowHeaders);

    for (int m = 0; m < msg_sizes.length; ++m) {
      table.set(0, m, PrettyPrint.usecs((long) microseconds_collective[m]) + ", " + PrettyPrint.bytesPerSecond((long) bandwidths_collective[m]));
    }

    for (int n = 0; n < H2O.CLOUD._memary.length; ++n) {
      for (int m = 0; m < msg_sizes.length; ++m) {
        table.set(1 + n, m, PrettyPrint.usecs((long) microseconds[m][n]) + ", " + PrettyPrint.bytesPerSecond((long) bandwidths[m][n]));
      }
    }
  }
}
