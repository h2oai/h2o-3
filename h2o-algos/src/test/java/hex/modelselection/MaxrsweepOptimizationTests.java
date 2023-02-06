package hex.modelselection;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.gam.MatrixFrameUtils.GamUtils.copy2DArray;
import static hex.modelselection.ModelSelection.replacement;
import static hex.modelselection.ModelSelectionMaxRSweepTests.assertEqualSV;
import static hex.modelselection.ModelSelectionUtils.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class MaxrsweepOptimizationTests  extends TestUtil {

  double[][] _cpm = new double[][]{{50000.0, -7.958078640513122E-13, 3.979039320256561E-13,
          2.2737367544323206E-13, 1.1652900866465643E-12, 4.689582056016661E-13, -4.547473508864641E-13,
          1.2221335055073723E-12, 6.608047442568932E-13, 122346.71425169753}, {-7.958078640513122E-13, 49999.0,
          -345.6139787378465, -205.35413258920994, 253.29361004290251, 308.5874618379251, 56.84257753101841,
          -143.108688427594, 143.11727542499693, -1852537.2069261894}, {3.979039320256561E-13,
          -345.6139787378465, 49999.00000000001, 6.2282639257916514, -91.95828276351028, -115.23819328439825,
          -163.12536216455945, 585.5553268287405, 249.7592249542846, 1815126.8928365733},
          {2.2737367544323206E-13, -205.35413258920994, 6.2282639257916514, 49999.0, 93.6080023311965,
                  180.38909518825784, 336.9634862705478, 126.58829573823623, -187.75488919015658, 1797504.437143965},
          {1.1652900866465643E-12, 253.29361004290251, -91.95828276351028, 93.6080023311965, 49998.99999999999,
                  234.90806821539226, -174.76753981209666, -415.1970001204393, 208.42665517330633, -1768751.0314145735},
          {4.689582056016661E-13, 308.5874618379251, -115.23819328439825, 180.38909518825784, 234.90806821539226,
                  49999.00000000001, 76.18684561711362, 70.78228811424982, -267.03987968155207, -1755817.796321408},
          {-4.547473508864641E-13, 56.84257753101841, -163.12536216455945, 336.9634862705478,
                  -174.76753981209666, 76.18684561711362, 49999.0, 119.30508493851517, 150.32124911394152,
                  1747644.8783670785}, {1.2221335055073723E-12, -143.108688427594, 585.5553268287405, 126.58829573823623,
          -415.1970001204393, 70.78228811424982, 119.30508493851517, 49999.0, -4.219695487168629,
          -1634928.905372516}, {6.608047442568932E-13, 143.11727542499693, 249.7592249542846,
          -187.75488919015658, 208.42665517330633, -267.03987968155207, 150.32124911394152, -4.219695487168629,
          49998.99999999999, 1684727.2390496698}, {122346.71425169753, -1852537.2069261894, 1815126.8928365733,
          1797504.437143965, -1768751.0314145735, -1755817.796321408, 1747644.8783670785, -1634928.905372516,
          1684727.2390496698, 4.542312022970976E9}}; // second to last is intercept, last row/column xyT, yy

  int[][] _pred2CPMIndices = new int[][]{{0},{1},{2},{3},{4},{5},{6},{7},{8}};
  
  public class CPMInd {
    public double[][] _cpm;
    public int[][] _pred2BigCPMIndices;
    
    public CPMInd(double[][] cpm, int[][] pred2CPMInd) {
      _cpm = cpm;
      _pred2BigCPMIndices = pred2CPMInd;
    }
  }

  public CPMInd loadProblemCPM(String filePath) {
    Frame cpm = Scope.track(parseTestFile(filePath));

    int matSize = cpm.numCols();
    double[][] bigCPM = new double[matSize][matSize];
    new ArrayUtils.FrameToArray(0, matSize-1, cpm.numRows(), bigCPM).doAll(cpm);
    int pred2CPMSize = matSize-2;
    int[][] pred2BigCPMIndices = new int[pred2CPMSize][1];
    for (int index=0; index<pred2CPMSize; index++)
      pred2BigCPMIndices[index] = new int[]{index};
    return new CPMInd(bigCPM, pred2BigCPMIndices);
  }
  
  @Test
  public void testReplacement() {
    // at predictor size 31, before calling replacement, currSubsetIndices is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 
    // 174, 127, 66, 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 62, 24}, 
    // errorVariance: 2.9087173871588173E9
    CPMInd cpmPredInfo = loadProblemCPM("smalldata/model_selection/cpm.csv");
    int[] currSubSet = new int[]{77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127, 66, 169, 122, 136, 137, 188, 179, 
            30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 62, 24};
    List<Integer> validSubset = IntStream.range(0, cpmPredInfo._cpm.length-2).boxed().collect(Collectors.toList());
    List<Integer> currSubsetIndices = new ArrayList<>(Arrays.asList(77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127, 
            66, 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 62, 24));
    validSubset.remove(Stream.of(currSubSet).collect(Collectors.toList()));
    // generate sweep model from code
    double[][] currCPM =  extractPredSubsetsCPM(cpmPredInfo._cpm, currSubSet, cpmPredInfo._pred2BigCPMIndices, true);
    SweepVector[][] origSweepVectors = sweepCPM(currCPM, IntStream.range(0, currCPM.length-1).toArray(), true);
    ModelSelection.SweepModel currModel = new ModelSelection.SweepModel(currSubSet, currCPM, origSweepVectors, 2.9087173871588173E9);
    BitSet predictorIndices = new BitSet(cpmPredInfo._cpm.length-2);
    Set<BitSet> usedCombos = new HashSet<>();
    ModelSelection.SweepModel bestModel = replacement(currSubsetIndices, validSubset, usedCombos, predictorIndices, currModel,
            true, cpmPredInfo._cpm, cpmPredInfo._pred2BigCPMIndices);
    // after all replacements at predictor size 31, currSubsetIndices is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174,
    // 127, 66, 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 78, 24}, mse = 2.90838176257814E9
    int[] newSubset = new int[]{77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127, 66, 169, 122, 136, 137, 188, 179, 30,
            192, 177, 180, 172, 134, 193, 49, 72, 34, 78, 24};
    double[][] targetCPM = extractPredSubsetsCPM(cpmPredInfo._cpm, newSubset, cpmPredInfo._pred2BigCPMIndices, true);
    // generate correct answser
    SweepVector[][] targetSV = sweepCPM(targetCPM, IntStream.range(0, currCPM.length-1).toArray(), true);
    
    
  }
  
  @Test
  public void testForwardR() {
    // at size 31, before replacement step, currSubsetIndices is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127, 66,
    // 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 62, 24}, 
    // errorVariance: 2.9087173871588173E9.
    // before first replacement, currSubsetIndices of size 30 is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127, 66,
    // 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 34, 62, 24}, errorVariance: 2.9087173871588173E9
    // at predPos = 27, removedInd = 72.
    
    // after first replacement step, currSubsetIndices of size 31 is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127, 66, 169,
    // 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 78, 34, 62, 24}, errorVariance: 2.908569000043742E9
    // before second replacement, the currSubsetIndices of size 30 is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174, 127,
    // 66, 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 24}, 
    // errorVariance 2.908569000043742E9. RemovedSubInd = 62, predPos = 29
    // after all replacements at predictor size 31, currSubsetIndices is {77, 96, 74, 75, 87, 88, 85, 6, 100, 3, 174,
    // 127, 66, 169, 122, 136, 137, 188, 179, 30, 192, 177, 180, 172, 134, 193, 49, 72, 34, 78, 24}, mse = 2.90838176257814E9

    
  }
  
  @Test
  public void testBigCPMReplacement() {
    Scope.enter();
    try {
      CPMInd cpmPredInfo = loadProblemCPM("smalldata/model_selection/cpm.csv");
      double[][] cpm = cpmPredInfo._cpm;
      int[][] pred2CpmInd = cpmPredInfo._pred2BigCPMIndices;
      // size 8, old predSubet = (77,96,74,75,87,88,100,6), predPos = 6, replaced with 85, new predSubset=(77,96,74,75,87,88,85,6)
      testOneReplacementSet(new int[]{77,96,74,75,87,88,100,6}, new int[]{85}, new int[]{6}, cpmPredInfo);
      // size 30, old predsubset = (77,96,74,75,87,88,85,6,100,3,174,127,66,169,122,136,137,188,179,30,192,177,180,172,134,193,49,78,34,62), predPos=27, replaced with 72
      int[] origSubset = new int[]{77,96,74,75,87,88,85,6,100,3,174,127,66,169,122,136,137,188,179,30,192,177,180,172,
              134,193,49,78,34,62};
      testOneReplacementSet(origSubset, new int[]{72}, new int[]{27}, cpmPredInfo);
      // size 31, old predsubset = (77,96,74,75,87,88,85,6,100,3,174,127,66,169,122,136,137,188,179,30,192,177,180,172,134,193,49,72,34,62,24), predPos = 27, replaced 78 with 72
      // size 31, old predsubset = (77,96,74,75,87,88,85,6,100,3,174,127,66,169,122,136,137,188,179,30,192,177,180,172,134,193,49,72,34,62,24), predPos = 29, replaced with 78
      // at size 31, final predsubset looks like (77,96,74,75,87,88,85,6,100,3,174,127,66,169,122,136,137,188,179,30,192,177,180,172,134,193,49,72,34,78,24)
      origSubset = new int[]{77,96,74,75,87,88,85,6,100,3,174,127,66,169,122,136,137,188,179,30,192,177,180,172,134,
              193,49,72,34,62,24};
      testOneReplacementSet(origSubset, new int[]{72, 78}, new int[]{27, 29}, cpmPredInfo);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testReplacementSubset4Pred2Numerics() {
    // starting predictor subsets 0, 1, 2, 3 want to eventually replace the three predictors with 4,5,6,7 but in 
    // different orders of replacement
    CPMInd cpmPredInfo = new CPMInd(_cpm, _pred2CPMIndices);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{0,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{1,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{0,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{2,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{0,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{3,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{1,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{2,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{1,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{3,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{2,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5}, new int[]{3,2}, cpmPredInfo);
  }

  /***
   * All possible combinations with 4 positions:
   * 0/1/2/3
   * 0/1/3/2
   * 0/3/1/2
   * 3/0/1/2
   * 3/0/2/1
   * 0/3/2/1
   * 0/2/3/1
   * 0/2/1/3
   * 2/0/1/3
   * 2/0/3/1
   * 2/3/0/1
   * 3/2/0/1
   * 3/2/1/0
   * 2/1/3/0
   * 2/1/0/3
   * 1/2/0/3
   * 1/2/3/0
   * 1/3/2/0
   * 3/1/2/0
   * 3/1/0/2
   * 1/3/0/2
   * 1/0/3/2
   * 1/0/2/3
   * 2/3/1/0
   */
  @Test
  public void testReplacementSubset4Pred4Numerics() {
    // starting predictor subsets 0, 1, 2, 3 want to eventually replace the three predictors with 4,5,6,7 but in 
    // different orders of replacement
    CPMInd cpmPredInfo = new CPMInd(_cpm, _pred2CPMIndices);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{0,1,2,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{0,1,3,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{0,3,1,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{3,0,1,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{3,0,2,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{0,3,2,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{0,2,3,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{0,2,1,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{2,0,1,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{2,0,3,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{2,3,0,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{3,2,0,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{3,2,1,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{2,1,3,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{2,1,0,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{1,2,0,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{1,2,3,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{1,3,2,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{3,1,2,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{3,1,0,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{1,3,0,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{1,0,3,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{1,0,2,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3}, new int[]{4,5,6,7}, new int[]{2,3,1,0}, cpmPredInfo);
  }



  /***
   * All possible combinations with 3 positions:
   * 0/1/2
   * 0/2/1
   * 2/0/1
   * 2/1/0
   * 1/2/0
   * 1/0/2
   */
  @Test
  public void testReplacementSubset3Pred3Numerics() {
    // starting predictor subsets 0, 1, 2, want to eventually replace the three predictors with 3,4,5 but in different
    // orders of replacement
    CPMInd cpmPredInfo = new CPMInd(_cpm, _pred2CPMIndices);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4,5}, new int[]{1,0,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4,5}, new int[]{0,1,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4,5}, new int[]{0,2,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4,5}, new int[]{2,0,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4,5}, new int[]{2,1,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4,5}, new int[]{1,2,0}, cpmPredInfo);
  }

  /***
   * 3 choose 2
   * 01/10
   * 12/21
   * 02/20
   */
  @Test
  public void testReplacementSubset3Pred2Numerics() {
    // starting predictor subsets 0, 1, 2, want to eventually replace the three predictors with 3,4 but in different
    // orders of replacement
    CPMInd cpmPredInfo = new CPMInd(_cpm, _pred2CPMIndices);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4}, new int[]{0,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4}, new int[]{0,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4}, new int[]{1,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4}, new int[]{2,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4}, new int[]{1,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3,4}, new int[]{2,1}, cpmPredInfo);
  }

  @Test
  public void testReplacementSubset6Pred2Numerics() {
    // starting predictor subsets 0, 1, 2, want to eventually replace the three predictors with 3,4 but in different
    // orders of replacement
    CPMInd cpmPredInfo = new CPMInd(_cpm, _pred2CPMIndices);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{2,4}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{0,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{1,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{0,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{2,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{0,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{3,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{0,4}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{4,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{0,5}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{5,0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{1,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{2,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{1,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{3,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{1,4}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{4,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{1,5}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{5,1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{2,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{3,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{4,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{2,5}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{5,2}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{3,4}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{4,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{3,5}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{5,3}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{4,5}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2,3,4,5}, new int[]{6,7}, new int[]{5,4}, cpmPredInfo);
  }

  /***
   * 3 choose 1
   * 0
   * 1
   * 2
   */
  @Test
  public void testReplacementSubset3Pred1Numerics() {
    // starting predictor subsets 0, 1, 2, want to eventually replace the three predictors with 3 but in different
    // orders of replacement
    CPMInd cpmPredInfo = new CPMInd(_cpm, _pred2CPMIndices);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3}, new int[]{0}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3}, new int[]{1}, cpmPredInfo);
    testOneReplacementSet(new int[]{0,1,2}, new int[]{3}, new int[]{2}, cpmPredInfo);
  }
  
  public void testOneReplacementSet(int[] orignalPreds, int[] replacePreds, int[] replacementPredPos, CPMInd cpmPredInfo) {
    // prepare the cpm with originalPreds
    double[][] cpm = extractPredSubsetsCPM(cpmPredInfo._cpm, orignalPreds, cpmPredInfo._pred2BigCPMIndices, true);
    SweepVector[][] origSweepVectors = sweepCPM(cpm, IntStream.range(0, cpm.length-1).toArray(), true);
    int numReplacePreds = replacePreds.length;
    int[] predSubset = orignalPreds.clone();
    List<Integer> currentSubsetIndices = Arrays.stream(orignalPreds).boxed().collect(Collectors.toList());
    for (int index=0; index<numReplacePreds; index++) {
      int predPos = replacementPredPos[index];
      predSubset[predPos] = replacePreds[index];
      ModelSelection.SweepModel bestModel = testOneReplacement(predSubset, currentSubsetIndices, predPos, cpm, origSweepVectors, cpmPredInfo);
      currentSubsetIndices = Arrays.stream(bestModel._predSubset).boxed().collect(Collectors.toList());
      cpm = bestModel._CPM;
      origSweepVectors = bestModel._sweepVector;
    }
  }

  /***
   * origCPM: cpm that have been swept with predictors in currentSubsetIndices;
   * origSVs: sweepVectors from the swept of origCPM;
   * currentSubsetIndices: list containing original predictors subset before replacement;
   * predPos: position of new predictor to be replaced;
   * newPred: predictor index that should replace the old predictor at position predPos
   */
  public ModelSelection.SweepModel testOneReplacement(int[] predSubsetWithReplacements, List<Integer> currentSubsetIndices, int predPos,
                                                      double[][] origCPM, SweepVector[][] origSVs, CPMInd cpmPredInfo) {
    // generate target cpm and sweep vector to compare
    double[][] targetCPM = extractPredSubsetsCPM(cpmPredInfo._cpm, predSubsetWithReplacements,
            cpmPredInfo._pred2BigCPMIndices, true);
    ModelSelectionUtils.SweepVector[][] correctSV = sweepCPM(targetCPM, IntStream.range(0, targetCPM.length-1).toArray(), true);
    int[] predSubset = currentSubsetIndices.stream().mapToInt(x->x).toArray();
    List<Integer> newSubset = Arrays.stream(predSubsetWithReplacements).boxed().collect(Collectors.toList());
    int[] subsetPred = newSubset.stream().mapToInt(x->x).toArray();
    double[][] subsetCPMO = new double[origCPM.length][origCPM.length];
    copy2DArray(origCPM, subsetCPMO);
    int[] replacedPredSweepIndices = extractSweepIndices(currentSubsetIndices, predPos, predSubset[predPos], cpmPredInfo._pred2BigCPMIndices,
            true);  // sweep index corresponding to old predictor that is going to be replaced
    sweepCPM(subsetCPMO, replacedPredSweepIndices, false);  // undo sweeping by old predictor
    ModelSelection.SweepModel bestModel = new ModelSelection.SweepModel(subsetPred, origCPM, origSVs, 10);
    List<Integer> newAllSweepIndices = IntStream.range(0, bestModel._CPM.length-1).boxed().collect(Collectors.toList());
    double[][] subsetCPM = unsweptPredAfterReplacedPred(subsetPred, subsetCPMO, cpmPredInfo._cpm, cpmPredInfo._pred2BigCPMIndices,
            true, predPos, replacedPredSweepIndices, newAllSweepIndices);  // undo sweeping by predictor after predPos
    updateCPMSV(bestModel, subsetCPM, replacedPredSweepIndices, newAllSweepIndices, replacedPredSweepIndices);
    TestUtil.checkDoubleArrays(bestModel._CPM, targetCPM, 1e-6);
    for (int index=0; index<correctSV.length; index++) {
      assertEqualSV(correctSV[index], bestModel._sweepVector[index]);
    }
    return bestModel;
  }

  /**
   * Given a cpm of size 4x4 filled with pred 0, 1, 2, it is swept then a replacement algorithm demands that the first
   * predictor to be replaced with 4, the third predictor to be replaced with 8 and the final cpm should be equivalent
   * to a cpm filled with predictor 4, 1, 8 and fullly swept.
   */
  @Test
  public void testOptimizedReplacements() {
    double[][] targetCPM1Change = extractPredSubsetsCPM(_cpm, new int[]{4,1,2}, _pred2CPMIndices, true);
    ModelSelectionUtils.SweepVector[][] correctSVs1Change = sweepCPM(targetCPM1Change, IntStream.range(0, targetCPM1Change.length-1).toArray(), true);
    double[][] targetCPM2Changes = extractPredSubsetsCPM(_cpm, new int[]{4,1,8}, _pred2CPMIndices, true);
    ModelSelectionUtils.SweepVector[][] correctSVs2Changes = sweepCPM(targetCPM2Changes, IntStream.range(0, targetCPM2Changes.length-1).toArray(), true);
    // generate new CPM as in the code
    double[][] cpm4x4 = extractPredSubsetsCPM(_cpm, new int[]{0,1,2}, _pred2CPMIndices, true);
    ModelSelectionUtils.SweepVector[][] origSVs = sweepCPM(cpm4x4, IntStream.range(0, cpm4x4.length-1).toArray(), true);
    // replace the first predictor with 4
    double[][] subsetCPMO = new double[cpm4x4.length][cpm4x4.length];
    copy2DArray(cpm4x4, subsetCPMO);
    sweepCPM(subsetCPMO, new int[]{1}, false);  // undo sweeping by first predictor
    List<Integer> currSubsetIndices = new ArrayList<>(Arrays.asList(4,1,2));
    int[] subsetPred = new int[]{4,1,2};
    List<Integer> replacedPreds = new ArrayList<>(Arrays.asList(4));
    ModelSelection.SweepModel bestModel = new ModelSelection.SweepModel(subsetPred, cpm4x4, origSVs, 10);
    List<Integer> newAllSweepIndices = IntStream.range(0, bestModel._CPM.length-1).boxed().collect(Collectors.toList());
    int predPos = 0;
    List<Integer> originalSubsetIndices = new ArrayList<>(Arrays.asList(0,1,2));
    int[] sweepIndicesRemovedPred = extractSweepIndices(originalSubsetIndices, predPos, 0, _pred2CPMIndices, true);
    double[][] subsetCPM = unsweptPredAfterReplacedPred(subsetPred, subsetCPMO, _cpm, _pred2CPMIndices,
            true, predPos, sweepIndicesRemovedPred, newAllSweepIndices);  // undo sweeping by replaced predictor
    int[] newSweepIndices = extractSweepIndices(currSubsetIndices, predPos, subsetPred[predPos], _pred2CPMIndices,
            true);
    updateCPMSV(bestModel, subsetCPM, newSweepIndices, newAllSweepIndices, sweepIndicesRemovedPred);
    // compare subsetCPM, sweepvectors with correct ones generated at the beginning
    TestUtil.checkDoubleArrays(bestModel._CPM, targetCPM1Change, 1e-6);
    for (int index=0; index<correctSVs1Change.length; index++) {
      assertEqualSV(correctSVs1Change[index], bestModel._sweepVector[index]);
    }
  }
}
