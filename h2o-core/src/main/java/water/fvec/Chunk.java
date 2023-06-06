package water.fvec;

import water.*;
import water.parser.BufferedString;

import java.util.UUID;

/** A compression scheme, over a chunk of data - a single array of bytes.
 *  Chunks are mapped many-to-1 to a {@link Vec}.  The <em>actual</em> vector
 *  header info is in the Vec - which contains info to find all the bytes of
 *  the distributed vector.  Subclasses of this abstract class implement
 *  (possibly empty) compression schemes.
 *
 *  <p>Chunks are collections of elements, and support an array-like API.
 *  Chunks are subsets of a Vec; while the elements in a Vec are numbered
 *  starting at 0, any given Chunk has some (probably non-zero) starting row,
 *  and a length which is smaller than the whole Vec.  Chunks are limited to a
 *  single Java byte array in a single JVM heap, and only an int's worth of
 *  elements.  Chunks support both the notions of a global row-number and a
 *  chunk-local numbering.  The global row-number calls are variants of {@code
 *  at_abs} and {@code set_abs}.  If the row is outside the current Chunk's
 *  range, the data will be loaded by fetching from the correct Chunk.  This
 *  probably involves some network traffic, and if all rows are loaded then the
 *  entire dataset will be pulled locally (possibly triggering an OutOfMemory).
 *
 *  <p>The chunk-local numbering supports the common {@code for} loop iterator
 *  pattern, using {@code at*} and {@code set} calls, and is faster than the
 *  global row-numbering for tight loops (because it skips some range checks):
 *  <pre>{@code
 *  for (int row = 0; row < chunk._len; row++)
 *    ...chunk.atd(row)...
 *  }</pre>
 *
 *  <p>The array-like API allows loading and storing elements in and out of
 *  Chunks.  When loading, values are decompressed.  When storing, an attempt
 *  to compress back into the actual underlying Chunk subclass is made; if this
 *  fails the Chunk is "inflated" into a {@link NewChunk}, and the store
 *  completed there.  Later the NewChunk will be compressed (probably into a
 *  different underlying Chunk subclass) and put back in the K/V store under
 *  the same Key - effectively replacing the original Chunk; this is done when
 *  {@link #close} is called, and is taken care of by the standard {@link
 *  MRTask} calls.
 *
 *  <p>Chunk updates are not multi-thread safe; the caller must do correct
 *  synchronization.  This is already handled by the Map/Reduce {MRTask)
 *  framework.  Chunk updates are not visible cross-cluster until the {@link
 *  #close} is made; again this is handled by MRTask directly.
 *
 *  <p>In addition to normal load and store operations, Chunks support the
 *  notion a missing element via the {@link #isNA} call, and a "next non-zero"
 *  notion for rapidly iterating over sparse data.
 *
 *  <p><b>Data Types</b>
 *
 *  <p>Chunks hold Java primitive values, timestamps, UUIDs, or Strings.  All
 *  the Chunks in a Vec hold the same type.  Most of the types are compressed.
 *  Integer types (boolean, byte, short, int, long) are always lossless.  Float
 *  and Double types might lose 1 or 2 ulps in the compression.  Time data is
 *  held as milliseconds since the Unix Epoch.  UUIDs are held as 128-bit
 *  integers (a pair of Java longs).  Strings are compressed in various obvious
 *  ways.  Sparse data is held... sparsely; e.g. loading data in SVMLight
 *  format will not "blow up" the in-memory representation. Categoricals/factors
 *  are held as small integers, with a shared String lookup table on the side.
 *
 *  <p>Chunks support the notion of <em>missing</em> data.  Missing float and
 *  double data is always treated as a NaN, both if read or written.  There is
 *  no equivalent of NaN for integer data; reading a missing integer value is a
 *  coding error and will be flagged.  If you are working with integer data
 *  with missing elements, you must first check for a missing value before
 *  loading it:
 *  <pre>{@code
 *  if( !chk.isNA(row) ) ...chk.at8(row)....
 *  }</pre>
 *
 *  <p>The same holds true for the other non-real types (timestamps, UUIDs,
 *  Strings, or categoricals): they must be checked for missing before being
 *  used.
 *
 *  <p><b>Performance Concerns</b>
 *
 *  <p>The standard {@code for} loop mentioned above is the fastest way to
 *  access data; definitely faster (and less error prone) than iterating over
 *  global row numbers.  Iterating over a single Chunk is nearly always
 *  memory-bandwidth bound.  Often code will iterate over a number of Chunks
 *  aligned together (the common use-case of looking a whole rows of a
 *  dataset).  Again, typically such a code pattern is memory-bandwidth bound
 *  although the X86 will stop being able to prefetch well beyond 100 or 200
 *  Chunks.
 *
 *  <p>Note that Chunk alignment is guaranteed within all the Vecs of a Frame:
 *  Same numbered Chunks of <em>different</em> Vecs will have the same global
 *  row numbering and the same length, enabling a particularly simple and
 *  efficient way to iterate over all rows.
 *
 *  <p>This example computes the Euclidean distance between all the columns and
 *  a given point, and stores the squared distance back in the last column.
 *  Note that due "NaN poisoning" if any row element is missing, the entire
 *  distance calculated will be NaN.
 *  <pre>{@code
 *  final double[] _point;                             // The given point
 *  public void map( Chunk[] chks ) {                  // Map over a set of same-numbered Chunks
 *   for( int row=0; row < chks[0]._len; row++ ) {    // For all rows
 *     double dist=0;                                 // Squared distance
 *     for( int col=0; col < chks.length-1; col++ ) { // For all cols, except the last output col
 *       double d = chks[col].atd(row) - _point[col]; // Distance along this dimension
 *       dist += d*d;                                 // Sum-squared-distance
 *     }
 *     chks[chks.length-1].set( row, dist );          // Store back the distance in the last col
 *   }
 *  }}</pre>
 */

