package water;

import water.util.ReflectionUtils;
import water.util.UnsafeUtils;
import water.fvec.*;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Keys!  H2O supports a distributed Key/Value store, with exact Java Memory
 * Model consistency.  Keys are a means to find a {@link Value} somewhere in
 * the Cloud, to cache it locally, to allow globally consistent updates to a
 * {@link Value}.  Keys have a *home*, a specific Node in the Cloud, which is
 * computable from the Key itself.  The Key's home node breaks ties on racing
 * updates, and tracks caching copies (via a hardware-like MESI protocol), but
 * otherwise is not involved in the DKV.  All operations on the DKV, including
 * Gets and Puts, are found in {@link DKV}.
 * <p>
 * Keys are defined as a simple byte-array, plus a hashCode and a small cache
 * of Cloud-specific information.  The first byte of the byte-array determines
 * if this is a user-visible Key or an internal system Key; an initial byte of
 * &lt;32 is a system Key.  User keys are generally externally visible, system
 * keys are generally limited to things kept internal to the H2O Cloud.  Keys
 * might be a high-count item, hence we care about the size.
 * <p>
 * System keys for {@link Job}, {@link Vec}, {@link Chunk} and {@link
 * water.fvec.Vec.VectorGroup} have special initial bytes; Keys for these classes can be
 * determined without loading the underlying Value.  Layout for {@link Vec} and
 * {@link Chunk} is further restricted, so there is an efficient mapping
 * between a numbered Chunk and it's associated Vec.
 * <p>
 * System keys (other than the restricted Vec and Chunk keys) can have their
 * home node forced, by setting the desired home node in the first few Key
 * bytes.  Otherwise home nodes are selected by pseudo-random hash.  Selecting
 * a home node is sometimes useful for Keys with very high update rates coming
 * from a specific Node.
 * <p>
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 * @version 1.0
 */
final public class Key<T extends Keyed> extends Iced<Key<T>> implements Comparable {
  // The Key!!!
  // Limited to 512 random bytes - to fit better in UDP packets.
  static final int KEY_LENGTH = 512;
  public final byte[] _kb;      // Key bytes, wire-line protocol
  transient final int _hash;    // Hash on key alone (and not value)

  // The user keys must be ASCII, so the values 0..31 are reserved for system
  // keys. When you create a system key, please do add its number to this list
  static final byte BUILT_IN_KEY = 2;
  public static final byte JOB = 3;
  public static final byte VEC = 4; // Vec
  public static final byte CHK = 5; // Chunk
  public static final byte GRP = 6; // Vec.VectorGroup

  public static final byte HIDDEN_USER_KEY = 31;
  public static final byte USER_KEY = 32;

  // For Fluid Vectors, we have a special Key layout.
  // 0 - key type byte, one of VEC, CHK or GRP
  // 1 - homing byte, always -1/0xFF as these keys use the hash to figure their home out
  // 4 - Vector Group
  // 4 - Chunk # for CHK, or 0xFFFFFFFF for VEC
  static final int VEC_PREFIX_LEN = 1+1+4+4;

  /** True is this is a {@link Vec} Key.
   *  @return True is this is a {@link Vec} Key */
  public final boolean isVec() { return _kb != null && _kb.length > 0 && _kb[0] == VEC; }

  /** True is this is a {@link Chunk} Key.
   *  @return True is this is a {@link Chunk} Key */
  public final boolean isChunkKey() { return _kb != null && _kb.length > 0 && _kb[0] == CHK; }

  /** Returns the {@link Vec} Key from a {@link Chunk} Key.
   *  @return Returns the {@link Vec} Key from a {@link Chunk} Key. */
  public final Key getVecKey() { assert isChunkKey(); return water.fvec.Vec.getVecKey(this); }

  /** Convenience function to fetch key contents from the DKV.
   * @return null if the Key is not mapped, or an instance of {@link Keyed} */
  public final T get() {
    Value val = DKV.get(this);
    return val == null ? null : (T)val.get();
  }

