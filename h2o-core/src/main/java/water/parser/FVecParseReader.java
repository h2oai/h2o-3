package water.parser;

import water.fvec.Chunks;
import water.fvec.Vec;
import water.fvec.Chunk;

/**
 * Parser data in taking data from fluid vec chunk.
 *  @author tomasnykodym
 */
public class FVecParseReader implements ParseReader {
  final Vec _vec;
  Chunks _chk;
  int _idx;
  final long _firstLine;
  private long _goffset = 0;
  public FVecParseReader(Chunks chk){
    _chk = chk;
    _idx = _chk.cidx();
    _firstLine = chk.start();
    _vec = chk.vec(0);
  }
  @Override public byte[] getChunkData(int cidx) {
    if(cidx != _idx)
      _chk = cidx < _vec.nChunks()?_vec.chunkForChunkIdx(_idx = cidx):null;
    if(_chk == null)
      return null;
    _goffset = _chk.start();
    return _chk.getChunk(0).getBytes();
  }
  @Override public int  getChunkDataStart(int cidx) { return -1; }
  @Override public void setChunkDataStart(int cidx, int offset) { }
  @Override public long getGlobalByteOffset(){
    return _goffset;
  }
}
