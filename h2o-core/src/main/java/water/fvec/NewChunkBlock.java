package water.fvec;

import water.DKV;
import water.Futures;
import water.H2O;
import water.parser.BufferedString;

/**
 * Created by tomas on 7/13/16.
 */
public class NewChunkBlock extends ChunkBlock {
  NewChunk [] _nchks; // TODO temporary solution

  @Override
  public NewChunk getChunk(int i) {return _nchks[i];}

  public NewChunkBlock(AppendableVecBlock appendableVecBlock, int cidx) {
    throw H2O.unimpl(); // TODO
  }

  public void addNum(int vecId, long mantissa, int exponent){
    _nchks[vecId].addNum(mantissa,exponent);
  }
  public void addNum(int vecId, double dval){
    _nchks[vecId].addNum(dval);
  }
  public void addStr(int vecId, BufferedString str) {
    _nchks[vecId].addStr(str);
  }
  public void addZeros(int vecId, int nzs){
    _nchks[vecId].addZeros(nzs);
  }
  public void addNAs(int vecId, int nas){
    _nchks[vecId].addNAs(nas);
  }
  // Append a UUID, stored in _ls & _ds
  public void addUUID( int vecId, long lo, long hi ) {
    _nchks[vecId].addUUID(lo,hi);
  }
  public void addUUID( int vecId, Chunk c, long row ) {
    if( c.isNA_abs(row) ) addUUID(vecId,C16Chunk._LO_NA,C16Chunk._HI_NA);
    else addUUID(vecId,c.at16l_abs(row),c.at16h_abs(row));
  }
  public void addUUID( int vecId, Chunk c, int row ) {
    if( c.isNA(row) ) addUUID(vecId,C16Chunk._LO_NA,C16Chunk._HI_NA);
    else addUUID(vecId,c.at16l(row),c.at16h(row));
  }

  @Override
  public Futures close(Futures fs ) {
    for(int i = 0; i < _chunks.length; ++i)
      _chunks[i] = ((NewChunk)_chunks[i]).compress();
    DKV.put(_key,this,fs);
    return fs;
  }

}
