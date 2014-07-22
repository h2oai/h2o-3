package water.fvec;

import water.*;
import water.parser.ValueString;

/** A compression scheme, over a chunk - a single array of bytes.  The *actual*
 *  vector header info is in the Vec struct - which contains info to find all
 *  the bytes of the distributed vector.  This struct is basically a 1-entry
 *  chunk cache of the total vector.  Subclasses of this abstract class
 *  implement (possibly empty) compression schemes.  */
public abstract class Chunk extends Iced implements Cloneable {
  protected long _start = -1;    // Start element; filled after AutoBuffer.read
  public final long start() { return _start; } // Start element; filled after AutoBuffer.read
  private int _len;            // Number of elements in this chunk
  public int len() { return _len; }
  public int set_len(int _len) { return this._len = _len; }
  private Chunk _chk2;       // Normally==null, changed if chunk is written to
  public final Chunk chk2() { return _chk2; } // Normally==null, changed if chunk is written to
  protected Vec _vec;            // Owning Vec; filled after AutoBuffer.read
  public final Vec vec() { return _vec; }   // Owning Vec; filled after AutoBuffer.read
  protected byte[] _mem;  // Short-cut to the embedded memory; WARNING: holds onto a large array
  public final byte[] getBytes() { return _mem; } // Short-cut to the embedded memory; WARNING: holds onto a large array
  // Used by a ParseExceptionTest to break the Chunk invariants & trigger an NPE
  public final void crushBytes() { _mem=null; }

