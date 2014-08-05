package water.fvec;

import java.util.Arrays;
import water.*;
import water.parser.ParseTime;

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
  public static final byte NUMBER = 4;
  public static final byte TIME   = 8;
  public static final byte UUID   =16;
  public static final byte STRING =32;
  byte [] _chunkTypes;
  long _naCnt;
  long _enumCnt;
  long _strCnt;
  final long _timCnt[] = new long[ParseTime.TIME_PARSE.length];
  long _totalCnt;

  public AppendableVec( Key key ) {
    super(key, (long[])null);
    _espc = new long[4];
    _chunkTypes = new byte[4];
  }

  public void setDomain( String domain[] ) { _domain = domain; }

  // A NewVector chunk was "closed" - completed.  Add it's info to the roll-up.
  // This call is made in parallel across all node-local created chunks, but is
  // not called distributed.
  synchronized void closeChunk( NewChunk chk ) {
    final int cidx = chk._cidx;
    while( cidx >= _espc.length ) {
      _espc   = Arrays.copyOf(_espc,_espc.length<<1);
      _chunkTypes = Arrays.copyOf(_chunkTypes,_chunkTypes.length<<1);
    }
    _espc[cidx] = chk.len();
    _chunkTypes[cidx] = chk.type();
    _naCnt += chk.naCnt();
    _enumCnt += chk.enumCnt();
    _strCnt += chk.strCnt();
    for( int i=0; i<_timCnt.length; i++ ) _timCnt[i] += chk._timCnt[i];
    _totalCnt += chk.len();
  }

  // What kind of data did we find?  NA's?  Strings-only?  Floats or Ints?
  boolean shouldBeEnum() {
    // We declare column to be string/enum only if it does not have ANY numbers in it.
    return _enumCnt > 0 && (_enumCnt + _strCnt + _naCnt) == _totalCnt;
  }

  // Class 'reduce' call on new vectors; to combine the roll-up info.
  // Called single-threaded from the M/R framework.
  public void reduce( AppendableVec nv ) {
    if( this == nv ) return;    // Trivially done

    // Combine arrays of elements-per-chunk
    long e1[] = nv._espc;       // Shorter array of longs?
    byte t1[] = nv._chunkTypes;
    if( e1.length > _espc.length ) {
      e1 = _espc;               // Keep the shorter one in e1
      t1 = _chunkTypes;
      _espc = nv._espc;         // Keep longer in the object
      _chunkTypes = nv._chunkTypes;
    }
    for( int i=0; i<e1.length; i++ ){ // Copy non-zero elements over
      assert _chunkTypes[i] == 0 || t1[i] == 0;
      if( e1[i] != 0 && _espc[i]==0 )
        _espc[i] = e1[i];
      _chunkTypes[i] |= t1[i];
    }
    _naCnt += nv._naCnt;
    _enumCnt += nv._enumCnt;
    _strCnt += nv._strCnt;
    water.util.ArrayUtils.add(_timCnt,nv._timCnt);
    _totalCnt += nv._totalCnt;
  }


  // "Close" out a NEW vector - rewrite it to a plain Vec that supports random
  // reads, plus computes rows-per-chunk, min/max/mean, etc.
  public Vec close(Futures fs) {
    // Compute #chunks
    int nchunk = _espc.length;
    DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    while( nchunk > 0 && _espc[nchunk-1] == 0 ) {
      nchunk--;
      DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    }
    boolean hasNumber = false, hasEnum = false, hasTime=false, hasUUID=false, hasString=false;
    for( int i = 0; i < nchunk; ++i ) {
      if( (_chunkTypes[i] & TIME  ) != 0 ) { hasNumber = true; hasTime=true; }
      if( (_chunkTypes[i] & NUMBER) != 0 )   hasNumber = true;
      if( (_chunkTypes[i] & ENUM  ) != 0 )   hasEnum   = true;
      if( (_chunkTypes[i] & UUID  ) != 0 )   hasUUID   = true;
      if( (_chunkTypes[i] & STRING) != 0 )   hasString = true;
    }
    // number wins, we need to go through the enum chunks and declare them all
    // NAs (chunk is considered enum iff it has only enums + possibly some nas)
    if( hasNumber && hasEnum ) {
      for(int i = 0; i < nchunk; ++i)
        if(_chunkTypes[i] == ENUM)
          DKV.put(chunkKey(i), new C0DChunk(Double.NaN, (int)_espc[i]),fs);
    }

    // UUID wins over enum, string & number
    if( hasUUID && (hasEnum || hasNumber || hasString) ) {
      hasEnum=hasNumber=hasString=false;
      for(int i = 0; i < nchunk; ++i)
        if((_chunkTypes[i] & UUID)==0)
          DKV.put(chunkKey(i), new C0DChunk(Double.NaN, (int)_espc[i]),fs);
    }

    // Make sure time is consistent
    int t = -1;
    if( hasTime ) {
      // Find common time parse, and all zeros - or inconsistent time parse
      for( int i=0; i<_timCnt.length; i++ )
        if( _timCnt[i] != 0 )
          if( t== -1 ) t=i;     // common time parse
          else t = -2;          // inconsistent parse
      if( t < 0 )               // blow off time parse
        for(int i = 0; i < nchunk; ++i)
          if(_chunkTypes[i] == TIME)
            DKV.put(chunkKey(i), new C0DChunk(Double.NaN, (int)_espc[i]),fs);

    }
    assert t<0 || _domain == null;

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
    Vec vec = new Vec(_key, espc, _domain, hasUUID, hasString, (byte)t);
    DKV.put(_key,vec,fs);       // Inject the header
    return vec;
  }

  // Default read/write behavior for AppendableVecs
  @Override protected boolean readable() { return false; }
  @Override protected boolean writable() { return true ; }
  @Override public NewChunk chunkForChunkIdx(int cidx) { return new NewChunk(this,cidx); }
  // None of these are supposed to be called while building the new vector
  @Override protected Value chunkIdx( int cidx ) { throw H2O.fail(); }
  @Override public long length() { throw H2O.fail(); }
  @Override public int nChunks() { throw H2O.fail(); }
  @Override int elem2ChunkIdx( long i ) { throw H2O.fail(); }
  @Override protected long chunk2StartElem( int cidx ) { throw H2O.fail(); }
  @Override public long byteSize() { return 0; }
  @Override public String toString() { return "[AppendableVec, unknown size]"; }
}
