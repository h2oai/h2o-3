package hex.genmodel.algos.tree;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.GenmodelBitSet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Common ancestor for {@link DrfMojoModel} and {@link GbmMojoModel}.
 * See also: `hex.tree.SharedTreeModel` and `hex.tree.TreeVisitor` classes.
 */
public abstract class SharedTreeMojoModel extends MojoModel implements SharedTreeGraphConverter{
    private static final int NsdNaVsRest = NaSplitDir.NAvsREST.value();
    private static final int NsdNaLeft = NaSplitDir.NALeft.value();
    private static final int NsdLeft = NaSplitDir.Left.value();
    
    private ScoreTree _scoreTree;

    /**
     * {@code _ntree_groups} is the number of trees requested by the user. For
     * binomial case or regression this is also the total number of trees
     * trained; however in multinomial case each requested "tree" is actually
     * represented as a group of trees, with {@code _ntrees_per_group} trees
     * in each group. Each of these individual trees assesses the likelihood
     * that a given observation belongs to class A, B, C, etc. of a
     * multiclass response.
     */
    protected int _ntree_groups;
    protected int _ntrees_per_group;
    /**
     * Array of binary tree data, each tree being a {@code byte[]} array. The
     * trees are logically grouped into a rectangular grid of dimensions
     * {@link #_ntree_groups} x {@link #_ntrees_per_group}, however physically
     * they are stored as 1-dimensional list, and an {@code [i, j]} logical
     * tree is mapped to the index {@link #treeIndex(int, int)}.
     */
    protected byte[][] _compressed_trees;

    /**
     * Array of auxiliary binary tree data, each being a {@code byte[]} array.
     */
    protected byte[][] _compressed_trees_aux;

    /**
     * GLM's beta used for calibrating output probabilities using Platt Scaling.
     */
    protected double[] _calib_glm_beta;


    protected void postInit() {
      if (_mojo_version == 1.0) {
        _scoreTree = new ScoreTree0(); // First version
      } else if (_mojo_version == 1.1) {
        _scoreTree = new ScoreTree1(); // Second version
      } else
        _scoreTree = new ScoreTree2(); // Current version
    }

    public final int getNTreeGroups() {
      return _ntree_groups;
    }

    public final int getNTreesPerGroup() {
      return _ntrees_per_group;
    }


    /**
     * @deprecated use {@link #scoreTree0(byte[], double[], boolean)} instead.
     */
    @Deprecated
    public static double scoreTree0(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
      // note that nclasses is ignored (and in fact, always was)
      return scoreTree0(tree, row, computeLeafAssignment);
    }

    /**
     * @deprecated use {@link #scoreTree1(byte[], double[], boolean)} instead.
     */
    @Deprecated
    public static double scoreTree1(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
      // note that nclasses is ignored (and in fact, always was)
      return scoreTree1(tree, row, computeLeafAssignment);
    }

    /**
     * @deprecated use {@link #scoreTree(byte[], double[], boolean, String[][])} instead.
     */
    @Deprecated
    public static double scoreTree(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment, String[][] domains) {
      // note that {@link nclasses} is ignored (and in fact, always was)
      return scoreTree(tree, row, computeLeafAssignment, domains);
    }

