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
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */
public abstract class Paxos {
  // Whether or not we have common knowledge
  public static volatile boolean _commonKnowledge = false;
  // Whether or not we're allowing distributed-writes.  The cloud is not
  // allowed to change shape once we begin writing.
  public static volatile boolean _cloudLocked = false;

  public static NonBlockingHashMap<H2Okey,H2ONode> PROPOSED = new NonBlockingHashMap();

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

    // Never heard of this dude?  See if we want to kill him off for being cloud-locked
    if( !PROPOSED.contains(h2o) ) {
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

      // Add to proposed set, update cloud hash
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
      if( !h2o2._heartbeat._common_knowledge ) {
        return print("Missing common knowledge from all nodes!" ,PROPOSED);
      }
    _commonKnowledge = true;    // Yup!  Have global consensus

    Paxos.class.notifyAll(); // Also, wake up a worker thread stuck in DKV.put
    Paxos.print("Announcing new Cloud Membership: ", H2O.CLOUD._memary);
    Log.info("Cloud of size ", H2O.CLOUD.size(), " formed ", H2O.CLOUD.toString());
    return 0;
  }

  static private int doHash() {
    int hash = 0;
    for( H2ONode h2o : PROPOSED.values() )
      hash += h2o.hashCode();
    assert hash != 0;
    return hash;
  }

  // Before we start doing distributed writes... block until the cloud
  // stablizes.  After we start doing distributed writes, it is an error to
  // change cloud shape - the distributed writes will be in the wrong place.
  static void lockCloud() {
    if( _cloudLocked ) return; // Fast-path cutout
    synchronized(Paxos.class) {
      while( !_commonKnowledge )
        try { Paxos.class.wait(); } catch( InterruptedException ignore ) { }
      _cloudLocked = true;
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
