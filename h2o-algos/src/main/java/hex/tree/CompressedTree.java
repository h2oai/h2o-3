package hex.tree;

import java.util.Random;

import hex.genmodel.algos.tree.SharedTreeMojoModel;
import water.*;
import water.util.IcedBitSet;
import water.util.SB;

//---------------------------------------------------------------------------
// Note: this description seems to be out-of-date
//
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
//

public class CompressedTree extends Keyed<CompressedTree> {
  final byte [] _bits;
  final int _nclass;     // Number of classes being predicted (for an integer prediction tree)
  final long _seed;
  final String[][] _domains;

  public CompressedTree(byte[] bits, int nclass, long seed, int tid, int cls, String[][] domains) {
    super(makeTreeKey(tid, cls));
    _bits = bits;
    _nclass = nclass;
    _seed = seed;
    _domains = domains;
  }

  public double score(final double row[]) {
    return SharedTreeMojoModel.scoreTree(_bits, row, _nclass, false, _domains);
  }

  public String getDecisionPath(final double row[]) {
    double d = SharedTreeMojoModel.scoreTree(_bits, row, _nclass, true, _domains);
    return SharedTreeMojoModel.getDecisionPath(d);
  }

  public Random rngForChunk(int cidx) {
    Random rand = new Random(_seed);
    for (int i = 0; i < cidx; i++) rand.nextLong();
    long seed = rand.nextLong();
    return new Random(seed);
  }

  public String toString(SharedTreeModel.SharedTreeOutput tm) {
    final String[] names = tm._names;
    final SB sb = new SB();
    new TreeVisitor<RuntimeException>(this) {
      @Override protected void pre(int col, float fcmp, IcedBitSet gcmp, int equal, int naSplitDirInt) {
        if (naSplitDirInt == DhnasdNaVsRest)
          sb.p("!Double.isNaN(" + sb.i().p(names[col]).p(")"));
        else if (naSplitDirInt == DhnasdNaLeft)
          sb.p("Double.isNaN(" + sb.i().p(names[col]).p(") || "));
        else if (equal==1)
          sb.p("!Double.isNaN(" + sb.i().p(names[col]).p(") && "));
        if (naSplitDirInt != DhnasdNaVsRest) {
          sb.i().p(names[col]).p(' ');
          if (equal == 0) sb.p("< ").p(fcmp);
          else if (equal == 1) sb.p("!=").p(fcmp);
          else sb.p("in ").p(gcmp);
        }
        sb.ii(1).nl();
      }
      @Override protected void post(int col, float fcmp, int equal) {
        sb.di(1);
      }
      @Override protected void leaf(float pred) {
        sb.i().p("return ").p(pred).nl();
      }
    }.visit();
    return sb.toString();
  }

  public static Key<CompressedTree> makeTreeKey(int treeId, int clazz) {
    return Key.makeSystem("tree_" + treeId + "_" + clazz + "_" + Key.rand());
  }

  @Override protected long checksum_impl() {
    throw new UnsupportedOperationException();
  }

}
