package water.fvec;

import water.*;
import water.util.ArrayUtils;
import java.util.Arrays;


/**
 * Created by tomas on 10/5/16.
 */
public class VecAry extends Vec {
  int [] _vecIds;
  int [] _colFilter; // take only cols present in the filter
  boolean _invertedFilter; // take only cols not in the filter
  int [] _permutation; // permute the order

  private transient Vec [] _vecs;

  private Vec[] fetchVecs(){
    Vec [] vecs = _vecs;
    if(_vecs == null) {
      vecs = new Vec[_vecIds.length];
      for(int i = 0; i < vecs.length; ++i)
        vecs[i] = getVec(i);
      _vecs = vecs;
    }
    return vecs;
  }

  public VecAry(Key<Vec> key, int rowLayout) {
    super(key, rowLayout);
  }

  Vec getVec(int i){
    Key k = keyTemplate();
    k._kb[0] = Key.VEC;
    Vec.setVecId(k,_vecIds[i]);
    return DKV.getGet(k);
  }



  @Override
  public ChunkAry chunkForChunkIdx(int cidx) {
    fetchVecs();
    Key k = chunkKey(cidx);
    DBlock [] blocks = new DBlock[_vecIds.length];
    Chunk [] cs = new Chunk[0];
    int [] ids = null;
    int s = 0;
    int fid = 0; // idx into the filter array
    for(int i = 0; i < _vecIds.length;++i) {
      DBlock db = blocks[i] = DKV.getGet(Vec.setVecId(k,_vecIds[i]));
      int n = _vecs[i].numCols();
      if(db._ids != null && ids == null) // switch to sparse
        ids = ArrayUtils.seq(0,s);
      Chunk [] csx = db._cs;
      if(_colFilter != null) {
        int x = fid;
        while(x < _colFilter.length && _colFilter[x] < s)
          x++;
        if(!_invertedFilter){
          if(x - fid < n){ // only take subset
            csx = new Chunk[x - fid];
            for(int y = fid; y < x; y++)
              csx[y-fid] = db._cs[_colFilter[y]-s];
          }
        } else if(x != fid) { // got some ignored columns
          csx = new Chunk[n - x + fid];
          int y = fid;
          for(int z = 0; z < n; ++z){
            if(s + z == _colFilter[y])y++;
            else  csx[z-y+fid] = db._cs[z];
          }
        }
        fid = x;
      }
      cs = ArrayUtils.join(cs,csx);
      s += _vecs[i].numCols();
    }
    // now apply permutation if needed
    if(_permutation != null) {
      Chunk [] cs2 = cs.clone();
      for(int i = 0; i < _permutation.length; ++i)
        cs2[i] = cs[_permutation[i]];
      cs = cs2;
    }
    return new ChunkAry(this,cidx,cs,null);
  }

  @Override // update changed blocks, need to reverse the mapping
  Futures closeChunk(int cidx, ChunkAry c, Futures fs) {
    // columns changed
    Vec [] vecs = fetchVecs();
    int nextId = 0;
    int vecId = 0;
    int nsum = 0;
    DBlock db = null;
    int [] changedCols = c.changedCols();
    int [] rperm = null;
    if(_permutation != null){
      for(int i = 0; i < _permutation.length; ++i)
        rperm[_permutation[i]] = i;
    }
    for(int i:changedCols) {
      int id = _permutation == null?i:rperm[i];
      if(_colFilter != null){
        if(_invertedFilter){
          int j = Arrays.binarySearch(_colFilter,id);
          id += j < 0?-j-1:j;
        } else id = _colFilter[id];
      }
      while(id >= nextId){
        nsum = nextId;
        db = DKV.getGet(vecs[vecId].chunkKey(cidx));
        nextId += vecs[vecId++].numCols();
      }
      db.setChunk(id - nsum, c.getChunk(i));
    }
    if(db != null)
      DKV.put(vecs[vecId].newChunkKey(cidx),db,fs);
    // need to update changed DBlocks
    return fs;
  }

}