public abstract class Chunk extends Iced<Chunk> implements Vec.Holder {

  public Chunk() {}
  private Chunk(byte [] bytes) {_mem = bytes;initFromBytes();}





  /** Global starting row for this local Chunk; a read-only field. */
  transient long _start = -1;
  /** Global starting row for this local Chunk */
  public final long start() { return _start; }
  /** Global index of this chunk filled during chunk load */
  transient int _cidx = -1;

  /** Number of rows in this Chunk; publicly a read-only field.  Odd API
   *  design choice: public, not-final, read-only, NO-ACCESSOR.
   *
   *  <p>NO-ACCESSOR: This is a high-performance field, and must have a known
   *  zero-cost cost-model; accessors hide that cost model, and make it
   *  not-obvious that a loop will be properly optimized or not.
   *
   *  <p>not-final: set in various deserializers.
   *  <p>Proper usage: read the field, probably in a hot loop.
   *  <pre>
   *  for( int row=0; row &lt; chunk._len; row++ )
   *    ...chunk.atd(row)...
   *  </pre>
   **/
  public transient int _len;
  /** Internal set of _len.  Used by lots of subclasses.  Not a publically visible API. */
  int set_len(int len) { return _len = len; }
  /** Read-only length of chunk (number of rows). */
  public int len() { return _len; }

  /** Normally==null, changed if chunk is written to.  Not a publically readable or writable field. */
  private transient Chunk _chk2;
  /** Exposed for internal testing only.  Not a publically visible API. */
  public Chunk chk2() { return _chk2; }

  /** Owning Vec; a read-only field */
  transient Vec _vec;
  /** Owning Vec */
  public Vec vec() { return _vec; }

  /** Set the owning Vec */
  public void setVec(Vec vec) { _vec = vec; }

  /** Set the start */
  public void setStart(long start) { _start = start; }
  /** The Big Data.  Frequently set in the subclasses, but not otherwise a publically writable field. */
  byte[] _mem;
  /** Short-cut to the embedded big-data memory.  Generally not useful for
   *  public consumption, since the data remains compressed and holding on to a
   *  pointer to this array defeats the user-mode spill-to-disk. */
  public byte[] getBytes() { return _mem; }

  public void setBytes(byte[] mem) { _mem = mem; }



