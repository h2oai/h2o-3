package hex.tree;

import java.util.Arrays;
import java.util.Random;

import water.*;
import water.util.IcedBitSet;
import water.util.SB;

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
public class CompressedTree extends Keyed {
  private static final int DhnasdNaVsRest = DHistogram.NASplitDir.NAvsREST.value();
  private static final int DhnasdNaLeft = DHistogram.NASplitDir.NALeft.value();
  private static final int DhnasdLeft = DHistogram.NASplitDir.Left.value();

  final byte [] _bits;
  final int _nclass;            // Number of classes being predicted (for an integer prediction tree)
  final long _seed;
  public CompressedTree( byte[] bits, int nclass, long seed, int tid, int cls ) {
    super(makeTreeKey(tid, cls));
    _bits = bits;
    _nclass = nclass;
    _seed = seed;
  }

  /**
   * Highly efficient (critical path) tree scoring
   *
   * WARNING: If you making any changes to this code, you should synchronize them with the class
   * `hex.genmodel.algos.DrfRawModel` in package `h2o_genmodel`.
   */
  public double score( final double row[]) { return score(row, false); }
  public double score( final double row[], boolean computeLeafAssignment) {
    /*
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      ZipOutputStream zos = new ZipOutputStream(bos);
      zos.putNextEntry(new ZipEntry("tree"));
      zos.write(_bits);
      zos.closeEntry();
      zos.close();
      bos.close();

      long time1 = System.nanoTime();
      ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
      ZipInputStream zis = new ZipInputStream(bis);
      ZipEntry zae = zis.getNextEntry();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      int len = 0;
      byte[] buffer = new byte[4096];
      while ((len = zis.read(buffer)) > 0) {
        out.write(buffer, 0, len);
      }
      out.close();
      long time2 = System.nanoTime();
      System.out.println("Size of input = " + _bits.length + " compressed = " + bos.size() + " uncompressed = " + out.size());
      System.out.println("Time taken = " + (time2 - time1)/1e6 + "ms");
    } catch (IOException e) {}
    */

    AutoBuffer ab = new AutoBuffer(_bits);
    IcedBitSet ibs = null;      // Lazily set on hitting first group test
    long bitsRight = 0;
    int level = 0;
    while(true) {
      int nodeType = ab.get1U();
      int colId = ab.get2();
      if (colId == 65535) return scoreLeaf(ab);
      int naSplitDir = ab.get1U();
      final boolean NaVsRest = naSplitDir == DhnasdNaVsRest;
      final boolean NaLeft = naSplitDir == DhnasdNaLeft;
      final boolean Left = naSplitDir == DhnasdLeft;
      int equal = (nodeType&12) >> 2;
      assert (equal >= 0 && equal <= 3): "illegal equal value " + equal+" at "+ab+" in bitpile "+Arrays.toString(_bits);

      float splitVal = -1;
      if (!NaVsRest) {
        // Extract value or group to split on
        if (equal == 0 || equal == 1) { // Standard float-compare test (either < or ==)
          splitVal = ab.get4f();       // Get the float to compare
        } else {                       // Bitset test
          if (ibs == null) ibs = new IcedBitSet(0);
          if (equal == 2) ibs.fill2(_bits, ab);
          else ibs.fill3(_bits, ab);
        }
      }

      // Compute the amount to skip.
      int lmask =  nodeType & 0x33;
      int rmask = (nodeType & 0xC0) >> 2;
      int skip = 0;
      switch(lmask) {
      case 0:  skip = ab.get1U();  break;
      case 1:  skip = ab.get2 ();  break;
      case 2:  skip = ab.get3 ();  break;
      case 3:  skip = ab.get4 ();  break;
      case 16: skip = _nclass < 256?1:2;  break; // Small leaf
      case 48: skip = 4;          break; // skip the prediction
      default: assert false:"illegal lmask value " + lmask+" at "+ab+" in bitpile "+Arrays.toString(_bits);
      }

      // WARNING: Generated code has to be consistent with this code:
      assert(equal!=1); //no longer supported
      double d = row[colId];
      if ((Double.isNaN(d) && !NaLeft) ||                                            // NA goes right
              !NaVsRest &&
              ( ( (equal==0            ) && d >= splitVal         ) ||  // greater or equals goes right
//                ( (equal==1            ) && d == splitVal         ) ||  // equals goes right
                ( (equal==2 || equal==3) && ibs.contains((int)d) ) )    // if contained in bitset, go right
      ) {
        // RIGHT
        if (!(Double.isNaN(d) && (NaLeft||Left))) { //missing value with NALeft or Left goes LEFT as well
          ab.skip(skip);        // Skip to the right subtree
          if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
          lmask = rmask;        // And set the leaf bits into common place
        }
      } else {
        // LEFT
        assert(!Double.isNaN(d) || NaLeft || Left);
      }
      level++;
      if( (lmask&16)==16 ) {
        if (computeLeafAssignment) {
          bitsRight |= 1 << level; //mark the end of the tree
          return Double.longBitsToDouble(bitsRight);
        }
        return scoreLeaf(ab);
      }
    }
  }

  public String getDecisionPath(final double row[] ) {
    double d = score(row, true);
    long l = Double.doubleToRawLongBits(d);
    StringBuilder sb = new StringBuilder();
    int pos=0;
    for (int i=0;i<64;++i) {
      long right = (l>>i)&0x1L;
      sb.append(right==1? "R" : "L");
      if (right==1) pos=i;
    }
    return sb.substring(0, pos);
  }

  private float scoreLeaf( AutoBuffer ab ) { return ab.get4f(); }

  public Random rngForChunk( int cidx ) {
    Random rand = new Random(_seed);
    for( int i=0; i<cidx; i++ ) rand.nextLong();
    long seed = rand.nextLong();
    return new Random(seed);
  }

  @Override protected long checksum_impl() { throw water.H2O.fail(); }

  public String toString( SharedTreeModel.SharedTreeOutput tm ) {
    final String[] names = tm._names;
    final SB sb = new SB();
    new TreeVisitor<RuntimeException>(this) {
      @Override protected void pre(int col, float fcmp, IcedBitSet gcmp, int equal, int naSplitDirInt) {
        if (naSplitDirInt == DhnasdNaVsRest)
          sb.p("!Double.isNaN("+sb.i().p(names[col]).p(")"));
        else if (naSplitDirInt == DhnasdNaLeft)
          sb.p("Double.isNaN("+sb.i().p(names[col]).p(") || "));
        else if (equal==1)
          sb.p("!Double.isNaN("+sb.i().p(names[col]).p(") && "));
        if (naSplitDirInt != DhnasdNaVsRest) {
          sb.i().p(names[col]).p(' ');
          if (equal == 0) sb.p("< ").p(fcmp);
          else if (equal == 1) sb.p("!=").p(fcmp);
          else sb.p("in ").p(gcmp);
        }
        sb.ii(1).nl();
      }
      @Override protected void post( int col, float fcmp, int equal ) { sb.di(1); }
      @Override protected void leaf( float pred ) { sb.i().p("return ").p(pred).nl(); }
    }.visit();
    return sb.toString();
  }

  public static Key<CompressedTree> makeTreeKey(int treeId, int clazz) {
    return Key.makeSystem("tree_" + treeId + "_" + clazz + "_" + Key.rand());
  }
}
