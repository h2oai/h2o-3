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
public class AppendableVec extends AVec {
  // Temporary ESPC, for uses which do not know the number of Chunks up front.
  public long _tmp_espc[];
  // Allow Chunks to have their final Chunk index (set at closing) offset by
  // this much.  Used by the Parser to fold together multi-file AppendableVecs.
  public final int _chunkOff;

  final byte [] _types;
  final int [] _blocks;


  public AppendableVec( Key<AVec> key, byte... type ) { this(key, new int[]{type.length}, new long[4], type, 0);}
  public AppendableVec( Key<AVec> key, int [] blocks, byte... type ) { this(key, blocks, new long[4], type, 0); }

  public AppendableVec( Key<AVec> key, int [] blocks, long[] tmp_espc, byte [] type, int chunkOff) {
    super( key, -1/*no rowLayout yet*/);
    _tmp_espc = tmp_espc;
    _chunkOff = chunkOff;
    _types = type;
    _blocks = blocks == null?new int[_types.length]:blocks;
  }
  public VecAry closeVecs(String[] domains, int rowLayout, Futures fs) {
    return closeVecs(new String[][]{domains},rowLayout,fs);
  }

  // "Close" out a NEW vector - rewrite it to a plain Vec that supports random
  // reads, plus computes rows-per-chunk, min/max/mean, etc.
  public VecAry closeVecs(String[][] domains, int rowLayout, Futures fs) {
    // Compute #chunks
    AVec [] vecs = new AVec[_blocks.length];
    int nchunk = _tmp_espc.length;
    int off = 0;
    VectorGroup vg = group();
    int vecStart = VectorGroup.getVecId(_key._kb);
    for(int i = 0; i < _blocks.length; ++i) {
      Key<AVec> k = vg.vecKey(vecStart+i);
      vecs[i] = (_blocks[i] == 1)
          ?new Vec(k, rowLayout, domains[off], _types[off])
          :new VecBlock(k, rowLayout, _blocks[i],Arrays.copyOfRange(domains,off,off+_blocks[i]), Arrays.copyOfRange(_types,off,off+_blocks[i]));
      DKV.put(vecs[i]._key,vecs[i],fs);       // Inject the header into the K/V store
      off += _blocks[i];
      DKV.remove(vecs[i].chunkKey(_key,nchunk,i),fs); // remove potential trailing key
      while( nchunk > 1 && _tmp_espc[nchunk-1] == 0 ) {
        nchunk--;
        DKV.remove(vecs[i].chunkKey(_key,nchunk,i),fs); // remove potential trailing key
      }
    }
    return new VecAry(vecs);
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


  public VecAry layout_and_close(Futures fs) {
    return layout_and_close(fs, (String[][])null);
  }
  public VecAry layout_and_close(Futures fs, String [] domain) {
    assert _types.length==1;
    return layout_and_close(fs, new String[][]{domain});
  }
  public VecAry layout_and_close(Futures fs, String [][] domains) { return closeVecs(domains, compute_rowLayout(_key,_tmp_espc),fs); }


  public static int compute_rowLayout(Key<AVec> k, long [] tmp_espc) {
    int nchunk = tmp_espc.length;
    while( nchunk > 1 && tmp_espc[nchunk-1] == 0 )
      nchunk--;
    // Compute elems-per-chunk.
    // Roll-up elem counts, so espc[i] is the starting element# of chunk i.
    long espc[] = new long[nchunk+1]; // Shorter array
    long x=0;                   // Total row count so far
    for( int i=0; i<nchunk; i++ ) {
      espc[i] = x;              // Start elem# for chunk i
      x += tmp_espc[i];        // Raise total elem count
    }
    espc[nchunk]=x;             // Total element count in last
    return AVec.ESPC.rowLayout(k,espc);
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

  @Override public ChunkAry chunkForChunkIdx(int cidx) {throw new UnsupportedOperationException();}


  public synchronized void closeChunk(int cidx, int len) {
    if(_tmp_espc == null)
      _tmp_espc = new long[cidx+1];
    else if(_tmp_espc.length <= cidx)
      _tmp_espc = Arrays.copyOf(_tmp_espc,cidx+1);
    _tmp_espc[cidx] = len;
  }

  @Override
  public AVec doCopy() {
    throw new UnsupportedOperationException("AppendableVec does not support copy.");
  }

  // None of these are supposed to be called while building the new vector
  @Override public Value chunkIdx( int cidx ) { throw H2O.fail(); }

  @Override
  public boolean hasCol(int id) {
    return false;
  }

  @Override
  public RollupStats getRollups(int colId, boolean histo) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDomain(int vec, String[] domain) {throw new UnsupportedOperationException();}

  @Override
  public void setBad(int colId) {

  }

  @Override public long length() { throw H2O.fail(); }
  @Override public int nChunks() { throw H2O.fail(); }
  @Override public int elem2ChunkIdx( long i ) { throw H2O.fail(); }
  @Override public long chunk2StartElem( int cidx ) { throw H2O.fail(); }
  @Override public long byteSize() {return 0;}

  @Override
  public void preWriting(int... colIds) {}

  @Override
  public Futures postWrite(Futures fs) {return fs;}


  @Override public String toString() { return "[AppendableVec, unknown size]"; }

  public void setNCols(int newColCnt) {
    throw H2O.unimpl();
  }

  public VecAry closeVecs(int rowLayout, Futures fs) {
    return closeVecs((String[])null,rowLayout,fs);
  }
}
