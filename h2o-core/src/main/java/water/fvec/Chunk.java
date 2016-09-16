package water.fvec;

import water.*;
import water.parser.BufferedString;

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
 *  at} and {@code set}.  If the row is outside the current Chunk's range, the
 *  data will be loaded by fetching from the correct Chunk.  This probably
 *  involves some network traffic, and if all rows are loaded then the entire
 *  dataset will be pulled local (possibly triggering an OutOfMemory).
 *
 *  <p>The chunk-local numbering supports the common {@code for} loop iterator
 *  pattern, using {@code at} and {@code set} calls that end in a '{@code 0}',
 *  and is faster than the global row-numbering for tight loops (because it
 *  avoids some range checks):
 *  <pre>
 *  for( int row=0; row &lt; chunk._len; row++ )
 *    ...chunk.atd(row)...
 *  </pre>
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
 *  framework.  Chunk updates are not visible cross-cluster until the
 *  {@link #close} is made; again this is handled by MRTask directly.
 *
 *  <p>In addition to normal load and store operations, Chunks support the
 *  notion a missing element via the {@code isNA_abs()} calls, and a "next
 *  non-zero" notion for rapidly iterating over sparse data.
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
 *  <pre>
 *  if( !chk.isNA(row) ) ...chk.at8(row)....
 *  </pre>
 *
 *  <p>The same holds true for the other non-real types (timestamps, UUIDs,
 *  Strings, or categoricals); they must be checked for missing before being used.
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
final double[] _point;                             // The given point
public void map( Chunk[] chks ) {                  // Map over a set of same-numbered Chunks
  for( int row=0; row < chks[0]._len; row++ ) {    // For all rows
    double dist=0;                                 // Squared distance
    for( int col=0; col < chks.length-1; col++ ) { // For all cols, except the last output col
      double d = chks[col].atd(row) - _point[col]; // Distance along this dimension
      dist += d*d;                                 // Sum-squared-distance
    }
    chks[chks.length-1].set( row, dist );          // Store back the distance in the last col
  }
}}</pre>
 */

public abstract class Chunk extends Iced<Chunk> {

  final boolean _sparseZero;
  final boolean _sparseNA;
  public Chunk() {_sparseNA = false; _sparseZero = false;}
  protected Chunk(byte [] bytes, boolean sparseZero, boolean sparseNA) {
    _sparseZero = sparseZero;
    _sparseNA = sparseNA;
    _mem = bytes;initFromBytes();
  }
  public abstract int len();

  /**
   * Sparse bulk interface, stream through the compressed values and extract them into dense double array.
   * @param vals holds extracted values, length must be >= this.sparseLen()
   * @param vals holds extracted chunk-relative row ids, length must be >= this.sparseLen()
   * @return number of extracted (non-zero) elements, equal to sparseLen()
   */
  public int asSparseDoubles(double[] vals, int[] ids){return asSparseDoubles(vals,ids,Double.NaN);}
  public int asSparseDoubles(double [] vals, int [] ids, double NA) {
    int len = len();
    if(vals.length < sparseLen())
      throw new IllegalArgumentException();
    getDoubles(vals,0,len);
    for(int i = 0; i < len; ++i) ids[i] = i;
    return len;
  }

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  public double [] getDoubles(double[] vals, int from, int to){ return getDoubles(vals,from,to, Double.NaN);}
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      vals[i - from] = atd_impl(i);
      if(Double.isNaN(vals[i-from]))
        vals[i - from] = NA;
    }
    return vals;
  }

  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i)
      vals[i] = isNA_impl(i)?NA:at4_impl(i);
    return vals;
  }


  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  public double[] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids) vals[j++] = atd_impl(i);
    return vals;
  }


  /** Number of rows in this Chunk; publically a read-only field.  Odd API
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
  /** Internal set of _len.  Used by lots of subclasses.  Not a publically visible API. */
  /** Read-only length of chunk (number of rows). */

  /** Normally==null, changed if chunk is written to.  Not a publically readable or writable field. */

  public Chunk compress(){return this;}
  /** Exposed for internal testing only.  Not a publically visible API. */

  /** The Big Data.  Frequently set in the subclasses, but not otherwise a publically writable field. */
  byte[] _mem;
  /** Short-cut to the embedded big-data memory.  Generally not useful for
   *  public consumption, since the data remains compressed and holding on to a
   *  pointer to this array defeats the user-mode spill-to-disk. */
  public byte[] getBytes() { return _mem; }

  public void setBytes(byte[] mem) { _mem = mem; }

  /** Used by a ParseExceptionTest to break the Chunk invariants and trigger an
   *  NPE.  Not intended for public use. */
  public final void crushBytes() { _mem=null; }

  /**
   *  Get indeces of non-zero values stored in this chunk
   *  @return array of chunk-relative indices of values stored in this chunk. */
  public int nonzeros(int [] res) {
    int k = 0;
    int len = len();
    for( int i = 0; i < len; ++i)
      if(atd_impl(i) != 0)
        res[k++] = i;
    return k;
  }

  public int at4_impl(int i) {
    long l = at8_impl(i);
    int res = (int)l;
    if(res != l) throw new IllegalArgumentException(l + " does not fit in int");
    return res;
  }


  public final NewChunk add2NewChunk(NewChunk nc, int from, int to){
    return add2NewChunk_impl(nc, from,to);
  }

  public final NewChunk add2NewChunk(NewChunk nc, int [] lines){
    return add2NewChunk_impl(nc, lines);
  }
  public abstract NewChunk add2NewChunk_impl(NewChunk nc, int from, int to);
  public abstract NewChunk add2NewChunk_impl(NewChunk nc, int [] lines);


  public int sparseLenZero() {return _sparseZero?sparseLen():len();}
  public int sparseLenNA() {return _sparseNA?sparseLen():len();}


  /** Write a {@code long} using absolute row numbers.  There is no way to
   *  write a missing value with this call.  Under rare circumstances this can
   *  throw: if the long does not fit in a double (value is larger magnitude
   *  than 2^52), AND float values are stored in Vector.  In this case, there
   *  is no common compatible data representation.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link Vec#closeChunk} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
//  final void set_abs(long i, long l) { long x = i-_start; if (0 <= x && x < _len) set((int) x, l); else _vec.set(i,l); }

  /** Write a {@code double} using absolute row numbers; NaN will be treated as
   *  a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link Vec#closeChunk} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
//  final void set_abs(long i, double d) { long x = i-_start; if (0 <= x && x < _len) set((int) x, d); else _vec.set(i,d); }

  /** Write a {@code float} using absolute row numbers; NaN will be treated as
   *  a missing value.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link Vec#closeChunk} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
//  final void set_abs( long i, float  f) { long x = i-_start; if (0 <= x && x < _len) set((int) x, f); else _vec.set(i,f); }

  /** Set the element as missing, using absolute row numbers.
   *
   *  <p>As with all the {@code set} calls, if the value written does not fit
   *  in the current compression scheme, the Chunk will be inflated into a
   *  NewChunk and the value written there.  Later, the NewChunk will be
   *  compressed (after a {@link Vec#closeChunk} call) and written back to the DKV.
   *  i.e., there is some interesting cost if Chunk compression-types need to
   *  change.
   *
   *  <p>This version uses absolute element numbers, but must convert them to
   *  chunk-relative indices - requiring a load from an aliasing local var,
   *  leading to lower quality JIT'd code (similar issue to using iterator
   *  objects). */