  // *Desired* distribution function on keys & replication factor. Replica #0
  // is the master, replica #1, 2, 3, etc represent additional desired
  // replication nodes. Note that this function is just the distribution
  // function - it does not DO any replication, nor does it dictate any policy
  // on how fast replication occurs. Returns -1 if the desired replica
  // is nonsense, e.g. asking for replica #3 in a 2-Node system.
  int D( int repl ) {
    int hsz = H2O.CLOUD.size();

    if (0 == hsz) return -1;    // Clients starting up find no cloud, be unable to home keys

    // See if this is a specifically homed Key
    if( !user_allowed() && repl < _kb[1] ) { // Asking for a replica# from the homed list?
      assert repl == 0 : "No replication is support now";
      assert _kb[0] != Key.CHK;
      H2ONode h2o = H2ONode.intern(_kb,2+repl*(H2ONode.H2Okey.SIZE /* serialized bytesize of H2OKey - depends on IP protocol */));
      // Reverse the home to the index
      int idx = h2o.index();
      if( idx >= 0 ) return idx;
      // Else homed to a node which is no longer in the cloud!
      // Fall back to the normal home mode
    }

    // Distribution of Fluid Vectors is a special case.
    // Fluid Vectors are grouped into vector groups, each of which must have
    // the same distribution of chunks so that MRTask run over group of
    // vectors will keep data-locality.  The fluid vecs from the same group
    // share the same key pattern + each has 4 bytes identifying particular
    // vector in the group.  Since we need the same chunks end up on the same
    // node in the group, we need to skip the 4 bytes containing vec# from the
    // hash.  Apart from that, we keep the previous mode of operation, so that
    // ByteVec would have first 64MB distributed around cloud randomly and then
    // go round-robin in 64MB chunks.
    if( _kb[0] == CHK ) {
      // Homed Chunk?
      if( _kb[1] != -1 ) throw H2O.fail();
      // For round-robin on Chunks in the following pattern:
      // 1 Chunk-per-node, until all nodes have 1 chunk (max parallelism).
      // Then 2 chunks-per-node, once around, then 4, then 8, then 16.
      // Getting several chunks-in-a-row on a single Node means that stencil
      // calculations that step off the end of one chunk into the next won't
      // force a chunk local - replicating the data.  If all chunks round robin
      // exactly, then any stencil calc will double the cached volume of data
      // (every node will have it's own chunk, plus a cached next-chunk).
      // Above 16-chunks-in-a-row we hit diminishing returns.
      int cidx = UnsafeUtils.get4(_kb, 1 + 1 + 4); // Chunk index
      int x = cidx/hsz; // Multiples of cluster size
      // 0 -> 1st trip around the cluster;            nidx= (cidx- 0*hsz)>>0
      // 1,2 -> 2nd & 3rd trip; allocate in pairs:    nidx= (cidx- 1*hsz)>>1
      // 3,4,5,6 -> next 4 rounds; allocate in quads: nidx= (cidx- 3*hsz)>>2
      // 7-14 -> next 8 rounds in octets:             nidx= (cidx- 7*hsz)>>3
      // 15+ -> remaining rounds in groups of 16:     nidx= (cidx-15*hsz)>>4
      int z = x==0 ? 0 : (x<=2 ? 1 : (x<=6 ? 2 : (x<=14 ? 3 : 4)));
      int nidx = (cidx-((1<<z)-1)*hsz)>>z;
      return ((nidx+repl)&0x7FFFFFFF) % hsz;
    }

    // Easy Cheesy Stupid:
    return ((_hash+repl)&0x7FFFFFFF) % hsz;
  }


  /** List of illegal characters which are not allowed in user keys. */
  static final CharSequence ILLEGAL_USER_KEY_CHARS = " !@#$%^&*()+={}[]|\\;:\"'<>,/?";

  // 64 bits of Cloud-specific cached stuff. It is changed atomically by any
  // thread that visits it and has the wrong Cloud. It has to be read *in the
  // context of a specific Cloud*, since a re-read may be for another Cloud.
  private transient volatile long _cache;
  private static final AtomicLongFieldUpdater<Key> _cacheUpdater =
    AtomicLongFieldUpdater.newUpdater(Key.class, "_cache");


  // Accessors and updaters for the Cloud-specific cached stuff.
  // The Cloud index, a byte uniquely identifying the last 256 Clouds. It
  // changes atomically with the _cache word, so we can tell which Cloud this
  // data is a cache of.
  private static int cloud( long cache ) { return (int)(cache>>> 0)&0x00FF; }
  // Shortcut node index for Home replica#0. This replica is responsible for
  // breaking ties on writes. 'char' because I want an unsigned 16bit thing,
  // limit of 65534 Cloud members. -1 is reserved for a bare-key
  private static int home ( long cache ) { return (int)(cache>>> 8)&0xFFFF; }
  // Our replica #, or -1 if we're not one of the first 127 replicas. This
  // value is found using the Cloud distribution function and changes for a
  // changed Cloud.
  private static int replica(long cache) { return (byte)(cache>>>24)&0x00FF; }
  // Desired replication factor. Can be zero for temp keys. Not allowed to
  // later, because it messes with e.g. meta-data on disk.
  private static int desired(long cache) { return (int)(cache>>>32)&0x00FF; }