  final long at8_abs(long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < _len) return at8((int) x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ _len));
  }

  /** Load a {@code double} value using absolute row numbers.  Returns
   *  Double.NaN if value is missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #atd} since it range-checks within a chunk.
   *  @return double value at the given row, or NaN if the value is missing */
  final double at_abs(long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < _len) return atd((int) x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ _len));
  }

  /** Missing value status.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #isNA} since it range-checks within a chunk.
   *  @return true if the value is missing */
  final boolean isNA_abs(long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < _len) return isNA((int) x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ _len));
  }

  /** Low half of a 128-bit UUID, or throws if the value is missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #at16l} since it range-checks within a chunk.
   *  @return Low half of a 128-bit UUID, or throws if the value is missing.  */
  final long at16l_abs(long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < _len) return at16l((int) x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ _len));
  }

  /** High half of a 128-bit UUID, or throws if the value is missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #at16h} since it range-checks within a chunk.
   *  @return High half of a 128-bit UUID, or throws if the value is missing.  */
  final long at16h_abs(long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < _len) return at16h((int) x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ _len));
  }

  /** String value using absolute row numbers, or null if missing.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects).
   *
   *  <p>Slightly slower than {@link #atStr} since it range-checks within a chunk.
   *  @return String value using absolute row numbers, or null if missing. */
  final BufferedString atStr_abs(BufferedString bStr, long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < _len) return atStr(bStr, (int) x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ _len));
  }

  /** Load a {@code double} value using chunk-relative row numbers.  Returns Double.NaN
   *  if value is missing.
   *  @return double value at the given row, or NaN if the value is missing */
  public final double atd(int i) { return _chk2 == null ? atd_impl(i) : _chk2. atd_impl(i); }

  /** Load a {@code long} value using chunk-relative row numbers.  Floating
   *  point values are silently rounded to a long.  Throws if the value is
   *  missing.
   *  @return long value at the given row, or throw if the value is missing */
  public final long at8(int i) { return _chk2 == null ? at8_impl(i) : _chk2. at8_impl(i); }

  /** Missing value status using chunk-relative row numbers.
   *
   *  @return true if the value is missing */
  public final boolean isNA(int i) { return _chk2 == null ?isNA_impl(i) : _chk2.isNA_impl(i); }

  /** Low half of a 128-bit UUID, or throws if the value is missing.
   *
   *  @return Low half of a 128-bit UUID, or throws if the value is missing.  */
  public final long at16l(int i) { return _chk2 == null ? at16l_impl(i) : _chk2.at16l_impl(i); }

  /** High half of a 128-bit UUID, or throws if the value is missing.
   *
   *  @return High half of a 128-bit UUID, or throws if the value is missing.  */
  public final long at16h(int i) { return _chk2 == null ? at16h_impl(i) : _chk2.at16h_impl(i); }

  /** String value using chunk-relative row numbers, or null if missing.
   *
   *  @return String value or null if missing. */
  public final BufferedString atStr(BufferedString bStr, int i) { return _chk2 == null ? atStr_impl(bStr, i) : _chk2.atStr_impl(bStr, i); }
  
  public String stringAt(int i) {
    return atStr(new BufferedString(), i).toString();
  }


  /** Write a {@code long} using absolute row numbers.  There is no way to
   *  write a missing value with this call.  Under rare circumstances this can
   *  throw: if the long does not fit in a double (value is larger magnitude
   *  than 2^52), AND float values are stored in Vector.  In this case, there
   *  is no common compatible data representation.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
  final void set_abs(long i, long l) { long x = i-_start; if (0 <= x && x < _len) set((int) x, l); else _vec.set(i,l); }

  /** Write a {@code double} using absolute row numbers; NaN will be treated as
   *  a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
  final void set_abs(long i, double d) { long x = i-_start; if (0 <= x && x < _len) set((int) x, d); else _vec.set(i,d); }

  /** Write a {@code float} using absolute row numbers; NaN will be treated as
   *  a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
  final void set_abs( long i, float  f) { long x = i-_start; if (0 <= x && x < _len) set((int) x, f); else _vec.set(i,f); }

  /** Set the element as missing, using absolute row numbers.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
  final void setNA_abs(long i) { long x = i-_start; if (0 <= x && x < _len) setNA((int) x); else _vec.setNA(i); }

  /** Set a {@code String}, using absolute row numbers.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
  public final void set_abs(long i, String str) { long x = i-_start; if (0 <= x && x < _len) set((int) x, str); else _vec.set(i,str); }

  public final void set_abs(long i, UUID uuid) { long x = i-_start; if (0 <= x && x < _len) set((int) x, uuid); else _vec.set(i,uuid); }

  public boolean hasFloat(){return true;}
  public boolean hasNA(){return true;}

  /** Replace all rows with this new chunk */
  public void replaceAll( Chunk replacement ) {
    assert _len == replacement._len;
    _vec.preWriting();          // One-shot writing-init
    _chk2 = replacement;
    assert _chk2._chk2 == null; // Replacement has NOT been written into
  }

  public Chunk deepCopy() {
    Chunk c2 = clone();
    c2._vec=null;
    c2._start=-1;
    c2._cidx=-1;
    c2._mem = _mem.clone();
    c2.initFromBytes();
    assert len() == c2._len;
    return c2;
  }

  private void setWrite() {
    if( _chk2 != null ) return; // Already setWrite
    assert !(this instanceof NewChunk) : "Cannot direct-write into a NewChunk, only append";
    setWrite(clone());
  }

  private void setWrite(Chunk ck) {
    assert(_chk2==null);
    _vec.preWriting();          // One-shot writing-init
    _chk2 = ck;
    assert _chk2._chk2 == null; // Clone has NOT been written into
  }

  /** Write a {@code long} with check-relative indexing.  There is no way to
   *  write a missing value with this call.  Under rare circumstances this can
   *  throw: if the long does not fit in a double (value is larger magnitude
   *  than 2^52), AND float values are stored in Vector.  In this case, there
   *  is no common compatible data representation.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final long set(int idx, long l) {
    setWrite();
    if( _chk2.set_impl(idx,l) ) return l;
    (_chk2 = inflate()).set_impl(idx,l);
    return l;
  }

  public final double [] set(double [] d){
    assert d.length == _len && _chk2 == null;
    setWrite(new NewChunk(this,d));
    return d;
  }
  /** Write a {@code double} with check-relative indexing.  NaN will be treated
   *  as a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final double set(int idx, double d) {
    setWrite();
    if( _chk2.set_impl(idx,d) ) return d;
    (_chk2 = inflate()).set_impl(idx,d);
    return d;
  }

  /** Write a {@code float} with check-relative indexing.  NaN will be treated
   *  as a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final float set(int idx, float f) {
    setWrite();
    if( _chk2.set_impl(idx,f) ) return f;
    (_chk2 = inflate()).set_impl(idx,f);
    return f;
  }

  /** Set a value as missing.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final boolean setNA(int idx) {
    setWrite();
    if( _chk2.setNA_impl(idx) ) return true;
    (_chk2 = inflate()).setNA_impl(idx);
    return true;
  }

  /** Write a {@code String} with check-relative indexing.  {@code null} will
   *  be treated as a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link #close} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *  @return the set value
   */
  public final String set(int idx, String str) {
    setWrite();
    if( _chk2.set_impl(idx,str) ) return str;
    (_chk2 = inflate()).set_impl(idx,str);
    return str;
  }

  public final UUID set(int idx, UUID uuid) {
    setWrite();
    long lo = uuid.getLeastSignificantBits();
    long hi = uuid.getMostSignificantBits();

    if( _chk2.set_impl(idx, lo, hi) ) return uuid;
    _chk2 = inflate();
    _chk2.set_impl(idx,lo, hi);
    return uuid;
  }

  private Object setUnknown(int idx) {
    setNA(idx);
    return null;
  }

  /**
   * @param idx index of the value in Chunk
   * @param x new value to set
   * @return x on success, or null if something went wrong
   */
  public final Object setAny(int idx, Object x) {
    return x instanceof String         ? set(idx, (String) x) :
           x instanceof Double         ? set(idx, (Double)x) :
           x instanceof Float          ? set(idx, (Float)x) :
           x instanceof Long           ? set(idx, (Long)x) :
           x instanceof Integer        ? set(idx, ((Integer)x).longValue()) :
           x instanceof UUID           ? set(idx, (UUID) x) :
           x instanceof java.util.Date ? set(idx, ((java.util.Date) x).getTime()) :
           /* otherwise */               setUnknown(idx);
      }

  /** After writing we must call close() to register the bulk changes.  If a
   *  NewChunk was needed, it will be compressed into some other kind of Chunk.
   *  The resulting Chunk (either a modified self, or a compressed NewChunk)
   *  will be written to the DKV.  Only after that {@code DKV.put} completes
   *  will all readers of this Chunk witness the changes.
   *  @return the passed-in {@link Futures}, for flow-coding.
   */
  public Futures close( int cidx, Futures fs ) {
    if( this  instanceof NewChunk ) _chk2 = this;
    if( _chk2 == null ) return fs;          // No change?
    if( _chk2 instanceof NewChunk ) _chk2 = ((NewChunk)_chk2).new_close();

    DKV.put(_vec.chunkKey(cidx),_chk2,fs,true); // Write updated chunk back into K/V
    return fs;
  }

  /** @return Chunk index */
  public int cidx() {
    assert _cidx != -1 : "Chunk idx was not properly loaded!";
    return _cidx;
  }

  public final Chunk setVolatile(double [] ds) {
    Chunk res;
    Value v = new Value(_vec.chunkKey(_cidx), res = new C8DVolatileChunk(ds),ds.length*8,Value.ICE);
    DKV.put(v._key,v);
    return res;
  }

  public final Chunk setVolatile(float[] fs) {
    Chunk res;
    Value v = new Value(_vec.chunkKey(_cidx), res = new C4FVolatileChunk(fs), fs.length*3, Value.ICE);
    DKV.put(v._key,v);
    return res;
  }

  public final Chunk setVolatile(int[] vals) {
    Chunk res;
    Value v = new Value(_vec.chunkKey(_cidx), res = new C4VolatileChunk(vals),vals.length*4,Value.ICE);
    DKV.put(v._key,v);
    return res;
  }

  public boolean isVolatile() {return false;}

  static class WrongType extends IllegalArgumentException {
    private final Class<?> expected;
    private final Class<?> actual;

    public WrongType(Class<?> expected, Class<?> actual) {
      super("Expected: " + expected + ", actual: " + actual);
      this.expected = expected;
      this.actual = actual;
    }
  }

  static WrongType wrongType(Class<?> expected, Class<?> actual) { return new WrongType(expected, actual); }

  /** Chunk-specific readers.  Not a public API */
  abstract double   atd_impl(int idx);
  abstract long     at8_impl(int idx);
  abstract boolean isNA_impl(int idx);
  long at16l_impl(int idx) { throw wrongType(UUID.class, Object.class); }
  long at16h_impl(int idx) { throw wrongType(UUID.class, Object.class); }
  BufferedString atStr_impl(BufferedString bStr, int idx) { throw new IllegalArgumentException("Not a String"); }

  /** Chunk-specific writer.  Returns false if the value does not fit in the
   *  current compression scheme.  */
  abstract boolean set_impl  (int idx, long l );
  abstract boolean set_impl  (int idx, double d );
  abstract boolean set_impl  (int idx, float f );
  abstract boolean setNA_impl(int idx);
  boolean set_impl (int idx, String str) { return false; }
  boolean set_impl(int i, long lo, long hi) { return false; }
  //Zero sparse methods:

  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return true if this Chunk is sparse.  */
  public boolean isSparseZero() {return false;}

  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return At least as large as the count of non-zeros, but may be significantly smaller than the {@link #_len} */
  public int sparseLenZero() {return _len;}

  /**
   * Skips a section of either NAs or Zeros in a sparse chunk.
   * Note: This method can only be used when NAs and Zeros have the same meaning to the caller!
   * @param rid Start search from this index (excluded).
   * @return Index of a next non-sparse value (this can include NA and Zero
   * depending on if the chunk is zero-sparse or na-sparse).
   */
  public int nextNZ(int rid){ return rid + 1;}

  /**
   * Version of nextNZ() that allows caller to prevent skipping NAs.
   * @param rid Start search from this index (excluded).
   * @param onlyTrueZero if true, only actual Zeros can be skipped. NA-sparse chunks will be treated as dense.
   * @return Index of a next non-sparse value.
   */
  public int nextNZ(int rid, boolean onlyTrueZero) { return rid + 1; }

  /**
   *  Get indeces of non-zero values stored in this chunk
   *  @return array of chunk-relative indices of values stored in this chunk. */
  public int nonzeros(int [] res) {
    int k = 0;
    for( int i = 0; i < _len; ++i)
      if(atd(i) != 0)
        res[k++] = i;
    return k;
  }

  //NA sparse methods:
  
  /** Sparse Chunks have a significant number of NAs, and support for
   *  skipping over large runs of NAs in a row.
   *  @return true if this Chunk is sparseNA.  */
  public boolean isSparseNA() {return false;}

  /** Sparse Chunks have a significant number of NAs, and support for
   *  skipping over large runs of NAs in a row.
   *  @return At least as large as the count of non-NAs, but may be significantly smaller than the {@link #_len} */
  public int sparseLenNA() {return _len;}

  /** Report the Chunk min-value (excluding NAs), or NaN if unknown.  Actual
   *  min can be higher than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double min() { return Double.NaN; }
  /** Report the Chunk max-value (excluding NAs), or NaN if unknown.  Actual
   *  max can be lower than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double max() { return Double.NaN; }


  public final NewChunk inflate(){ return extractRows(new NewChunk(this), 0,_len);}


  /** Return the next Chunk, or null if at end.  Mostly useful for parsers or
   *  optimized stencil calculations that want to "roll off the end" of a
   *  Chunk, but in a highly optimized way. */
  public Chunk nextChunk( ) { return _vec.nextChunk(this); }

  /** @return String version of a Chunk, class name and range*/
  @Override public String toString() { return getClass().getSimpleName() + "[" + _start + ".." + (_start + _len - 1) + "]"; }

  /** In memory size in bytes of the compressed Chunk plus embedded array. */
  public long byteSize() {
    long s= _mem == null ? 0 : _mem.length;
    s += (2+5)*8 + 12; // 2 hdr words, 5 other words, @8bytes each, plus mem array hdr
    if( _chk2 != null ) s += _chk2.byteSize();
    return s;
  }

  /** Custom serializers implemented by Chunk subclasses: the _mem field
   *  contains ALL the fields already. */
  public final  AutoBuffer write_impl(AutoBuffer bb) {return bb.putA1(_mem);}

  @Override
  public byte [] asBytes(){return _mem;}

  @Override
  public final Chunk reloadFromBytes(byte [] ary){
    _mem = ary;
    initFromBytes();
    return this;
  }

  protected abstract void initFromBytes();
  public final Chunk read_impl(AutoBuffer ab){
    _mem = ab.getA1();
    initFromBytes();
    return this;
  }

//  /** Custom deserializers, implemented by Chunk subclasses: the _mem field
//   *  contains ALL the fields already.  Init _start to -1, so we know we have
//   *  not filled in other fields.  Leave _vec and _chk2 null, leave _len
//   *  unknown. */
//  abstract public Chunk read_impl( AutoBuffer ab );

  // -----------------
  // Support for fixed-width format printing
//  private String pformat () { return pformat0(); }
//  private int pformat__len { return pformat_len0(); }

  /** Fixed-width format printing support.  Filled in by the subclasses. */
  public byte precision() { return -1; } // Digits after the decimal, or -1 for "all"

//  protected String pformat0() {
//    long min = (long)_vec.min();
//    if( min < 0 ) return "% "+pformat_len0()+"d";
//    return "%"+pformat_len0()+"d";
//  }
//  protected int pformat_len0() {
//    int len=0;
//    long min = (long)_vec.min();
//    if( min < 0 ) len++;
//    long max = Math.max(Math.abs(min),Math.abs((long)_vec.max()));
//    throw H2O.unimpl();
//    //for( int i=1; i<DParseTask.powers10i.length; i++ )
//    //  if( max < DParseTask.powers10i[i] )
//    //    return i+len;
//    //return 20;
//  }
//  protected int pformat_len0( double scale, int lg ) {
//    double dx = Math.log10(scale);
//    int x = (int)dx;
//    throw H2O.unimpl();
//    //if( DParseTask.pow10i(x) != scale ) throw H2O.unimpl();
//    //int w=1/*blank/sign*/+lg/*compression limits digits*/+1/*dot*/+1/*e*/+1/*neg exp*/+2/*digits of exp*/;
//    //return w;
//  }

  /** Used by the parser to help report various internal bugs.  Not intended for public use. */
  public final void reportBrokenCategorical(int i, int j, long l, int[] cmap, int levels) {
    StringBuilder sb = new StringBuilder("Categorical renumber task, column # " + i + ": Found OOB index " + l + " (expected 0 - " + cmap.length + ", global domain has " + levels + " levels) pulled from " + getClass().getSimpleName() +  "\n");
    int k = 0;
    for(; k < Math.min(5,_len); ++k)
      sb.append("at8_abs[" + (k+_start) + "] = " + atd(k) + ", _chk2 = " + (_chk2 != null?_chk2.atd(k):"") + "\n");
    k = Math.max(k,j-2);
    sb.append("...\n");
    for(; k < Math.min(_len,j+2); ++k)
      sb.append("at8_abs[" + (k+_start) + "] = " + atd(k) + ", _chk2 = " + (_chk2 != null?_chk2.atd(k):"") + "\n");
    sb.append("...\n");
    k = Math.max(k,_len-5);
    for(; k < _len; ++k)
      sb.append("at8_abs[" + (k+_start) + "] = " + atd(k) + ", _chk2 = " + (_chk2 != null?_chk2.atd(k):"") + "\n");
    throw new RuntimeException(sb.toString());
  }

  public abstract <T extends ChunkVisitor> T processRows(T v, int from, int to);
  public abstract <T extends ChunkVisitor> T processRows(T v, int [] ids);

  // convenience methods wrapping around visitor interface
  public NewChunk extractRows(NewChunk nc, int from, int to){
    return processRows(new ChunkVisitor.NewChunkVisitor(nc),from,to)._nc;
  }
  public NewChunk extractRows(NewChunk nc, int[] rows){
    return processRows(new ChunkVisitor.NewChunkVisitor(nc),rows)._nc;
  }
  public NewChunk extractRows(NewChunk nc, int row){
    return processRows(new ChunkVisitor.NewChunkVisitor(nc),row,row+1)._nc;
  }

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  public double [] getDoubles(double[] vals, int from, int to){ return getDoubles(vals,from,to, Double.NaN);}
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    return processRows(new ChunkVisitor.DoubleAryVisitor(vals,NA),from,to).vals;
  }
  public double[] getDoubles() {
    return getDoubles(MemoryManager.malloc8d(_len), 0, _len);
  }
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    return processRows(new ChunkVisitor.IntAryVisitor(vals,NA),from,to).vals;
  }
  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  public double[] getDoubles(double [] vals, int [] ids){
    return processRows(new ChunkVisitor.DoubleAryVisitor(vals),ids).vals;
  }
  /**
   * Sparse bulk interface, stream through the compressed values and extract them into dense double array.
   * @param vals holds extracted values, length must be >= this.sparseLen()
   * @param ids holds extracted chunk-relative row ids, length must be >= this.sparseLen()
   * @return number of extracted (non-zero) elements, equal to sparseLen()
   */
  public int getSparseDoubles(double[] vals, int[] ids){return getSparseDoubles(vals,ids,Double.NaN);}
  public int getSparseDoubles(double [] vals, int [] ids, double NA) {
    return processRows(new ChunkVisitor.SparseDoubleAryVisitor(vals,ids,isSparseNA(),NA),0,_len).sparseLen();
  }
}