  /** Load a long value.  Floating point values are silently rounded to an
    * integer.  Throws if the value is missing.
    * <p>
    * Loads from the 1-entry chunk cache, or misses-out.  This version uses
    * absolute element numbers, but must convert them to chunk-relative indices
    * - requiring a load from an aliasing local var, leading to lower quality
    * JIT'd code (similar issue to using iterator objects).
    * <p>
    * Slightly slower than 'at0' since it range checks within a chunk. */
  final long  at8( long i ) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < len()) return at80((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ len()));
  }

  /** Load a double value.  Returns Double.NaN if value is missing.
   *  <p>
   * Loads from the 1-entry chunk cache, or misses-out.  This version uses
   * absolute element numbers, but must convert them to chunk-relative indices
   * - requiring a load from an aliasing local var, leading to lower quality
   * JIT'd code (similar issue to using iterator objects).
   * <p>
   * Slightly slower than 'at80' since it range checks within a chunk. */
  public final double at( long i ) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < len()) return at0((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ len()));
  }

  /** Fetch the missing-status the slow way. */
  final boolean isNA(long i) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < len()) return isNA0((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ len()));
  }
  public final long at16l( long i ) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < len()) return at16l0((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ len()));
  }
  public final long at16h( long i ) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < len()) return at16h0((int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ len()));
  }
  public final ValueString atStr( ValueString vstr, long i ) {
    long x = i - (_start>0 ? _start : 0);
    if( 0 <= x && x < len()) return atStr0(vstr,(int)x);
    throw new ArrayIndexOutOfBoundsException(""+_start+" <= "+i+" < "+(_start+ len()));
  }


  /** The zero-based API.  Somewhere between 10% to 30% faster in a tight-loop
   *  over the data than the generic at() API.  Probably no gain on larger
   *  loops.  The row reference is zero-based on the chunk, and should
   *  range-check by the JIT as expected.  */
  public final double  at0  ( int i ) { return _chk2 == null ? atd_impl(i) : _chk2. atd_impl(i); }
  public final long    at80 ( int i ) { return _chk2 == null ? at8_impl(i) : _chk2. at8_impl(i); }
  public final boolean isNA0( int i ) { return _chk2 == null ?isNA_impl(i) : _chk2.isNA_impl(i); }
  public final long   at16l0( int i ) { return _chk2 == null ? at16l_impl(i) : _chk2.at16l_impl(i); }
  public final long   at16h0( int i ) { return _chk2 == null ? at16h_impl(i) : _chk2.at16h_impl(i); }
  public final ValueString atStr0( ValueString vstr, int i ) { return _chk2 == null ? atStr_impl(vstr,i) : _chk2.atStr_impl(vstr,i); }


  /** Write element the slow way, as a long.  There is no way to write a
   *  missing value with this call.  Under rare circumstances this can throw:
   *  if the long does not fit in a double (value is larger magnitude than
   *  2^52), AND float values are stored in Vector.  In this case, there is no
   *  common compatible data representation. */
  public final void set( long i, long   l) { long x = i-_start; if (0 <= x && x < len()) set0((int)x,l); else _vec.set(i,l); }
  /** Write element the slow way, as a double.  Double.NaN will be treated as
   *  a set of a missing element. */
  public final void set( long i, double d) { long x = i-_start; if (0 <= x && x < len()) set0((int)x,d); else _vec.set(i,d); }
  /** Write element the slow way, as a float.  Float.NaN will be treated as
   *  a set of a missing element. */
  public final void set( long i, float  f) { long x = i-_start; if (0 <= x && x < len()) set0((int)x,f); else _vec.set(i,f); }
  /** Set the element as missing the slow way.  */
  final void setNA( long i ) { long x = i-_start; if (0 <= x && x < len()) setNA0((int)x); else _vec.setNA(i); }

  public final void set( long i, String str) { long x = i-_start; if (0 <= x && x < len()) set0((int)x,str); else _vec.set(i,str); }
  
  private void setWrite() {
    if( _chk2 != null ) return; // Already setWrite
    assert !(this instanceof NewChunk) : "Cannot direct-write into a NewChunk, only append";
    _vec.preWriting();          // One-shot writing-init
    _chk2 = (Chunk)clone();     // Flag this chunk as having been written into
    assert _chk2._chk2 == null; // Clone has NOT been written into
  }

  /**
   * Set a long element in a chunk given a 0-based chunk local index.
   *
   * Write into a chunk.
   * May rewrite/replace chunks if the chunk needs to be
   * "inflated" to hold larger values.  Returns the input value.
   *
   * Note that the idx is an int (instead of a long), which tells you
   * that index 0 is the first row in the chunk, not the whole Vec.
   */
  public final long set0(int idx, long l) {
    setWrite();
    if( _chk2.set_impl(idx,l) ) return l;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,l);
    return l;
  }

  /** Set a double element in a chunk given a 0-based chunk local index. */
  public final double set0(int idx, double d) {
    setWrite();
    if( _chk2.set_impl(idx,d) ) return d;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,d);
    return d;
  }

  /** Set a floating element in a chunk given a 0-based chunk local index. */
  public final float set0(int idx, float f) {
    setWrite();
    if( _chk2.set_impl(idx,f) ) return f;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,f);
    return f;
  }

  /** Set the element in a chunk as missing given a 0-based chunk local index. */
  public final boolean setNA0(int idx) {
    setWrite();
    if( _chk2.setNA_impl(idx) ) return true;
    (_chk2 = inflate_impl(new NewChunk(this))).setNA_impl(idx);
    return true;
  }

  public final String set0(int idx, String str) {
    setWrite();
    if( _chk2.set_impl(idx,str) ) return str;
    (_chk2 = inflate_impl(new NewChunk(this))).set_impl(idx,str);
    return str;
  }

  /** After writing we must call close() to register the bulk changes */
  public Futures close( int cidx, Futures fs ) {
    if( this  instanceof NewChunk ) _chk2 = this;
    if( _chk2 == null ) return fs;          // No change?
    if( _chk2 instanceof NewChunk ) _chk2 = ((NewChunk)_chk2).new_close();
    DKV.put(_vec.chunkKey(cidx),_chk2,fs,true); // Write updated chunk back into K/V
    if( _vec._cache == this ) _vec._cache = null;
    return fs;
  }

  public int cidx() { return _vec.elem2ChunkIdx(_start); }

  /** Chunk-specific readers.  */ 
  abstract protected double   atd_impl(int idx);
  abstract protected long     at8_impl(int idx);
  abstract protected boolean isNA_impl(int idx);
  protected long at16l_impl(int idx) { throw new IllegalArgumentException("Not a UUID"); }
  protected long at16h_impl(int idx) { throw new IllegalArgumentException("Not a UUID"); }
  protected ValueString atStr_impl(ValueString vstr, int idx) { throw new IllegalArgumentException("Not a String"); }
  
  /** Chunk-specific writer.  Returns false if the value does not fit in the
   *  current compression scheme.  */
  abstract boolean set_impl  (int idx, long l );
  abstract boolean set_impl  (int idx, double d );
  abstract boolean set_impl  (int idx, float f );
  abstract boolean setNA_impl(int idx);
  boolean set_impl (int idx, String str) { throw new IllegalArgumentException("Not a String"); }

  int nextNZ(int rid){return rid+1;}
  public boolean isSparse() {return false;}
  public int sparseLen(){return len();}

  /** Get chunk-relative indices of values (nonzeros for sparse, all for dense) stored in this chunk.
   *  For dense chunks, this will contain indices of all the rows in this chunk.
   *  @return array of chunk-relative indices of values stored in this chunk.
   */
  public int nonzeros(int [] res) {
    for( int i = 0; i < len(); ++i) res[i] = i;
    return len();
  }

  /**
   * Get chunk-relative indices of values (nonzeros for sparse, all for dense) stored in this chunk.
   * For dense chunks, this will contain indices of all the rows in this chunk.
   *
   * @return array of chunk-relative indices of values stored in this chunk.
   */
  public final int [] nonzeros () {
    int [] res = MemoryManager.malloc4(sparseLen());
    nonzeros(res);
    return res;
  }

/** Chunk-specific bulk inflater back to NewChunk.  Used when writing into a
   *  chunk and written value is out-of-range for an update-in-place operation.
   *  Bulk copy from the compressed form into the nc._ls array.   */ 
  abstract NewChunk inflate_impl(NewChunk nc);

  /** Return the next Chunk, or null if at end.  Mostly useful for parsers or
   *  optimized stencil calculations that want to "roll off the end" of a
   *  Chunk, but in a highly optimized way. */
  Chunk nextChunk( ) { return _vec.nextChunk(this); }

  @Override public String toString() { return getClass().getSimpleName(); }

  public long byteSize() {
    long s= _mem == null ? 0 : _mem.length;
    s += (2+5)*8 + 12; // 2 hdr words, 5 other words, @8bytes each, plus mem array hdr
    if( _chk2 != null ) s += _chk2.byteSize();
    return s;
  }

  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  abstract public AutoBuffer write_impl( AutoBuffer ab );
  abstract public Chunk read_impl( AutoBuffer ab );

  // -----------------
  // Support for fixed-width format printing
//  private String pformat () { return pformat0(); }
//  private int pformat_len() { return pformat_len0(); }
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
}
