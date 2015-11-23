package water.fvec;

import water.*;
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
  // Temporary ESPC, for uses which do not know the number of Chunks up front.
  public long _tmp_espc[];
  // Allow Chunks to have their final Chunk index (set at closing) offset by
  // this much.  Used by the Parser to fold together multi-file AppendableVecs.
  public final int _chunkOff;


  public AppendableVec( Key<Vec> key, byte type ) { this(key, new long[4], type, 0); }

  public AppendableVec( Key<Vec> key, long[] tmp_espc, byte type, int chunkOff) {
    super( key, -1/*no rowLayout yet*/, null, type ); 
    _tmp_espc = tmp_espc;
    _chunkOff = chunkOff;
  }
  // A NewVector chunk was "closed" - completed.  Add it's info to the roll-up.
  // This call is made in parallel across all node-local created chunks, but is
  // not called distributed.
  synchronized void closeChunk( int cidx, int len ) {
    // The Parser will pre-allocate the _tmp_espc large enough (the Parser
    // knows how many final Chunks there will be up front).  Other users are
    // encouraged to set a "large enough" espc - and a shared one at that - to
    // avoid these copies.

    // Set the length into the temp ESPC at the Chunk index (accounting for _chunkOff)
    cidx -= _chunkOff;
    while( cidx >= _tmp_espc.length ) // should not happen if espcs are preallocated and shared!
      _tmp_espc = Arrays.copyOf(_tmp_espc, _tmp_espc.length<<1);
    _tmp_espc[cidx] = len;
  }

  public static Vec[] closeAll(AppendableVec [] avs) {
    Futures fs = new Futures();
    Vec [] res = closeAll(avs,fs);
    fs.blockForPending();
    return res;
  }

  public static Vec[] closeAll(AppendableVec [] avs, Futures fs) {
    Vec [] res = new Vec[avs.length];
    final int rowLayout = avs[0].compute_rowLayout();
    for(int i = 0; i < avs.length; ++i)
      res[i] = avs[i].close(rowLayout,fs);
    return res;
  }

  // Class 'reduce' call on new vectors; to combine the roll-up info.
  // Called single-threaded from the M/R framework.
  public void reduce( AppendableVec nv ) {
    if( this == nv ) return;    // Trivially done
    if( _tmp_espc == nv._tmp_espc ) return;
    // Combine arrays of elements-per-chunk
    long e1[] = nv._tmp_espc;           // Shorter array of longs?
    if (e1.length > _tmp_espc.length) { // Keep longer array
      e1 = _tmp_espc;                   // Keep the shorter one in e1
      _tmp_espc = nv._tmp_espc;         // Keep longer in the object
    }
    for( int i=0; i<e1.length; i++ )      // Copy non-zero elements over
      if( _tmp_espc[i]==0 && e1[i] != 0 ) // Read-filter (old code unconditionally did a R-M-W cycle)
        _tmp_espc[i] = e1[i];             // Only write if needed
  }


  public Vec layout_and_close(Futures fs) { return close(compute_rowLayout(),fs); }

  public int compute_rowLayout() {
    int nchunk = _tmp_espc.length;
    while( nchunk > 1 && _tmp_espc[nchunk-1] == 0 )
      nchunk--;
    // Compute elems-per-chunk.
    // Roll-up elem counts, so espc[i] is the starting element# of chunk i.
    long espc[] = new long[nchunk+1]; // Shorter array
    long x=0;                   // Total row count so far
    for( int i=0; i<nchunk; i++ ) {
      espc[i] = x;              // Start elem# for chunk i
      x += _tmp_espc[i];        // Raise total elem count
    }
    espc[nchunk]=x;             // Total element count in last
    return ESPC.rowLayout(_key,espc);
  }


  // "Close" out a NEW vector - rewrite it to a plain Vec that supports random
  // reads, plus computes rows-per-chunk, min/max/mean, etc.
  public Vec close(int rowLayout, Futures fs) {
    // Compute #chunks
    int nchunk = _tmp_espc.length;
    DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    while( nchunk > 1 && _tmp_espc[nchunk-1] == 0 ) {
      nchunk--;
      DKV.remove(chunkKey(nchunk),fs); // remove potential trailing key
    }

    // Replacement plain Vec for AppendableVec.
    Vec vec = new Vec(_key, rowLayout, domain(), _type);
    DKV.put(_key,vec,fs);       // Inject the header into the K/V store
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
