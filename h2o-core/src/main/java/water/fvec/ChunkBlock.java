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



  private void setChunk(int i, Chunk c) {
    c._vidx = i;
    c._achunk = this;
    _chunks[i] = c;
  }
  public void setChunks(Chunk[] chks) {
    _chunks = chks;
    for(int i = 0; i < chks.length; ++i)
      setChunk(i,chks[i]);
  }
  public ChunkBlock (){ _numCols = 0;}
  public ChunkBlock (AVec v,int cidx, int ncols) { _vec = v; _cidx = cidx; _chunks = new Chunk[ncols];}
  public ChunkBlock (AVec v,int cidx,Chunk [] chks) { _vec = v; _cidx = cidx; setChunks(chks);}
  public ChunkBlock (AVec v,int cidx, int ncols, int len, double con) {
    _vec = v; _cidx = cidx;
    Chunk [] chunks = new Chunk[ncols];
    for(int i = 0; i < _chunks.length; ++i)
      chunks[i] = new C0DChunk(con,len);
    setChunks(chunks);
  }

  public int cidx(){return _cidx;}
  public ChunkBlock(Chunk [] chunks){
    _numCols = chunks.length;
    _nzChunks = null;
    setChunks(chunks);

  }
  public ChunkBlock(int numCols, int [] nzChunks, Chunk [] chunks) {
    _numCols = numCols;
    _nzChunks = nzChunks;
    setChunks(chunks);
  }
  public boolean isSparse(){return _nzChunks != null;}

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
    setChunks(chunks);
    return this;
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

  public synchronized Futures close(Futures fs) {
    boolean modified = false;
    for(int i = 0; i < _chunks.length; ++i) {
      Chunk c = _chunks[i];
      if( c instanceof NewChunk)
        c._chk2 = c;
      if(c._chk2 != null) {
        modified = true;
        if(c._chk2 instanceof NewChunk)
          c._chk2 = ((NewChunk) c._chk2).compress();
        setChunk(i,c._chk2);
      }
    }
    if(_vec instanceof AppendableVec)
      ((AppendableVec) _vec).closeChunk(_cidx,len());
    if(modified)  DKV.put(_vb.chunkKey(_cidx),this,fs);
    return fs;
  }

  public int len() {return _chunks[0]._len;}

  @Override
  public Futures updateChunk(int chunkIdx, Chunk c, Futures fs) {
    _chunks[chunkIdx] = c;
    DKV.put(_vec.chunkKey(chunkIdx),this,fs);
    return fs;
  }

  public void resize(int newColCnt) {
    _chunks = Arrays.copyOf(_chunks,newColCnt);
    _numCols = newColCnt;
  }
}
