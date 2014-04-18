package water;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import jsr166y.ForkJoinPool;
import water.Job.ProgressMonitor;
import water.fvec.*;
import water.nbhm.NonBlockingSetInt;
import water.persist.*;

/**
 * The core Value stored in the distributed K/V store.  It contains an
 * underlying byte[] which may be spilled to disk and freed by the
 * {@link MemoryManager}.
 */
public final class Value extends Iced implements ForkJoinPool.ManagedBlocker {

  // ---
  // The Key part of a Key/Value store.  Transient, because the Value is
  // typically found via its Key, and so the Key is available before we
  // get the Value.
  public transient Key _key;

  // ---
  // Type-id of serialized object; see TypeMap for the list.
  // Might be a primitive array type, or a Iced POJO
  private short _type;
  int type() { return _type; }
  private String className() { return TypeMap.className(_type); }

  // Max size of Values before we start asserting.
  // Sizes around this big, or larger are probably true errors.
  // In any case, they will cause issues with both GC (giant pause times on
  // many collectors) and I/O (long term blocking of TCP I/O channels to
  // service a single request, causing starvation of other requests).
  private static final int MAX = 10*1024*1024;

  // ---
  // Values are wads of bits; known small enough to 'chunk' politely on disk,
  // or fit in a Java heap (larger Values are built via arraylets) but (much)
  // larger than a UDP packet.  Values can point to either the disk or ram
  // version or both.  There's no caching smarts, nor compression nor de-dup
  // smarts.  This is just a local placeholder for some user bits being held at
  // this local Node.
  public int _max; // Max length of Value bytes

  // ---
  // A array of this Value when cached in DRAM, or NULL if not cached.  The
  // contents of _mem are immutable (Key/Value mappings can be changed by an
  // explicit PUT action).  Cleared to null asynchronously by the memory
  // manager (but only if persisted to some disk or in a POJO).  Can be filled
  // in by reloading from disk, or by serializing a POJO.
  private volatile byte[] _mem;
  final byte[] rawMem() { return _mem; }

  // ---
  // A POJO version of the _mem array, or null if the _mem has not been
  // serialized or if _mem is primitive data and not a POJO.  Cleared to null
  // asynchronously by the memory manager (but only if persisted to some disk,
  // or in the _mem array).  Can be filled in by deserializing the _mem array.

  // NOTE THAT IF YOU MODIFY any fields of a POJO that is part of a Value,
  // - this is NOT the recommended programming style,
  // - those changes are visible to all CPUs on the writing node,
  // - but not to other nodes, and
  // - the POJO might be dropped by the MemoryManager and reconstituted from
  //   disk and/or the byte array back to it's original form, losing your changes.
  private volatile Freezable _pojo;
  Freezable rawPOJO() { return _pojo; }

  // Free array (but always be able to rebuild the array)
  final void freeMem() {
    assert isPersisted() || _pojo != null || _key._kb[0]==Key.DVEC;
    _mem = null;
  }
  // Free POJO (but always be able to rebuild the POJO)
  final void freePOJO() {
    assert isPersisted() || _mem != null;
    _pojo = null;
  }

  // The FAST path get-byte-array - final method for speed.
  // Will (re)build the mem array from either the POJO or disk.
  // Never returns NULL.
  public final byte[] memOrLoad() {
    byte[] mem = _mem;          // Read once!
    if( mem != null ) return mem;
    Freezable pojo = _pojo;     // Read once!
    if( pojo != null )          // Has the POJO, make raw bytes
      // Chunks have custom serializer here that skips all steps; just the chunk itself
      if( pojo instanceof Chunk ) return (_mem = ((Chunk)pojo).getBytes());
      else return (_mem = pojo.write(new AutoBuffer()).buf());
    if( _max == 0 ) return (_mem = new byte[0]);
    return (_mem = loadPersist());
  }
  // Just an empty shell of a Value, no local data but the Value is "real".
  // Any attempt to look at the Value will require a remote fetch.
  final boolean isEmpty() { return _max > 0 && _mem==null && _pojo == null && !isPersisted(); }
  private final byte[] getBytes() {
    assert _type==TypeMap.PRIM_B && _pojo == null;
    byte[] mem = _mem;          // Read once!
    return mem != null ? mem : (_mem = loadPersist());
  }

