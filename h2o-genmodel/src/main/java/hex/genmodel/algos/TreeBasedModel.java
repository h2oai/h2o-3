package hex.genmodel.algos;

import hex.genmodel.MojoModel;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.GenmodelBitSet;
import hex.genmodel.utils.NaSplitDir;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Common ancestor for {@link DrfModel} and {@link GbmModel}.
 * See also: `hex.tree.SharedTreeModel` and `hex.tree.TreeVisitor` classes.
 */
public abstract class TreeBasedModel extends MojoModel {
    private static final int NsdNaVsRest = NaSplitDir.NAvsREST.value();
    private static final int NsdNaLeft = NaSplitDir.NALeft.value();
    private static final int NsdLeft = NaSplitDir.Left.value();
    protected int _ntrees;
    protected byte[][] _compressed_trees;

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
    public static double scoreTree(final byte[] tree, final double[] row, final int nclasses,
                                   boolean computeLeafAssignment) {
        ByteBufferWrapper ab = new ByteBufferWrapper(tree);
        GenmodelBitSet bs = null;  // Lazily set on hitting first group test
        long bitsRight = 0;
        int level = 0;
        while (true) {
            int nodeType = ab.get1U();
            int colId = ab.get2();
            if (colId == 65535) return ab.get4f();
            int naSplitDir = ab.get1U();
            final boolean naVsRest = naSplitDir == NsdNaVsRest;
            final boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
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
            if (Double.isNaN(d)? !leftward : !naVsRest && (equal == 0? d >= splitVal : bs.contains((int)d))) {
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

    public static double scoreTree(final byte[] tree, final double[] row, final int nclasses) {
        return scoreTree(tree, row, nclasses, false);
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
    // Private
    //------------------------------------------------------------------------------------------------------------------

    protected TreeBasedModel(MojoReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _ntrees = (int) info.get("n_trees");
        _compressed_trees = new byte[_ntrees * _nclasses][];
    }

    /**
     * Score all trees and fill in the `preds` array.
     */
    protected void scoreAllTrees(double[] row, double[] preds, int nClassesToScore) {
        java.util.Arrays.fill(preds, 0);
        for (int i = 0; i < nClassesToScore; i++) {
            int k = _nclasses == 1? 0 : i + 1;
            for (int j = 0; j < _ntrees; j++) {
                preds[k] += scoreTree(fetchTree(i, j), row, _nclasses);
            }
        }
    }

    private byte[] fetchTree(int classIdx, int treeIdx) {
        try {
            int itree = classIdx * _ntrees + treeIdx;
            byte[] tree = _compressed_trees[itree];
            if (tree == null) {
                tree = _reader.getBinaryFile(String.format("trees/t%02d_%03d.bin", classIdx, treeIdx));
                _compressed_trees[itree] = tree;
            }
            return tree;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
