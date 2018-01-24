package water;

import java.util.Arrays;
import water.H2ONode.H2Okey;
import water.init.JarHash;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;

/**
 * (Not The) Paxos
 *
 * Used to define Cloud membership.  See:
 *   http://en.wikipedia.org/wiki/Paxos_%28computer_science%29
 *
 * Detects and builds a "cloud" - a cooperating group of nodes, with mutual
 * knowledge of each other.  Basically tracks all the nodes that *this* node
 * has ever heard of, and when *all* of the other nodes have all heard of each
 * other, declares the situation as "commonKnowledge", and a Cloud.  This
 * algorithm differs from Paxos in a number of obvious ways:
 * - it is not robust against failing nodes
 * - it requires true global consensus (a Quorum of All)
 * - it is vastly simpler than Paxos
 *
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */
public abstract class Paxos {
  // Whether or not we have common knowledge
  public static volatile boolean _commonKnowledge = false;
  // Whether or not we're allowing distributed-writes.  The cloud is not
  // allowed to change shape once we begin writing.
  public static volatile boolean _cloudLocked = false;

  public static final NonBlockingHashMap<H2Okey,H2ONode> PROPOSED = new NonBlockingHashMap<>();

  // ---
  // This is a packet announcing what Cloud this Node thinks is the current
  // Cloud, plus other status bits
  static synchronized int doHeartbeat( H2ONode h2o ) {
    // Kill somebody if the jar files mismatch.  Do not attempt to deal with
    // mismatched jars.
    if(!H2O.ARGS.client && !h2o._heartbeat._client) {
      // don't check md5 for client nodes
      if (!h2o._heartbeat.check_jar_md5()) {
        System.out.println("Jar check fails; my hash=" + Arrays.toString(JarHash.JARHASH));
        System.out.println("Jar check fails; received hash=" + Arrays.toString(h2o._heartbeat._jar_md5));
        if (H2O.CLOUD.size() > 1) {
          Log.warn("Killing " + h2o + " because of H2O version mismatch (md5 differs).");
          UDPRebooted.T.mismatch.send(h2o);
        } else {
          H2O.die("Attempting to join " + h2o + " with an H2O version mismatch (md5 differs).  (Is H2O already running?)  Exiting.");
        }
        return 0;
      }
    }else{
      if (!h2o._heartbeat.check_jar_md5()) { // we do not want to disturb the user in this case
        // Just report that client with different md5 tried to connect
        ListenerService.getInstance().report("client_wrong_md5", new Object[]{h2o._heartbeat._jar_md5});
      }
    }
    
    if(h2o._heartbeat._cloud_name_hash != H2O.SELF._heartbeat._cloud_name_hash){
      // ignore requests from this node as they are coming from different cluster
      return 0;
    }


    // Update manual flatfile in case of flatfile is enabled
    if (H2O.isFlatfileEnabled()) {
      if (!H2O.ARGS.client && h2o._heartbeat._client && !H2O.isNodeInFlatfile(h2o)) {
        // A new client was reported to this node so we propagate this information to all nodes in the cluster, to this
        // as well
        UDPClientEvent.ClientEvent.Type.CONNECT.broadcast(h2o);
      } else if (H2O.ARGS.client && !H2O.isNodeInFlatfile(h2o)) {
        // This node is a client and using a flatfile to figure out a topology of the cluster. The flatfile passed to the
        // client is always modified at the start of H2O to contain only a single node. This node is used to propagate
        // information about the client to the cluster. Once the nodes have the information about the client, then propagate
        // themselves via heartbeat to the client
        H2O.addNodeToFlatfile(h2o);
      }
    }else{
      // We are operating in the multicast mode and heard from the client, so we need to report the client on this node
      if(h2o._heartbeat._client){
        H2O.reportClient(h2o);
      }
    }

    // Never heard of this dude?  See if we want to kill him off for being cloud-locked
    if( !PROPOSED.contains(h2o) && !h2o._heartbeat._client ) {
      if( _cloudLocked ) {
        Log.warn("Killing "+h2o+" because the cloud is no longer accepting new H2O nodes.");
        UDPRebooted.T.locked.send(h2o);
        return 0;
      }
      if( _commonKnowledge ) {
        _commonKnowledge = false; // No longer sure about things
        H2O.SELF._heartbeat._common_knowledge = false;
        Log.debug("Cloud voting in progress");
      }

      // Add to proposed set, update cloud hash.  Do not add clients
      H2ONode res = PROPOSED.putIfAbsent(h2o._key,h2o);
      assert res==null;
      H2O.SELF._heartbeat._cloud_hash += h2o.hashCode();

    } else if( _commonKnowledge ) {
      return 0;                 // Already know about you, nothing more to do
    }
    int chash = H2O.SELF._heartbeat._cloud_hash;
    assert chash == doHash() : "mismatched hash4, HB="+chash+" full="+doHash();
    assert !_commonKnowledge;

    // Do we have consensus now?
    H2ONode h2os[] = PROPOSED.values().toArray(new H2ONode[PROPOSED.size()]);
    if( H2O.ARGS.client && h2os.length == 0 ) return 0; // Client stalls until it finds *some* cloud
    for( H2ONode h2o2 : h2os )
      if( chash != h2o2._heartbeat._cloud_hash )
        return print("Heartbeat hashes differ, self=0x"+Integer.toHexString(chash)+" "+h2o2+"=0x"+Integer.toHexString(h2o2._heartbeat._cloud_hash)+" ",PROPOSED);
    // Hashes are same, so accept the new larger cloud-size
    H2O.CLOUD.set_next_Cloud(h2os,chash);

    // Demand everybody has rolled forward to same size before consensus
    boolean same_size=true;
    for( H2ONode h2o2 : h2os )
      same_size &= (h2o2._heartbeat._cloud_size == H2O.CLOUD.size());
    if( !same_size ) return 0;

    H2O.SELF._heartbeat._common_knowledge = true;
    for( H2ONode h2o2 : h2os )
      if( !h2o2._heartbeat._common_knowledge )
        return print("Missing common knowledge from all nodes!" ,PROPOSED);
    _commonKnowledge = true;    // Yup!  Have global consensus

    Paxos.class.notifyAll(); // Also, wake up a worker thread stuck in DKV.put
    Paxos.print("Announcing new Cloud Membership: ", H2O.CLOUD._memary);
    Log.info("Cloud of size ", H2O.CLOUD.size(), " formed ", H2O.CLOUD.toString());
    H2Okey leader = H2O.CLOUD.leader()._key;
    H2O.notifyAboutCloudSize(H2O.SELF_ADDRESS, H2O.API_PORT, leader.getAddress(), leader.htm_port(), H2O.CLOUD.size());
    return 0;
  }

