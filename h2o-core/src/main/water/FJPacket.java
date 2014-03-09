package water;

import water.H2O.H2OCountedCompleter;

/**
 * A class to handle the work of a received UDP packet.  Typically we'll do a
 * small amount of work based on the packet contents (such as returning a Value
 * requested by another Node, or recording a heartbeat).
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */
public class FJPacket extends H2OCountedCompleter {
  final AutoBuffer _ab;
  final int _ctrl;              // 1st byte of packet
  FJPacket( AutoBuffer ab, int ctrl ) { _ab = ab; _ctrl = ctrl; }

  @Override public void compute2() {
    _ab.getPort(); // skip past the port
    if( _ctrl <= UDP.udp.ack.ordinal() )
      UDP.udp.UDPS[_ctrl]._udp.call(_ab).close();
    else
      RPC.remote_exec(_ab);
    tryComplete();
  }
  // Run at max priority until we decrypt the packet enough to get priorities out
  static private byte[] UDP_PRIORITIES =
    new byte[]{-1,
               H2O.MAX_PRIORITY,    // Heartbeat
               H2O.MAX_PRIORITY,    // Rebooted
               H2O.MAX_PRIORITY,    // Timeline
               H2O.ACK_ACK_PRIORITY,// Ack Ack
               H2O.ACK_PRIORITY,    // Ack
               H2O.DESERIAL_PRIORITY}; // Exec is very high, so we deserialize early
  @Override public byte priority() { return UDP_PRIORITIES[_ctrl]; }
}
