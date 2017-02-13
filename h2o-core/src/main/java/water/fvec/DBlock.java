package water.fvec;


import water.Iced;
import water.util.ArrayUtils;

/**
 * Created by tomas on 10/6/16.
 */
public abstract class DBlock extends Iced<DBlock> {
  public abstract Chunk getColChunk(int c);
  public abstract ChunkAry chunkAry(VecAry v, int cidx);
  public abstract DBlock setChunk(int i, Chunk c);
  public abstract Chunk[] chunks();
  public int numCols(){return 1;}

  public static class MultiChunkBlock extends DBlock  {
    Chunk[] _cs;
    public MultiChunkBlock(Chunk[] cs){
      assert cs.length > 1;
      _cs = cs;
    }

    public DBlock setChunk(int i, Chunk c) {
      _cs[i] = c;
      return this;
    }
    @Override
    public Chunk[] chunks() {return _cs;}

    @Override
    public Chunk getColChunk(int c) {return _cs[c];}

    @Override
    public ChunkAry chunkAry(VecAry v, int cidx) {
      return new ChunkAry(v,cidx,v._colFilter == null?_cs: ArrayUtils.select(_cs,v._colFilter));
    }
    public void removeChunks(int[] ids) {
      for(int i:ids)_cs[i] = null;
    }
    public int numCols(){return _cs.length;}
  }
}