  // The FAST path get-POJO - final method for speed.
  // Will (re)build the POJO from the _mem array.
  // Never returns NULL.
  public <T extends Iced> T get() {
    Iced pojo = (Iced)_pojo;    // Read once!
    if( pojo != null ) return (T)pojo;
    pojo = TypeMap.newInstance(_type);
    pojo.read(new AutoBuffer(memOrLoad()));
    return (T)(_pojo = pojo);
  }
  public <T extends Freezable> T get(Class<T> fc) {
    Freezable pojo = _pojo;     // Read once!
    if( pojo != null ) return (T)pojo;
    pojo = TypeMap.newFreezable(_type);
    pojo.read(new AutoBuffer(memOrLoad()));
    assert fc.isAssignableFrom(pojo.getClass());
    return (T)(_pojo = pojo);
  }

  // ---
  // Time of last access to this value.
  transient long _lastAccessedTime = System.currentTimeMillis();
  private final void touch() {_lastAccessedTime = System.currentTimeMillis();}

  // ---
  // Backend persistence info.  3 bits are reserved for 8 different flavors of
  // backend storage.  1 bit for whether or not the latest _mem field is
  // entirely persisted on the backend storage, or not.  Note that with only 1
  // bit here there is an unclosable datarace: one thread could be trying to
  // change _mem (e.g. to null for deletion) while another is trying to write
  // the existing _mem to disk (for persistence).  This datarace only happens
  // if we have racing deletes of an existing key, along with racing persist
  // attempts.  There are other races that are stopped higher up the stack: we
  // do not attempt to write to disk, unless we have *all* of a Value, so
  // extending _mem (from a remote read) should not conflict with writing _mem
  // to disk.
  //
  // The low 3 bits are final.
  // The on/off disk bit is strictly cleared by the higher layers (e.g. Value.java)
  // and strictly set by the persistence layers (e.g. PersistIce.java).
  private volatile byte _persist; // 3 bits of backend flavor; 1 bit of disk/notdisk
  public  final static byte ICE = 1<<0; // ICE: distributed local disks
  public  final static byte HDFS= 2<<0; // HDFS: backed by hadoop cluster
  public  final static byte S3  = 3<<0; // Amazon S3
  public  final static byte NFS = 4<<0; // NFS: Standard file system
  public  final static byte TCP = 7<<0; // TCP: For profile purposes, not a storage system
  private final static byte BACKEND_MASK = (8-1);
  private final static byte NOTdsk = 0<<3; // latest _mem is persisted or not
  private final static byte ON_dsk = 1<<3;
  final private void clrdsk() { _persist &= ~ON_dsk; } // note: not atomic
  public final void setdsk() { _persist |=  ON_dsk; } // note: not atomic
  public final boolean isPersisted() { return (_persist&ON_dsk)!=0; }
  final byte backend() { return (byte)(_persist&BACKEND_MASK); }

  // ---
  // Interface for using the persistence layer(s).
  boolean onICE (){ return (backend()) ==  ICE; }
  private boolean onHDFS(){ return (backend()) == HDFS; }
  private boolean onNFS (){ return (backend()) ==  NFS; }
  private boolean onS3  (){ return (backend()) ==   S3; }

  /** Store complete Values to disk */
  void storePersist() throws IOException {
    if( isPersisted() ) return;
    Persist.I[backend()].store(this);
  }

  /** Remove dead Values from disk */
  void removePersist() {
    // do not yank memory, as we could have a racing get hold on to this
    //  free_mem();
    if( !isPersisted() || !onICE() ) return; // Never hit disk?
    clrdsk();  // Not persisted now
    Persist.I[backend()].delete(this);
  }
  /** Load some or all of completely persisted Values */
  byte[] loadPersist() {
    assert isPersisted();
    return Persist.I[backend()].load(this);
  }

