package hex.genmodel.algos.tree;

import hex.genmodel.MojoModel;
import hex.genmodel.algos.drf.DrfMojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.GenmodelBitSet;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Common ancestor for {@link DrfMojoModel} and {@link GbmMojoModel}.
 * See also: `hex.tree.SharedTreeModel` and `hex.tree.TreeVisitor` classes.
 */
public abstract class SharedTreeMojoModel extends MojoModel {
    private static final int NsdNaVsRest = NaSplitDir.NAvsREST.value();
    private static final int NsdNaLeft = NaSplitDir.NALeft.value();
    private static final int NsdLeft = NaSplitDir.Left.value();

    protected Number _mojo_version;

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
   * Highly efficient (critical path) tree scoring
   *
   * Given a tree (in the form of a byte array) and the row of input data, compute either this tree's
   * predicted value when `computeLeafAssignment` is false, or the the decision path within the tree (but no more
   * than 64 levels) when `computeLeafAssignment` is true.
   *
   * Note: this function is also used from the `hex.tree.CompressedTree` class in `h2o-algos` project.
   */
  @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
    public static double scoreTree(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment, String[][] domains) {
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
                    case 16: ab.skip(nclasses < 256? 1 : 2);  break;  // Small leaf
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

    public static String getDecisionPath(double leafAssignment) {
        long l = Double.doubleToRawLongBits(leafAssignment);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < 64; ++i) {
            boolean right = ((l>>i) & 0x1L) == 1;
            sb.append(right? "R" : "L");
            if (right) pos = i;
        }
        return sb.substring(0, pos);
    }

    //------------------------------------------------------------------------------------------------------------------
    // Computing a Tree Graph
    //------------------------------------------------------------------------------------------------------------------

    private void computeTreeGraph(SharedTreeSubgraph sg, SharedTreeNode node, byte[] tree, ByteBufferWrapper ab, HashMap<Integer, AuxInfo> auxMap, int nclasses) {
        int nodeType = ab.get1U();
        int colId = ab.get2();
        if (colId == 65535) {
            float leafValue = ab.get4f();
            node.setPredValue(leafValue);
            return;
        }
        String colName = getNames()[colId];
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
                node.setBitset(getDomainValues(colId), bs);
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
                case 16:
                    ab2.skip(nclasses < 256 ? 1 : 2);
                    break;  // Small leaf
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
                computeTreeGraph(sg, newNode, tree, ab2, auxMap, nclasses);
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
                computeTreeGraph(sg, newNode, tree, ab2, auxMap, nclasses);
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
                String className = "";
                {
                    String[] domainValues = getDomainValues(getResponseIdx());
                    if (domainValues != null) {
                        className = ", Class " + domainValues[i];
                    }
                }
                int itree = treeIndex(j, i);

                SharedTreeSubgraph sg = g.makeSubgraph("Tree " + j + className);
                SharedTreeNode node = sg.makeRootNode();
                node.setSquaredError(Float.NaN);
                node.setPredValue(Float.NaN);
                byte[] tree = _compressed_trees[itree];
                ByteBufferWrapper ab = new ByteBufferWrapper(tree);
                ByteBufferWrapper abAux = new ByteBufferWrapper(_compressed_trees_aux[itree]);
                HashMap<Integer, AuxInfo> auxMap = new HashMap<>();
                while (abAux.hasRemaining()) {
                  AuxInfo auxInfo = new AuxInfo(abAux);
                  auxMap.put(auxInfo.nid, auxInfo);
                }
                computeTreeGraph(sg, node, tree, ab, auxMap, _nclasses);
            }

            if (treeToPrint >= 0) {
                break;
            }
        }

        return g;
    }

    static class AuxInfo {
      AuxInfo(ByteBufferWrapper abAux) {
        // node ID
        nid = abAux.get4();

        // parent node ID
        pid = abAux.get4();

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
                "sqErrR: " + sqErrR + "\n";
      }

      public int nid, pid, nidL, nidR;
      public float weightL, weightR, predL, predR, sqErrL, sqErrR;
    }

    void checkConsistency(AuxInfo auxInfo, SharedTreeNode node) {
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

    protected SharedTreeMojoModel(String[] columns, String[][] domains) {
        super(columns, domains);
    }

    /**
     * Score all trees and fill in the `preds` array.
     */
    protected void scoreAllTrees(double[] row, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        for (int i = 0; i < _ntrees_per_group; i++) {
            int k = _nclasses == 1? 0 : i + 1;
            for (int j = 0; j < _ntree_groups; j++) {
                int itree = treeIndex(j, i);
                if (_mojo_version.equals(1.0)) { //First version
                    preds[k] += scoreTree0(_compressed_trees[itree], row, _nclasses, false);
                } else if (_mojo_version.equals(1.1)) { //Second version
                    preds[k] += scoreTree1(_compressed_trees[itree], row, _nclasses, false);
                } else if (_mojo_version.equals(1.2)) { //CURRENT VERSION
                    preds[k] += scoreTree(_compressed_trees[itree], row, _nclasses, false, _domains);
                }
            }
        }
    }

    protected int treeIndex(int groupIndex, int classIndex) {
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
   * @param nclasses
   * @param computeLeafAssignment
   * @return
   */
  @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
  public static double scoreTree0(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
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
            bs.fill3(tree, ab);
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
          case 16: ab.skip(nclasses < 256? 1 : 2);  break;  // Small leaf
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
   * @param nclasses
   * @param computeLeafAssignment
   * @return
   */
  @SuppressWarnings("ConstantConditions")  // Complains that the code is too complex. Well duh!
  public static double scoreTree1(byte[] tree, double[] row, int nclasses, boolean computeLeafAssignment) {
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
          case 16: ab.skip(nclasses < 256? 1 : 2);  break;  // Small leaf
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

}
