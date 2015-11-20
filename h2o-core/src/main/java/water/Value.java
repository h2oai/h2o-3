package water;

import jsr166y.ForkJoinPool;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/** The core Value stored in the distributed K/V store, used to cache Plain Old
 *  Java Objects, and maintain coherency around the cluster.  It contains an
 *  underlying byte[] which may be spilled to disk and freed by the {@link
 *  MemoryManager}, which is the {@link Iced} serialized version of the POJO,
 *  and a cached copy of the POJO itself.
 *  <p>
 *  Requests to extract the POJO from the Value object first try to return the
 *  cached POJO.  If that is missing, then they will re-inflate the POJO from
 *  the {@link Iced} byte[].  If that is missing it is only because the byte[]
 *  was swapped to disk by the {@link Cleaner}.  It will be reloaded from disk
 *  and then inflated as normal.
 *  <p>
 *  The H2O {@link DKV} supports the full <em>Java Memory Model</em> coherency
 *  but only with Gets and Puts.  Normal Java updates to the cached POJO are
 *  local-node visible (due to X86 and Java coherency rules) but NOT cluster-wide
 *  visible until a Put completes after the update.
 *  <p>
 *  By the same token, updates ot the POJO are not reflected in the serialized
 *  form nor the disk-spill copy unless a Put is triggered.  As long as a local
 *  thread keeps a pointer to the POJO, they can update it at will.  If they
 *  wish to recover the POJO from the DKV at a later time with all updates
 *  intact, they <em>must</em> do a final Put after all updates.
 *  <p>
 *  Value objects maintain the needed coherency state, as well as any cached
 *  copies, plus a bunch of utility and convenience functions.
 */
public final class Value extends Iced implements ForkJoinPool.ManagedBlocker {

  /** The Key part of a Key/Value store.  Transient, because the Value is
   *  typically found via its Key, and so the Key is available before we get
   *  the Value and does not need to be passed around the wire.  Not final,
   *  because Keys are interned slowly (for faster compares) and periodically a
   *  Value's Key will be updated to an interned but equivalent Key.  
   *  <p>
   *  Should not be set by any user code.  */
  public transient Key _key;

  // ---
  // Type-id of serialized object; see TypeMap for the list.
  // Might be a primitive array type, or a Iced POJO
  private short _type;
  public int type() { return _type; }
  /** Class name of the embedded POJO, without needing an actual POJO. */
  public String className() { return TypeMap.className(_type); }

  // Max size of Values before we start asserting.
  // Sizes around this big, or larger are probably true errors.
  // In any case, they will cause issues with both GC (giant pause times on
  // many collectors) and I/O (long term blocking of TCP I/O channels to
  // service a single request, causing starvation of other requests).
  public static final int MAX = 256*1024*1024;

  /** Size of the serialized wad of bits.  Values are wads of bits; known small
   *  enough to 'chunk' politely on disk, or fit in a Java heap (larger Vecs
   *  are built via Chunks) but (much) larger than a UDP packet.  Values can
   *  point to either the disk or ram version or both.  There's no compression
   *  smarts (done by the big data Chunks) nor de-dup smarts (done by the
   *  nature of a K/V).  This is just a local placeholder for some user bits
   *  being held at this local Node. */
  public int _max;

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

  /** Invalidate byte[] cache.  Only used to eagerly free memory, for data
   *  which is expected to be read-once. */
  public final void freeMem() {
    assert isPersisted() || _pojo != null || _key.isChunkKey();
    _mem = null;
  }
  /** Invalidate POJO cache.  Only used to eagerly free memory, for data
   *  which is expected to be read-once. */
  public final void freePOJO() {
    assert isPersisted() || _mem != null;
    _pojo = null;
  }

  /** The FAST path get-byte-array - final method for speed.  Will (re)build
   *  the mem array from either the POJO or disk.  Never returns NULL.
   *  @return byte[] holding the serialized POJO  */
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

  /** The FAST path get-POJO as an {@link Iced} subclass - final method for
   *  speed.  Will (re)build the POJO from the _mem array.  Never returns NULL.
   *  @return The POJO, probably the cached instance.  */
  public final <T extends Iced> T get() {
    touch();
    Iced pojo = (Iced)_pojo;    // Read once!
    if( pojo != null ) return (T)pojo;
    pojo = TypeMap.newInstance(_type);
    pojo.read(new AutoBuffer(memOrLoad()));
    return (T)(_pojo = pojo);
  }
  /** The FAST path get-POJO as a {@link Freezable} - final method for speed.
   *  Will (re)build the POJO from the _mem array.  Never returns NULL.  This
   *  version has more type-checking.
   *  @return The POJO, probably the cached instance.  */
  public final <T extends Freezable> T get(Class<T> fc) {
    T pojo = getFreezable();
    assert fc.isAssignableFrom(pojo.getClass());
    return pojo;
  }
  /** The FAST path get-POJO as a {@link Freezable} - final method for speed.
   *  Will (re)build the POJO from the _mem array.  Never returns NULL.  
   *  @return The POJO, probably the cached instance.  */
  public final <T extends Freezable> T getFreezable() {
    touch();
    Freezable pojo = _pojo;     // Read once!
    if( pojo != null ) return (T)pojo;
    pojo = TypeMap.newFreezable(_type);
    pojo.read(new AutoBuffer(memOrLoad()));
    return (T)(_pojo = pojo);
  }

