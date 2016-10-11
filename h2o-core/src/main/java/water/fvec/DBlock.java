package water.fvec;

import water.Iced;
import water.Key;
import water.Keyed;

import java.util.Arrays;

/**
 * Created by tomas on 10/6/16.
 */
public class DBlock extends Iced<DBlock> {
  int _numCols;
  Chunk [] _cs; // chunks containing the data
  int [] _ids;  // ids of non-zero chunks, null if not sparse
  public DBlock(){}
  public DBlock(Chunk [] cs, int [] id){
    _cs = cs;
    _ids = id;
  }

  public void setChunk(int i, Chunk chunk) {
    if(_ids != null){
      int j = Arrays.binarySearch(_ids,i);
      if(j >= 0){
        _cs[j] = chunk;
        return;
      }
      // else cancel sparse
      Chunk [] ncs = new Chunk[_numCols];
      for(int k = 0; k < _numCols; ++k)
        ncs[k] = new C0DChunk(0,0);
      for(int k = 0; i < _ids.length; ++k)
        ncs[_ids[k]] = _cs[k];
      _cs = ncs;
    }
    _cs[i] = chunk;
  }
}
