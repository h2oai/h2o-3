package hex.genmodel.algos.tree;

import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.GenmodelBitSet;

import java.util.Arrays;

public final class ScoreTree0 implements ScoreTree {
  private static final int NsdNaVsRest = NaSplitDir.NAvsREST.value();
  private static final int NsdNaLeft = NaSplitDir.NALeft.value();
  private static final int NsdLeft = NaSplitDir.Left.value();

  @Override
  public final double scoreTree(byte[] tree, double[] row, boolean computeLeafAssignment, String[][] domains) {
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
      if (Double.isNaN(d) ? !leftward : !naVsRest && (equal == 0 ? d >= splitVal : bs.contains0((int) d))) {
        // go RIGHT
        switch (lmask) {
          case 0:
            ab.skip(ab.get1U());
            break;
          case 1:
            ab.skip(ab.get2());
            break;
          case 2:
            ab.skip(ab.get3());
            break;
          case 3:
            ab.skip(ab.get4());
            break;
          case 48:
            ab.skip(4);
            break;  // skip the prediction
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
