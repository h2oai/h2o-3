package water.fvec;

import water.AutoBuffer;
import water.MemoryManager;
import water.util.UnsafeUtils;
import java.util.HashSet;

/**
 * The "few unique doubles"-compression function
 */
public class CUDChunk extends Chunk {
  public static int MAX_UNIQUES=256;
  public static int computeByteSize(int uniques, int len) {
    return 4 + 4 // _len + numUniques
            + (uniques << 3) //unique double values
            + (len << 1); //mapping of row -> unique value index (0...255)
  }
  int numUniques;
  CUDChunk() {}
  CUDChunk(byte[] bs, HashSet<Double> hs, int len) {
    numUniques = hs.size();
    set_len(len);
    _mem = MemoryManager.malloc1(computeByteSize(numUniques, _len), false);
    UnsafeUtils.set4(_mem, 0, _len);
    UnsafeUtils.set4(_mem, 4, numUniques);
    int j=0;
    for (Double d : hs)
      UnsafeUtils.set8d(_mem, 8 + (j++ << 3), d);
    for (int i=0; i<len; ++i) {
      double d = UnsafeUtils.get8d(bs, i << 3);
      int pos = -1;
      for (j=0; j<numUniques; ++j) //binary search not needed for now
        if (Double.compare(d, UnsafeUtils.get8d(_mem, 8 + (j << 3))) == 0)
          pos = j;
      assert(pos >= 0);
      assert((byte)pos==pos);
      UnsafeUtils.set1(_mem, 8 + (numUniques << 3) + i, (byte)pos);
    }
    _start = -1;
  }
  @Override protected final long   at8_impl( int i ) {
    double res = atd_impl(i);
    if( Double.isNaN(res) ) throw new IllegalArgumentException("at8_impl but value is missing");
    return (long)res;
  }
  @Override protected final double   atd_impl( int i ) {
    int whichUnique = (int)UnsafeUtils.get1(_mem, 8 + (numUniques << 3) + i);
    return UnsafeUtils.get8d(_mem, 8 + (whichUnique << 3));
  }
  @Override protected final boolean isNA_impl( int i ) { return Double.isNaN(atd_impl(i)); }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) {
    for (int j = 0; j < numUniques; ++j) {
      if (Double.compare(d, UnsafeUtils.get8d(_mem, 8 + (j << 3))) == 0) {
        UnsafeUtils.set1(_mem, 8 + (numUniques << 3) + i, (byte) j);
        return true;
      }
    }
    return false;
  }
  @Override boolean set_impl(int i, float f ) {
    return set_impl(i, (double)f);
  }
  @Override boolean setNA_impl(int idx) {
    return set_impl(idx, Double.NaN);
  }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.alloc_doubles(_len);
    for( int i=0; i< _len; i++ )
      nc.doubles()[i] = atd_impl(i);
    nc.set_sparseLen(nc.set_len(_len));
    return nc;
  }
  @Override public CUDChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    _len = UnsafeUtils.get4(_mem, 0);
    numUniques = UnsafeUtils.get4(_mem, 4);
    set_len(_len);
    return this;
  }
}
