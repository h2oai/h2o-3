package water;

/**
 * A UDP Heartbeat packet.
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */
class UDPHeartbeat extends UDP {
  @Override AutoBuffer call(AutoBuffer ab) {
    if( ab._h2o != H2O.SELF ) { // Do not update self-heartbeat object
      // The self-heartbeat is the sole holder of racey cloud-concensus hashes
      // and if we update it here we risk dropping an update.
      ab._h2o._heartbeat = new HeartBeat().read(ab);
      Paxos.doHeartbeat(ab._h2o);

      // record clients this node has ever heard off only in multicast mode.
      // in flatfile mode we can use ClientEvent to discover client nodes
      if(!H2O.isFlatfileEnabled() && ab._h2o._heartbeat._client){
        H2O.reportClient(ab._h2o);
      }
    }
    return ab;
  }

  static void build_and_multicast( H2O cloud, HeartBeat hb ) {
    // Paxos.print_debug("send: heartbeat ",cloud._memset);
    assert hb._cloud_hash != 0 || hb._client; // Set before send, please
    H2O.SELF._heartbeat = hb;
    hb.write(new AutoBuffer(H2O.SELF,udp.heartbeat._prior).putUdp(UDP.udp.heartbeat)).close();
  }
}
