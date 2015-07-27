package water;

import water.H2O.H2OCountedCompleter;
import water.UDP.udp;

/**
 * A class to handle the work of a received UDP packet.  Typically we'll do a
 * small amount of work based on the packet contents (such as returning a Value
 * requested by another Node, or recording a heartbeat).
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */
class FJPacket extends H2OCountedCompleter {
  final AutoBuffer _ab;
  final int _ctrl;              // 1st byte of packet
  FJPacket( AutoBuffer ab, int ctrl ) { _ab = ab; _ctrl = ctrl; }

  @Override protected void compute2() {
    _ab.getPort(); // skip past the port
    if( _ctrl <= UDP.udp.nack.ordinal() )
      UDP.udp.UDPS[_ctrl]._udp.call(_ab).close();
    else
      RPC.remote_exec(_ab);
    tryComplete();
  }
  /** Exceptional completion path; mostly does printing if the exception was
   *  not handled earlier in the stack.  */
  @Override public boolean onExceptionalCompletion(Throwable ex, jsr166y.CountedCompleter caller) {
    System.err.println("onExCompletion for "+this);
    ex.printStackTrace();
    water.util.Log.err(ex);
    return true;
  }
  // Run at max priority until we decrypt the packet enough to get priorities out
  @Override protected byte priority() {
    return UDP.udp.UDPS[_ctrl]._prior;
  }
}
