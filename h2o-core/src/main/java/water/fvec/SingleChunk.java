package water.fvec;

import water.DKV;
import water.Futures;
import water.H2O;
import water.parser.BufferedString;

/**
 * Created by tomas on 8/3/16.
 */
public class SingleChunk extends AVec.AChunk<SingleChunk> {
  Chunk _c;

  public SingleChunk(AVec v, int cidx) {this(v,cidx,null);}
  public SingleChunk(AVec v, int cidx, Chunk c) {
    _vec = v;
    _cidx = cidx;
    _c = c;
  }
  private void setChunk(Chunk c) {
    _c = c;
    c._achunk = this;
    c._vidx = 0;
  }
  @Override
  public Chunk getChunk(int i) {
    if(i != 0) throw new ArrayIndexOutOfBoundsException(i);
    return _c;
  }

  @Override
  public Chunk[] getChunks() {
    return new Chunk[]{_c};
  }

  @Override
  public Futures close(Futures fs) {
    if(_c._chk2 != null)
      setChunk(_c._chk2 instanceof NewChunk?((NewChunk)_c._chk2).compress():_c._chk2);
    throw H2O.unimpl();
//    return fs;
  }

  @Override
  public Futures updateChunk(int chunkIdx, Chunk c, Futures fs) {
    if(chunkIdx != 0) throw new ArrayIndexOutOfBoundsException(chunkIdx);
    _c = c;
    if(_vec instanceof AppendableVec)
      ((AppendableVec) _vec).closeChunk(_cidx,c._len);
    DKV.put(_vec.chunkKey(chunkIdx),this,fs);
    return fs;
  }

}
