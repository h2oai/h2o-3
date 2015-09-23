package water;


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
      new AutoBuffer(target,udp.rebooted._prior).putUdp(udp.rebooted).put1(ordinal()).close();
    }
    void broadcast() { send(H2O.SELF); }
  }

  static void checkForSuicide(int first_byte, AutoBuffer ab) {
    if( first_byte != UDP.udp.rebooted.ordinal() ) return;
    int type = ab.get1();
    suicide( T.values()[type], ab._h2o);
  }



  public static class ShutdownTsk extends DTask<ShutdownTsk> {
    final H2ONode _killer;
    final int _timeout;
    final transient boolean [] _confirmations;
    final int _nodeId;

    public ShutdownTsk(H2ONode killer, int nodeId, int timeout, boolean [] confirmations){
      _nodeId = nodeId;
      _killer = killer;
      _timeout = timeout;
      _confirmations = confirmations;
    }
    @Override public byte priority(){
      return H2O.GUI_PRIORITY;
    }
    transient boolean _didShutDown;
    private synchronized void doShutdown(int exitCode, String msg){
      if(_didShutDown)return;
      Log.info(msg);
      H2O.closeAll();
      H2O.exit(exitCode);
    }
    @Override
    protected void compute2() {
      Log.info("Orderly shutdown from " + _killer);
      // start a separate thread which will force termination after timeout expires (in case we don't get ack ack in time)
      new Thread(){
        @Override public void run(){
          try {Thread.sleep(_timeout);} catch (InterruptedException e) {}
          doShutdown(0,"Orderly shutdown may not have been acknowledged to " + _killer + " (no ackack), still exiting with exit code 0.");
        }
      }.start();
      tryComplete();
    }
    @Override public void onAck(){
      _confirmations[_nodeId] = true;
    }
    @Override public void onAckAck(){
      doShutdown(0,"Orderly shutdown acknowledged to " + _killer + ", exiting with exit code 0.");
    }

  }
  static void suicide( T cause, final H2ONode killer ) {
    String m;
    switch( cause ) {
    case none:   return;
    case reboot: return;
    case shutdown:
      Log.warn("Orderly shutdown should be handled via ShutdownTsk. Message is from outside of the cloud? Ignoring it.");
      return;
    case oom:      m = "Out of Memory, Heap Space exceeded, increase Heap Size,";                                break;
    case error:    m = "Error leading to a cloud kill";                                                          break;
    case locked:   m = "Attempting to join an H2O cloud that is no longer accepting new H2O nodes";              break;
    case mismatch: m = "Attempting to join an H2O cloud with a different H2O version (is H2O already running?)"; break;
    default:       m = "Received kill " + cause;                                                                 break;
    }
    H2O.closeAll();
    Log.err(m+" from "+killer);
    H2O.die("Exiting.");
  }

  @Override AutoBuffer call(AutoBuffer ab) {
    checkForSuicide(udp.rebooted.ordinal(),ab);
    if( ab._h2o != null )
      ab._h2o.rebooted();
    return ab;
  }


  // Pretty-print bytes 1-15; byte 0 is the udp_type enum
  @Override String print16( AutoBuffer ab ) {
    ab.getPort();
    return T.values()[ab.get1()].toString();
  }
}
