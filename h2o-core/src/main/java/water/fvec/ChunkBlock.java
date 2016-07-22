package water.fvec;

import water.*;

import java.util.Arrays;

/**
 * Created by tomas on 6/28/16.
 *
 * Simple storage for multiple chunks.
 */
public class ChunkBlock extends AVec.AChunk<ChunkBlock> {
  transient VecBlock _vb;
  transient long _start;
  int _cidx;
  private int _numCols;    // VecGroup vecId

  private int [] _nzChunks; // nz ids for sparse
  public final int[] nzChunks(){return _nzChunks;}

  protected Chunk [] _chunks; // data for nz vecs
  public final Chunk [] chunks(){return _chunks;}

  private final int _len;
  public ChunkBlock(){_len = -1; _numCols = 0;}

  public ChunkBlock(Chunk [] chunks){
    _numCols = chunks.length;
    _nzChunks = null;
    _chunks = chunks;
    _len = chunks[0]._len;
  }
  public ChunkBlock(int numCols, int len, int [] nzChunks, Chunk [] chunks, double sparseElem) {
    _numCols = numCols;
    _nzChunks = nzChunks;
    _chunks = chunks;
    _len = len;
  }
  public int chunkId(){return _cidx;}
  public boolean isSparse(){return _nzChunks != null;}

  public transient volatile boolean _modified;

  public final AutoBuffer write_impl(AutoBuffer ab) {
    ab.put4(_numCols);
    ab.putA4(_nzChunks);
    ab.putA(_chunks);
    return ab;
  }
  public final ChunkBlock read_impl(AutoBuffer ab){
    _numCols = ab.get4();
    _nzChunks = ab.getA4();
    Chunk [] chunks = ab.getA(Chunk.class);
    _chunks = chunks;
    return this;
  }

  public synchronized void updateChunk(int id, Chunk chk2) {
    if(isSparse()) {
      int i = Arrays.binarySearch(_nzChunks,id);
      if(i < 0) {
        i = -i -1;
        int [] nzchunks = Arrays.copyOf(_nzChunks,_nzChunks.length+1);
        Chunk [] chunks = Arrays.copyOf(_chunks,_chunks.length+1);
        for(int j = _nzChunks.length; j > i; ++j) {
          nzchunks[j] = nzchunks[j - 1];
          chunks[j] = chunks[j-1];
        }
        nzchunks[i] = id;
        chunks[i] = chk2;
      }

    } else {
      _chunks[id] = chk2;
    }
    _modified = true;
  }

  public Chunk [] getChunks(){return _chunks;}
  @Override
  public Chunk getChunk(int i) {
    if(!_vb.hasVec(i))
      throw new NullPointerException();
    if(isSparse()) {
      i = Arrays.binarySearch(_nzChunks,i);
    }
    return _chunks[i];
  }

  public synchronized Futures close(Futures fs){
    if(_modified) {
      DKV.put(_vb.chunkKey(_cidx),this,fs);
      _modified = false;
    }
    return fs;
  }


  public int len() {
    return _len;
  }
}