  private static long build_cache( int cidx, int home, int replica, int desired ) {
    return // Build the new cache word
        ((long)(cidx &0xFF)<< 0) |
        ((long)(home &0xFFFF)<< 8) |
        ((long)(replica&0xFF)<<24) |
        ((long)(desired&0xFF)<<32) |
        ((long)(0 )<<40);
  }

  int home ( H2O cloud ) { return home (cloud_info(cloud)); }
  int replica( H2O cloud ) { return replica(cloud_info(cloud)); }
  int desired( ) { return desired(_cache); }

  /** True if the {@link #home_node} is the current node.
   *  @return True if the {@link #home_node} is the current node */
  public boolean home() { return home_node()==H2O.SELF; }
  /** The home node for this Key.
   *  @return The home node for this Key. */
  public H2ONode home_node( ) {
    H2O cloud = H2O.CLOUD;
    return cloud._memary[home(cloud)];
  }

  // Update the cache, but only to strictly newer Clouds
  private boolean set_cache( long cache ) {
    while( true ) { // Spin till get it
      long old = _cache; // Read once at the start
      if( !H2O.larger(cloud(cache),cloud(old)) ) // Rolling backwards?
        // Attempt to set for an older Cloud. Blow out with a failure; caller
        // should retry on a new Cloud.
        return false;
      assert cloud(cache) != cloud(old) || cache == old;
      if( old == cache ) return true; // Fast-path cutout
      if( _cacheUpdater.compareAndSet(this,old,cache) ) return true;
      // Can fail if the cache is really old, and just got updated to a version
      // which is still not the latest, and we are trying to update it again.
    }
  }
  // Return the info word for this Cloud. Use the cache if possible
  long cloud_info( H2O cloud ) {
    long x = _cache;
    // See if cached for this Cloud. This should be the 99% fast case.
    if( cloud(x) == cloud._idx ) return x;

    // Cache missed! Probably it just needs (atomic) updating.
    // But we might be holding the stale cloud...
    // Figure out home Node in this Cloud
    char home = (char)D(0);
    // Figure out what replica # I am, if any
    int desired = desired(x);
    int replica = -1;
    for( int i=0; i<desired; i++ ) {
      int idx = D(i);
      if( idx >= 0 && cloud._memary[idx] == H2O.SELF ) {
        replica = i;
        break;
      }
    }
    long cache = build_cache(cloud._idx,home,replica,desired);
    set_cache(cache); // Attempt to upgrade cache, but ignore failure
    return cache; // Return the magic word for this Cloud
  }

  // Default desired replication factor. Unless specified otherwise, all new
  // k-v pairs start with this replication factor.
  static final byte DEFAULT_DESIRED_REPLICA_FACTOR = 1;

  // Construct a new Key.
  private Key(byte[] kb) {
    if( kb.length > KEY_LENGTH ) throw new IllegalArgumentException("Key length would be "+kb.length);
    _kb = kb;
    // Quicky hash: http://en.wikipedia.org/wiki/Jenkins_hash_function
    int hash = 0;
    for( byte b : kb ) {
      hash += b;
      hash += (hash << 10);
      hash ^= (hash >> 6);
    }
    hash += (hash << 3);
    hash ^= (hash >> 11);
    hash += (hash << 15);
    _hash = hash;
  }

  // Make new Keys.  Optimistically attempt interning, but no guarantee.
  static <P extends Keyed> Key<P> make(byte[] kb, byte rf) {
    if( rf == -1 ) throw new IllegalArgumentException();
    Key key = new Key(kb);
    Key key2 = H2O.getk(key); // Get the interned version, if any
    if( key2 != null ) // There is one! Return it instead
      return key2;

    // Set the cache with desired replication factor, and a fake cloud index
    H2O cloud = H2O.CLOUD; // Read once
    key._cache = build_cache(cloud._idx-1,0,0,rf);
    key.cloud_info(cloud); // Now compute & cache the real data
    return key;
  }

  /** A random string, useful as a Key name or partial Key suffix.
   *  @return A random short string */
  public static String rand() {
    UUID uid = UUID.randomUUID();
    long l1 = uid.getLeastSignificantBits();
    long l2 = uid. getMostSignificantBits();
    return "_"+Long.toHexString(l1)+Long.toHexString(l2);
  }

