package water;

import java.io.IOException;
import water.init.NetworkInit;
import water.persist.PersistManager;
import water.util.Log;

/**
 * A UDP Rebooted packet: this node recently rebooted
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

class UDPRebooted extends UDP {
  static enum T {
    none,
    reboot,
    shutdown,
    oom,
    error,
    locked,
    mismatch;

    void send(H2ONode target) {
      assert this != none;
      new AutoBuffer(target).putUdp(udp.rebooted).put1(ordinal()).close();
    }
    void broadcast() { send(H2O.SELF); }
  }

  static void checkForSuicide(int first_byte, AutoBuffer ab) {
    if( first_byte != UDP.udp.rebooted.ordinal() ) return;
    int type = ab.get1();
    suicide( T.values()[type], ab._h2o);
  }

  static void suicide( T cause, H2ONode killer ) {
    String m;
    switch( cause ) {
    case none:   return;
    case reboot: return;
    case shutdown:
      closeAll();
      Log.info("Orderly shutdown command from "+killer);
      H2O.exit(0);
      return;
    case oom:      m = "Out of Memory, Heap Space exceeded, increase Heap Size,";                                break;
    case error:    m = "Error leading to a cloud kill";                                                          break;
    case locked:   m = "Attempting to join an H2O cloud that is no longer accepting new H2O nodes";              break;
    case mismatch: m = "Attempting to join an H2O cloud with a different H2O version (is H2O already running?)"; break;
    default:       m = "Received kill " + cause;                                                                 break;
    }
    closeAll();
    Log.err(m+" from "+killer);
    H2O.die("Exiting.");
  }

  @Override AutoBuffer call(AutoBuffer ab) {
    if( ab._h2o != null ) ab._h2o.rebooted();
    return ab;
  }

  // Try to gracefully close/shutdown all i/o channels.
  private static void closeAll() {
    try { NetworkInit._udpSocket.close(); } catch( IOException ignore ) { }
    try { H2O.getJetty().stop(); } catch( Exception ignore ) { }
    try { TCPReceiverThread.SOCK.close(); } catch( IOException ignore ) { }
    PersistManager PM = H2O.getPM();
    if( PM != null ) PM.getIce().cleanUp();
  }

  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  @Override String print16( AutoBuffer ab ) {
    ab.getPort();
    return T.values()[ab.get1()].toString();
  }
}
