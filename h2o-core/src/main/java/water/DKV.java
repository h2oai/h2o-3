package water;

/** A Distributed Key/Value Store.
 *  <p>
 *  Functions to Get and Put Values into the K/V store by Key.
 *  <p>
 *  The <em>Java Memory Model</em> is observed for all operations.  Reads/Gets
 *  will block until the data is available, and will pull from the local cache
 *  is possible.
 *  <p>
 *  Writes/Puts do not block directly, but take a Futures argument.  Typically
 *  a Put requires some kind of coherency traffic and perhaps multiple network
 *  hops.  The Futures argument can be used to tell when when a given Put (or a
 *  collection of them) has completed.  Calls to Put without a Futures merely
 *  make one internally and block till completion.
 *  <p>
 *  <em><b>Performance Concerns</b></em>
 *  <p>
 *  Keys can be cached locally, or not.  Cached reads take no more time than a
 *  NonBlockingHashMap lookup (typically a hundred nanos or so).  Remote reads
 *  require the serialized POJO to pass over the network, plus a little bit of
 *  management logic; time is typically completely determined by network speeds
 *  and object size.
 *  <p>
 *  Local Puts (one where the Key is homed on this Node) update directly in the
 *  local K/V store, taking no more time than a NonBlockingHashMap write.
 *  Remote Puts will serialize and ship data over the wire, taking time related
 *  to object size and network speed.
 *  <p>
 *  Blocking for a Put to complete takes longer, requiring all invalidates to
 *  have happened and perhaps a response from the home node (multiple
 *  network-hop latencies); the invalidates and response are typically a single
 *  UDP packet, but must make a round-trip.
 *  <p>
 *  Puts to unrelated Keys can all proceed in parallel, and will typically be
 *  network bound, and can be blocked for in bulk by a single Futures argument.
 *  <p>
 *  Puts to the same Key will be serialized (the first Put will fully complete,
 *  including all invalidates, before a 2nd Put to the same Key from the same
 *  Node can proceed).  Assuming no other Node does a Get on this Key, no
 *  invalidates will be required for the 2nd and later Puts and they will need
 *  only the single round-trip.
 *  <p>
 *  Note that this class works on one Key at a time, and does not understand
 *  composite Key structures (such as a {@link water.fvec.Vec} Key and all its related
 *  {@link water.fvec.Chunk} Keys - instead it serves as the building block for such
 *  structures.
 *  <p>
 *  @author <a href="mailto:cliffc@h2o.ai"></a>
 *  @version 1.0
 */
public abstract class DKV {

  /** Make the mapping <em>key -&gt; v</em>.  Blocking, caching.  */
  static public Value put( Key key, Iced v ) { return put(key,new Value(key,v)); }
  /** Make the mapping <em>key -&gt; v</em>.  Caching.  */
  static public Value put( Key key, Iced v, Futures fs ) { return put(key,new Value(key,v),fs); }
  /** Make the mapping <em>key -&gt; v</em>.  */
  static public Value put( Key key, Iced v, Futures fs,boolean dontCache ) {
    return put(key,new Value(key,v),fs,dontCache);
  }
  /** Make the mapping <em>keyed._key -&gt; keyed</em>.  Blocking, caching.  */
  static public Value put( Keyed keyed ) { return put(keyed._key,new Value(keyed._key,keyed)); }
  /** Make the mapping <em>keyed._key -&gt; keyed</em>.  Caching.  */
  static public Value put( Keyed keyed, Futures fs ) { return put(keyed._key,new Value(keyed._key,keyed),fs); }

  /** Make the mapping <em>key -&gt; val</em>.  Blocking, caching.  */
  static public Value put( Key key, Value val ) {
    Futures fs = new Futures();
    Value old = put(key,val,fs);
    fs.blockForPending();
    return old;
  }
  /** Make the mapping <em>key -&gt; val</em>.  Caching.  */
  static public Value put( Key key, Value val, Futures fs ) {
    return put(key,val,fs,false);
  }
  /** Make the mapping <em>key -&gt; val</em>.  */
  static public Value put( Key key, Value val, Futures fs, boolean dontCache ) {
    assert key != null;
    assert val==null || val._key == key:"non-matching keys " + key + " != " + val._key;
    while( true ) {
      Value old = Value.STORE_get(key); // Raw-get: do not lazy-manifest if overwriting
      Value res = DputIfMatch(key,val,old,fs,dontCache);
      if( res == old ) return old; // PUT is globally visible now?
      if( val != null && val._key != key ) key = val._key;
    }
  }

  /** Remove any mapping for <em>key</em>.  Blocking.  */
  static public Value remove( Key key ) { return put(key,null); }
  /** Remove any mapping for <em>key</em>.  */
  static public Value remove( Key key, Futures fs ) { return put(key,null,fs); }

  /** Default caching call to {@link #DputIfMatch(Key,Value,Value,Futures,boolean)}  */
  static public Value DputIfMatch( Key key, Value val, Value old, Futures fs) { return DputIfMatch(key, val, old, fs, false);  }