  /** Factory making a Key from a byte[]
   *  @return Desired Key */
  public static <P extends Keyed> Key<P> make(byte[] kb) { return make(kb, DEFAULT_DESIRED_REPLICA_FACTOR); }

  /** Factory making a Key from a String
   *  @return Desired Key */
  public static <P extends Keyed> Key<P> make(String s) {
    return make(decodeKeyName(s != null? s : rand()));
  }

  public static <P extends Keyed> Key<P> makeSystem(String s) {
    return make(s,DEFAULT_DESIRED_REPLICA_FACTOR,BUILT_IN_KEY, false);
  }
  public static <P extends Keyed> Key<P> makeUserHidden(String s) {
    return make(s,DEFAULT_DESIRED_REPLICA_FACTOR,HIDDEN_USER_KEY, false);
  }

  /**
   * Make a random key, homed to a given node.
   * @param node a node at which the new key is homed.
   * @return the new key
   */
  public static <P extends Keyed> Key<P> make(H2ONode node) {
    return make(decodeKeyName(rand()),DEFAULT_DESIRED_REPLICA_FACTOR,BUILT_IN_KEY,false,node);
  }
  static <P extends Keyed> Key<P> make(String s, byte rf) { return make(decodeKeyName(s), rf);}
  /** Factory making a random Key
   *  @return Desired Key */
  public static <P extends Keyed> Key<P> make() { return make(rand()); }

  /** Factory making a homed system Key.  Requires the initial system byte but
   *  then allows a String for the remaining bytes.  Requires a list of exactly
   *  one H2ONode to home at.  The hint specifies if it is an error to name an
   *  H2ONode that is NOT in the Cloud, or if some other H2ONode can be
   *  substituted.  The rf parameter and passing more than 1 H2ONode are both
   *  depreciated.
   *  @return the desired Key   */
  public static <P extends Keyed> Key<P> make(String s, byte rf, byte systemType, boolean hint, H2ONode... replicas) {
    return make(decodeKeyName(s),rf,systemType,hint,replicas);
  }
  /** Factory making a homed system Key.  Requires the initial system byte and
   *  uses {@link #rand} for the remaining bytes.  Requires a list of exactly
   *  one H2ONode to home at.  The hint specifies if it is an error to name an
   *  H2ONode that is NOT in the Cloud, or if some other H2ONode can be
   *  substituted.  The rf parameter and passing more than 1 H2ONode are both
   *  depreciated.
   *  @return the desired Key   */
  public static <P extends Keyed> Key<P> make(byte rf, byte systemType, boolean hint, H2ONode... replicas) {
    return make(rand(),rf,systemType,hint,replicas);
  }


  // Make a Key which is homed to specific nodes.
  public static <P extends Keyed> Key<P> make(byte[] kb, byte rf, byte systemType, boolean required, H2ONode... replicas) {
    // no more than 3 replicas allowed to be stored in the key
    assert 0 <=replicas.length && replicas.length<=3;
    assert systemType<32; // only system keys allowed
    boolean inCloud=true;
    for( H2ONode h2o : replicas ) if( !H2O.CLOUD.contains(h2o) ) inCloud = false;
    if( required ) assert inCloud; // If required placement, error to find a client as the home
    else if( !inCloud ) replicas = new H2ONode[0]; // If placement is a hint & cannot be placed, then ignore

    // Key byte layout is:
    // 0 - systemType, from 0-31
    // 1 - replica-count, plus up to 3 bits for ip4 vs ip6
    // 2-n - zero, one, two or 3 IP4 (4+2 bytes) or IP6 (16+2 bytes) addresses
    // 2-5- 4 bytes of chunk#, or -1 for masters
    // n+ - repeat of the original kb
    AutoBuffer ab = new AutoBuffer();
    ab.put1(systemType).put1(replicas.length);
    for( H2ONode h2o : replicas )
      h2o.write(ab);
    ab.put4(-1);
    ab.putA1(kb,kb.length);
    return make(Arrays.copyOf(ab.buf(),ab.position()),rf);
  }

  /** Remove a Key from the DKV, including any embedded Keys.
   */
  public void remove() { remove(new Futures()).blockForPending(); }
  public Futures remove(Futures fs) {
    Value val = DKV.get(this);
    if( val!=null ) ((Keyed)val.get()).remove(fs);
    return fs;
  }

  /** True if a {@link #USER_KEY} and not a system key.
   * @return True if a {@link #USER_KEY} and not a system key */
  public boolean user_allowed() { return type()==USER_KEY; }

