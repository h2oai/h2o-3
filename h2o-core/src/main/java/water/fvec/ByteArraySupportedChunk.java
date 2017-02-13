package water.fvec;

import water.*;

/** A compression scheme, over a chunk of data - a single array of bytes.
 *  Chunks are mapped many-to-1 to a {@link Vec}.  The <em>actual</em> vector
 *  header info is in the Vec - which contains info to find all the bytes of
 *  the distributed vector.  Subclasses of this abstract class implement
 *  (possibly empty) compression schemes.
 *
 *  <p>Chunks are collections of elements, and support an array-like API.
 *  Chunks are subsets of a Vec; while the elements in a Vec are numbered
 *  starting at 0, any given ByteArraySupportedChunk has some (probably non-zero) starting row,
 *  and a length which is smaller than the whole Vec.  Chunks are limited to a
 *  single Java byte array in a single JVM heap, and only an int's worth of
 *  elements.  Chunks support both the notions of a global row-number and a
 *  chunk-local numbering.  The global row-number calls are variants of {@code
 *  at} and {@code set}.  If the row is outside the current ByteArraySupportedChunk's range, the
 *  data will be loaded by fetching from the correct ByteArraySupportedChunk.  This probably
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
 *  to compress back into the actual underlying ByteArraySupportedChunk subclass is made; if this
 *  fails the ByteArraySupportedChunk is "inflated" into a {@link NewChunk}, and the store
 *  completed there.  Later the NewChunk will be compressed (probably into a
 *  different underlying ByteArraySupportedChunk subclass) and put back in the K/V store under
 *  the same Key - effectively replacing the original ByteArraySupportedChunk; this is done when
 *
 *  <p>ByteArraySupportedChunk updates are not multi-thread safe; the caller must do correct
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
 *  global row numbers.  Iterating over a single ByteArraySupportedChunk is nearly always
 *  memory-bandwidth bound.  Often code will iterate over a number of Chunks
 *  aligned together (the common use-case of looking a whole rows of a
 *  dataset).  Again, typically such a code pattern is memory-bandwidth bound
 *  although the X86 will stop being able to prefetch well beyond 100 or 200
 *  Chunks.
 *
 *  <p>Note that ByteArraySupportedChunk alignment is guaranteed within all the Vecs of a Frame:
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
public void map( ByteArraySupportedChunk[] chks ) {                  // Map over a set of same-numbered Chunks
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

public abstract class ByteArraySupportedChunk extends Chunk {
  /** The Big Data.  Frequently set in the subclasses, but not otherwise a publically writable field. */
  byte[] _mem;

  public abstract int len();

  public ByteArraySupportedChunk() {}
  private ByteArraySupportedChunk(byte [] bytes) {_mem = bytes;initFromBytes();}


  /** Short-cut to the embedded big-data memory.  Generally not useful for
   *  public consumption, since the data remains compressed and holding on to a
   *  pointer to this array defeats the user-mode spill-to-disk. */
  public byte[] getBytes() { return _mem; }

  public void setBytes(byte[] mem) { _mem = mem; }


  @Override
  public ByteArraySupportedChunk deepCopy() {
    ByteArraySupportedChunk c2 = (ByteArraySupportedChunk)clone();
    c2._mem = _mem.clone();
    return c2;
  }


  //Zero sparse methods:


  //NA sparse methods:

  /** In memory size in bytes of the compressed ByteArraySupportedChunk plus embedded array. */
  @Override
  public long byteSize() {
    return super.byteSize() + _mem.length + 24; // approximate size is Chunk object overhead + data bytes + mem array header
  }

  /** Custom serializers implemented by ByteArraySupportedChunk subclasses: the _mem field
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
  public final ByteArraySupportedChunk read_impl(AutoBuffer ab){
    _mem = ab.getA1();
    initFromBytes();
    return this;
  }


//  /** Custom deserializers, implemented by ByteArraySupportedChunk subclasses: the _mem field
//   *  contains ALL the fields already.  Init _start to -1, so we know we have
//   *  not filled in other fields.  Leave _vec and _chk2 null, leave _len
//   *  unknown. */
//  abstract public ByteArraySupportedChunk read_impl( AutoBuffer ab );

  // -----------------
  // Support for fixed-width format printing
//  private String pformat () { return pformat0(); }
//  private int pformat__len { return pformat_len0(); }


}
