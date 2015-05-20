package water;

/**
 * A task initiator has his response, we can quit sending him ACKs.
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

class UDPAckAck extends UDP {
  // Received an ACKACK for a remote Task.  Drop the task tracking
  @Override AutoBuffer call(AutoBuffer ab) {
    ab._h2o.remove_task_tracking(ab.getTask());
    return ab;
  }

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  @Override String print16( AutoBuffer ab ) { return "task# "+ab.getTask(); }
}
