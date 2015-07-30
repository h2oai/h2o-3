package water;

/**
 * A remote task re-request; NACK indicating "we heard you"
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

class UDPNack extends UDP {
  // Received an ACK for a remote Task.  Ping the task.
  private static long THEN;
  AutoBuffer call(AutoBuffer ab) {
    int tnum = ab.getTask();
    RPC<?> t = ab._h2o.taskGet(tnum);
    if( t != null ) {
      assert t._tasknum==tnum;
      t._nack = true;
      UDPTimeOutThread.PENDING.remove(t._tasknum);
    }
    return ab;
  }

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  String print16( AutoBuffer b ) { return "task# "+b.getTask(); }
}

