package water.fvec;

import water.Iced;
import water.Key;
import water.H2O;

public class Vec extends Iced {

  public Vec makeZero() { throw H2O.unimpl(); }
  public void postWrite() { throw H2O.unimpl(); }
  public int nChunks() { throw H2O.unimpl(); }
  public Key chunkKey( int cidx ) { throw H2O.unimpl(); }
  public Chunk chunkForChunkIdx( int cidx ) { throw H2O.unimpl(); }
  public VectorGroup group() { throw H2O.unimpl(); }
  public static class VectorGroup extends Iced {
    // The common shared vector group for length==1 vectors
    public static VectorGroup VG_LEN1 = new VectorGroup();
    public int reserveKeys(int n) { throw H2O.unimpl(); }
    public Key vecKey(int i) { throw H2O.unimpl(); }
  }
}