  String nameOfPersist() { return nameOfPersist(backend()); }
  static String nameOfPersist(int x) {
    switch( x ) {
    case ICE : return "ICE";
    case HDFS: return "HDFS";
    case S3  : return "S3";
    case NFS : return "NFS";
    case TCP : return "TCP";
    default  : return null;
    }
  }

  /** Set persistence to HDFS from ICE */
  private void setHdfs() throws IOException {
    assert onICE();
    byte[] mem = memOrLoad();   // Get into stable memory
    removePersist();            // Remove from ICE disk
    _persist = Value.HDFS|Value.NOTdsk;
    storePersist();
    assert onHDFS();       // Flipped to HDFS
    _mem = mem; // Close a race with the H2O cleaner zapping _mem while removing from ice
  }


  public boolean isFrame()    { return _type == TypeMap.FRAME; }
  public boolean isVecGroup() { return _type == TypeMap.VECGROUP; }
  public boolean isLockable() { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof Lockable; }
  public boolean isVec()      { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof Vec; }
  private boolean isByteVec() { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof ByteVec; }
  private boolean isRawData() {
    if( isFrame() ) {
      Frame fr = get();
      return fr.vecs().length == 1 && (fr.vecs()[0] instanceof ByteVec);
    }
    // either simple value with bytearray, un-parsed value array or byte vec
    return _type == TypeMap.PRIM_B || isByteVec();
  }

  // For plain Values, just the length in bytes.
  // For Frames, the compressed size of all vecs within the frame.
  private long length() {
    return isFrame() ? ((Frame)get()).byteSize() : _max;
  }

  private InputStream openStream() throws IOException {  return openStream(null); }
  /** Creates a Stream for reading bytes */
  private InputStream openStream(ProgressMonitor p) throws IOException {
    if(onNFS() ) return PersistNFS .openStream(_key  );
    if(onHDFS()) return PersistHdfs.openStream(_key,p);
    if(onS3()  ) return PersistS3  .openStream(_key,p);
    if( isFrame() ) throw new IllegalArgumentException("Tried to pass a Frame to openStream (maybe tried to parse a (already-parsed) Frame?)");
    assert _type==TypeMap.PRIM_B : "Expected byte[] type but got "+TypeMap.className(_type);
    return new ByteArrayInputStream(memOrLoad());
  }

  // --------------------------------------------------------------------------
  // Set just the initial fields
  public Value(Key k, int max, byte[] mem, short type, byte be ) {
    assert mem==null || mem.length==max;
    assert max < MAX : "Value size=0x"+Integer.toHexString(max);
    _key = k;
    _max = max;
    _mem = mem;
    _type = type;
    _pojo = null;
    // For the ICE backend, assume new values are not-yet-written.
    // For HDFS & NFS backends, assume we from global data and preserve the
    // passed-in persist bits
    byte p = (byte)(be&BACKEND_MASK);
    _persist = (p==ICE) ? p : be;
    _rwlock = new AtomicInteger(0);
    _replicas = k.home() ? new NonBlockingSetInt() : null;
  }
  public Value(Key k, byte[] mem ) { this(k, mem.length, mem, TypeMap.PRIM_B, ICE); }
  private Value(Key k, int max ) { this(k, max, new byte[max], TypeMap.PRIM_B, ICE); }
  private Value(Key k, int max, byte be ) { this(k, max, null, TypeMap.PRIM_B,  be); }
  Value(Key k, String s ) { this(k, s.getBytes()); }
  Value(Key k, Iced pojo ) { this(k,pojo,ICE); }
  Value(Key k, Iced pojo, byte be ) {
    _key = k;
    _pojo = pojo;
    _type = (short)pojo.frozenType();
    _mem = pojo.write(new AutoBuffer()).buf();
    _max = _mem.length;
    assert _max < MAX : "Value size=0x"+Integer.toHexString(_max);
    // For the ICE backend, assume new values are not-yet-written.
    // For HDFS & NFS backends, assume we from global data and preserve the
    // passed-in persist bits
    byte p = (byte)(be&BACKEND_MASK);
    _persist = (p==ICE) ? p : be;
    _rwlock = new AtomicInteger(0);
    _replicas = k.home() ? new NonBlockingSetInt() : null;
  }
  public Value(Key k, Freezable pojo) { this(k,pojo,ICE); }
  public Value(Key k, Freezable pojo, byte be) {
    _key = k;
    _pojo = pojo;
    _type = (short)pojo.frozenType();
    _mem = pojo.write(new AutoBuffer()).buf();
    _max = _mem.length;
    byte p = (byte)(be&BACKEND_MASK);
    _persist = (p==ICE) ? p : be;
    _rwlock = new AtomicInteger(0);
    _replicas = k.home() ? new NonBlockingSetInt() : null;
  }

