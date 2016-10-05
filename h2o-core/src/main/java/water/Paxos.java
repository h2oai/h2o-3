package water;

import java.util.Arrays;
import water.H2ONode.H2Okey;
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
    if( !h2o._heartbeat.check_jar_md5() ) {
      if( H2O.CLOUD.size() > 1 ) {
        Log.warn("Killing "+h2o+" because of H2O version mismatch (md5 differs).");
        UDPRebooted.T.mismatch.send(h2o);
      } else {
        H2O.die("Attempting to join "+h2o+" with an H2O version mismatch (md5 differs).  (Is H2O already running?)  Exiting.");
      }
      return 0;
    }

    // I am not client but received client heartbeat in flatfile mode.
    // Means that somebody is trying to connect to this cloud.
    // => update list of static hosts (it needs clean up)
    if (!H2O.ARGS.client && H2O.isFlatfileEnabled()
         && h2o._heartbeat._client
         && !H2O.isNodeInFlatfile(h2o)) {
      // Extend static list of nodes to multicast to propagate information to client
      H2O.addNodeToFlatfile(h2o);
      // A new client `h2o` is connected so we broadcast it around to other nodes
      // Note: this could cause a temporary flood of messages since the other
      // nodes will later inform about the connected client as well.
      // Note: It would be helpful to have a control over flatfile-based multicast to inject a small wait.
      UDPClientEvent.ClientEvent.Type.CONNECT.broadcast(h2o);
    } else if (H2O.ARGS.client
               && H2O.isFlatfileEnabled()
               && !H2O.isNodeInFlatfile(h2o)) {
      // This node is a client and using a flatfile to figure out a topology of the cluster.
      // In this case we do not expect that we have a complete flatfile but use information
      // provided by a host we received heartbeat from.
      // That means that the host is in our flatfile already or it was notified about this client node
      // via a node which is already in the flatfile)
      H2O.addNodeToFlatfile(h2o);
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
    H2O.notifyAboutCloudSize(H2O.SELF_ADDRESS, H2O.API_PORT, H2O.CLOUD.size());
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
