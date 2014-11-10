package hex.tree;

import java.util.Arrays;
import water.*;
import water.util.IcedBitSet;

// --------------------------------------------------------------------------
// Highly compressed tree encoding:
//    tree: 1B nodeType, 2B colId, 4B splitVal, left-tree-size, left, right
//    nodeType: (from lsb):
//        2 bits (1,2) skip-tree-size-size,
//        2 bits (4,8) operator flag (0 --> <, 1 --> ==, 2 --> small (4B) group, 3 --> big (var size) group),
//        1 bit  ( 16) left leaf flag,
//        1 bit  ( 32) left leaf type flag (0: subtree, 1: small cat, 2: big cat, 3: float)
//        1 bit  ( 64) right leaf flag,
//        1 bit  (128) right leaf type flag (0: subtree, 1: small cat, 2: big cat, 3: float)
//    left, right: tree | prediction
//    prediction: 4 bytes of float (or 1 or 2 bytes of class prediction)
class CompressedTree extends Keyed {
  final byte [] _bits;
  final int _nclass;            // Number of classes being predicted (for an integer prediction tree)
  final long _seed;
  public CompressedTree( byte[] bits, int nclass, long seed, int tid, int cls ) {
    super(Key.makeSystem("tree_"+tid+"_"+cls+"_"+Key.rand()));
    _bits = bits; _nclass = nclass; _seed = seed; 
  }

  /** Highly efficient (critical path) tree scoring */
  public float score( final double row[] ) {
    AutoBuffer ab = new AutoBuffer(_bits);
    IcedBitSet ibs = null;      // Lazily set on hitting first group test
    while(true) {
      int nodeType = ab.get1();
      int colId = ab.get2();
      if( colId == 65535 ) return scoreLeaf(ab);

      // boolean equal = ((nodeType&4)==4);
      int equal = (nodeType&12) >> 2;
      assert (equal >= 0 && equal <= 3): "illegal equal value " + equal+" at "+ab+" in bitpile "+Arrays.toString(_bits);

      // Extract value or group to split on
      float splitVal = -1;
      boolean grpContains = false;
      if(equal == 0 || equal == 1) { // Standard float-compare test (either < or ==)
        splitVal = ab.get4f();       // Get the float to compare
      } else {                       // Bitset test
        int off = (equal == 3) ? ab.get2() : 0; // number of zero-bits skipped during serialization
        int sz  = (equal == 3) ? ab.get2() : 4; // size of serialized bitset (part containing some non-zeros) in bytes
        if( ibs == null ) ibs = new IcedBitSet(0);
        ibs.fill(_bits,ab.position(),sz,off);
        ab.skip(sz);            // Skip inline bitset
      }

      // Compute the amount to skip.
      int lmask =  nodeType & 0x33;
      int rmask = (nodeType & 0xC0) >> 2;
      int skip = 0;
      switch(lmask) {
      case 0:  skip = ab.get1();  break;
      case 1:  skip = ab.get2();  break;
      case 2:  skip = ab.get3();  break;
      case 3:  skip = ab.get4();  break;
      case 16: skip = _nclass < 256?1:2;  break; // Small leaf
      case 48: skip = 4;          break; // skip the prediction
      default: assert false:"illegal lmask value " + lmask+" at "+ab+" in bitpile "+Arrays.toString(_bits);
      }

      // WARNING: Generated code has to be consistent with this code:
      //   - Double.NaN <  3.7f => return false => BUT left branch has to be selected (i.e., ab.position())
      //   - Double.NaN != 3.7f => return true  => left branch has to be select selected (i.e., ab.position())
      double d = row[colId];
      if( !Double.isNaN(d) ) {  // NaNs always go to bin 0
        if( ( equal==0 && ((float)d) >= splitVal) ||
            ( equal==1 && ((float)d) == splitVal) ||
            ( (equal==2 || equal==3) && ibs.contains((int)d) )) {
          ab.skip(skip);        // Skip to the right subtree
          lmask = rmask;        // And set the leaf bits into common place
        }
      } /* else Double.isNaN() is true => use left branch */
      if( (lmask&16)==16 ) return scoreLeaf(ab);
    }
  }

  private float scoreLeaf( AutoBuffer ab ) { return ab.get4f(); }

  @Override public long checksum() { throw water.H2O.fail(); }
}
