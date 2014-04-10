package water;

/**
 * Distributed Key/Value Store
 *
 * This class handles the distribution pattern.
 *
 * @author <a href="mailto:cliffc@0xdata.com"></a>
 * @version 1.0
 */
public abstract class DKV {
  // This put is a top-level user-update, and not a reflected or retried
  // update.  i.e., The User has initiated a change against the K/V store.
  // This is a WEAK update: it is not strongly ordered with other updates
  static public Value put( Key key, Value val ) { return put(key,val,null); }
  static public Value put( Key key, Value val, Futures fs ) { return put(key,val,fs,false);}
  static public Value put( Key key, Value val, Futures fs, boolean dontCache ) {
    assert key != null;
    assert val==null || val._key == key:"non-matching keys " + key.toString() + " != " + val._key.toString();
    while( true ) {
      Value old = H2O.raw_get(key); // Raw-get: do not lazy-manifest if overwriting
      Value res = DputIfMatch(key,val,old,fs,dontCache);
      if( res == old ) return old; // PUT is globally visible now?
      if( val != null && val._key != key ) key = val._key;
    }
  }
  static public Value put( Key key, Iced v ) { return put(key,v,null); }
  static public Value put( Key key, Iced v, Futures fs ) {
    return put(key,new Value(key,v),fs);
  }
  static public Value put( Key key, Iced v, Futures fs,boolean donCache ) {
    return put(key,new Value(key,v),fs,donCache);
  }

  // Remove this Key
  static public Value remove( Key key ) { return remove(key,null); }
  static public Value remove( Key key, Futures fs ) { return put(key,null,fs); }

  // Do a PUT, and on success trigger replication.  Some callers need the old
  // value, and some callers need the Futures so we can block later to ensure
  // the result is there.  Many callers don't need either value.  So rather
  // than making a special object to return the pair of values, I've settled
  // for a "callers pay" model with a more complex return setup.  The return
  // value is a Futures if one is needed, or the old Value if not.  If a
  // Futures is returned the old Value is stashed inside of it for the caller
  // to consume.
  static public Value DputIfMatch( Key key, Value val, Value old, Futures fs) {
    return DputIfMatch(key, val, old, fs, false);
  }
  static public Value DputIfMatch( Key key, Value val, Value old, Futures fs, boolean dontCache ) {
    // First: I must block repeated remote PUTs to the same Key until all prior
    // ones complete - the home node needs to see these PUTs in order.
    // Repeated PUTs on the home node are already ordered.
    if( old != null && !key.home() ) old.startRemotePut();

    // local update first, since this is a weak update
    Value res = H2O.putIfMatch(key,val,old);
    if( res != old )            // Failed?
      return res;               // Return fail value

    // Check for trivial success: no need to invalidate remotes if the new
    // value equals the old.
    if( old != null && old == val ) return old; // Trivial success?
    if( old != null && val != null && val.equals(old) )
      return old;               // Less trivial success, but no network i/o

    // Before we start doing distributed writes... block until the cloud
    // stablizes.  After we start doing distrubuted writes, it is an error to
    // change cloud shape - the distributed writes will be in the wrong place.
    Paxos.lockCloud();

    // The 'D' part of DputIfMatch: do Distribution.
    // If PUT is on     HOME, invalidate remote caches
    // If PUT is on non-HOME, replicate/push to HOME
    if( key.home() ) {          // On     HOME?
      if( old != null ) old.lockAndInvalidate(H2O.SELF,fs);
    } else {                    // On non-HOME?
      // Start a write, but do not block for it
      TaskPutKey.put(key.home_node(),key,val,fs, dontCache);
    }
    return old;
  }

  // Stall until all existing writes have completed.
  // Used to order successive writes.
  static public void write_barrier() {
    for( H2ONode h2o : H2O.CLOUD._memary )
      for( RPC rpc : h2o.tasks() )
        if( rpc._dt instanceof TaskPutKey || rpc._dt instanceof Atomic )
          rpc.get();
  }

  // User-Weak-Get a Key from the distributed cloud.
  static public Value get( Key key, int len, int priority ) {
    // Read the Cloud once per put-attempt, to keep a consistent snapshot.
    H2O cloud = H2O.CLOUD;
    Value val = H2O.get(key);
    // Hit in local cache?
    if( val != null ) {
      if( len > val._max ) len = val._max; // See if we have enough data cached locally
      if( len == 0 || val.rawMem() != null || val.rawPOJO() != null || val.isPersisted() ) return val;
      assert !key.home(); // Master must have *something*; we got nothing & need to fetch
    }

    // While in theory we could read from any replica, we always need to
    // inform the home-node that his copy has been Shared... in case it
    // changes and he needs to issue an invalidate.  For now, always and only
    // fetch from the Home node.
    H2ONode home = cloud._memary[key.home(cloud)];

    // If we missed in the cache AND we are the home node, then there is
    // no V for this K (or we have a disk failure).
    if( home == H2O.SELF ) return null;

    // Pending write to same key from this node?  Take that write instead.
    // Moral equivalent of "peeking into the cpu store buffer".  Can happen,
    // e.g., because a prior 'put' of a null (i.e. a remove) is still mid-
    // send to the remote, so the local get has missed above, but a remote
    // get still might 'win' because the remote 'remove' is still in-progress.
    for( RPC<?> rpc : home.tasks() ) {
      DTask dt = rpc._dt;       // Read once; racily changing
      if( dt instanceof TaskPutKey ) {
        assert rpc._target == home;
        TaskPutKey tpk = (TaskPutKey)dt;
        Key k = tpk._key;
        if( k != null && key.equals(k) )
          return tpk._xval;
      }
    }
    // Get data "the hard way"
    return TaskGetKey.get(home,key,priority);
  }
  static public Value get( Key key ) { return get(key,Integer.MAX_VALUE,H2O.GET_KEY_PRIORITY); }
}