  // Custom serializers: the _mem field is racily cleared by the MemoryManager
  // and the normal serializer then might ship over a null instead of the
  // intended byte[].  Also, the value is NOT on the deserialize'd machines disk
  @Override public AutoBuffer write_impl( AutoBuffer ab ) {
    touch();
    return ab.put1(_persist).put2(_type).putA1(memOrLoad());
  }
  // Custom serializer: set _max from _mem length; set replicas & timestamp.
  @Override public Value read_impl(AutoBuffer bb) {
    assert _key == null;        // Not set yet
    _persist = bb.get1();       // Set persistence backend but...
    if( onICE() ) clrdsk();     // ... the on-disk flag is local, just deserialized thus not on MY disk
    _type = (short) bb.get2();
    _mem = bb.getA1();
    _max = _mem.length;
    assert _max < MAX : "Value size=0x"+Integer.toHexString(_max);
    _pojo = null;
    // On remote nodes _rwlock is initialized to 0 (signaling a remote PUT is
    // in progress) flips to -1 when the remote PUT is done, or +1 if a notify
    // needs to happen.
    _rwlock = new AtomicInteger(-1); // Set as 'remote put is done'
    _replicas = new NonBlockingSetInt();
    touch();
    return this;
  }

  // ---------------------
  // Ordering of K/V's!  This field tracks a bunch of things used in ordering
  // updates to the same Key.  Ordering Rules:
  // - Program Order.  You see your own writes.  All writes in a single thread
  //   strongly ordered (writes never roll back).  In particular can:
  //   PUT(v1), GET, PUT(null) and The Right Thing happens.
  // - Unrelated writes can race (unless fencing).
  // - Writes are not atomic: some people can see a write ahead of others.
  // - Last-write-wins: if we do a zillion writes to the same Key then wait "a
  //   long time", then do reads all reads will see the same last value.
  // - Blocking on a PUT stalls until the PUT is cloud-wide visible
  //
  // For comparison to H2O get/put MM
  // IA Memory Ordering,  8 principles from Rich Hudson, Intel
  // 1. Loads are not reordered with other loads
  // 2. Stores are not reordered with other stores
  // 3. Stores are not reordered with older loads
  // 4. Loads may be reordered with older stores to different locations but not
  //    with older stores to the same location
  // 5. In a multiprocessor system, memory ordering obeys causality (memory
  //    ordering respects transitive visibility).
  // 6. In a multiprocessor system, stores to the same location have a total order
  // 7. In a multiprocessor system, locked instructions have a total order
  // 8. Loads and stores are not reordered with locked instructions.
  //
  // My (KN, CNC) interpretation of H2O MM from today:
  // 1. Gets are not reordered with other Gets
  // 2  Puts may be reordered with Puts to different Keys.
  // 3. Puts may be reordered with older Gets to different Keys, but not with
  //    older Gets to the same Key.
  // 4. Gets may be reordered with older Puts to different Keys but not with
  //    older Puts to the same Key.
  // 5. Get/Put amongst threads doesn't obey causality
  // 6. Puts to the same Key have a total order.
  // 7. no such thing. although RMW operation exists with Put-like constraints.
  // 8. Gets and Puts may be reordered with RMW operations
  // 9. A write barrier exists that creates Sequential Consistency.  Same-key
  //    ordering (3-4) can't be used to create the effect.
  //
  // A Reader/Writer lock for the home node to control racing Gets and Puts.
  // - 0 for unlocked
  // - +N for locked by N concurrent GETs-in-flight
  // - -1 for write-locked
  //
  // An ACKACK from the client GET lowers the reader lock count.
  //
  // Home node PUTs alter which Value is mapped to a Key, then they block until
  // there are no active GETs, then atomically set the write-lock, then send
  // out invalidates to all the replicas.  PUTs return when all invalidates
  // have reported back.
  //
  // An initial remote PUT will default the value to 0.  A 2nd PUT attempt will
  // block until the 1st one completes (multiple writes to the same Key from
  // the same JVM block, so there is at most 1 outstanding write to the same
  // Key from the same JVM).  The 2nd PUT will CAS the value to 1, indicating
  // the need for the finishing 1st PUT to call notify().
  //
  // Note that this sequence involves a lot of blocking on repeated writes with
  // cached readers, but not the readers - i.e., writes are slow to complete.
  private transient AtomicInteger _rwlock;
  private boolean RW_CAS( int old, int nnn, String msg ) {
    if( !_rwlock.compareAndSet(old,nnn) ) return false;
    //System.out.println(_key+", "+old+" -> "+nnn+", "+msg);
    return true;
  }
  // List of who is replicated where
  private transient NonBlockingSetInt _replicas;
  private int numReplicas() { return _replicas.size(); }
  /** True if h2o has a copy of this Value */
  boolean isReplicatedTo( H2ONode h2o ) { return _replicas.contains(h2o._unique_idx); }

