package hex.tree;

import java.util.Random;

import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
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

public class CompressedTree extends MemKeyed<CompressedTree> {

  private static final String KEY_PREFIX = "tree_";

  transient long _seed;

  public CompressedTree(byte[] bits, long seed, int tid, int cls) {
    super(makeTreeKey(seed, tid, cls), bits);
    _seed = seed;
  }

  @Override
  public void postReload() {
    _seed = getTreeCoords()._seed;
  }

  public double score(final double[] row, final String[][] domains) {
    return SharedTreeMojoModel.scoreTree(_mem, row, false, domains);
  }

  @Deprecated
  public String getDecisionPath(final double[] row, final String[][] domains) {
    double d = SharedTreeMojoModel.scoreTree(_mem, row, true, domains);
    return SharedTreeMojoModel.getDecisionPath(d);
  }

  public <T> T getDecisionPath(final double[] row, final String[][] domains, final SharedTreeMojoModel.DecisionPathTracker<T> tr) {
    double d = SharedTreeMojoModel.scoreTree(_mem, row, true, domains);
    return SharedTreeMojoModel.getDecisionPath(d, tr);
  }

  public SharedTreeSubgraph toSharedTreeSubgraph(final CompressedTree auxTreeInfo,
                                                 final String[] colNames, final String[][] domains) {
    TreeCoords tc = getTreeCoords();
    String treeName = SharedTreeMojoModel.treeName(tc._treeId, tc._clazz, domains[domains.length - 1]);
    return SharedTreeMojoModel.computeTreeGraph(tc._treeId, treeName, _mem, auxTreeInfo._mem, colNames, domains);
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

  private static Key<CompressedTree> makeTreeKey(long seed, int tid, int cls) {
    return Key.makeSystem("tree_" + tid + "_" + cls + "_" + seed + "_" + Key.rand());
  }
  
  /**
   * Retrieves tree coordinates in the tree ensemble
   * @return instance of TreeCoord
   */
  TreeCoords getTreeCoords() {
    return TreeCoords.parseTreeCoords(_key);
  }

  @Override protected long checksum_impl() {
    throw new UnsupportedOperationException();
  }

  static class TreeCoords {
    int _treeId;
    int _clazz;
    long _seed;

    private static TreeCoords parseTreeCoords(Key<CompressedTree> ctKey) {
      String key = ctKey.toString();
      int prefixIdx = key.indexOf(KEY_PREFIX);
      if (prefixIdx < 0)
        throw new IllegalStateException("Unexpected structure of a CompressedTree key=" + key);
      String[] keyParts = key.substring(prefixIdx + KEY_PREFIX.length()).split("_", 4);
      TreeCoords tc = new TreeCoords();
      tc._treeId = Integer.parseInt(keyParts[0]);
      tc._clazz = Integer.parseInt(keyParts[1]);
      tc._seed = Long.parseLong(keyParts[2]);
      return tc;
    }
 
  }

  public CompressedTree deepCopy() {
    TreeCoords tc = TreeCoords.parseTreeCoords(_key);
    return new CompressedTree(_mem.clone(), tc._seed, tc._treeId, tc._clazz);
  } 
  
}
