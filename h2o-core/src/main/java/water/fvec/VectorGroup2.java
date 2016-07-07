package water.fvec;

import water.*;

import java.util.Arrays;

/**
 * Created by tomas on 7/6/16.
 */
public class VectorGroup2 extends Keyed<VectorGroup2> {
  private long [] _espc;

  // todo add blocks?

  public long [] espc(){return _espc;}

  public RollupStats rollups(int vecId) {
    return null;
  }


  final private Key _placementKey;


  public VectorGroup2(){_placementKey = Key.make();}
  public VectorGroup2(Key k){_placementKey = k;}

  public H2ONode chunkHomeNode(int cidx) {
    // todo add option for explicit chunk placement
    return H2O.CLOUD._memary[(_placementKey.home_node().index() + cidx)%H2O.CLOUD.size()];
  }

  private volatile transient ChunkBlock _cache;
  public ChunkBlock chunksForChunkIdx(int cidx) {
    ChunkBlock cb = _cache;
    if(_cache != null && cb.chunkId() == cidx)
      return cb;
    throw H2O.unimpl();
//    return _cache = cb;
  }

  public Chunk chunkForChunkIdx(int vecId, int chunkId) {
    return chunksForChunkIdx(chunkId).getChunk(vecId);
  }

  public static class Vecs extends Iced {
//    public Chunk []

  }
}
