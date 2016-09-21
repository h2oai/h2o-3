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
  public int _maxCidx;
  // Allow Chunks to have their final Chunk index (set at closing) offset by
  // this much.  Used by the Parser to fold together multi-file AppendableVecs.
  public final int _chunkOff;

  final byte [] _types;


  public AppendableVec(Key<Vec> key, byte... type ) { this(key, new long[4], type, 0);}

  public AppendableVec(Key<Vec> key, long[] tmp_espc, byte [] type, int chunkOff) {
    super( key, -1/*no rowLayout yet*/);
    _tmp_espc = tmp_espc;
    _chunkOff = chunkOff;
    _types = type;
  }

  // "Close" out a NEW vector - rewrite it to a plain Vec that supports random
  // reads, plus computes rows-per-chunk, min/max/mean, etc.
  public Vec closeVec(Futures fs) {return closeVec(fs, (String[][])null);}
  public Vec closeVec(Futures fs,String[] domain) {return closeVec(fs, new String[][]{domain});}
  public Vec closeVec(Futures fs,String[][] domains) {
    // Compute #chunks
    Vec v = new Vec(_key, compute_rowLayout(),_types,domains);
    DKV.put(_key,v,fs);       // Inject the header into the K/V store
    return v;
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
    _maxCidx = Math.max(_maxCidx,nv._maxCidx);
  }

  private int compute_rowLayout() {
    int nchunk = _maxCidx;
    Futures fs = new Futures(); // never block, don't have to
    while( nchunk > 1 && _tmp_espc[nchunk-1] == 0 ) {
      nchunk--;
      DKV.remove(chunkKey(nchunk),fs);
    }
    // Compute elems-per-chunk.
    // Roll-up elem counts, so espc[i] is the starting element# of chunk i.
    long espc[] = new long[nchunk+1]; // Shorter array
    long x=0;                   // Total row count so far
    for( int i=0; i<nchunk; i++ ) {
      espc[i] = x;              // Start elem# for chunk i
      x += _tmp_espc[i];        // Raise total elem count
    }
    espc[nchunk]=x;             // Total element count in last
    return Vec.ESPC.rowLayout(_key,espc);
  }




  // Default read/write behavior for AppendableVecs
  @Override protected boolean readable() { return false; }
  @Override protected boolean writable() { return true ; }

  @Override
  public String[] domain(int i) {
    return new String[0];
  }

  @Override
  public byte type(int colId) {
    return 0;
  }

  @Override public Chunks.AppendableChunks chunkForChunkIdx(int cidx) {
    NewChunk [] ncs = new NewChunk[numCols()];
    for(int i = 0; i < ncs.length; ++i)
      ncs[i] = new NewChunk();
    Chunks.AppendableChunks cs = new Chunks.AppendableChunks(ncs);
    cs._cidx = cidx;
    cs._vec = this;
    cs._start = -1;
    return cs;
  }

  public synchronized void closeChunk(int cidx, int len) {
    cidx -= _chunkOff;
    if(_tmp_espc == null)
      _tmp_espc = new long[cidx+1];
    else if(_tmp_espc.length <= cidx)
      _tmp_espc = Arrays.copyOf(_tmp_espc, cidx + 1);
    _maxCidx = Math.max(_maxCidx,cidx);
    _tmp_espc[cidx] = len;
  }

  @Override
  public Vec doCopy() {
    throw new UnsupportedOperationException("AppendableVec does not support copy.");
  }

  // None of these are supposed to be called while building the new vector
  @Override public Value chunkIdx( int cidx ) { throw H2O.fail(); }

  @Override
  public void setDomain(int vec, String[] domain) {throw new UnsupportedOperationException();}


  @Override public long length() { throw H2O.fail(); }
  @Override public int nChunks() { throw H2O.fail(); }
  @Override public int elem2ChunkIdx( long i ) { throw H2O.fail(); }
  @Override public long chunk2StartElem( int cidx ) { throw H2O.fail(); }
  @Override public long byteSize() {return 0;}

  @Override public String toString() { return "[AppendableVec, unknown size]"; }

}
