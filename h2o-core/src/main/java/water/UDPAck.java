package water;

/**
 * A remote task request has just returned an ACK with answer
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

class UDPAck extends UDP {
  // Received an ACK for a remote Task.  Ping the task.
  AutoBuffer call(AutoBuffer ab) {
    int tnum = ab.getTask();
    RPC<?> t = ab._h2o.taskGet(tnum);
    // Forgotten task, but still must ACKACK
    if( t == null ) return RPC.ackack(ab,tnum);
    return t.response(ab); // Do the 2nd half of this task, includes ACKACK
  }

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  String print16( AutoBuffer b ) { return "task# "+b.getTask(); }
}

