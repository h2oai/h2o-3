package water.fvec;

import water.*;
import water.parser.ParseTime;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * A NEW single distributed vector column.
 *
 * The NEW vector has no data, and takes no space.  It supports distributed
 * parallel writes to it, via calls to append2.  Such writes happen in parallel
 * and all writes are ordered.  Writes *will* be local to the node doing them,
 * specifically to allow control over locality.  By default, writes will go
 * local-homed chunks with no compression; there is a final 'close' to the NEW
 * vector which may do compression; the final 'close' will return some other
 * Vec type.  NEW Vectors do NOT support reads!
 */
public class AppendableVec extends Vec {

  public long _tmp_espc[];
  public static final byte NA          = 1;
  public static final byte CATEGORICAL = 2;
  public static final byte NUMBER      = 3;
  public static final byte TIME        = 4;
  public static final byte UUID        = 5;
  public static final byte STRING      = 6;

  private byte[] _chunkTypes;

  public void setTypes(byte [] ts) {
    _chunkTypes = ts;
  }

  /**
   * This is a hack to fix SVMLight parsing. AppendableVec.close() checks the
   * types of each chunk.  This sets the chunk type for uncreated chunks of
   * all 0, are counted as numeric and not NA.
   * @param cidx
   */
  public void setPrecedingChunkTypes(int cidx, byte type) {
    if (_chunkTypes.length < cidx)
      _chunkTypes = Arrays.copyOf(_chunkTypes,cidx<<1);
    for (int i =0; i < cidx; i++) _chunkTypes[i] = type;
  }

  long _naCnt;
  long _catCnt;
  long _strCnt;
  long _timCnt;
  long _totalCnt;

  public int _chunkOff;         // Public so the parser can find it


  public AppendableVec( Key key){
    this(key, new long[4], 0);
  }

  public AppendableVec( Key key, long [] espc, int chunkOff) {
    super(key, null); // NOTE: passing null for espc and then keeping private copy so that the size can change
    _tmp_espc = espc;
    _chunkTypes = new byte[4];
    _chunkOff = chunkOff;
  }
  // A NewVector chunk was "closed" - completed.  Add it's info to the roll-up.
  // This call is made in parallel across all node-local created chunks, but is
  // not called distributed.
  synchronized void closeChunk( NewChunk chk ) {
    final int cidx = chk._cidx - _chunkOff;
    while( cidx >= _chunkTypes.length )
      _chunkTypes = Arrays.copyOf(_chunkTypes,_chunkTypes.length<<1);
    while( cidx >= _tmp_espc.length ) // should not happen if espcs are preallocated and shared!
      _tmp_espc = Arrays.copyOf(_tmp_espc, _tmp_espc.length<<1);
    _tmp_espc[cidx] = chk._len;
    _chunkTypes[cidx] = chk.type();
    _naCnt += chk.naCnt();
    _catCnt += chk.catCnt();
    _strCnt += chk.strCnt();
    _timCnt += chk._timCnt;
    _totalCnt += chk._len;
  }

  public static Vec[] closeAll(AppendableVec [] avs) {
    Futures fs = new Futures();
    Vec [] res = closeAll(avs,fs);
    fs.blockForPending();
    return res;
  }

  public static Vec[] closeAll(AppendableVec [] avs, Futures fs) {
    Vec [] res = new Vec[avs.length];
    for(int i = 0; i < avs.length; ++i)
      res[i] = avs[i].close(fs);
    return res;
  }

  /**
   * Add AV build over sub-range of this vec (used e.g. by multifile parse where each file produces its own AV which represents sub-range of the resulting vec)
   * @param av
   */
  public void setSubRange(AppendableVec av) {
    assert _key.equals(av._key):"mismatched keys " + _key + ", " + av._key;
    System.arraycopy(av._tmp_espc, 0, _tmp_espc, av._chunkOff, av._tmp_espc.length);
    System.arraycopy(av._chunkTypes, 0, _chunkTypes, av._chunkOff, av._tmp_espc.length); // intentionally espc length which is guaranteed to be correct length, types may be longer!
    _strCnt += av._strCnt;
    _naCnt += av._naCnt;
    _catCnt += av._catCnt;
    _timCnt += av._timCnt;
    _totalCnt += av._totalCnt;
  }

  // We declare column to be string/categorical if it has more categoricals than numbers
  public boolean shouldBeCategorical() {
    long numCnt = _totalCnt - _strCnt - _naCnt - _catCnt;
    return _catCnt > numCnt;
  }