  /** Update the mapping for Key <em>key</em>, from Value <em>old</em> to Value
   *  <em>val</em>.  Fails if the Key is not mapped to <em>old</em>, returning
   *  the Value it IS mapped to.  Takes a required {@link Futures}, which can
   *  be used to note when the operation has completed globally.  If the
   *  <em>dontCache</em> hint is passed in, the Value <em>val</em> is NOT
   *  cached locally, useful streaming a large dataset through and expecting
   *  most of the data to eventually be homed remotely.
   *  <p>
   *  Additionally, this operation <em>locks</em> the Cloud to the current size.
   *  No new Nodes may join after a Key is successfully entered into the DKV.
   *  <p>
   *  @return The Value this Key used to be mapped to; if the returned
   *  Value.equals(old) then the update succeeded, else it failed.
   */
  static public Value DputIfMatch( Key key, Value val, Value old, Futures fs, boolean dontCache ) {
    // For debugging where keys are created from
//    try { System.err.flush(); System.err.println(key); Thread.dumpStack(); System.err.flush(); } catch (Throwable t) {}

    // First: I must block repeated remote PUTs to the same Key until all prior
    // ones complete - the home node needs to see these PUTs in order.
    // Repeated PUTs on the home node are already ordered.
    if( old != null && !key.home() ) old.startRemotePut();

    // local update first, since this is a weak update
    if( val == null && key.home() ) val = Value.makeNull(key);
    Value res = H2O.putIfMatch(key,val,old);
    if( res != old )            // Failed?
      return res;               // Return fail value

    // Check for trivial success: no need to invalidate remotes if the new
    // value equals the old.
    if( old != null && old == val ) {
      System.out.println("No invalidate, new==old");
      return old; // Trivial success?
    }
    if( old != null && val != null && val.equals(old) ) {
      System.out.println("No invalidate, new.equals(old)");
      return old;               // Less trivial success, but no network i/o
    }

    // Before we start doing distributed writes... block until the cloud
    // stabilizes.  After we start doing distributed writes, it is an error to
    // change cloud shape - the distributed writes will be in the wrong place.
    Paxos.lockCloud(key);

    // The 'D' part of DputIfMatch: do Distribution.
    // If PUT is on     HOME, invalidate remote caches
    // If PUT is on non-HOME, replicate/push to HOME
    if( key.home() ) {          // On     HOME?
      if( old != null ) old.lockAndInvalidate(H2O.SELF,val,fs);
      else val.lowerActiveGetCount(null);  // Remove initial read-lock, accounting for pending inv counts
    } else {                    // On non-HOME?
      // Start a write, but do not block for it
      TaskPutKey.put(key.home_node(),key,val,fs, dontCache);
    }
    return old;
  }

  // Stall until all existing writes have completed.
  // Used to order successive writes.
  static void write_barrier() {
    for( H2ONode h2o : H2O.CLOUD._memary )
      for( RPC rpc : h2o.tasks() )
        if( rpc._dt instanceof TaskPutKey || rpc._dt instanceof Atomic )
          rpc.get();
  }

  static public <T extends Iced> T getGet(String key) { return key == null ? null : (T)getGet(Key.make(key)); }
  static public <T extends Iced> T getGet(Key key) {
    if (null == key) return null;
    Value v = get(key);
    if (null == v) return null;
    return v.get();
  }

  /** Return the {@link Value} mapped to Key <em>key</em>, or null if no
   *  mapping.  Blocks till data available, always caches.
   *  @return The {@link Value} mapped to Key <em>key</em>, or null if no
   *  mapping. */
  static public Value get    ( Key key ) { return get(key,true ); }
  /** Prefetch and cache the Value for Key <em>key</em>.  Non-blocking. */
  static public void prefetch( Key key ) {        get(key,false); }
  /** Return the {@link Value} mapped to Key formed by <em>key_name</em>, or
   *  null if no mapping.  Blocks till data available, always caches.
   *  @return The {@link Value} mapped to Key formed by <em>key_name</em>, or
   *  null if no mapping. */
  static public Value get    ( String key_name)  { return get(Key.make(key_name),true ); }
  /** Prefetch and cache the Value for Key formed by <em>key_name</em>.
   *  Non-blocking. */
  static public void prefetch( String key_name ) {        get(Key.make(key_name),false); }

  static private Value get( Key key, boolean blocking ) {
    // Read the Cloud once per put-attempt, to keep a consistent snapshot.
    H2O cloud = H2O.CLOUD;
    Value val = Value.STORE_get(key);
    // Hit in local cache?
    if( val != null ) {
      if( val.rawMem() != null || val.rawPOJO() != null || val.isPersisted() )
        return val;
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
    TaskPutKey tpk = home.pendingPutKey(key);
    if( tpk != null ) return tpk._xval == null || tpk._xval.isNull() ? null : tpk._xval;

    // Get data "the hard way"
    RPC<TaskGetKey> tgk = TaskGetKey.start(home,key);
    return blocking ? TaskGetKey.get(tgk) : null;
  }
}
