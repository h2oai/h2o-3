package water;

/**
 * A remote task request has just returned an ACK with answer
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */

public class UDPAck extends UDP {
  // Received an ACK for a remote Task.  Ping the task.
  AutoBuffer call(AutoBuffer ab) {
    int tnum = ab.getTask();
    RPC<?> t = ab._h2o.taskGet(tnum);
    assert t== null || t._tasknum == tnum;
    if( t != null ) t.response(ab); // Do the 2nd half of this task, includes ACKACK
    else ab.close(false,false);
    // Else forgotten task, but still must ACKACK
    return new AutoBuffer(ab._h2o).putTask(UDP.udp.ackack.ordinal(),tnum);
  }

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  public String print16( AutoBuffer b ) { return "task# "+b.getTask(); }
}

