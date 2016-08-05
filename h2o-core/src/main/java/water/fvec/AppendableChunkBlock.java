package water.fvec;

import water.Futures;

/**
 * Created by tomas on 8/4/16.
 */
public class AppendableChunkBlock extends AVec.AChunk<AppendableChunkBlock> {
  NewChunk [] _ncs;



  @Override
  public Chunk getChunk(int i) {throw new UnsupportedOperationException();}
  @Override
  public Chunk[] getChunks() {throw new UnsupportedOperationException();}

  @Override
  public Futures close(Futures fs) {
    return null;
  }

  @Override
  public Futures updateChunk(int chunkIdx, Chunk c, Futures fs) {
    return null;
  }


  public void addVec(int vidx, NewChunk chunk) {

  }
}
