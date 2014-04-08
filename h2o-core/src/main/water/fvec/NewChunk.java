package water.fvec;

import water.Futures;
import water.H2O;

// An uncompressed chunk of data, supporting an append operation
public class NewChunk extends Chunk {
  int _xs[];
  long _ls[];
  NewChunk( Chunk old ) { throw H2O.unimpl(); }
  NewChunk( AppendableVec av, int cidx ) { throw H2O.unimpl(); }
  void addNum(long elem) { throw H2O.unimpl(); }
  void addNum(double elem) { throw H2O.unimpl(); }
  protected Chunk new_close() { throw H2O.unimpl(); }
  protected double   atd_impl(int idx) { throw H2O.unimpl(); }
  protected long     at8_impl(int idx) { throw H2O.unimpl(); }
  protected boolean isNA_impl(int idx) { throw H2O.unimpl(); }
  boolean set_impl  (int idx, long l ) { throw H2O.unimpl(); }
  boolean set_impl  (int idx, double d ) { throw H2O.unimpl(); }
  boolean set_impl  (int idx, float f ) { throw H2O.unimpl(); }
  boolean setNA_impl(int idx) { throw H2O.unimpl(); }
  NewChunk inflate_impl(NewChunk nc) { throw H2O.fail(); }
}
