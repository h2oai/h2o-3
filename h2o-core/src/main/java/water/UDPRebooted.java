package water;


import water.util.Log;

/**
 * A UDP Rebooted packet: this node recently rebooted
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */

public class UDPRebooted extends UDP {
  public static boolean BIG_DEBUG = false;
  public static byte MAGIC_SAFE_CLUSTER_KILL_BYTE = 42;

  public static enum T {
    none,
    reboot,
    shutdown,
    oom,
    error,
    locked,
    mismatch;

    public void send(H2ONode target) {
      assert this != none;
      // Note! To ensure that H2O version without the PUBDEV-4959 fix does not bring H2O with this fix into some unwanted
      // state we need to first discover if we are indeed receiving shutdown packet from a H2O version with this fix.
      // For this, we overload this first byte which is sent in both versions and contain ordinal number of the request type.
      // If we choose number different than the possible ordinal number we can safely discover on which version we are running.

      // When we discover that we run on a new version we can check if
      // the shutdown request comes from the node in the current cluster
      // otherwise we just ignore the request

      AutoBuffer ab;
      if (target == H2O.SELF) {
        ab = AutoBuffer.createForMulticastWrite(udp.rebooted);
      } else {
        ab = new AutoBuffer(target, udp.rebooted._prior).putUdp(udp.rebooted);
      }
      ab.put1(MAGIC_SAFE_CLUSTER_KILL_BYTE)
              .put1(ordinal())
              .putInt(H2O.SELF._heartbeat._cloud_name_hash)
              .close();
    }
    void broadcast() { send(H2O.SELF); }
  }
  static void checkForSuicide(int first_byte, AutoBuffer ab) {
    if( first_byte != UDP.udp.rebooted.ordinal() ) return;
    int shutdownPacketType = ab.get1();
    if(shutdownPacketType == MAGIC_SAFE_CLUSTER_KILL_BYTE) { // we are running on a version with PUBDEV-4959 fix
      shutdownPacketType = ab.get1(); // read the real type
      int cloud_name_hash_origin = ab.getInt();
      if (cloud_name_hash_origin == H2O.SELF._heartbeat._cloud_name_hash) {
        suicide(T.values()[shutdownPacketType], ab._h2o);
      }else {
        ListenerService.getInstance().report("shutdown_fail", cloud_name_hash_origin);
      }
    }else{
      ListenerService.getInstance().report("shutdown_ignored");
      Log.warn("Receive "+ T.values()[shutdownPacketType].toString()+ " request from H2O with older version than 3.14.0.4. This request" +
              " will be ignored");
    }
    // if we receive request from H2O with a wrong version, just ignore the request
  }



  public static class ShutdownTsk extends DTask<ShutdownTsk> {
    final H2ONode _killer;
    final int _timeout;
    final transient boolean [] _confirmations;
    final int _nodeId;
    final int _exitCode;

    public ShutdownTsk(H2ONode killer, int nodeId, int timeout, boolean [] confirmations, int exitCode){
      super(H2O.GUI_PRIORITY);
      _nodeId = nodeId;
      _killer = killer;
      _timeout = timeout;
      _confirmations = confirmations;
      _exitCode = exitCode;
    }
    transient boolean _didShutDown;
    private synchronized void doShutdown(int exitCode, String msg){
      if(_didShutDown)return;
      Log.info(msg);
      H2O.closeAll();
      H2O.exit(exitCode);
    }
    @Override
    public void compute2() {
      Log.info("Orderly shutdown from " + _killer);
      // start a separate thread which will force termination after timeout expires (in case we don't get ack ack in time)
      new Thread(){
        @Override public void run(){
          try {Thread.sleep(_timeout);} catch (InterruptedException e) {}
          doShutdown(_exitCode,"Orderly shutdown may not have been acknowledged to " + _killer + " (no ackack), exiting with exit code " + _exitCode + ".");
        }
      }.start();
      tryComplete();
    }
    @Override public void onAck(){
      _confirmations[_nodeId] = true;
    }
    @Override public void onAckAck(){
      doShutdown(_exitCode,"Orderly shutdown acknowledged to " + _killer + ", exiting with exit code " + _exitCode + ".");
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
    case error:    if (BIG_DEBUG) Thread.dumpStack();
                   m = "Error leading to a cloud kill";                                                          break;
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
    ab.getPort(); // This method has side-effect of setting the next position in AB to first byte after port, that's
                  // why it is used here
    return T.values()[ab.get1()].toString();
  }
}