  // Class 'reduce' call on new vectors; to combine the roll-up info.
  // Called single-threaded from the M/R framework.
  public void reduce( AppendableVec nv ) {
    if( this == nv ) return;    // Trivially done
    // Combine arrays of elements-per-chunk
    if(_tmp_espc != nv._tmp_espc) {
      long e1[] = nv._tmp_espc;       // Shorter array of longs?
      if (e1.length > _tmp_espc.length) { // should not happen for shared espcs!
        e1 = _tmp_espc;               // Keep the shorter one in e1
        _tmp_espc = nv._tmp_espc;         // Keep longer in the object
      }
      for( int i=0; i<e1.length; i++ ) // Copy non-zero elements over
        _tmp_espc[i] |= e1[i];
    }
    byte t1[] = nv._chunkTypes;
    if(t1.length > _chunkTypes.length) {
      t1 = _chunkTypes;
      _chunkTypes = nv._chunkTypes;
    }
    for( int i =0 ; i < t1.length; ++i)
      _chunkTypes[i] |= t1[i];
    _naCnt += nv._naCnt;
    _catCnt += nv._catCnt;
    _strCnt += nv._strCnt;
    _timCnt += nv._timCnt;
    _totalCnt += nv._totalCnt;
  }


  // "Close" out a NEW vector - rewrite it to a plain Vec that supports random
  // reads, plus computes rows-per-chunk, min/max/mean, etc.
  public Vec close(Futures fs) {
    // Compute #chunks
    int nchunk = _tmp_espc.length;
    DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    while( nchunk > 1 && _tmp_espc[nchunk-1] == 0 ) {
      nchunk--;
      DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    }

    // Histogram chunk types
    int[] ctypes = new int[STRING+1];
    for( int i = 0; i < nchunk; ++i )
      ctypes[_chunkTypes[i]]++;

    // Odd case: new categorical columns are usually made as new numeric columns,
    // with a domain pasted on afterwards.  All chunks look like
    // numbers.... but they are all valid categorical numbers.
    boolean genCatCol = false;
    if( ctypes[CATEGORICAL]==0 && ctypes[TIME] == 0 && ctypes[UUID] == 0 && ctypes[STRING] == 0 ) genCatCol = true;
    // Make sure categoricals are consistent.  If we have a domain, and it is
    // dominating assume CATEGORICAL type.
    if( domain() != null && (genCatCol || ctypes[CATEGORICAL]>ctypes[NUMBER]) ) {
      ctypes[CATEGORICAL] += ctypes[NUMBER]; ctypes[NUMBER]=0;
      ctypes[CATEGORICAL] += ctypes[NA]; ctypes[NA] = 0;        // All NA case
      if (nchunk == 0) ctypes[CATEGORICAL]++;                   // No rows in vec
    }

    // Find the dominant non-NA Chunk type.
    int idx = 0;
    for( int i=0; i<ctypes.length; i++ )
      if( i != NA && ctypes[i] > ctypes[idx] )
        idx = i;

    if( idx!=CATEGORICAL ) setDomain(null);

    // Make Chunks other than the dominant type fail out to NAs.  This includes
    // converting numeric chunks to NAs in categorical columns - we cannot reverse
    // print the numbers to get the original text for the categorical back.
    for(int i = 0; i < nchunk; ++i)
      if(_chunkTypes[i] != idx && 
         !(idx==CATEGORICAL && _chunkTypes[i]==NUMBER && genCatCol)) // Odd case: numeric chunks being forced/treated as a boolean categorical
        DKV.put(chunkKey(i), new C0DChunk(Double.NaN, (int) _tmp_espc[i]),fs);

    byte type;
    switch( idx ) {
    case CATEGORICAL  : type = T_CAT; break;
    case NUMBER: type = T_NUM ; break;
    case TIME  : type = T_TIME; break;
    case UUID  : type = T_UUID; break;
    case STRING: type = T_STR ; break;
    default    : type = T_BAD ; break;
    }

    // Compute elems-per-chunk.
    // Roll-up elem counts, so espc[i] is the starting element# of chunk i.
    long espc[] = new long[nchunk+1]; // Shorter array
    long x=0;                   // Total row count so far
    for( int i=0; i<nchunk; i++ ) {
      espc[i] = x;              // Start elem# for chunk i
      x += _tmp_espc[i];            // Raise total elem count
    }
    espc[nchunk]=x;             // Total element count in last
    // Replacement plain Vec for AppendableVec.
    Vec vec = new Vec(_key, espc, domain(), type);
    DKV.put(_key,vec,fs);       // Inject the header
    return vec;
  }

  // Default read/write behavior for AppendableVecs
  @Override protected boolean readable() { return false; }
  @Override protected boolean writable() { return true ; }
  @Override public NewChunk chunkForChunkIdx(int cidx) { return new NewChunk(this,cidx); }
  // None of these are supposed to be called while building the new vector
  @Override public Value chunkIdx( int cidx ) { throw H2O.fail(); }
  @Override public long length() { throw H2O.fail(); }
  @Override public int nChunks() { throw H2O.fail(); }
  @Override public int elem2ChunkIdx( long i ) { throw H2O.fail(); }
  @Override protected long chunk2StartElem( int cidx ) { throw H2O.fail(); }
  @Override public long byteSize() { return 0; }
  @Override public String toString() { return "[AppendableVec, unknown size]"; }
}
