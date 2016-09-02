package water.parser;

import water.fvec.Chunk;
import water.fvec.Vec;

/**
 * Parser data in taking data from fluid vec chunk.
 *  @author tomasnykodym
 */
public class FVecParseReader implements ParseReader {
  final Vec _vec;
  Chunk _chk;
  int _idx;
  final long _firstLine;
  private long _goffset = 0;
  public FVecParseReader(Chunk chk){
    _chk = chk;
    _idx = _chk.cidx();
    _firstLine = chk.start();
    _vec = chk.vec();
  }
  @Override public byte[] getChunkData(int cidx) {
    if(cidx != _idx)
      _chk = cidx < _vec.nChunks()?_vec.chunkForChunkIdx(_idx = cidx):null;
    if(_chk == null)
      return null;
    _goffset = _chk.start();
    return _chk.getBytes();
  }
  @Override public int  getChunkDataStart(int cidx) { return -1; }
  @Override public void setChunkDataStart(int cidx, int offset) { }
  @Override public long getGlobalByteOffset(){
    return _goffset;
  }
  /**
   * Exposes directly the underlying chunk. This function is safe to be used only
   * in implementations of Parsers that cannot be used in a streaming context.
   * Use with caution.
   * @return underlying Chunk
   */
  public Chunk getChunk() { return _chk; }
}
