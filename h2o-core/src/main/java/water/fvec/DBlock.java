package water.fvec;


import water.Freezable;
import water.Iced;
import water.Key;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * Created by tomas on 10/6/16.
 */
public abstract class DBlock extends Iced<DBlock> {
  public boolean isColumnBased() {return true;}
  public abstract Chunk getColChunk(int c);
  public abstract ChunkAry chunkAry(VecAry v, int cidx);

  public abstract int numCols();
  public abstract DBlock subRange(int off, int to);
  public abstract DBlock setChunk(int i, Chunk c);

  public abstract Chunk[] chunks();
  public abstract int[] ids();

  public abstract int sparseCols();


  public static class MultiChunkBlock extends DBlock  {
    Chunk [] _cs;
    int [] _ids;
    int _numCols;
    public MultiChunkBlock(Chunk [] cs){
      _numCols = cs.length;
      assert _numCols > 1;
      int zs = 0;
      for(int i = 0; i <cs.length; ++i)
        if(cs[i].sparseLenZero() == 0)
          zs++;
      if (zs*4 >= cs.length){ // at most 1/4 nz chunks => keep it sparse
        Chunk [] sparseChks = new Chunk[cs.length-zs];
        int [] ids = new int[sparseChks.length];
        int j = 0;
        for(int i = 0; i <cs.length; ++i) {
          if (cs[i].sparseLenZero() != 0) {
            sparseChks[j] = cs[i];
            ids[j] = i;
            j++;
          }
        }
        _cs = sparseChks;
        _ids = ids;
      } else _cs = cs;
    }

    public DBlock setChunk(int i, Chunk c){
      if(_ids == null) {
        _cs[i] = c;
        return this;
      }
      int id = Arrays.binarySearch(_ids,i);
      if(id >= 0){
        _cs[id] = c;
        return this;
      }
      // make dense
      Chunk [] cs = new Chunk[_numCols];
      int k = 0;
      for(int j = 0; j < cs.length; ++j){
        if(k < _ids.length && j == _ids[k]){
          cs[j] = _cs[k++];
        } else cs[j] = new C0DChunk(0,0);
      }
      cs[i] = c;
      _cs = cs;
      _ids = null;
      return this;
    }

    @Override
    public Chunk[] chunks() {return _cs;}
    @Override
    public int[] ids() {return _ids;}

    @Override
    public Chunk getColChunk(int c) {
      if(_ids == null) return _cs[c];
      c = Arrays.binarySearch(_ids,c);
      if(c >= 0) return _cs[c];
      return new C0DChunk(0,0);
    }

    @Override
    public ChunkAry chunkAry(VecAry v, int cidx) {
      return new ChunkAry(v,cidx,v._colFilter == null?_cs: ArrayUtils.select(_cs,v._colFilter),_ids);
    }

    @Override
    public int numCols() {return _cs.length;}

    @Override
    public MultiChunkBlock subRange(int off, int to) {
      return new MultiChunkBlock(Arrays.copyOfRange(_cs,off,to));
    }

    public void removeChunks(int[] ids) {
      for(int i:ids)_cs[i] = null;
    }
    public int sparseCols(){return _ids == null?_cs.length:_ids.length;}


    public Chunk[] getDenseChunks(int numRows, int numCols) {
      if(_ids == null) return _cs;
      Chunk [] res = new Chunk[numCols];
      int j = 0;
      for(int i = 0; i < res.length; ++i) {
        if(j < _ids.length && i == _ids[j]){
          res[i] = _cs[j++];
        } else res[i] = new C0DChunk(0,numRows);
      }
      return res;
    }
  }




}


