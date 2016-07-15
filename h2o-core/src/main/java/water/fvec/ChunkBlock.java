package water.fvec;

import water.*;

import java.util.Arrays;

/**
 * Created by tomas on 6/28/16.
 *
 * Simple storage for multiple chunks.
 */
public class ChunkBlock extends Keyed<ChunkBlock> {
  transient VecBlock _vb;
  transient long _start;
  int _cidx;
  private int _vecStart;    // VecGroup vecId
  private int _vecEnd;      // VecGroup vecId
  public final int vecStart(){return _vecStart;}
  public final int vecEnd() {return _vecEnd;}
  private int [] _nzChunks; // nz ids for sparse
  public final int[] nzChunks(){return _nzChunks;}

  protected Chunk [] _chunks; // data for nz vecs
  public final Chunk [] chunks(){return _chunks;}

  private final int _len;
  public ChunkBlock(){_len = -1;}
  public ChunkBlock(int vecStart, int vecEnd, int [] nzChunks, Chunk [] chunks) {
    _vecStart = vecStart;
    _vecEnd = vecEnd;
    _nzChunks = nzChunks;
    for(int i = 0; i < chunks.length; ++i) {
      chunks[i].setCB(this);
    }
    _chunks = chunks;
    _len = chunks[0]._len;
  }
  public int chunkId(){return _cidx;}
  public boolean isSparse(){return _nzChunks != null;}

  public transient volatile boolean _modified;

  public final AutoBuffer write_impl(AutoBuffer ab) {
    ab.put4(_vecStart).put4(_vecEnd);
    ab.putA4(_nzChunks);
    ab.putA(_chunks);
    return ab;
  }
  public final ChunkBlock read_impl(AutoBuffer ab){
    _vecStart = ab.get4();
    _vecEnd = ab.get4();
    _nzChunks = ab.getA4();
    Chunk [] chunks = ab.getA(Chunk.class);
    for(int i = 0; i < chunks.length; ++i)
      chunks[i].setCB(this);
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
  public Chunk getChunk(int i) {
    if(i < _vecStart || i > _vecEnd)
      throw new IndexOutOfBoundsException(_vecStart + " <= " + i + " < " + _vecEnd);
    if(isSparse()) {
      i = Arrays.binarySearch(_nzChunks,i);
      if(i < 0) return new C0DChunk(0,_chunks[0]._len).setCB(this);
    }
    return _chunks[i];
  }

  public synchronized Futures close(Futures fs){
    if(_modified) {
      DKV.put(_key,this,fs);
      _modified = false;
    }
    return fs;
  }




  public int size() {return _vecEnd - _vecStart;}


  public int len() {
    return _len;
  }
}
