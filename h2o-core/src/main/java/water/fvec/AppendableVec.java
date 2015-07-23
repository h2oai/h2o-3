package water.fvec;

import water.*;
import water.parser.ParseTime;
import water.util.Log;
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

  public long _espc[];
  public static final byte NA     = 1;
  public static final byte ENUM   = 2;
  public static final byte NUMBER = 3;
  public static final byte TIME   = 4;
  public static final byte UUID   = 5;
  public static final byte STRING = 6;

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
  long _enumCnt;
  long _strCnt;
  long _timCnt;
  long _totalCnt;

  public int _chunkOff;         // Public so the parser can find it


  public AppendableVec( Key key){
    this(key, new long[4], 0);
  }

  public AppendableVec( Key key, long [] espc, int chunkOff) {
    super(key, null); // NOTE: passing null for espc and then keeping private copy so that the size can change
    _espc = espc;
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
    while( cidx >= _espc.length ) // should not happen if espcs are preallocated and shared!
      _espc = Arrays.copyOf(_espc,_espc.length<<1);
    _espc[cidx] = chk._len;
    _chunkTypes[cidx] = chk.type();
    _naCnt += chk.naCnt();
    _enumCnt += chk.enumCnt();
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
    System.arraycopy(av._espc, 0, _espc, av._chunkOff, av._espc.length);
    System.arraycopy(av._chunkTypes, 0, _chunkTypes, av._chunkOff, av._espc.length); // intentionally espc length which is guaranteed to be correct length, types may be longer!
    _strCnt += av._strCnt;
    _naCnt += av._naCnt;
    _enumCnt += av._enumCnt;
    _timCnt += av._timCnt;
    _totalCnt += av._totalCnt;
  }

  // We declare column to be string/enum if it has more enums than numbers
  public boolean shouldBeEnum() {
    long numCnt = _totalCnt - _strCnt - _naCnt - _enumCnt;
    return _enumCnt > numCnt;
  }

  // Class 'reduce' call on new vectors; to combine the roll-up info.
  // Called single-threaded from the M/R framework.
  public void reduce( AppendableVec nv ) {
    if( this == nv ) return;    // Trivially done
    // Combine arrays of elements-per-chunk
    if(_espc != nv._espc) {
      long e1[] = nv._espc;       // Shorter array of longs?
      if (e1.length > _espc.length) { // should not happen for shared espcs!
        e1 = _espc;               // Keep the shorter one in e1
        _espc = nv._espc;         // Keep longer in the object
      }
      for( int i=0; i<e1.length; i++ ) // Copy non-zero elements over
        _espc[i] |= e1[i];
    }
    byte t1[] = nv._chunkTypes;
    if(t1.length > _chunkTypes.length) {
      t1 = _chunkTypes;
      _chunkTypes = nv._chunkTypes;
    }
    for( int i =0 ; i < t1.length; ++i)
      _chunkTypes[i] |= t1[i];
    _naCnt += nv._naCnt;
    _enumCnt += nv._enumCnt;
    _strCnt += nv._strCnt;
    _timCnt += nv._timCnt;
    _totalCnt += nv._totalCnt;
  }


  // "Close" out a NEW vector - rewrite it to a plain Vec that supports random
  // reads, plus computes rows-per-chunk, min/max/mean, etc.
  public Vec close(Futures fs) {
    // Compute #chunks
    int nchunk = _espc.length;
    DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    while( nchunk > 1 && _espc[nchunk-1] == 0 ) {
      nchunk--;
      DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    }

    // Histogram chunk types
    int[] ctypes = new int[STRING+1];
    for( int i = 0; i < nchunk; ++i )
      ctypes[_chunkTypes[i]]++;

    // Odd case: new enum columns are usually made as new numeric columns,
    // with a domain pasted on afterwards.  All chunks look like
    // numbers.... but they are all valid enum numbers.
    boolean genEnumCol = false;
    if( ctypes[ENUM]==0 && ctypes[TIME] == 0 && ctypes[UUID] == 0 && ctypes[STRING] == 0 ) genEnumCol = true;
    // Make sure enums are consistent.  If we have a domain, and it is
    // dominating assume ENUM type.
    if( domain() != null && (genEnumCol || ctypes[ENUM]>ctypes[NUMBER]) ) {
      ctypes[ENUM] += ctypes[NUMBER]; ctypes[NUMBER]=0;
      ctypes[ENUM] += ctypes[NA]; ctypes[NA] = 0;        // All NA case
      if (nchunk == 0) ctypes[ENUM]++;                   // No rows in vec
    }

    // Find the dominant non-NA Chunk type.
    int idx = 0;
    for( int i=0; i<ctypes.length; i++ )
      if( i != NA && ctypes[i] > ctypes[idx] )
        idx = i;

    if( idx!=ENUM ) setDomain(null);

    // Make Chunks other than the dominant type fail out to NAs.  This includes
    // converting numeric chunks to NAs in Enum columns - we cannot reverse
    // print the numbers to get the original text for the Enum back.
    for(int i = 0; i < nchunk; ++i)
      if(_chunkTypes[i] != idx && 
         !(idx==ENUM && _chunkTypes[i]==NUMBER && genEnumCol)) // Odd case: numeric chunks being forced/treated as a boolean enum
        DKV.put(chunkKey(i), new C0DChunk(Double.NaN, (int)_espc[i]),fs);

    byte type;
    switch( idx ) {
    case ENUM  : type = T_ENUM; break;
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
      x += _espc[i];            // Raise total elem count
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
  @Override int elem2ChunkIdx( long i ) { throw H2O.fail(); }
  @Override protected long chunk2StartElem( int cidx ) { throw H2O.fail(); }
  @Override public long byteSize() { return 0; }
  @Override public String toString() { return "[AppendableVec, unknown size]"; }
}