//  final void setNA_abs(long i) { long x = i-_start; if (0 <= x && x < _len) setNA((int) x); else _vec.setNA(i); }

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
//  public final void set_abs(long i, String str) { long x = i-_start; if (0 <= x && x < _len) set((int) x, str); else _vec.set(i,str); }

  public boolean hasFloat(){return true;}
  public boolean hasNA(){return true;}


  public Chunk deepCopy() {
    Chunk c2 = (Chunk)clone();
    c2._mem = _mem.clone();
    return c2;
  }



  /** Chunk-specific readers.  Not a public API */
  public abstract double   atd_impl(int idx);
  public abstract long     at8_impl(int idx);
  public abstract boolean isNA_impl(int idx);
  long at16l_impl(int idx) { throw new IllegalArgumentException("Not a UUID"); }
  long at16h_impl(int idx) { throw new IllegalArgumentException("Not a UUID"); }
  public BufferedString atStr_impl(BufferedString bStr, int idx) { throw new IllegalArgumentException("Not a String"); }

  /** Chunk-specific writer.  Returns false if the value does not fit in the
   *  current compression scheme.  */
  abstract boolean set_impl  (int idx, long l );
  abstract boolean set_impl  (int idx, double d );
  abstract boolean set_impl  (int idx, float f );
  abstract boolean setNA_impl(int idx);
  boolean set_impl (int idx, String str) { throw new IllegalArgumentException("Not a String"); }

  //Zero sparse methods:
  
  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return true if this Chunk is sparse.  */
  public boolean isSparse() {return _sparseZero || _sparseNA;}
  public boolean isSparseZero() {return _sparseZero;}
  public final boolean isSparseNA(){return _sparseNA;}

  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return At least as large as the count of non-zeros, but may be significantly smaller than the {@link #len()} */
  public int sparseLen() {return len();}

  public int nextNZ(int rid){ return rid + 1;}

//  /**
//   *  Get indeces of non-zero values stored in this chunk
//   *  @return array of chunk-relative indices of values stored in this chunk. */
//  public int nonzeros(int [] res) {
//    int k = 0;
//    for( int i = 0; i < _len; ++i)
//      if(atd(i) != 0)
//        res[k++] = i;
//    return k;
//  }
//
  //NA sparse methods:
  

  
  /** Report the Chunk min-value (excluding NAs), or NaN if unknown.  Actual
   *  min can be higher than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double min() { return Double.NaN; }
  /** Report the Chunk max-value (excluding NAs), or NaN if unknown.  Actual
   *  max can be lower than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double max() { return Double.NaN; }


  public final NewChunk inflate(){return add2NewChunk(new NewChunk(len()),0,len());}


  /** @return String version of a Chunk, currently just the class name */
  @Override public String toString() { return getClass().getSimpleName(); }



  /** Custom serializers implemented by Chunk subclasses: the _mem field
   *  contains ALL the fields already. */
  public final  AutoBuffer write_impl(AutoBuffer bb) {return bb.putA1(_mem);}

  @Override
  public final byte [] asBytes(){return _mem;}

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

}
