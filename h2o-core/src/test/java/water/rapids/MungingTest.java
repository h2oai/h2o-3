package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static junit.framework.TestCase.assertTrue;


public class MungingTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testRankWithinGroupby() {
    try {
      Scope.enter();
      // generate training frame randomly
      Random generator = new Random();
      int numRowsG = generator.nextInt(10000) + 15000 + 200;
      int groupby_factors = generator.nextInt(5) + 2;
      Frame groupbyCols = TestUtil.generate_enum_only(2, numRowsG, groupby_factors, 0);
      Scope.track(groupbyCols);
      Frame sortCols = TestUtil.generate_int_only(2, numRowsG, groupby_factors*2, 0.01);
      Scope.track(sortCols);
      Frame train = groupbyCols.add(sortCols);  // complete frame generation
      DKV.put(train);
      Scope.track(train);

      String newCol = "new_rank_col";
      Frame tempFrame = generateResult(train, new int[] {0, 1}, new int[]{2, 3}, newCol);
      Frame answerFrame = tempFrame.sort(new int[]{0,1,2,3}, new int[]{1,1,1,1});
      Scope.track(tempFrame);
      Scope.track(answerFrame);
      String x = String.format("(rank_within_groupby %s [0,1] [2,3] [1,1] %s 0)",train._key, newCol);
      Val res = Rapids.exec(x);
      Frame finalResult  = res.getFrame();  // need to compare this to correct result
      Scope.track(finalResult);
      assertIdenticalUpToRelTolerance(finalResult, answerFrame, 1e-10);
    } finally {
      Scope.exit();
    }
  }

  public static int findKeyIndex(ArrayList<double[]> tempMap, double[] currKey) {
    if (tempMap == null || tempMap.size()==0)
      return -1;

    int arraySize = tempMap.size();
    for (int aIndex = 0; aIndex < arraySize; aIndex++)
      if (Arrays.equals(tempMap.get(aIndex), currKey))
        return aIndex;
    return -1;
  }

  public Frame generateResult(Frame inputFrame, int[] groupbyCols, int[] sortCols, String newRankCol) {
    Frame sortedFrame = inputFrame.sort(sortCols, new int[]{1, 1}); // sorted frame here.
    Vec rankVec = inputFrame.anyVec().makeCon(Double.NaN);
    sortedFrame.add(newRankCol, rankVec);  // add new rank column of invalid rank, NAs

    int groupbyLen = groupbyCols.length;
    double[] key = new double[groupbyLen];
    int currentRank = 1;
    int rankCol = sortedFrame.numCols() - 1;
    ArrayList<double[]> keys = new ArrayList<>();
    ArrayList<Integer> accuRanks = new ArrayList<>();

    for (long rowIndex = 0; rowIndex < sortedFrame.numRows(); rowIndex++) {
      boolean nasFound = false;
      for (int sInd : sortCols) {
        if (Double.isNaN(sortedFrame.vec(sInd).at(rowIndex))) {
          nasFound = true;
          continue;
        }
      }
      // always read in the group keys regardless of NAs
      for (int cind = 0; cind < groupbyLen; cind++) {
        key[cind] = sortedFrame.vec(groupbyCols[cind]).at(rowIndex);
      }
      if (!nasFound) {
        int index = findKeyIndex(keys, key);
        if (index < 0) {  // new key
          keys.add(Arrays.copyOf(key, groupbyLen));
          accuRanks.add(2); //
          currentRank = 1;
        } else {  // existing key
          currentRank = accuRanks.get(index);
          accuRanks.set(index, currentRank+1);

        }
        sortedFrame.vec(rankCol).set(rowIndex, currentRank);
      }
    }
    return sortedFrame;
  }

}