  /** Atomically insert h2o into the replica list; reports false if the Value
   *  flagged against future replication with a -1.  Also bumps the active
   *  Get count, which remains until the Get completes (we receive an ACKACK). */
  boolean setReplica( H2ONode h2o ) {
    assert _key.home(); // Only the HOME node for a key tracks replicas
    assert h2o != H2O.SELF;     // Do not track self as a replica
    while( true ) {     // Repeat, in case racing GETs are bumping the counter
      int old = _rwlock.get();
      if( old == -1 ) return false; // Write-locked; no new replications.  Read fails to read *this* value
      assert old >= 0;              // Not negative
      if( RW_CAS(old,old+1,"rlock+") ) break;
    }
    // Narrow non-race here.  Here is a time window where the rwlock count went
    // up, but the replica list does not account for the new replica.  However,
    // the rwlock cannot go down until an ACKACK is received, and the ACK
    // (hence ACKACK) doesn't go out until after this function returns.
    _replicas.add(h2o._unique_idx);
    // Both rwlock taken, and replica count is up now.
    return true;
  }

  /** Atomically lower active GET count */
  void lowerActiveGetCount( H2ONode h2o ) {
    assert _key.home();    // Only the HOME node for a key tracks replicas
    assert h2o != H2O.SELF;// Do not track self as a replica
    while( true ) {        // Repeat, in case racing GETs are bumping the counter
      int old = _rwlock.get(); // Read the lock-word
      assert old > 0;      // Since lowering, must be at least 1
      assert old != -1;    // Not write-locked, because we are an active reader
      assert _replicas.contains(h2o._unique_idx); // Self-bit is set
      if( RW_CAS(old,old-1,"rlock-") ) {
        if( old-1 == 0 )   // GET count fell to zero?
          synchronized( this ) { notifyAll(); } // Notify any pending blocked PUTs
        return;            // Repeat until count is lowered
      }
    }
  }