  /**
   * Highly efficient (critical path) tree scoring
   *
   * Given a tree (in the form of a byte array) and the row of input data, compute either this tree's
   * predicted value when `computeLeafAssignment` is false, or the the decision path within the tree (but no more
   * than 64 levels) when `computeLeafAssignment` is true.
   *
   * Note: this function is also used from the `hex.tree.CompressedTree` class in `h2o-algos` project.
   */
  @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
    public static double scoreTree(byte[] tree, double[] row, boolean computeLeafAssignment, String[][] domains) {
        ByteBufferWrapper ab = new ByteBufferWrapper(tree);
        GenmodelBitSet bs = null;
        long bitsRight = 0;
        int level = 0;
        while (true) {
            int nodeType = ab.get1U();
            int colId = ab.get2();
            if (colId == 65535) {
              if (computeLeafAssignment) {
                bitsRight |= 1 << level;  // mark the end of the tree
                return Double.longBitsToDouble(bitsRight);
              } else {
                return ab.get4f();
              }
            }
            int naSplitDir = ab.get1U();
            boolean naVsRest = naSplitDir == NsdNaVsRest;
            boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
            int lmask = (nodeType & 51);
            int equal = (nodeType & 12);  // Can be one of 0, 8, 12
            assert equal != 4;  // no longer supported

            float splitVal = -1;
            if (!naVsRest) {
                // Extract value or group to split on
                if (equal == 0) {
                    // Standard float-compare test (either < or ==)
                    splitVal = ab.get4f();  // Get the float to compare
                } else {
                    // Bitset test
                    if (bs == null) bs = new GenmodelBitSet(0);
                    if (equal == 8)
                        bs.fill2(tree, ab);
                    else
                        bs.fill3(tree, ab);
                }
            }

          // This logic:
          //
          //        double d = row[colId];
          //        if (Double.isNaN(d) || ( equal != 0 && bs != null && !bs.isInRange((int)d) ) || (domains != null && domains[colId] != null && domains[colId].length <= (int)d)
          //              ? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains((int)d))) {

          // Really does this:
          //
          //        if (value is NaN or value is not in the range of the bitset or is outside the domain map length (but an integer) ) {
          //            if (leftward) {
          //                go left
          //            }
          //            else {
          //                go right
          //            }
          //        }
          //        else {
          //            if (naVsRest) {
          //                go left
          //            }
          //            else {
          //                if (numeric) {
          //                    if (value < split value) {
          //                        go left
          //                    }
          //                    else {
          //                        go right
          //                    }
          //                }
          //                else {
          //                    if (value not in bitset) {
          //                        go left
          //                    }
          //                    else {
          //                        go right
          //                    }
          //                }
          //            }
          //        }

            double d = row[colId];
            if (Double.isNaN(d) || ( equal != 0 && bs != null && !bs.isInRange((int)d) ) || (domains != null && domains[colId] != null && domains[colId].length <= (int)d)
                    ? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains((int)d))) {
                // go RIGHT
                switch (lmask) {
                    case 0:  ab.skip(ab.get1U());  break;
                    case 1:  ab.skip(ab.get2());  break;
                    case 2:  ab.skip(ab.get3());  break;
                    case 3:  ab.skip(ab.get4());  break;
                    case 48: ab.skip(4);  break;  // skip the prediction
                    default:
                        assert false : "illegal lmask value " + lmask + " in tree " + Arrays.toString(tree);
                }
                if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
                lmask = (nodeType & 0xC0) >> 2;  // Replace leftmask with the rightmask
            } else {
                // go LEFT
                if (lmask <= 3)
                    ab.skip(lmask + 1);
            }

            level++;
            if ((lmask & 16) != 0) {
                if (computeLeafAssignment) {
                    bitsRight |= 1 << level;  // mark the end of the tree
                    return Double.longBitsToDouble(bitsRight);
                } else {
                    return ab.get4f();
                }
            }
        }
    }

    public interface DecisionPathTracker<T> {
        boolean go(int depth, boolean right);
        T terminate();
    }

    public static class StringDecisionPathTracker implements DecisionPathTracker<String> {
        private final char[] _sb = new char[64];
        private int _pos = 0;
        @Override
        public boolean go(int depth, boolean right) {
            _sb[depth] = right ? 'R' : 'L';
            if (right) _pos = depth;
            return true;
        }
        @Override
        public String terminate() {
            String path = new String(_sb, 0, _pos);
            _pos = 0;
            return path;
        }
    }

    public static class LeafDecisionPathTracker implements DecisionPathTracker<LeafDecisionPathTracker> {
        private final AuxInfoLightReader _auxInfo;
        private boolean _wentRight = false; // Was the last step _right_?

        // OUT
        private int _nodeId = 0; // Returned when the tree is empty (consistent with SharedTreeNode of an empty tree)

        private LeafDecisionPathTracker(byte[] auxTree) {
          _auxInfo = new AuxInfoLightReader(new ByteBufferWrapper(auxTree));
        }

        @Override
        public boolean go(int depth, boolean right) {
          if (!_auxInfo.hasNext()) {
            assert _wentRight || depth == 0; // this can only happen if previous step was _right_ or the tree has no nodes
            return false;
          }
          _auxInfo.readNext();
          if (right) {
            if (_wentRight && _nodeId != _auxInfo._nid)
              return false;
            _nodeId = _auxInfo.getRightNodeIdAndSkipNode();
            _auxInfo.skipNodes(_auxInfo._numLeftChildren);
            _wentRight = true;
          } else { // left
            _wentRight = false;
            if (_auxInfo._numLeftChildren == 0) {
              _nodeId = _auxInfo.getLeftNodeIdAndSkipNode();
              return false;
            } else {
              _auxInfo.skipNode(); // proceed to next _left_ node
            }
          }
          return true;
        }

        @Override
        public LeafDecisionPathTracker terminate() {
          return this;
        }

        final int getLeafNodeId() {
          return _nodeId;
        }
    }

    public static <T> T getDecisionPath(double leafAssignment, DecisionPathTracker<T> tr) {
        long l = Double.doubleToRawLongBits(leafAssignment);
        for (int i = 0; i < 64; ++i) {
            boolean right = ((l>>i) & 0x1L) == 1;
            if (! tr.go(i, right)) break;
        }
        return tr.terminate();
    }

    public static String getDecisionPath(double leafAssignment) {
        return getDecisionPath(leafAssignment, new StringDecisionPathTracker());
    }

    public static int getLeafNodeId(double leafAssignment, byte[] auxTree) {
        LeafDecisionPathTracker tr = new LeafDecisionPathTracker(auxTree);
        return getDecisionPath(leafAssignment, tr).getLeafNodeId();
    }

    //------------------------------------------------------------------------------------------------------------------
    // Computing a Tree Graph
    //------------------------------------------------------------------------------------------------------------------

    private static void computeTreeGraph(SharedTreeSubgraph sg, SharedTreeNode node, byte[] tree, ByteBufferWrapper ab, HashMap<Integer, AuxInfo> auxMap,
                                         String names[], String[][] domains) {
        int nodeType = ab.get1U();
        int colId = ab.get2();
        if (colId == 65535) {
            float leafValue = ab.get4f();
            node.setPredValue(leafValue);
            return;
        }
        String colName = names[colId];
        node.setCol(colId, colName);

        int naSplitDir = ab.get1U();
        boolean naVsRest = naSplitDir == NsdNaVsRest;
        boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
        node.setLeftward(leftward);
        node.setNaVsRest(naVsRest);

        int lmask = (nodeType & 51);
        int equal = (nodeType & 12);  // Can be one of 0, 8, 12
        assert equal != 4;  // no longer supported

        if (!naVsRest) {
            // Extract value or group to split on
            if (equal == 0) {
                // Standard float-compare test (either < or ==)
                float splitVal = ab.get4f();  // Get the float to compare
                node.setSplitValue(splitVal);
            } else {
                // Bitset test
                GenmodelBitSet bs = new GenmodelBitSet(0);
                if (equal == 8)
                    bs.fill2(tree, ab);
                else
                    bs.fill3(tree, ab);
                node.setBitset(domains[colId], bs);
            }
        }

        AuxInfo auxInfo = auxMap.get(node.getNodeNumber());

        // go RIGHT
        {
            ByteBufferWrapper ab2 = new ByteBufferWrapper(tree);
            ab2.skip(ab.position());

            switch (lmask) {
                case 0:
                    ab2.skip(ab2.get1U());
                    break;
                case 1:
                    ab2.skip(ab2.get2());
                    break;
                case 2:
                    ab2.skip(ab2.get3());
                    break;
                case 3:
                    ab2.skip(ab2.get4());
                    break;
                case 48:
                    ab2.skip(4);
                    break;  // skip the prediction
                default:
                    assert false : "illegal lmask value " + lmask + " in tree " + Arrays.toString(tree);
            }
            int lmask2 = (nodeType & 0xC0) >> 2;  // Replace leftmask with the rightmask

            SharedTreeNode newNode = sg.makeRightChildNode(node);
            newNode.setWeight(auxInfo.weightR);
            newNode.setNodeNumber(auxInfo.nidR);
            newNode.setPredValue(auxInfo.predR);
            newNode.setSquaredError(auxInfo.sqErrR);
            if ((lmask2 & 16) != 0) {
                float leafValue = ab2.get4f();
                newNode.setPredValue(leafValue);
                auxInfo.predR = leafValue;
            }
            else {
                computeTreeGraph(sg, newNode, tree, ab2, auxMap, names, domains);
            }
        }

        // go LEFT
        {
            ByteBufferWrapper ab2 = new ByteBufferWrapper(tree);
            ab2.skip(ab.position());

            if (lmask <= 3)
                ab2.skip(lmask + 1);

            SharedTreeNode newNode = sg.makeLeftChildNode(node);
            newNode.setWeight(auxInfo.weightL);
            newNode.setNodeNumber(auxInfo.nidL);
            newNode.setPredValue(auxInfo.predL);
            newNode.setSquaredError(auxInfo.sqErrL);
            if ((lmask & 16) != 0) {
                float leafValue = ab2.get4f();
                newNode.setPredValue(leafValue);
                auxInfo.predL = leafValue;
            }
            else {
                computeTreeGraph(sg, newNode, tree, ab2, auxMap, names, domains);
            }
        }
        if (node.getNodeNumber() == 0) {
          float p = (float)(((double)auxInfo.predL*(double)auxInfo.weightL + (double)auxInfo.predR*(double)auxInfo.weightR)/((double)auxInfo.weightL + (double)auxInfo.weightR));
          if (Math.abs(p) < 1e-7) p = 0;
          node.setPredValue(p);
          node.setSquaredError(auxInfo.sqErrR + auxInfo.sqErrL);
          node.setWeight(auxInfo.weightL + auxInfo.weightR);
        }
        checkConsistency(auxInfo, node);
    }

    /**
     * Compute a graph of the forest.
     *
     * @return A graph of the forest.
     */
    public SharedTreeGraph _computeGraph(int treeToPrint) {
        SharedTreeGraph g = new SharedTreeGraph();

        if (treeToPrint >= _ntree_groups) {
            throw new IllegalArgumentException("Tree " + treeToPrint + " does not exist (max " + _ntree_groups + ")");
        }

        int j;
        if (treeToPrint >= 0) {
            j = treeToPrint;
        }
        else {
            j = 0;
        }

        for (; j < _ntree_groups; j++) {
            for (int i = 0; i < _ntrees_per_group; i++) {
                int itree = treeIndex(j, i);
                String[] domainValues = isSupervised() ? getDomainValues(getResponseIdx()) : null;
                String treeName = treeName(j, i, domainValues);
                SharedTreeSubgraph sg = g.makeSubgraph(treeName);
                computeTreeGraph(sg, _compressed_trees[itree], _compressed_trees_aux[itree],
                        getNames(), getDomainValues());
            }

            if (treeToPrint >= 0) {
                break;
            }
        }

        return g;
    }

    public static SharedTreeSubgraph computeTreeGraph(int treeNum, String treeName, byte[] tree, byte[] auxTreeInfo,
                                                      String names[], String[][] domains) {
      SharedTreeSubgraph sg = new SharedTreeSubgraph(treeNum, treeName);
      computeTreeGraph(sg, tree, auxTreeInfo, names, domains);
      return sg;
    }

    private static void computeTreeGraph(SharedTreeSubgraph sg, byte[] tree, byte[] auxTreeInfo,
                                         String names[], String[][] domains) {
      SharedTreeNode node = sg.makeRootNode();
      node.setSquaredError(Float.NaN);
      node.setPredValue(Float.NaN);
      ByteBufferWrapper ab = new ByteBufferWrapper(tree);
      ByteBufferWrapper abAux = new ByteBufferWrapper(auxTreeInfo);
      HashMap<Integer, AuxInfo> auxMap = readAuxInfos(abAux);
      computeTreeGraph(sg, node, tree, ab, auxMap, names, domains);
    }

    private static HashMap<Integer, AuxInfo> readAuxInfos(ByteBufferWrapper abAux) {
      HashMap<Integer, AuxInfo> auxMap = new HashMap<>();
      Map<Integer, AuxInfo> nodeIdToParent = new HashMap<>();
      nodeIdToParent.put(0, new AuxInfo());
      boolean reservedFieldIsParentId = false; // In older H2O versions `reserved` field was used for parent id
      while (abAux.hasRemaining()) {
        AuxInfo auxInfo = new AuxInfo(abAux);
        if (auxMap.size() == 0) {
          reservedFieldIsParentId = auxInfo.reserved < 0; // `-1` indicates No Parent, reserved >= 0 indicates reserved is not used for parent ids!
        }
        AuxInfo parent = nodeIdToParent.get(auxInfo.nid);
        if (parent == null)
          throw new IllegalStateException("Parent for nodeId=" + auxInfo.nid + " not found.");
        assert !reservedFieldIsParentId || parent.nid == auxInfo.reserved : "Corrupted Tree Info: parent nodes do not correspond (pid: " +
                parent.nid + ", reserved: " + auxInfo.reserved + ")";
        auxInfo.setPid(parent.nid);
        nodeIdToParent.put(auxInfo.nidL, auxInfo);
        nodeIdToParent.put(auxInfo.nidR, auxInfo);
        auxMap.put(auxInfo.nid, auxInfo);
      }
      return auxMap;
    }

    public static String treeName(int groupIndex, int classIndex, String[] domainValues) {
      String className = "";
      {
        if (domainValues != null) {
          className = ", Class " + domainValues[classIndex];
        }
      }
      return "Tree " + groupIndex + className;
    }

    // Please see AuxInfo for details of the serialized format
    private static class AuxInfoLightReader {
      private final ByteBufferWrapper _abAux;
      int _nid;
      int _numLeftChildren;

      private AuxInfoLightReader(ByteBufferWrapper abAux) {
        _abAux = abAux;
      }

      private void readNext() {
        _nid = _abAux.get4();
        _numLeftChildren = _abAux.get4();
      }

      private boolean hasNext() {
        return _abAux.hasRemaining();
      }

      private int getLeftNodeIdAndSkipNode() {
        _abAux.skip(4 * 6);
        int n = _abAux.get4();
        _abAux.skip(4);
        return n;
      }

      private int getRightNodeIdAndSkipNode() {
        _abAux.skip(4 * 7);
        return _abAux.get4();
      }

      private void skipNode() {
        _abAux.skip(AuxInfo.SIZE - 8);
      }

      private void skipNodes(int num) {
        _abAux.skip(AuxInfo.SIZE * num);
      }

    }

    static class AuxInfo {
      private static int SIZE = 10 * 4;

      private AuxInfo() {
        nid = -1;
        reserved = -1;
      }

      // Warning: any changes in this structure need to be reflected also in AuxInfoLightReader!!!
      AuxInfo(ByteBufferWrapper abAux) {
        // node ID
        nid = abAux.get4();

        // ignored - can contain either parent id or number of children (depending on a MOJO version)
        reserved = abAux.get4();

        //sum of observation weights (typically, that's just the count of observations)
        weightL = abAux.get4f();
        weightR = abAux.get4f();

        //predicted values
        predL   = abAux.get4f();
        predR   = abAux.get4f();

        //squared error
        sqErrL  = abAux.get4f();
        sqErrR  = abAux.get4f();

        //node IDs (consistent with tree construction)
        nidL = abAux.get4();
        nidR = abAux.get4();
      }

      final void setPid(int parentId) {
        pid = parentId;
      }

      @Override public String toString() {
        return  "nid: " + nid + "\n" +
                "pid: " + pid + "\n" +
                "nidL: " + nidL + "\n" +
                "nidR: " + nidR + "\n" +
                "weightL: " + weightL + "\n" +
                "weightR: " + weightR + "\n" +
                "predL: " + predL + "\n" +
                "predR: " + predR + "\n" +
                "sqErrL: " + sqErrL + "\n" +
                "sqErrR: " + sqErrR + "\n" +
                "reserved: " + reserved + "\n";
      }

      public int nid, pid, nidL, nidR;
      private final int reserved;
      public float weightL, weightR, predL, predR, sqErrL, sqErrR;
    }

    static void checkConsistency(AuxInfo auxInfo, SharedTreeNode node) {
      boolean ok = true;
      ok &= (auxInfo.nid == node.getNodeNumber());
      double sum = 0;
      if (node.leftChild!=null) {
        ok &= (auxInfo.nidL == node.leftChild.getNodeNumber());
        ok &= (auxInfo.weightL == node.leftChild.getWeight());
        ok &= (auxInfo.predL == node.leftChild.predValue);
        ok &= (auxInfo.sqErrL == node.leftChild.squaredError);
        sum += node.leftChild.getWeight();
      }
      if (node.rightChild!=null) {
        ok &= (auxInfo.nidR == node.rightChild.getNodeNumber());
        ok &= (auxInfo.weightR == node.rightChild.getWeight());
        ok &= (auxInfo.predR == node.rightChild.predValue);
        ok &= (auxInfo.sqErrR == node.rightChild.squaredError);
        sum += node.rightChild.getWeight();
      }
      if (node.parent!=null) {
        ok &= (auxInfo.pid == node.parent.getNodeNumber());
        ok &= (Math.abs(node.getWeight() - sum) < 1e-5 * (node.getWeight() + sum));
      }
      if (!ok) {
        System.out.println("\nTree inconsistency found:");
        node.print();
        node.leftChild.print();
        node.rightChild.print();
        System.out.println(auxInfo.toString());
      }
    }

    //------------------------------------------------------------------------------------------------------------------
    // Private
    //------------------------------------------------------------------------------------------------------------------

    protected SharedTreeMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    /**
     * Score all trees and fill in the `preds` array.
     */
    protected void scoreAllTrees(double[] row, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        scoreTreeRange(row, 0, _ntree_groups, preds);
    }

    /**
     * Transforms tree predictions into the final model predictions.
     * For classification: converts tree preds into probability distribution and picks predicted class.
     * For regression: projects tree prediction from link-space into the original space.
     * @param row input row.
     * @param offset offset.
     * @param preds final output, same structure as of {@link SharedTreeMojoModel#score0}.
     * @return preds array.
     */
    public abstract double[] unifyPreds(double[] row, double offset, double[] preds);

  /**
   * Generates a (per-class) prediction using only a single tree.
   * @param row input row
   * @param index index of the tree (0..N-1)
   * @param preds array of partial predictions.
   */
    public final void scoreSingleTree(double[] row, int index, double preds[]) {
      scoreTreeRange(row, index, index + 1, preds);
    }

    /**
     * Generates (partial, per-class) predictions using only trees from a given range.
     * @param row input row
     * @param fromIndex low endpoint (inclusive) of the tree range
     * @param toIndex high endpoint (exclusive) of the tree range
     * @param preds array of partial predictions.
     *              To get final predictions pass the result to {@link SharedTreeMojoModel#unifyPreds}.
     */
    public final void scoreTreeRange(double[] row, int fromIndex, int toIndex, double[] preds) {
        final int clOffset = _nclasses == 1 ? 0 : 1;
        for (int classIndex = 0; classIndex < _ntrees_per_group; classIndex++) {
            int k = clOffset + classIndex;
            int itree = treeIndex(fromIndex, classIndex);
            for (int groupIndex = fromIndex; groupIndex < toIndex; groupIndex++) {
                if (_compressed_trees[itree] != null) { // Skip all empty trees
                  preds[k] += _scoreTree.scoreTree(_compressed_trees[itree], row, false, _domains);
                }
                itree++;
            }
        }
    }

    // note that _ntree_group = _treekeys.length
    // ntrees_per_group = _treeKeys[0].length
    public String[] getDecisionPathNames() {
      int classTrees = 0;
      for (int i = 0; i < _ntrees_per_group; ++i) {
        int itree = treeIndex(0, i);
        if (_compressed_trees[itree] != null) classTrees++;
      }
      final int outputcols = _ntree_groups * classTrees;
      final String[] names = new String[outputcols];
      for (int c = 0; c < _ntrees_per_group; c++) {
        for (int tidx = 0; tidx < _ntree_groups; tidx++) {
          int itree = treeIndex(tidx, c);
          if (_compressed_trees[itree] != null) {
            names[itree] = "T" + (tidx + 1) + ".C" + (c + 1);
          }
        }
      }
      return names;
    }

    public static class LeafNodeAssignments {
      public String[] _paths;
      public int[] _nodeIds;
    }

    public LeafNodeAssignments getLeafNodeAssignments(final double[] row) {
      LeafNodeAssignments assignments = new LeafNodeAssignments();
      assignments._paths = new String[_compressed_trees.length];
      if (_mojo_version >= 1.3 && _compressed_trees_aux != null) { // enable only for compatible MOJOs
        assignments._nodeIds = new int[_compressed_trees_aux.length];
      }
      traceDecisions(row, assignments._paths, assignments._nodeIds);
      return assignments;
    }

    public String[] getDecisionPath(final double[] row) {
      String[] paths = new String[_compressed_trees.length];
      traceDecisions(row, paths, null);
      return paths;
    }

    private void traceDecisions(final double[] row, String[] paths, int[] nodeIds) {
      if (_mojo_version < 1.2) {
        throw new IllegalArgumentException("You can only obtain decision tree path with mojo versions 1.2 or higher");
      }
      for (int j = 0; j < _ntree_groups; j++) {
        for (int i = 0; i < _ntrees_per_group; i++) {
          int itree = treeIndex(j, i);
          double d = scoreTree(_compressed_trees[itree], row, true, _domains);
          if (paths != null)
            paths[itree] = SharedTreeMojoModel.getDecisionPath(d);
          if (nodeIds != null) {
            assert _mojo_version >= 1.3;
            nodeIds[itree] = SharedTreeMojoModel.getLeafNodeId(d, _compressed_trees_aux[itree]);
          }
        }
      }
    }

    /**
     * Locates a tree in the array of compressed trees.
     * @param groupIndex index of the tree in a class-group of trees
     * @param classIndex index of the class
     * @return index of the tree in _compressed_trees.
     */
    final int treeIndex(int groupIndex, int classIndex) {
        return classIndex * _ntree_groups + groupIndex;
    }


  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  // DO NOT CHANGE THE CODE BELOW THIS LINE
  /////////////////////////////////////////////////////
  /**
   * SET IN STONE FOR MOJO VERSION "1.00" - DO NOT CHANGE
   * @param tree
   * @param row
   * @param computeLeafAssignment
   * @return
   */
  @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
  public static double scoreTree0(byte[] tree, double[] row, boolean computeLeafAssignment) {
    ByteBufferWrapper ab = new ByteBufferWrapper(tree);
    GenmodelBitSet bs = null;  // Lazily set on hitting first group test
    long bitsRight = 0;
    int level = 0;
    while (true) {
      int nodeType = ab.get1U();
      int colId = ab.get2();
      if (colId == 65535) return ab.get4f();
      int naSplitDir = ab.get1U();
      boolean naVsRest = naSplitDir == NsdNaVsRest;
      boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
      int lmask = (nodeType & 51);
      int equal = (nodeType & 12);  // Can be one of 0, 8, 12
      assert equal != 4;  // no longer supported

      float splitVal = -1;
      if (!naVsRest) {
        // Extract value or group to split on
        if (equal == 0) {
          // Standard float-compare test (either < or ==)
          splitVal = ab.get4f();  // Get the float to compare
        } else {
          // Bitset test
          if (bs == null) bs = new GenmodelBitSet(0);
          if (equal == 8)
            bs.fill2(tree, ab);
          else
            bs.fill3_1(tree, ab);
        }
      }

      double d = row[colId];
      if (Double.isNaN(d)? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains0((int)d))) {
        // go RIGHT
        switch (lmask) {
          case 0:  ab.skip(ab.get1U());  break;
          case 1:  ab.skip(ab.get2());  break;
          case 2:  ab.skip(ab.get3());  break;
          case 3:  ab.skip(ab.get4());  break;
          case 48: ab.skip(4);  break;  // skip the prediction
          default:
            assert false : "illegal lmask value " + lmask + " in tree " + Arrays.toString(tree);
        }
        if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
        lmask = (nodeType & 0xC0) >> 2;  // Replace leftmask with the rightmask
      } else {
        // go LEFT
        if (lmask <= 3)
          ab.skip(lmask + 1);
      }

      level++;
      if ((lmask & 16) != 0) {
        if (computeLeafAssignment) {
          bitsRight |= 1 << level;  // mark the end of the tree
          return Double.longBitsToDouble(bitsRight);
        } else {
          return ab.get4f();
        }
      }
    }
  }

  /**
   * SET IN STONE FOR MOJO VERSION "1.10" - DO NOT CHANGE
   * @param tree
   * @param row
   * @param computeLeafAssignment
   * @return
   */
  @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
  public static double scoreTree1(byte[] tree, double[] row, boolean computeLeafAssignment) {
    ByteBufferWrapper ab = new ByteBufferWrapper(tree);
    GenmodelBitSet bs = null;
    long bitsRight = 0;
    int level = 0;
    while (true) {
      int nodeType = ab.get1U();
      int colId = ab.get2();
      if (colId == 65535) return ab.get4f();
      int naSplitDir = ab.get1U();
      boolean naVsRest = naSplitDir == NsdNaVsRest;
      boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
      int lmask = (nodeType & 51);
      int equal = (nodeType & 12);  // Can be one of 0, 8, 12
      assert equal != 4;  // no longer supported

      float splitVal = -1;
      if (!naVsRest) {
        // Extract value or group to split on
        if (equal == 0) {
          // Standard float-compare test (either < or ==)
          splitVal = ab.get4f();  // Get the float to compare
        } else {
          // Bitset test
          if (bs == null) bs = new GenmodelBitSet(0);
          if (equal == 8)
            bs.fill2(tree, ab);
          else
            bs.fill3_1(tree, ab);
        }
      }

      double d = row[colId];
      if (Double.isNaN(d) || ( equal != 0 && bs != null && !bs.isInRange((int)d) )
              ? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains((int)d))) {
        // go RIGHT
        switch (lmask) {
          case 0:  ab.skip(ab.get1U());  break;
          case 1:  ab.skip(ab.get2());  break;
          case 2:  ab.skip(ab.get3());  break;
          case 3:  ab.skip(ab.get4());  break;
          case 48: ab.skip(4);  break;  // skip the prediction
          default:
            assert false : "illegal lmask value " + lmask + " in tree " + Arrays.toString(tree);
        }
        if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
        lmask = (nodeType & 0xC0) >> 2;  // Replace leftmask with the rightmask
      } else {
        // go LEFT
        if (lmask <= 3)
          ab.skip(lmask + 1);
      }

      level++;
      if ((lmask & 16) != 0) {
        if (computeLeafAssignment) {
          bitsRight |= 1 << level;  // mark the end of the tree
          return Double.longBitsToDouble(bitsRight);
        } else {
          return ab.get4f();
        }
      }
    }
  }

  @Override
  public boolean calibrateClassProbabilities(double[] preds) {
    if (_calib_glm_beta == null)
      return false;
    assert _nclasses == 2; // only supported for binomial classification
    assert preds.length == _nclasses + 1;
    double p = GLM_logitInv((preds[1] * _calib_glm_beta[0]) + _calib_glm_beta[1]);
    preds[1] = 1 - p;
    preds[2] = p;
    return true;
  }

    @Override
    public SharedTreeGraph convert(final int treeNumber, final String treeClass) {
        return _computeGraph(treeNumber);
    }

}
