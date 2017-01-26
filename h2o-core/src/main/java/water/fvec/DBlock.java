package water.fvec;


import water.Freezable;
import water.Iced;
import water.Key;

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



  public static class MultiChunkBlock extends DBlock  {
    final Chunk [] _cs;
    public MultiChunkBlock(Chunk [] cs){_cs = cs;}

    @Override
    public Chunk getColChunk(int c) {return _cs[c];}

    public DBlock setChunk(int i, Chunk c){
      if(i >= _cs.length)
        System.out.println("haha");
      _cs[i] = c; return this;
    }
    @Override
    public ChunkAry chunkAry(VecAry v, int cidx) {return new ChunkAry(v,cidx,_cs);}

    @Override
    public int numCols() {return _cs.length;}

    @Override
    public MultiChunkBlock subRange(int off, int to) {
      return new MultiChunkBlock(Arrays.copyOfRange(_cs,off,to));
    }

    public void removeChunks(int[] ids) {
      for(int i:ids)_cs[i] = null;
    }
  }




}