  // ---
  // Time of last access to this value.
  transient long _lastAccessedTime = System.currentTimeMillis();
  private void touch() {_lastAccessedTime = System.currentTimeMillis();}
  // Exposed and used for testing only; used to trigger premature cleaning/disk-swapping
  void touchAt(long time) {_lastAccessedTime = time;}

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
  public  final static byte HDFS= 2<<0; // HDFS: backed by Hadoop cluster
  public  final static byte S3  = 3<<0; // Amazon S3
  public  final static byte NFS = 4<<0; // NFS: Standard file system
  public  final static byte TCP = 7<<0; // TCP: For profile purposes, not a storage system
  private final static byte BACKEND_MASK = (8-1);
  private final static byte NOTdsk = 0<<3; // latest _mem is persisted or not
  private final static byte ON_dsk = 1<<3;
  private void clrdsk() { _persist &= ~ON_dsk; } // note: not atomic
  /** Used by the persistance subclass to mark this Value as saved-on-disk. */
  public final void setdsk() { _persist |=  ON_dsk; } // note: not atomic
  /** Check if the backing byte[] has been saved-to-disk */
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
    H2O.getPM().store(backend(), this);
    assert isPersisted();
  }

  /** Remove dead Values from disk */
  void removePersist() {
    // do not yank memory, as we could have a racing get hold on to this
    //  free_mem();
    if( !isPersisted() || !onICE() ) return; // Never hit disk?
    clrdsk();  // Not persisted now
    H2O.getPM().delete(backend(), this);
  }
  /** Load some or all of completely persisted Values */
  byte[] loadPersist() {
    assert isPersisted();
    try { 
      return H2O.getPM().load(backend(), this);
    } catch( IOException ioe ) {
      throw Log.throwErr(ioe);
    }
  }

  String nameOfPersist() { return nameOfPersist(backend()); }
  /** One of ICE, HDFS, S3, NFS or TCP, according to where this Value is persisted.
   *  @return Short String of the persitance name */
  public static String nameOfPersist(int x) {
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

  /** Check if the Value's POJO is a subtype of given type integer.  Does not require the POJO.
   *  @return True if the Value's POJO is a subtype. */
  public static boolean isSubclassOf(int type, Class clz) { return clz.isAssignableFrom(TypeMap.theFreezable(type).getClass()); }

  /** Check if the Value's POJO is a {@link Key} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link Key} subtype. */
  public boolean isKey()      { return _type != TypeMap.PRIM_B  && TypeMap.theFreezable(_type) instanceof Key; }
  /** Check if the Value's POJO is a {@link Frame} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link Frame} subtype. */
  public boolean isFrame()    { return _type != TypeMap.PRIM_B  && TypeMap.theFreezable(_type) instanceof Frame; }
  /** Check if the Value's POJO is a {@link water.fvec.Vec.VectorGroup} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link water.fvec.Vec.VectorGroup} subtype. */
  public boolean isVecGroup() { return _type == TypeMap.VECGROUP; }
  /** Check if the Value's POJO is a {@link water.fvec.Vec.ESPC} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link water.fvec.Vec.ESPC} subtype. */
  public boolean isESPCGroup() { return _type == TypeMap.ESPCGROUP; }
  /** Check if the Value's POJO is a {@link Lockable} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link Lockable} subtype. */
  public boolean isLockable() { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof Lockable; }
  /** Check if the Value's POJO is a {@link Vec} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link Vec} subtype. */
  public boolean isVec()      { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof Vec; }
  /** Check if the Value's POJO is a {@link hex.Model} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link hex.Model} subtype. */
  public boolean isModel()    { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof hex.Model; }
  /** Check if the Value's POJO is a {@link Job} subtype.  Does not require the POJO.
   *  @return True if the Value's POJO is a {@link Job} subtype. */
  public boolean isJob()      { return _type != TypeMap.PRIM_B && TypeMap.theFreezable(_type) instanceof Job; }

  public Class<? extends Freezable> theFreezableClass() { return TypeMap.theFreezable(this._type).getClass(); }

  // --------------------------------------------------------------------------

  /** Construct a Value from all parts; not needed for most uses.  This special
   *  constructor is used by {@link water.fvec} to build Value objects over
   *  already-existing Files, so that the File contents will be lazily
   *  swapped-in as the Values are first used.  */
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
    _rwlock = new AtomicInteger(1);
    _replicas = null;
  }
  Value(Key k, byte[] mem ) { this(k, mem.length, mem, TypeMap.PRIM_B, ICE); }
  Value(Key k, String s ) { this(k, s.getBytes()); }
  Value(Key k, Iced pojo ) { this(k,pojo,ICE); }
  Value(Key k, Iced pojo, byte be ) {
    _key = k;
    _pojo = pojo;
    _type = (short)pojo.frozenType();
    _mem = (pojo instanceof Chunk)?((Chunk)pojo).getBytes():pojo.write(new AutoBuffer()).buf();
    _max = _mem.length;
    assert _max < MAX : "Value size = " + _max + " (0x"+Integer.toHexString(_max) + ") >= (MAX=" + MAX + ").";
    // For the ICE backend, assume new values are not-yet-written.
    // For HDFS & NFS backends, assume we from global data and preserve the
    // passed-in persist bits
    byte p = (byte)(be&BACKEND_MASK);
    _persist = (p==ICE) ? p : be;
    _rwlock = new AtomicInteger(1);
    _replicas = null;
  }
  /** Standard constructor to build a Value from a POJO and a Key.  */
  public Value(Key k, Freezable pojo) { this(k,pojo,ICE); }
  Value(Key k, Freezable pojo, byte be) {
    _key = k;
    _pojo = pojo;
    _type = (short)pojo.frozenType();
    _mem = pojo.write(new AutoBuffer()).buf();
    _max = _mem.length;
    byte p = (byte)(be&BACKEND_MASK);
    _persist = (p==ICE) ? p : be;
    _rwlock = new AtomicInteger(1);
    _replicas = null;
  }

  // Custom serializers: the _mem field is racily cleared by the MemoryManager
  // and the normal serializer then might ship over a null instead of the
  // intended byte[].  Also, the value is NOT on the deserialize'd machines disk
  @Override public AutoBuffer write_impl( AutoBuffer ab ) {
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
    assert _max < MAX : "Value size=0x"+Integer.toHexString(_max)+" during read is larger than "+Integer.toHexString(MAX)+", type: "+TypeMap.className(_type);
    _pojo = null;
    // On remote nodes _rwlock is initialized to 1 (signaling a remote PUT is
    // in progress) flips to -1 when the remote PUT is done, or +2 if a notify
    // needs to happen.
    _rwlock = new AtomicInteger(-1); // Set as 'remote put is done'
    _replicas = null;
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
  // An initial remote PUT will default the value to 1.  A 2nd PUT attempt will
  // block until the 1st one completes (multiple writes to the same Key from
  // the same JVM block, so there is at most 1 outstanding write to the same
  // Key from the same JVM).  The 2nd PUT will CAS the value to 2, indicating
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
  private volatile byte[] _replicas;
  private static final AtomicReferenceFieldUpdater<Value,byte[]> REPLICAS_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(Value.class,byte[].class, "_replicas");
  // Fills in the _replicas field atomically, on first set of a replica.
  private byte[] replicas( ) {
    byte[] r = _replicas;
    if( r != null ) return r;
    byte[] nr = new byte[H2O.CLOUD.size()+1/*1-based numbering*/+10/*limit of 10 clients*/];
    if( REPLICAS_UPDATER.compareAndSet(this,null,nr) ) return nr;
    r = _replicas/*read again, since CAS failed must be set now*/;
    assert r!= null;
    return r;
  }

  // Bump the read lock, once per pending-GET or pending-Invalidate
  boolean read_lock() {
    while( true ) {     // Repeat, in case racing GETs are bumping the counter
      int old = _rwlock.get();
      if( old == -1 ) return false; // Write-locked; no new replications.  Read fails to read *this* value
      assert old >= 0;              // Not negative
      if( RW_CAS(old,old+1,"rlock+") ) return true;
    }
  }

  /** Atomically insert h2o into the replica list; reports false if the Value
   *  flagged against future replication with a -1.  Also bumps the active
   *  Get count, which remains until the Get completes (we receive an ACKACK). */
  boolean setReplica( H2ONode h2o ) {
    assert _key.home(); // Only the HOME node for a key tracks replicas
    assert h2o != H2O.SELF;     // Do not track self as a replica
    if( !read_lock() ) return false; // Write-locked; no new replications.  Read fails to read *this* value
    // Narrow non-race here.  Here is a time window where the rwlock count went
    // up, but the replica list does not account for the new replica.  However,
    // the rwlock cannot go down until an ACKACK is received, and the ACK
    // (hence ACKACK) doesn't go out until after this function returns.
    replicas()[h2o._unique_idx] = 1;
    // Both rwlock taken, and replica count is up now.
    return true;
  }

  /** Atomically lower active GET and Invalidate count */
  void lowerActiveGetCount( H2ONode h2o ) {
    assert _key.home();    // Only the HOME node for a key tracks replicas
    assert h2o != H2O.SELF;// Do not track self as a replica
    while( true ) {        // Repeat, in case racing GETs are bumping the counter
      int old = _rwlock.get(); // Read the lock-word
      assert old > 0;      // Since lowering, must be at least 1
      assert old != -1;    // Not write-locked, because we are an active reader
      assert (h2o==null) || (_replicas!=null && _replicas[h2o._unique_idx]==1); // Self-bit is set
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
  Futures lockAndInvalidate( H2ONode sender, Value newval, Futures fs ) {
    assert _key.home(); // Only the HOME node for a key tracks replicas
    assert newval._rwlock.get() >= 1; // starts read-locked
    // Write-Lock against further GETs
    while( true ) {      // Repeat, in case racing GETs are bumping the counter
      int old = _rwlock.get();
      assert old >= 0 : _key+", rwlock="+old;  // Count does not go negative
      assert old != -1; // Only the thread doing a PUT ever locks
      if( old !=0 ) { // has readers?
        // Active readers: need to block until the GETs (of this very Value!)
        // all complete, before we can invalidate this Value - lest a racing
        // Invalidate bypass a GET.
        try { ForkJoinPool.managedBlock(this); } catch( InterruptedException ignore ) { }
      } else if( RW_CAS(0,-1,"wlock") )
        break;                  // Got the write-lock!
    }
    // We have the set of Nodes with replicas now.  Ship out invalidates.
    // Bump the newval read-lock by 1 for each pending invalidate
    byte[] r = _replicas;
    if( r!=null ) { // No replicas, nothing to invalidate
      int max = r.length;
      for( int i=0; i<max; i++ )
        if( r[i]==1 && H2ONode.IDX[i] != sender )
          TaskInvalidateKey.invalidate(H2ONode.IDX[i],_key,newval,fs);
    }
    newval.lowerActiveGetCount(null);  // Remove initial read-lock, accounting for pending inv counts
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
    replicas()[h2o._unique_idx]=1;
    _rwlock.set(1);             // An initial read-lock, so a fast PUT cannot wipe this one out before invalidates have a chance of being counted
  }

  /** Block this thread until all prior remote PUTs complete - to force
   *  remote-PUT ordering on the home node. */
  void startRemotePut() {
    assert !_key.home();
    int x;
    // assert I am waiting on threads with higher priority?
    while( (x=_rwlock.get()) != -1 ) // Spin until rwlock==-1
      if( x == 2 || RW_CAS(1,2,"remote_need_notify") )
        try { ForkJoinPool.managedBlock(this); } catch( InterruptedException ignore ) { }
  }

  /** The PUT for this Value has completed.  Wakeup any blocked later PUTs. */
  void completeRemotePut() {
    assert !_key.home();
    // Attempt an eager blind attempt, assuming no blocked pending notifies
    if( RW_CAS(1, -1,"remote_complete") ) return;
    synchronized(this) {
      boolean res = RW_CAS(2, -1,"remote_do_notify");
      assert res;               // Must succeed
      notifyAll();              // Wake up pending blocked PUTs
    }
  }

  // Construct a Value which behaves like a "null" or "deleted" Value, but
  // allows for counting pending invalidates on the delete operation... and can
  // thus stall future Puts overriding the deletion until the delete completes.
  static Value makeNull( Key key ) { assert key.home(); return new Value(key,0,null,(short)0,TCP); }
  boolean isNull() { assert _type != 0 || _key.home(); return _type == 0; }
  // Get from the local STORE.  If we fetch out a special Null Value, and it is
  // unlocked (it will never be write-locked, but may be read-locked if there
  // are pending invalidates on it), upgrade it in-place to a true null.
  // Return the not-Null value, or the true null.
  public static Value STORE_get( Key key ) {
    Value val = H2O.get(key);
    if( val == null ) return null; // A true null
    if( !val.isNull() ) return val; // Not a special Null
    if( val._rwlock.get()>0 ) return val; // Not yet invalidates all completed
    // One-shot throwaway attempt at upgrading the special Null to a true null
    H2O.putIfMatch(key,null,val);
    return null;
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
      assert r == 2 || r == -1; // Either waiting (2) or done (-1) but not started(1)
      return r == -1;           // done!
    }
  }
  /** Possibly blocks the current thread.  Returns true if isReleasable would
   * return true.  Used by the FJ Pool management to spawn threads to prevent
   * deadlock is otherwise all threads would block on waits. */
  @Override public synchronized boolean block() {
    while( !isReleasable() ) { try { wait(); } catch( InterruptedException ignore ) { } }
    return true;
  }
}
