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
 *
 *  <p>Chunk updates are not multi-thread safe; the caller must do correct
 *  synchronization.  This is already handled by the Map/Reduce {MRTask)
 *  framework.
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

public abstract class Chunk extends DBlock {
  /** The Big Data.  Frequently set in the subclasses, but not otherwise a publically writable field. */
  byte[] _mem;
  public int len(){return _len;}
  public transient int _len;
  public Chunk() {}
  private Chunk(byte [] bytes) {_mem = bytes;initFromBytes();}

  /**
   * Sparse bulk interface, stream through the compressed values and extract them into dense double array.
   * @param vals holds extracted values, length must be >= this.sparseLen()
   * @param vals holds extracted chunk-relative row ids, length must be >= this.sparseLen()
   * @return number of extracted (non-zero) elements, equal to sparseLen()
   */
  public int asSparseDoubles(double[] vals, int[] ids){return asSparseDoubles(vals,ids,Double.NaN);}
  public int asSparseDoubles(double [] vals, int [] ids, double NA) {
    if(vals.length < sparseLenZero())
      throw new IllegalArgumentException();
    getDoubles(vals,0,_len);
    for(int i = 0; i < _len; ++i) ids[i] = i;
    return _len;
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
      vals[i - from] = atd(i);
      if(Double.isNaN(vals[i-from]))
        vals[i - from] = NA;
    }
    return vals;
  }

  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i) {
      double d = atd(i);
      if(Double.isNaN(d))
        vals[i] = NA;
      else {
        vals[i] = (int)d;
        if(vals[i] != d) throw new IllegalArgumentException("Calling getIntegers on non-integer column");
      }
    }
    return vals;
  }


  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  public double[] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids) vals[j++] = atd(i);
    return vals;
  }
  /** Short-cut to the embedded big-data memory.  Generally not useful for
   *  public consumption, since the data remains compressed and holding on to a
   *  pointer to this array defeats the user-mode spill-to-disk. */
  public byte[] getBytes() { return _mem; }

  public void setBytes(byte[] mem) { _mem = mem; }


  Chunk compress(){return this;}



  public boolean hasFloat(){return true;}
  public boolean hasNA(){return true;}

  public Chunk deepCopy() {
    Chunk c2 = (Chunk)clone();
    c2._mem = _mem.clone();
    return c2;
  }



  /** Chunk-specific readers.  Not a public API */
  public abstract double atd(int idx);
  public int atd(int off, double [] vals){return atd(off,vals,vals.length);}
  public int atd(int off, double [] vals, int len){
    for(int i = 0; i < len; ++i){
      if(off+i == _len) return i;
      vals[i] = atd(off+i);
    }
    return len;
  }
  public void atd(int [] ids, double [] vals){
    int j = 0;
    for(int i:ids) vals[j++] = atd(i);
  }
  public abstract long at8(int idx);
  public int at8(int off, long [] vals){ return at8(off,vals,vals.length);}
  public int at8(int off, long [] vals, int len){
    for(int i = 0; i < len; ++i){
      if(off+i == _len) return i;
      vals[i] = at8(off+i);
    }
    return len;
  }
  public void at8(int [] ids, long [] vals){
    int j = 0;
    for(int i:ids) vals[j++] = at8(i);
  }
  public int at4(int i) {
    long l = at8(i);
    int x = (int)l;
    if(x != l) throw new IllegalArgumentException("Value does not fit into int.");
    return x;
  }
  public int at4(int off, int [] vals){ return at4(off,vals,vals.length);}
  public int at4(int off, int [] vals, int len){
    for(int i = 0; i < len; ++i){
      if(off+i == _len) return i;
      vals[i] = at4(off+i);
    }
    return len;
  }
  public void at4(int [] ids, int [] vals){
    int j = 0;
    for(int i:ids) vals[j++] = at4(i);
  }

  public abstract boolean isNA(int idx);
  public long at16l(int idx) { throw new IllegalArgumentException("Not a UUID"); }
  public long at16h(int idx) { throw new IllegalArgumentException("Not a UUID"); }
  public BufferedString atStr(BufferedString bStr, int idx) { throw new IllegalArgumentException("Not a String"); }

  //Zero sparse methods:
  
  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return true if this Chunk is sparse.  */
  public boolean isSparseZero() {return false;}

  /** Sparse Chunks have a significant number of zeros, and support for
   *  skipping over large runs of zeros in a row.
   *  @return At least as large as the count of non-zeros, but may be significantly smaller than the {@link #_len} */
  public int sparseLenZero() {return _len;}

  public int nextNZ(int rid){ return rid + 1;}

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

  // Next non-NA. Analogous to nextNZ()
  public int nextNNA(int rid){ return rid + 1;}
  
  /** Get chunk-relative indices of values (nonnas for nasparse, all for dense)
   *  stored in this chunk.  For dense chunks, this will contain indices of all
   *  the rows in this chunk.
   *  @return array of chunk-relative indices of values stored in this chunk. */
  public int nonnas(int [] res) {
    for( int i = 0; i < _len; ++i) res[i] = i;
    return _len;
  }
  
  /** Report the Chunk min-value (excluding NAs), or NaN if unknown.  Actual
   *  min can be higher than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double min() { return Double.NaN; }
  /** Report the Chunk max-value (excluding NAs), or NaN if unknown.  Actual
   *  max can be lower than reported.  Used to short-cut RollupStats for
   *  constant and boolean chunks. */
  double max() { return Double.NaN; }

  /** Chunk-specific bulk inflater back to NewChunk.  Used when writing into a
   *  chunk and written value is out-of-range for an update-in-place operation.
   *  Bulk copy from the compressed form into the nc._ls8 array.   */
  public final NewChunk inflate_impl(NewChunk nc){
    nc._len = nc._sparseLen = 0;
    return add2Chunk(nc,0,_len);
  }

  /** @return String version of a Chunk, currently just the class name */
  @Override public String toString() { return getClass().getSimpleName(); }

  /** In memory size in bytes of the compressed Chunk plus embedded array. */
  public long byteSize() {
    long s= _mem == null ? 0 : _mem.length;
    s += (2+5)*8 + 12; // 2 hdr words, 5 other words, @8bytes each, plus mem array hdr
    return s;
  }

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

  NewChunk add2Chunk(NewChunk nc, int from, int to){
    DVal dv = new DVal();
    for(int i = from; i < to; ++i)
      nc.addInflated(getInflated(i,dv));
    return nc;
  }
  NewChunk add2Chunk(NewChunk nc, int... rows){
    DVal dv = new DVal();
    for(int i:rows)
      nc.addInflated(getInflated(i,dv));
    return nc;
  }

  protected boolean set_impl(int row, double d) {
    if( Double.isNaN(d) ) return setNA_impl(row);
    long l = (long)d;
    return l == d && set_impl(row, l);
  }

  protected boolean set_impl(int row, long val) {return false;}
  protected boolean set_impl(int row, float val) {return set_impl(row,(double)val);}
  protected boolean set_impl(int row, String val) {return false;}
  protected boolean set_impl(int row, BufferedString val) {return set_impl(row,val.toString());}
  protected boolean setNA_impl(int row) {return false;}
  protected boolean set_impl(int row, long uuid_lo, long uuid_hi) {return false;}


  public abstract DVal getInflated(int i, DVal v);
  public boolean setInflated(int i, DVal v){ return false;}

  public void removeChunks(int [] ids){
    if(ids.length != 1 || ids[0] != 0)
      throw new IndexOutOfBoundsException();

  }
  @Override public int numCols(){return 1;}

  @Override public Chunk subRange(int off, int to){
    if(off != 0 || to != 1) throw new IndexOutOfBoundsException();
    return this;
  }

  @Override
  public Chunk getColChunk(int c){
    if(c != 0) throw new ArrayIndexOutOfBoundsException(c);
    return this;
  }
  @Override public ChunkAry chunkAry(VecAry v, int cidx){return new ChunkAry(v,cidx,this);}
}