  static private int doHash() {
    int hash = 0;
    for( H2ONode h2o : PROPOSED.values() )
      hash += h2o.hashCode();
    assert hash != 0 || H2O.ARGS.client;
    return hash;
  }

  // Before we start doing distributed writes... block until the cloud
  // stabilizes.  After we start doing distributed writes, it is an error to
  // change cloud shape - the distributed writes will be in the wrong place.
  static void lockCloud(Object reason) {
    if( _cloudLocked ) return; // Fast-path cutout
    lockCloud_impl(reason);
  }
  static private void lockCloud_impl(Object reason) {
    // Any fast-path cutouts must happen en route to here.
    Log.info("Locking cloud to new members, because "+reason.toString());
    synchronized(Paxos.class) {
      while( !_commonKnowledge )
        try { Paxos.class.wait(); } catch( InterruptedException ignore ) { }
      _cloudLocked = true;
      // remove nodes which are not in the cluster (e.g. nodes from flat-file which are not actually used)
      if(H2O.isFlatfileEnabled()){
        for(H2ONode n: H2O.getFlatfile()){
          if(!n._heartbeat._client && !PROPOSED.containsKey(n._key)){
            Log.info("Flatile::" + n._key + " not active in this cloud. Removing it from the list.");
            n.stopSendThread();
          }
        }
      }
    }
  }


  static int print( String msg, NonBlockingHashMap<H2Okey,H2ONode> p ) {
    return print(msg,p.values().toArray(new H2ONode[p.size()]));
  }
  static int print( String msg, H2ONode h2os[] ) { return print(msg,h2os,""); }
  static int print( String msg, H2ONode h2os[], String msg2 ) {
    Log.debug(msg,Arrays.toString(h2os),msg2);
    return 0;                   // handy flow-coding return
  }
}