  /** This value was atomically extracted from the local STORE by a successful
   *  TaskPutKey attempt (only 1 thread can ever extract and thus call here).
   *  No future lookups will find this Value, but there may be existing uses.
   *  Atomically set the rwlock count to -1 locking it from further GETs and
   *  ship out invalidates to caching replicas.  May need to block on active
   *  GETs.  Updates a set of Future invalidates that can be blocked against. */
  Futures lockAndInvalidate( H2ONode sender, Futures fs ) {
    assert _key.home(); // Only the HOME node for a key tracks replicas
    // Write-Lock against further GETs
    while( true ) {      // Repeat, in case racing GETs are bumping the counter
      int old = _rwlock.get();
      assert old >= 0 : _key+", rwlock="+old;  // Count does not go negative
      assert old != -1; // Only the thread doing a PUT ever locks
      if( old !=0 ) { // has readers?
        // Active readers: need to block until the GETs (of this very Value!)
        // all complete, before we can invalidate this Value - lest a racing
        // Invalidate bypass a GET.
        try { ForkJoinPool.managedBlock(this); } catch( InterruptedException e ) { }
      } else if( RW_CAS(0,-1,"wlock") )
        break;                  // Got the write-lock!
    }
    // We have the set of Nodes with replicas now.  Ship out invalidates.
    int max = _replicas.length();
    for( int i=0; i<max; i++ )
      if( _replicas.contains(i) && H2ONode.IDX[i] != sender )
        TaskInvalidateKey.invalidate(H2ONode.IDX[i],_key,fs);
    return fs;
  }

  /** Initialize the _replicas field for a PUT.  On the Home node (for remote
   *  PUTs), it is initialized to the one replica we know about, and not
   *  read-locked.  Used on a new Value about to be PUT on the Home node. */
  void initReplicaHome( H2ONode h2o, Key key ) {
    assert key.home();
    assert _key == null; // This is THE initializing key write for serialized Values
    assert h2o != H2O.SELF;     // Do not track self as a replica
    _key = key;
    // Set the replica bit for the one node we know about, and leave the
    // rest clear.  
    _replicas.add(h2o._unique_idx);
    _rwlock.set(0);             // No GETs are in-flight at this time. 
  }

  /** Block this thread until all prior remote PUTs complete - to force
   *  remote-PUT ordering on the home node. */
  void startRemotePut() {
    assert !_key.home();
    int x = 0;
    // assert I am waiting on threads with higher priority?
    while( (x=_rwlock.get()) != -1 ) // Spin until rwlock==-1
      if( x == 1 || RW_CAS(0,1,"remote_need_notify") )
        try { ForkJoinPool.managedBlock(this); } catch( InterruptedException e ) { }
  }

  /** The PUT for this Value has completed.  Wakeup any blocked later PUTs. */
  void completeRemotePut() {
    assert !_key.home();
    // Attempt an eager blind attempt, assuming no blocked pending notifies
    if( RW_CAS(0, -1,"remote_complete") ) return;
    synchronized(this) {
      boolean res = RW_CAS(1, -1,"remote_do_notify");
      assert res;               // Must succeed
      notifyAll();              // Wake up pending blocked PUTs
    }
  }

  /** Return true if blocking is unnecessary.
   *  Alas, used in TWO places and the blocking API forces them to share here. */
  @Override public boolean isReleasable() {
    int r = _rwlock.get();
    if( _key.home() ) {         // Called from lock_and_invalidate
      // Home-key blocking: wait for active-GET count to fall to zero
      return r == 0;
    } else {                    // Called from start_put
      // Remote-key blocking: wait for active-PUT lock to hit -1
      assert r == 1 || r == -1; // Either waiting (1) or done (-1) but not started(0)
      return r == -1;           // done!
    }
  }
  /** Possibly blocks the current thread.  Returns true if isReleasable would
   * return true.  Used by the FJ Pool management to spawn threads to prevent
   * deadlock is otherwise all threads would block on waits. */
  @Override public synchronized boolean block() {
    while( !isReleasable() ) { try { wait(); } catch( InterruptedException e ) { } }
    return true;
  }
}
