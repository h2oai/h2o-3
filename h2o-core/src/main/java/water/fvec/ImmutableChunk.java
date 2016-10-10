package water.fvec;

/**
 * A chunk having a special feature - it's immutable. So all setters are failing.
 * More, assuning we have a delegate, isNA checks
 * Good for delegation.
 */
public abstract class ImmutableChunk extends Chunk {
  @Override public boolean set_impl(int idx, long l)   { return false; }
  @Override public boolean set_impl(int idx, double d) { return false; }
  @Override public boolean set_impl(int idx, float f)  { return false; }
  @Override public boolean setNA_impl(int idx)         { return false; }

  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(nc.set_len(0));
    for( int i=0; i< _len; i++ )
      if( isNA(i) ) nc.addNA();
      else          nc.addNum(atd(i));
    return nc;
  }

  @Override protected final void initFromBytes () { throw water.H2O.fail(); }
  @Override public boolean isNA_impl(int idx) { return Double.isNaN(atd_impl(idx)); }  // ouch, not quick! runs thru atd_impl
}