  /** System type/byte of a Key, or the constant {@link #USER_KEY}
   *  @return Key type */
  // Returns the type of the key.
  public int type() { return ((_kb[0]&0xff)>=32) ? USER_KEY : (_kb[0]&0xff); }

  /** Return the classname for the Value that this Key points to, if any (e.g., "water.fvec.Frame"). */
  public String valueClass() {
    // Because Key<Keyed> doesn't have concrete parameterized subclasses (e.g.
    // class FrameKey extends Key<Frame>) we can't get the type parameter at
    // runtime.  See:
    // http://www.javacodegeeks.com/2013/12/advanced-java-generics-retreiving-generic-type-arguments.html
    //
    // Therefore, we have to fetch the type of the item the Key is pointing to at runtime.
    Value v = DKV.get(this);
    if (null == v)
      return null;
    else
      return v.className();
  }

  /** Return the base classname (not including the package) for the Value that this Key points to, if any (e.g., "Frame"). */
  public String valueClassSimple() {
    String vc = this.valueClass();

    if (null == vc) return null;

    String[] elements = vc.split("\\.");
    return elements[elements.length - 1];
  }

  static final char MAGIC_CHAR = '$'; // Used to hexalate displayed keys
  private static final char[] HEX = "0123456789abcdef".toCharArray();

  /** Converts the key to HTML displayable string.
   *
   * For user keys returns the key itself, for system keys returns their
   * hexadecimal values.
   *
   * @return key as a printable string
   */
  @Override public String toString() {
    int len = _kb.length;
    while( --len >= 0 ) {
      char a = (char) _kb[len];
      if (' ' <= a && a <= '#') continue;
      // then we have $ which is not allowed
      if ('%' <= a && a <= '~') continue;
      // already in the one above
      //if( 'a' <= a && a <= 'z' ) continue;
      //if( 'A' <= a && a <= 'Z' ) continue;
      //if( '0' <= a && a <= '9' ) continue;
      break;
    }
    if (len>=0) {
      StringBuilder sb = new StringBuilder();
      sb.append(MAGIC_CHAR);
      for( int i = 0; i <= len; ++i ) {
        byte a = _kb[i];
        sb.append(HEX[(a >> 4) & 0x0F]);
        sb.append(HEX[(a >> 0) & 0x0F]);
      }
      sb.append(MAGIC_CHAR);
      for( int i = len + 1; i < _kb.length; ++i ) sb.append((char)_kb[i]);
      return sb.toString();
    } else {
      return new String(_kb);
    }
  }

  private static byte[] decodeKeyName(String what) {
    if( what==null ) return null;
    if( what.length()==0 ) return null;
    if (what.charAt(0) == MAGIC_CHAR) {
      int len = what.indexOf(MAGIC_CHAR,1);
      if( len < 0 ) throw new IllegalArgumentException("No matching magic '"+MAGIC_CHAR+"', key name is not legal");
      String tail = what.substring(len+1);
      byte[] res = new byte[(len-1)/2 + tail.length()];
      int r = 0;
      for( int i = 1; i < len; i+=2 ) {
        char h = what.charAt(i);
        char l = what.charAt(i+1);
        h -= Character.isDigit(h) ? '0' : ('a' - 10);
        l -= Character.isDigit(l) ? '0' : ('a' - 10);
        res[r++] = (byte)(h << 4 | l);
      }
      System.arraycopy(tail.getBytes(), 0, res, r, tail.length());
      return res;
    } else {
      byte[] res = new byte[what.length()];
      for( int i=0; i<res.length; i++ ) res[i] = (byte)what.charAt(i);
      return res;
    }
  }

  @Override public int hashCode() { return _hash; }
  @Override public boolean equals( Object o ) {
    if( this == o ) return true;
    if( o == null ) return false;
    Key k = (Key)o;
    if( _hash != k._hash ) return false;
    return Arrays.equals(k._kb,_kb);
  }

  /** Lexically ordered Key comparison, so Keys can be sorted.  Modestly expensive. */
  @Override public int compareTo(Object o) {
    assert (o instanceof Key);
    return this.toString().compareTo(o.toString());
  }

  public static final AutoBuffer write_impl(Key k, AutoBuffer ab) {return ab.putA1(k._kb);}
  public static final Key read_impl(Key k, AutoBuffer ab) {return make(ab.getA1());}

  public static final AutoBuffer writeJSON_impl( Key k, AutoBuffer ab ) {
    ab.putJSONStr("name",k.toString());
    ab.put1(',');
    ab.putJSONStr("type", ReflectionUtils.findActualClassParameter(k.getClass(), 0).getSimpleName());
    return ab;
  }
}
