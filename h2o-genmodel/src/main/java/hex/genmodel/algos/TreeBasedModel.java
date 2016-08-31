package hex.genmodel.algos;

import hex.genmodel.RawModel;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.GenmodelBitSet;
import hex.genmodel.utils.NaSplitDir;

import java.util.Arrays;
import java.util.Map;

/**
 * Common ancestor for {@link DrfRawModel} and {@link GbmRawModel}.
 * See also: `hex.tree.SharedTreeModel` and `hex.tree.TreeVisitor` classes.
 */
public abstract class TreeBasedModel extends RawModel {
    private static final int NsdNaVsRest = NaSplitDir.NAvsREST.value();
    private static final int NsdNaLeft = NaSplitDir.NALeft.value();
    private static final int NsdLeft = NaSplitDir.Left.value();
    protected int _ntrees;
    protected byte[][] _compressed_trees;

    protected TreeBasedModel(ContentReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _ntrees = (int) info.get("n_trees");
    }

    /**
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
        GenmodelBitSet ibs = null;  // Lazily set on hitting first group test
        long bitsRight = 0;
        int level = 0;
        while (true) {
            int nodeType = ab.get1U();
            int colId = ab.get2();
            if (colId == 65535) return ab.get4f();
            int naSplitDir = ab.get1U();
            final boolean naVsRest = naSplitDir == NsdNaVsRest;
            final boolean leftward = naSplitDir == NsdNaLeft || naSplitDir == NsdLeft;
            int equal = (nodeType & 12);  // Can be one of 0, 4, 8, 12

            float splitVal = -1;
            if (!naVsRest) {
                // Extract value or group to split on
                if (equal <= 4) {
                    // Standard float-compare test (either < or ==)
                    splitVal = ab.get4f();  // Get the float to compare
                } else {
                    // Bitset test
                    if (ibs == null) ibs = new GenmodelBitSet(0);
                    if (equal == 8)
                        ibs.fill2(tree, ab);
                    else
                        ibs.fill3(tree, ab);
                }
            }

            // Compute the amount to skip.
            int lmask = nodeType & 0x33;
            int rmask = (nodeType & 0xC0) >> 2;
            int skip = 0;
            switch(lmask) {
                case 0:  skip = ab.get1U();  break;
                case 1:  skip = ab.get2();  break;
                case 2:  skip = ab.get3();  break;
                case 3:  skip = ab.get4();  break;
                case 16: skip = nclasses < 256? 1 : 2;  break;  // Small leaf
                case 48: skip = 4;  break;  // skip the prediction
                default:
                    assert false : "illegal lmask value " + lmask + " in bitpile " + Arrays.toString(tree);
            }

            assert equal != 4;  // no longer supported
            double d = row[colId];
            if (Double.isNaN(d)? !leftward
                               : !naVsRest && (equal == 0? d >= splitVal : ibs.contains((int)d))) {
                // go RIGHT
                ab.skip(skip);
                if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
                lmask = rmask; // And set the leaf bits into common place
            }   // if LEFT don't do anything

            level++;
            if ((lmask & 16) == 16) {
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

}
