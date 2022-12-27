package hex.modelselection;

import Jama.Matrix;
import hex.DataInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.gam.GamTestPiping.massageFrame;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.modelselection.ModelSelectionMaxRTests.compareResultFModelSummary;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.maxr;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.maxrsweep;
import static hex.modelselection.ModelSelectionUtils.*;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelSelectionMaxRSweepTests extends TestUtil {

    @Test
    public void testAllSweepingVectors() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877,  3.657043},
                {-3.925113,  9.961056,  4.638419, -2.373317},
                {1.192877,  4.638419,  9.649718, 3.444272},
                {3.657043, -2.373317,  3.444272, 3.636824}};
        final double[][] correctSweepVectors0s1s2 = new double[][]{{0.1000000, -0.3925113,  0.1192877,  0.1000000,
                0.3657043, -0.1000000, -0.3925113,  0.11928773, 0.1000000,  0.3657043}, {-0.0466143,  0.1187591,
                0.6064598,  0.1187591,  -0.1113825666211721, 0.0466143, -0.1187591,  0.6064598,  0.1187591,
                -0.1113825666211721}, {0.05574177,  0.09460483,  0.15599522,  0.15599522, 0.5579671497838223,
                -0.05574177, -0.09460483,  -0.15599522,  0.15599522, 0.5579671497838223}};
        // test sweeping vectors for sweep 0, 1, 2
        assertCorrectAllSweepVectors(clone2DArray(cpm), new int[]{0,1,2}, correctSweepVectors0s1s2);
        // test sweeping vectors for sweep 2, 1, 0
        final double[][] correctSweepVectors2s1s0 = new double[][]{{0.1236178,  0.4806792,  0.1036300,  0.1036300,
                0.3569298,  0.1236178,  0.4806792, -0.1036300,  0.1036300,  0.3569298},{-0.58184375,  0.12934160,
                0.06217182,  0.12934160, -0.52110530, -0.58184375, -0.12934160, -0.06217182,  0.12934160, -0.52110530},
                {0.13821485, -0.08041945,  0.05574177,  0.13821485,  0.12260698, -0.13821485,  0.08041945, -0.05574177,
                        0.13821485,  0.12260698}};
        assertCorrectAllSweepVectors(clone2DArray(cpm), new int[]{2,1,0}, correctSweepVectors2s1s0);
        // test sweeping vectors for 2, 0, 1

        final double[][] correctSweepVectors2s0s1 = new double[][]{{-0.3940459,  0.1003910,  0.4656553,  0.1003910,
                -0.2382596, -0.3940459, -0.1003910,  0.4656553,  0.1003910, -0.2382596},{0.1182966, -0.0466143,
                0.3573300,  0.1182966,  0.3219854, -0.1182966,  0.0466143,  0.3573300,  0.1182966,  0.3219854},
                {0.05574177,  0.09460483,  0.15599522,  0.15599522,  0.55796715, -0.05574177, -0.09460483, -0.15599522,
                        0.15599522,  0.55796715}};
        assertCorrectAllSweepVectors(clone2DArray(cpm), new int[]{1,0,2}, correctSweepVectors2s0s1);
    }

    public static void assertCorrectAllSweepVectors(double[][] cpm, int[] sweepIndices, double[][] rSweepVector) {
        int numSweepVec = 2*(cpm.length+1);
        SweepVector[][] sweepVecs =  sweepCPM(cpm, sweepIndices, true);
        int numSweep = sweepIndices.length;
        for (int sweepInd=0; sweepInd < numSweep; sweepInd++) {
            for (int index = 0; index < numSweepVec; index++) {
                assertTrue(Math.abs(rSweepVector[sweepInd][index] - sweepVecs[sweepInd][index]._value) < 1e-6);
            }
        }
    }
    // test to make sure sweeping vectors are generated correctly for one sweep at a time.
    @Test
    public void testSweepingVectorGeneration4OneSweep() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877,  3.657043},
                {-3.925113,  9.961056,  4.638419, -2.373317},
                {1.192877,  4.638419,  9.649718, 3.444272},
                {3.657043, -2.373317,  3.444272, 3.636824}};
        // after sweep 0
        final double[] sweep0Vector = new double[]{0.1000000, -0.3925113,  0.1192877,  0.1000000,  0.3657043, -0.1000000,
                -0.3925113,  0.11928773, 0.1000000,  0.3657043};
        assertCorrectOneSweepVectors(cpm, 0, sweep0Vector);
        // after sweep 1
        final double[] sweep1Vector = new double[]{-0.0466143,  0.1187591,  0.6064598,  0.1187591,  -0.1113825666211721,
                0.0466143, -0.1187591,  0.6064598,  0.1187591, -0.1113825666211721};
        assertCorrectOneSweepVectors(cpm, 1, sweep1Vector);
        // after sweep 2
        final double[] sweep2Vector = new double[]{0.05574177,  0.09460483,  0.15599522,  0.15599522,
                0.5579671497838223, -0.05574177, -0.09460483,  -0.15599522,  0.15599522, 0.5579671497838223};
        assertCorrectOneSweepVectors(cpm, 2, sweep2Vector);
    }

    @Test
    public void testApplyOneSweepVectorOneNewRowCol() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877, -2.675484,  3.657043},
                {-3.925113,  9.961056,  4.638419,  1.719902, -2.373317},
                {1.192877,  4.638419,  9.649718, -1.855625,  3.444272},
                {-2.675484,  1.719902, -1.855625,  6.197150, -3.117239},
                {3.657043, -2.373317,  3.444272, -3.117239,  3.636824}};
        int[][] predInd2CPMMap = new int[][]{{0},{1},{2},{3}};
        // without intercepts
        // after sweep 0 and adding only one col/row
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{0}, new int[]{0,1,2}, 3, new int[]{0,1,2,3},
                predInd2CPMMap, false);
        // after sweep 1 and adding only one col/row
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{1}, new int[]{0,1,2}, 3, new int[]{0,1,2,3},
                predInd2CPMMap, false);
        // after sweep 2 and adding only one col/row
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{2}, new int[]{0,1,2}, 3, new int[]{0,1,2,3},
                predInd2CPMMap, false);

        // with intercepts
        // after sweep 0 and adding only one col/row
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{0}, new int[]{0,1}, 3, new int[]{0,1,2},
                predInd2CPMMap, true);
        // after sweep 1 and adding only one col/row
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{1}, new int[]{0,1}, 3, new int[]{0,1,2},
                predInd2CPMMap, true);
        // after sweep 2 and adding only one col/row
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{2}, new int[]{0,1}, 3, new int[]{0,1,2},
                predInd2CPMMap, true);
    }

    /***
     * test and make sure sweeping performed using sweep vectors are correct.
     */
    @Test
    public void testApplyOneSweepVectorMultipleNewRowsCols() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877, -2.675484,  3.657043},
                {-3.925113,  9.961056,  4.638419,  1.719902, -2.373317},
                {1.192877,  4.638419,  9.649718, -1.855625,  3.444272},
                {-2.675484,  1.719902, -1.855625,  6.197150, -3.117239},
                {3.657043, -2.373317,  3.444272, -3.117239,  3.636824}};
        // without intercepts
        // after sweep 1 and adding two row/cols
        int[][] predInd2CPMMap = new int[][]{{0},{1},{2, 3}};
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{1}, new int[]{1,0}, 2, new int[]{1,0,2},
                predInd2CPMMap, false);
        // after sweep 0 and adding three row/cols
        predInd2CPMMap = new int[][]{{0},{1, 2, 3}};
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{0}, new int[]{0}, 1, new int[]{0,1},
                predInd2CPMMap, false);

        // with intercepts
        // after sweep 1 and adding two row/cols
        predInd2CPMMap = new int[][]{{1,2},{0}};
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{1}, new int[]{1}, 0, new int[]{1,0},
                predInd2CPMMap, true);
        // after sweep 0 and adding three row/cols
        predInd2CPMMap = new int[][]{{0},{1, 2}};
        assertCorrectOneSweepVectorApplication(clone2DArray(cpm), new int[]{0}, new int[]{0}, 1, new int[]{0,1},
                predInd2CPMMap, true);
    }

    @Test
    public void testApplyMultipleSweepVectorsMultipleNewRowsCols() {
        final double[][] allCPM = new double[][]{{10.0000000,  1.2775780, -2.3233446,  5.5573236, -2.6207931,
                -0.8321198, -2.275524}, {1.2775780,  6.9109954, -0.6907638,  1.6104894, -0.2288084,  1.0167696,
                -4.049839}, {-2.3233446, -0.6907638,  2.9167538, -0.9018913, -0.8793179, -1.6471318,  2.448738},
                {5.5573236,  1.6104894, -0.9018913, 11.4569287, -1.9699385, -2.2898801, -2.018988}, {-2.6207931,
                -0.2288084, -0.8793179, -1.9699385,  6.9525541, -0.2876347,  2.896199}, {-0.8321198,  1.0167696,
                -1.6471318, -2.2898801, -0.2876347, 11.3756280, -8.946720}, {-2.2755243, -4.0498392,  2.4487380,
                -2.0189880,  2.8961986, -8.9467197, 10.464016}};
        // without intercept
        int[][] predInd2CPMMap = new int[][]{{0},{1,2},{3,4,5}};
        assertCorrectMultipleSVectors(clone2DArray(allCPM), new int[]{0,1,2,3,4}, new int[]{1,2}, 0, new int[]{1,2,0},
                predInd2CPMMap, false);
        assertCorrectMultipleSVectors(clone2DArray(allCPM), new int[]{0,1,2}, new int[]{0,1}, 2, new int[]{0,1,2},
                predInd2CPMMap, false);
        assertCorrectMultipleSVectors(clone2DArray(allCPM), new int[]{0,1,2,3}, new int[]{0,2}, 1, new int[]{0,2,1},
                predInd2CPMMap, false);

        // with intercept
        predInd2CPMMap = new int[][]{{0,1,2},{3,4}};
        assertCorrectMultipleSVectors(clone2DArray(allCPM), new int[]{0,1,2}, new int[]{0}, 1, new int[]{0,1},
                predInd2CPMMap, true);
        assertCorrectMultipleSVectors(clone2DArray(allCPM), new int[]{0,1}, new int[]{1}, 0, new int[]{1,0},
                predInd2CPMMap, true);
        assertCorrectMultipleSVectors(clone2DArray(allCPM), new int[]{0,1,2}, new int[]{0}, 1, new int[]{0,1},
                predInd2CPMMap, true);
    }

    public static void assertCorrectMultipleSVectors(double[][] allCPM, int[] sweepIndices, int[] predIndices,
                                                     int newPredInd, int[] allPreds, int[][] pred2CPMIndices,
                                                     boolean hasIntercept) {
        // generate correct CPM after all sweeps are performed
        double[][] correctCPM = extractPredSubsetsCPM(allCPM, allPreds, pred2CPMIndices, hasIntercept);
        sweepCPM(correctCPM, sweepIndices, false);

        int newPredCPMLength = pred2CPMIndices[newPredInd].length;
        double[][] smallCPM = extractPredSubsetsCPM(allCPM, predIndices, pred2CPMIndices, hasIntercept);
        SweepVector[][] sweepVectors = sweepCPM(smallCPM, sweepIndices, true);
        double[][] rightSizeCPM = addNewPred2CPM(allCPM, smallCPM, allPreds, pred2CPMIndices, hasIntercept);
        if (newPredCPMLength == 1) {
            applySweepVectors2NewPred(sweepVectors, rightSizeCPM, newPredCPMLength, null);
        } else {
            SweepVector[][] newSweepVec = mapBasicVector2Multiple(sweepVectors, newPredCPMLength);
            applySweepVectors2NewPred(newSweepVec, rightSizeCPM, newPredCPMLength, null);
        }
        assert2DArraysEqual(correctCPM, rightSizeCPM, 1e-6);
    }

    public static void assertCorrectOneSweepVectorApplication(double[][] allCPM, int[] sweepIndices, int[] predIndices,
                                                              int newPredInd, int[] allPreds, int[][] pred2CPMIndices,
                                                              boolean hasIntercept) {
        // generate correct CPM after all sweeps are performed
        double[][] correctCPM = extractPredSubsetsCPM(allCPM, allPreds, pred2CPMIndices, hasIntercept);
        sweepCPM(correctCPM, sweepIndices, false);

        // generate CPM after sweeping and correct application of sweep vector
        int newPredCPMLength = pred2CPMIndices[newPredInd].length;
        double[][] smallCPM = extractPredSubsetsCPM(allCPM, predIndices, pred2CPMIndices, hasIntercept);
        SweepVector[][] sweepVectors = sweepCPM(smallCPM, sweepIndices, true);
        double[][] rightSizeCPM = addNewPred2CPM(allCPM, smallCPM, allPreds, pred2CPMIndices, hasIntercept);
        if (pred2CPMIndices[newPredInd].length > 1) {
            SweepVector[][] newSweepVec = mapBasicVector2Multiple(sweepVectors, newPredCPMLength);
            oneSweepWSweepVector(newSweepVec[0], rightSizeCPM, sweepIndices[0], newPredCPMLength);
        } else {
            oneSweepWSweepVector(sweepVectors[0], rightSizeCPM, sweepIndices[0], newPredCPMLength);
        }
        // compare CPM generated manually and by program
        assert2DArraysEqual(correctCPM, rightSizeCPM, 1e-6);
    }

    public static void assertCorrectOneSweepVectors(double[][] cpm, int sweepIndex, double[] rSweepVector) {
        int numSweepVec = 2*(cpm.length+1);
        SweepVector[] sweepVec = new SweepVector[numSweepVec];
        performOneSweep(cpm, sweepVec, sweepIndex, true);

        for (int index=0; index<numSweepVec; index++) {
            assertTrue(Math.abs(rSweepVector[index]-sweepVec[index]._value)<1e-6);
        }
    }

    /**
     * In this test, I want to test and make sure that the following methods are working:
     *  - updateSV4NewPred
     *  - sweepCPMNewPred
     */
    @Test
    public void testReplaceCPMwNewPred() {
        double[][] forwardCPMOrig = new double[][]{{50000.0, -7.958078640513122E-13, 3.979039320256561E-13, 
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
                1684727.2390496698, 4.542312022970976E9}};

        int[] sweepIndices = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        SweepVector[][] originalSV =  sweepCPM(forwardCPMOrig, sweepIndices, true);
        int cpmIndex = forwardCPMOrig.length-1;

        double[][] CPMNoSweptReplaced100 = new double[][]{{50000.0, -7.958078640513122E-13, 3.979039320256561E-13, 
                2.2737367544323206E-13, 1.1652900866465643E-12, 4.689582056016661E-13, -4.547473508864641E-13, 
                -5.826450433232822E-13, 6.608047442568932E-13, 122346.71425169753}, {-7.958078640513122E-13, 49999.0,
                -345.6139787378465, -205.35413258920994, 253.29361004290251, 308.5874618379251, 56.84257753101841,
                -114.69387539976455, 143.11727542499693, -1852537.2069261894}, {3.979039320256561E-13, 
                -345.6139787378465, 49999.00000000001, 6.2282639257916514, -91.95828276351028, -115.23819328439825,
                -163.12536216455945, 88.65144562616075, 249.7592249542846, 1815126.8928365733}, 
                {2.2737367544323206E-13, -205.35413258920994, 6.2282639257916514, 49999.0, 93.6080023311965,
                180.38909518825784, 336.9634862705478, -208.96637046945852, -187.75488919015658, 1797504.437143965}, 
                {1.1652900866465643E-12, 253.29361004290251, -91.95828276351028, 93.6080023311965, 49998.99999999999,
                234.90806821539226, -174.76753981209666, 386.5044736700327, 208.42665517330633, -1768751.0314145735}, 
                {4.689582056016661E-13, 308.5874618379251, -115.23819328439825, 180.38909518825784, 234.90806821539226,
                49999.00000000001, 76.18684561711362, -85.20464948988825, -267.03987968155207, -1755817.796321408}, 
                {-4.547473508864641E-13, 56.84257753101841, -163.12536216455945, 336.9634862705478, 
                -174.76753981209666, 76.18684561711362, 49999.0, -71.27696079239956, 150.32124911394152, 
                1747644.8783670785}, {-5.826450433232822E-13, -114.69387539976455, 88.65144562616075, 
                -208.96637046945852, 386.5044736700327, -85.20464948988825, -71.27696079239956, 49999.00000000001,
                431.326600219485, -1691641.472797731}, {6.608047442568932E-13, 143.11727542499693, 249.7592249542846,
                -187.75488919015658, 208.42665517330633, -267.03987968155207, 150.32124911394152, 431.326600219485,
                49998.99999999999, 1684727.2390496698}, {122346.71425169753, -1852537.2069261894, 1815126.8928365733,
                1797504.437143965, -1768751.0314145735, -1755817.796321408, 1747644.8783670785, -1691641.472797731,
                1684727.2390496698, 4.542312022970976E9}};
        int[] replacementPredIndices = new int[]{77, 96, 74, 75, 87, 88, 85, 6};
        ModelSelection.SweepModel bestModel = new ModelSelection.SweepModel(replacementPredIndices, forwardCPMOrig,
                originalSV, forwardCPMOrig[cpmIndex][cpmIndex]);

        int numSweep = sweepIndices.length;
        double[][][] correctSVM = new double[numSweep+1][][];
        double[][] startCPM  = ArrayUtils.deepClone(CPMNoSweptReplaced100);
        correctSVM[0] = ArrayUtils.deepClone(startCPM);
        for (int index=0; index<numSweep; index++) {
            sweepCPM(startCPM, new int[]{index}, false);
            correctSVM[index+1] = ArrayUtils.deepClone(startCPM);
        }
        SweepVector[][] correctSV = sweepCPM(CPMNoSweptReplaced100, sweepIndices, true);
        
        double[][] cpmSweptReplaced100UnsweptPredBehindReplaced = new double[][]{{2.0E-5, 3.201891160038978E-22, 
                -1.5764248359833566E-22, -8.928130882178259E-23, -4.663582437088764E-22, -1.8768805992674142E-22, 
                1.802818211414199E-22, -5.826450433232822E-13, 1.3219328713400555E-17, 2.4469342850339517}, 
                {3.201891160038978E-22, 2.0002987753672538E-5, 1.3771498130208506E-7, 8.292563653986642E-8,
                -1.0073946849400272E-7, -1.2292930060903938E-7, -2.3015257926214256E-8, -114.69387539976455,
                0.002889969553821915, -36.30344770276342}, {-1.5764248359833566E-22, 1.3771498130208506E-7, 
                2.000173502528362E-5, -2.5951251532267034E-9, 3.611087701741959E-8, 4.49906309131463E-8, 
                6.5175702968484E-8, 88.65144562616075, 0.005021123947728915, 36.01693775408436}, 
                {-8.928130882178259E-23, 8.292563653986642E-8, -2.5951251532267034E-9, 2.0001982239333215E-5, 
                -3.800448531821329E-8, -7.229786864964464E-8, -1.3492686872108926E-7, -208.96637046945852, 
                -0.0037531471332681596, 35.7536766242638}, {-4.663582437088764E-22, -1.0073946849400272E-7, 
                3.611087701741959E-8, -3.800448531821329E-8, 2.0001732562065185E-5, -9.3238646280667E-8, 
                7.054501420044328E-8, 386.5044736700327, 0.004206134061447402, -34.907231185426525}, 
                {-1.8768805992674142E-22, -1.2292930060903938E-7, 4.49906309131463E-8, -7.229786864964464E-8, 
                -9.3238646280667E-8, 2.0002007065512908E-5, -3.003052941110811E-8, -85.20464948988825, 
                -0.0053580634091829156, -34.82800764931544}, {1.802818211414199E-22, -2.3015257926214256E-8, 
                6.5175702968484E-8, -1.3492686872108926E-7, 7.054501420044328E-8, -3.003052941110811E-8, 
                2.000184048470361E-5, -71.27696079239956, 0.0030677419878852776, 34.802472799989175}, 
                {-5.826450433232822E-13, -114.69387539976455, 88.65144562616075, -208.96637046945852, 
                386.5044736700327, -85.20464948988825, -71.27696079239956, 49999.00000000001, 431.326600219485, 
                -1691641.472797731}, {-1.3219328713400555E-17, -0.002889969553821915, -0.005021123947728915, 
                0.0037531471332681596, -0.004206134061447402, 0.0053580634091829156, -0.0030677419878852776, 
                431.326600219485, 49993.85901781235, 1680383.833989036}, {-2.4469342850339517, 36.30344770276342, 
                -36.01693775408436, -35.7536766242638, 34.907231185426525, 34.82800764931544, -34.802472799989175, 
                -1691641.472797731, 1680383.833989036, 4.1614002561532125E9}};
        
        double[][] cpmSweptReplaced100 = new double[][]{{2.0E-5, 3.196101465161939E-22, -1.5058688521666574E-22,
                -8.904229499644892E-23, -4.692995897186566E-22, -1.8837700701293293E-22, 1.822590868023714E-22,
                -5.826450433232822E-13, -2.644632727315847E-22, 2.446934285033952}, {3.196101465161939E-22, 
                2.0003305092168775E-5, 1.373643535013321E-7, 8.257090027642731E-8, -1.0004278825304895E-7, 
                -1.2332046606092006E-7, -2.2968393912427673E-8, -114.69387539976455, -5.780154289964054E-8, 
                -36.49279626012891}, {-1.5058688521666574E-22, 1.373643535013321E-7, 2.0004972421053125E-5, 
                -2.3844957495585706E-9, 3.459916126844147E-8, 4.4799780521274465E-8, 6.60402188236639E-8, 
                88.65144562616075, -1.0045591532834093E-7, 36.241413366137415}, {-8.904229499644892E-23, 
                8.257090027642731E-8, -2.3844957495585706E-9, 2.0002390316351033E-5, -3.8736064227916566E-8, 
                -7.182096595644172E-8, -1.3503755132187345E-7, -208.96637046945852, 7.506762659804055E-8, 
                35.96436844793575}, {-4.692995897186566E-22, -1.0004278825304895E-7, 3.459916126844147E-8, 
                -3.8736064227916566E-8, 2.0003455200174627E-5, -9.39352031734195E-8, 7.040935439686753E-8, 
                386.5044736700327, -8.411808167349468E-8, -35.3268979933469}, {-1.8837700701293293E-22, 
                -1.2332046606092006E-7, 4.4799780521274465E-8, -7.182096595644172E-8, -9.39352031734195E-8, 
                2.0002625441322806E-5, -3.028861207765157E-8, -85.20464948988825, 1.0717175007630091E-7, 
                -34.59794521081789}, {1.822590868023714E-22, -2.2968393912427673E-8, 6.60402188236639E-8, 
                -1.3503755132187345E-7, 7.040935439686753E-8, -3.028861207765157E-8, 2.0002142002791998E-5, 
                -71.27696079239956, -6.136667203061276E-8, 34.77941766646261}, {-5.826450433232822E-13, 
                -114.69387539976455, 88.65144562616075, -208.96637046945852, 386.5044736700327, -85.20464948988825,
                -71.27696079239956, 49999.00000000001, 431.326600219485, -1691641.472797731}, {-2.644632727315847E-22,
                -5.780154289964054E-8, -1.0045591532834093E-7, 7.506762659804055E-8, -8.411808167349468E-8,
                1.0717175007630091E-7, -6.136667203061276E-8, 431.326600219485, 2.0002456857516552E-5, 
                33.60876880907906}, {-2.446934285033952, 36.49279626012891, -36.241413366137415, -35.96436844793575,
                35.3268979933469, 34.59794521081789, -34.77941766646261, -1691641.472797731, -33.60876880907906, 
                4.0483385894687824E9}};

        int[] newPredSweepIndex = new int[]{7};
        List<Integer> newSweepList = Arrays.stream(newPredSweepIndex).boxed().collect(Collectors.toList());
        int svLenHalfPerSweep = originalSV[0].length/2;
        int lastSVIndex = svLenHalfPerSweep-2;
        double[][] cpmSweptReplaced100_2 = ArrayUtils.deepClone(cpmSweptReplaced100UnsweptPredBehindReplaced);

        for (int index=0; index<newPredSweepIndex[0]; index++) {
            SweepVector[] newSV = updateSV4NewPred(originalSV[index], cpmSweptReplaced100, newSweepList, index, svLenHalfPerSweep, lastSVIndex);
            sweepCPMNewPredwSVs(cpmSweptReplaced100, index, newSV, newSweepList); // sweep newly replaced predictor
            sweepCPMNewPredwSVs(correctSVM[index], index, correctSV[index], newSweepList);
            assertEqualSV(newSV, correctSV[index]);
            assertCorrectReplacedPred(correctSVM[index+1], cpmSweptReplaced100, newPredSweepIndex, 1e-12);
        }
        
        updateCPMSV(bestModel, cpmSweptReplaced100_2, newPredSweepIndex, new ArrayList<>(Arrays.asList(0,1,2,3,4,5,6,7,8)), newPredSweepIndex);
        for (int index=0; index<correctSV.length; index++) {
            System.out.println("sweeping index "+index);
            assertEqualSV(correctSV[index], bestModel._sweepVector[index]);
        }
        assert2DArraysEqual(bestModel._CPM, correctSVM[correctSVM.length-1], 1e-12);
    }
    
    public void assertCorrectReplacedPred(double[][] cpm1, double[][] cpm2, int[] predIndices, double tol) {
        int predLen = predIndices.length;
        int cpmLen = cpm1.length;
        for (int index=0; index<predLen; index++) {
            int cInd = predIndices[index];
            for (int rInd=0; rInd<cpmLen; rInd++) {
                assertTrue("Expected: " + cpm1[rInd][cInd]+" Actual: "+cpm2[rInd][cInd] + " at row "+rInd+
                        " and col "+cInd, Math.abs(cpm1[rInd][cInd]-cpm2[rInd][cInd])<tol);
                assertTrue("Expected: " + cpm1[cInd][rInd]+" Actual: "+cpm2[cInd][rInd] + " at row "+cInd+"" +
                        " and col "+rInd, Math.abs(cpm1[cInd][rInd]-cpm2[cInd][rInd])<tol);
            }
        }
    }
    
    @Test
    public void testGenNewSV() {
        final double[][] cpmOnePred = new double[][]{{50000.0, -7.958078640513122E-13, 122346.71425169753}, 
                {-7.958078640513122E-13, 49999.0, -1852537.2069261894},{122346.71425169753, -1852537.2069261894, 
                4.542312022970976E9}};
        final double[][] cpmTwoPred = new double[][]{{50000.0, -7.958078640513122E-13, 3.979039320256561E-13, 
                122346.71425169753},{-7.958078640513122E-13, 49999.0, -345.6139787378465, -1852537.2069261894},
                {3.979039320256561E-13, -345.6139787378465, 49999.00000000001, 1815126.8928365733}, 
                {122346.71425169753, -1852537.2069261894, 1815126.8928365733, 4.542312022970976E9}};
        SweepVector[][] sVectorsOnePred = sweepCPM(cpmOnePred, new int[]{0, 1}, true);
        final double[][] sweptCPMTwoPred = new double[][]{{2.0E-5, 3.1832951221076914E-22, 3.979039320256561E-13, 
                2.4469342850339504},{3.1832951221076914E-22, 2.000040000800016E-5, -345.6139787378465, 
                -37.05148516822715},{3.979039320256561E-13, -345.6139787378465, 49999.00000000001, 1815126.8928365733}, 
                {-2.4469342850339504, 37.05148516822715, 1815126.8928365733, 4.473373393755198E9}};
        SweepVector[][] sVectorsTwoPred = sweepCPM(cpmTwoPred, new int[]{0, 1}, true);
        int numSV = sVectorsOnePred.length;
        for (int index=0; index<numSV; index++) {
            SweepVector[] newSV = genNewSV(sVectorsOnePred[index], sweptCPMTwoPred, 1, index);
            assertEqualSV(newSV, sVectorsTwoPred[index]);
        }
    }
    
    public static void assertEqualSV(SweepVector[] sv1, SweepVector[] sv2) {
        int svLen = sv1.length;
        for (int index=0; index<svLen; index++) {
            assertTrue("Expected row: "+sv1[index]._row+", actual row: "+sv2[index]._row+" and they are " +
                    "different.", sv1[index]._row == sv2[index]._row);
            assertTrue("Expected col: "+sv1[index]._column+", actual col: "+sv2[index]._column+" and they are " +
                    "different.", sv1[index]._column == sv2[index]._column);
            assertTrue("Expected value: "+sv1[index]._value+", actual value: "+sv2[index]._value+" and they are " +
                    "different at index "+index, Math.abs(sv1[index]._value - sv2[index]._value) < 1e-12);
        }
     }

    @Test
    public void testPerformAllSweepingNoSweepVector() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877, -2.675484,  3.657043},
                {-3.925113,  9.961056,  4.638419,  1.719902, -2.373317},
                {1.192877,  4.638419,  9.649718, -1.855625,  3.444272},
                {-2.675484,  1.719902, -1.855625,  6.197150, -3.117239},
                {3.657043, -2.373317,  3.444272, -3.117239,  3.636824}};
        final double[][] cpmAfterSweep0N1 = new double[][]{{0.1182966,  0.04661430,  0.3573300, -0.23632874,  0.3219854},
                {0.0466143,  0.11875914,  0.6064598,  0.07953825, -0.1113826},
                {-0.3573300, -0.60645976,  6.4104528, -1.94264564 , 3.5768221},
                {0.2363287, -0.07953825, -1.9426456,  5.42805824, -2.0642052},
                {-0.3219854,  0.11138257,  3.5768221, -2.06420516, 2.1949635}};
        assertCorrectAllSweeping(clone2DArray(cpm), new int[]{0, 1}, cpmAfterSweep0N1);
        assertCorrectAllSweeping(clone2DArray(cpm), new int[]{1, 0}, cpmAfterSweep0N1);
        final double[][] cpmAfterSweep0N1N2 = new double[][]{{0.13821485,  0.08041945, -0.05574177, -0.1280422,  0.1226070},
                {0.08041945,  0.17613316, -0.09460483,  0.2633219, -0.4497672},
                {-0.05574177, -0.09460483,  0.15599522, -0.3030434,  0.5579671},
                {0.12804222, -0.26332191,  0.30304344,  4.8393522, -0.9802727},
                {-0.12260698,  0.44976719, -0.55796715, -0.9802727,  0.1992143}};
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,1,2}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,2,1}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,0,1}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,1,0}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,2,0}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,0,2}, cpmAfterSweep0N1N2);
        final double[][] cpmAfterSweep0N1N2N3 = new double[][]{{0.14160266,  0.07345233, -0.04772369,  0.02645855,  0.0966703864},
                {0.07345233,  0.19046119, -0.11109422, -0.05441263, -0.3964279716},
                {-0.04772369, -0.11109422,  0.17497200,  0.06262066,  0.4965818245},
                {0.02645855, -0.05441263,  0.06262066,  0.20663923, -0.2025627940},
                {-0.09667039,  0.39642797, -0.49658182,  0.20256279,  0.0006474807}};
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,1,2,3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,2,1,3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{3,2,1,0}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{3,1,2,0}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,0,2,3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,3,0,2}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,3,1,0}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,1,0,3}, cpmAfterSweep0N1N2N3);
    }

    @Test
    public void testPerformSweepingMSEOnly() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877, -2.675484,  3.657043},
                {-3.925113,  9.961056,  4.638419,  1.719902, -2.373317},
                {1.192877,  4.638419,  9.649718, -1.855625,  3.444272},
                {-2.675484,  1.719902, -1.855625,  6.197150, -3.117239},
                {3.657043, -2.373317,  3.444272, -3.117239,  3.636824}};
        assertCorrectSweepMSE(clone2DArray(cpm), new int[]{0});
        assertCorrectSweepMSE(clone2DArray(cpm), new int[]{0, 1});
        assertCorrectSweepMSE(clone2DArray(cpm), new int[]{0, 1, 2});
        assertCorrectSweepMSE(clone2DArray(cpm), new int[]{0, 1, 2, 3});
    }

    public static void assertCorrectSweepMSE(double[][] cpm, int[] sweepIndice) {
        double[][] subsetCPM = clone2DArray(cpm);
        sweepCPM(cpm, sweepIndice, false);
        double mse = sweepMSE(subsetCPM, Arrays.stream(sweepIndice).boxed().collect(Collectors.toList()));
        assertTrue("Expected MSE: "+cpm[cpm.length-1][cpm.length-1]+" Actual: "+mse + " and they are not " +
                "equal", Math.abs(mse-cpm[cpm.length-1][cpm.length-1]) < 1e-12);
    }

    public static void assertCorrectAllSweeping(double[][] cpm, int[] sweepIndice, double[][] correctCPM) {
        sweepCPM(cpm, sweepIndice, false);
        assert2DArraysEqual(correctCPM, cpm, 1e-6);
    }

    // test perform one sweep without considering sweeping vectors.  We test with sweeping one index at a time, 
    // multiple indice.  Sweeping action is commutative and I will be testing that with multiple indice with indice
    // permuted.
    @Test
    public void testPerformOneSweepNoSweepVector() {
        final double[][] cpm = new double[][]{{10.000000, -3.925113,  1.192877, -2.675484,  3.657043},
                {-3.925113,  9.961056,  4.638419,  1.719902, -2.373317},
                {1.192877,  4.638419,  9.649718, -1.855625,  3.444272},
                {-2.675484,  1.719902, -1.855625,  6.197150, -3.117239},
                {3.657043, -2.373317,  3.444272, -3.117239,  3.636824}};
        final double[][] cpmAfterSweep0 = new double[][]{{0.1000000, -0.3925113,  0.1192877, -0.2675484,  0.3657043},
                {0.3925113,  8.4204048,  5.1066367,  0.6697443, -0.9378863},
                {-0.1192877,  5.1066367,  9.5074224, -1.5364727,  3.0080318},
                {0.2675484,  0.6697443, -1.5364727,  5.4813285, -2.1388030},
                {-0.3657043, -0.9378863,  3.0080318, -2.1388030,  2.2994276}};
        double[][] cpmClone = clone2DArray(cpm);
        assertCorrectSweeping(cpmClone, new int[]{0}, cpmAfterSweep0);
        final double[][] cpmAfterSweep0N1 = new double[][]{{0.1182966,  0.04661430,  0.3573300, -0.23632874,  0.3219854},
                {0.0466143,  0.11875914,  0.6064598,  0.07953825, -0.1113826},
                {-0.3573300, -0.60645976,  6.4104528, -1.94264564 , 3.5768221},
                {0.2363287, -0.07953825, -1.9426456,  5.42805824, -2.0642052},
                {-0.3219854,  0.11138257,  3.5768221, -2.06420516, 2.1949635}};
        assertCorrectSweeping(cpmClone, new int[]{1}, cpmAfterSweep0N1);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,1}, cpmAfterSweep0N1);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1, 0}, cpmAfterSweep0N1);
        final double[][] cpmAfterSweep0N1N2 = new double[][]{{0.13821485,  0.08041945, -0.05574177, -0.1280422,  0.1226070},
                {0.08041945,  0.17613316, -0.09460483,  0.2633219, -0.4497672},
                {-0.05574177, -0.09460483,  0.15599522, -0.3030434,  0.5579671},
                {0.12804222, -0.26332191,  0.30304344,  4.8393522, -0.9802727},
                {-0.12260698,  0.44976719, -0.55796715, -0.9802727,  0.1992143}};
        assertCorrectSweeping(cpmClone, new int[]{2}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,1,2}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,2,1}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,0,1}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,1,0}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,2,0}, cpmAfterSweep0N1N2);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,0,2}, cpmAfterSweep0N1N2);
        final double[][] cpmAfterSweep0N1N2N3 = new double[][]{{0.14160266,  0.07345233, -0.04772369,  0.02645855,  0.0966703864},
                {0.07345233,  0.19046119, -0.11109422, -0.05441263, -0.3964279716},
                {-0.04772369, -0.11109422,  0.17497200,  0.06262066,  0.4965818245},
                {0.02645855, -0.05441263,  0.06262066,  0.20663923, -0.2025627940},
                {-0.09667039,  0.39642797, -0.49658182,  0.20256279,  0.0006474807}};
        assertCorrectSweeping(cpmClone, new int[]{3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,1,2,3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{0,2,1,3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{3,2,1,0}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{3,1,2,0}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,0,2,3}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{1,3,0,2}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,3,1,0}, cpmAfterSweep0N1N2N3);
        assertCorrectSweeping(clone2DArray(cpm), new int[]{2,1,0,3}, cpmAfterSweep0N1N2N3);
    }

    public static double[][] clone2DArray(double[][] cpm) {
        int cpmDim = cpm.length;
        double[][] cpmClone = new double[cpmDim][cpmDim];
        for (int index=0; index<cpmDim; index++)
            System.arraycopy(cpm[index], 0, cpmClone[index], 0, cpmDim);
        return cpmClone;
    }

    public static void assert2DArraysEqual(double[][] array1, double[][] array2, double tot) {
        int dimArray = array1.length;
        for (int index=0; index<dimArray; index++)
            assertArrayEquals("expected and extracted cross-product swept matrice are not equal",
                    array1[index], array2[index], tot);
    }

    public static void assertCorrectSweeping(double[][] cpm, int[] sweepIndice, double[][] correctCPM) {
        int numSweep = sweepIndice.length;
        for (int index=0; index<numSweep; index++)
            performOneSweep(cpm, null, sweepIndice[index], false);

        assert2DArraysEqual(correctCPM, cpm, 1e-6);
    }

    @Test
    public void testExtractSubsetCPM() {
        final double[] vecValues = new double[]{1, 0.1, 0.2, 0.3, 0.4};
        final double[] vecValues2 = new double[]{0.1, 0.2, 0.3, 0.4, 1};
        final double[] resp = new double[]{2};
        final double[][] allCPM = generateCPM(vecValues2, resp);
        final double[][] allCPMInterceptFirst = generateCPM(vecValues, resp);

        // tests with intercept
        assertCorrectCPMExtraction(allCPM, new int[][]{{0},{},{},{}}, allCPMInterceptFirst, new int[]{0}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1},{},{},{}}, allCPMInterceptFirst, new int[]{0}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1, 2},{},{},{}}, allCPMInterceptFirst, new int[]{0}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1, 2, 3},{},{},{}}, allCPMInterceptFirst, new int[]{0}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1}, {}, {}}, allCPMInterceptFirst, new int[]{1}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1, 2}, {}, {}}, allCPMInterceptFirst, new int[]{1}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1, 2, 3}, {}, {}}, allCPMInterceptFirst, new int[]{1}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {2}, {}}, allCPMInterceptFirst, new int[]{2}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {2, 3}, {}}, allCPMInterceptFirst, new int[]{2}, true);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {}, {3}}, allCPMInterceptFirst, new int[]{3}, true);

        // tests with intercept
        assertCorrectCPMExtraction(allCPM, new int[][]{{0}, {}, {}, {}, {}}, allCPM, new int[]{0}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1}, {}, {}, {}, {}}, allCPM, new int[]{0}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1, 2}, {}, {}, {}, {}}, allCPM, new int[]{0}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1, 2, 3}, {}, {}, {}, {}}, allCPM, new int[]{0}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{0, 1, 2, 3, 4}, {}, {}, {}, {}}, allCPM, new int[]{0}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1}, {}, {}, {}}, allCPM, new int[]{1}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1, 2}, {}, {}, {}}, allCPM, new int[]{1}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1, 2, 3}, {}, {}, {}}, allCPM, new int[]{1}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{1, 2, 3, 4}, {}, {}, {}}, allCPM, new int[]{1}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {2}, {}, {}}, allCPM, new int[]{2}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {2, 3}, {}, {}}, allCPM, new int[]{2}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {2,3,4}, {}, {}}, allCPM, new int[]{2}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{}, {}, {}, {3}, {}}, allCPM, new int[]{3}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{},{}, {}, {3,4}, {}}, allCPM, new int[]{3}, false);
        assertCorrectCPMExtraction(allCPM, new int[][]{{}, {}, {}, {}, {4}, {}}, allCPM, new int[]{4}, false);
    }

    public static double[][] generateCPM(double[] vec, double[] resp) {
        int predLen = vec.length;
        int cpmSize = predLen+1;
        Matrix vecValue = new Matrix(vec, vec.length);
        Matrix respV = new Matrix(resp, 1);
        double[][] xtx = vecValue.times(vecValue.transpose()).getArray();
        double[][] xty = vecValue.times(new Matrix(resp, 1)).getArray();
        double[][] ytx = respV.times(vecValue.transpose()).getArray();
        double yty = respV.times(respV).getArray()[0][0];
        double[][] genCPM = new double[cpmSize][cpmSize];
        int xtxLen = xtx.length;
        for (int index=0; index<xtxLen; index++) {
            System.arraycopy(xtx[index], 0, genCPM[index], 0, xtxLen);
            genCPM[index][xtxLen] = xty[index][0];
        }
        System.arraycopy(ytx[0], 0, genCPM[xtxLen], 0, xtxLen);
        genCPM[xtxLen][xtxLen] = yty;
        return genCPM;
    }

    public static void assertCorrectCPMExtraction(double[][] allCPM, int[][] pred2CMPIndices,
                                                  double[][] CPMInterceptFirst, int[] predIndices, boolean hasIntercept) {
        double[][] extractedCpm = extractPredSubsetsCPM(allCPM, predIndices, pred2CMPIndices,
                hasIntercept);
        List<Integer> predIndicesList = Arrays.stream(pred2CMPIndices[predIndices[0]]).boxed().collect(Collectors.toList());
        if (hasIntercept) {
            predIndicesList = Arrays.stream(pred2CMPIndices[predIndices[0]]).map(x -> x + 1).boxed().collect(Collectors.toList());
            predIndicesList.add(0,0);
        }
        int allCPMSize = allCPM.length;
        predIndicesList.add(allCPMSize-1);
        int cpmDim = predIndicesList.size();
        double[][] correctCPM = new double[cpmDim][cpmDim];
        // generate correct matrix
        for (int rIndex=0; rIndex<cpmDim; rIndex++) {
            for (int cIndex=rIndex; cIndex<cpmDim; cIndex++) {
                correctCPM[rIndex][cIndex] = CPMInterceptFirst[predIndicesList.get(rIndex)][predIndicesList.get(cIndex)];
                correctCPM[cIndex][rIndex] = CPMInterceptFirst[predIndicesList.get(cIndex)][predIndicesList.get(rIndex)];
            }
        }
        assert2DArraysEqual(correctCPM, extractedCpm, 1e-6);
    }

    @Test
    public void testAddNewPred2CPM() {
        final double[][] allCPM = new double[][]{{10.0000000,  1.2775780, -2.3233446,  5.5573236, -2.6207931,
                -0.8321198, -2.275524}, {1.2775780,  6.9109954, -0.6907638,  1.6104894, -0.2288084,  1.0167696,
                -4.049839}, {-2.3233446, -0.6907638,  2.9167538, -0.9018913, -0.8793179, -1.6471318,  2.448738},
                {5.5573236,  1.6104894, -0.9018913, 11.4569287, -1.9699385, -2.2898801, -2.018988}, {-2.6207931,
                -0.2288084, -0.8793179, -1.9699385,  6.9525541, -0.2876347,  2.896199}, {-0.8321198,  1.0167696,
                -1.6471318, -2.2898801, -0.2876347, 11.3756280, -8.946720}, {-2.2755243, -4.0498392,  2.4487380,
                -2.0189880,  2.8961986, -8.9467197, 10.464016}};
        int[][] predInd2CPMIndices = new int[][]{{0, 1, 2}, {3, 4}, {5}};
        boolean hasIntercept = false;
        // pred 0 is chosen and we want to add predictor 1
        assertCorrectAddNewPred2CPM(allCPM, new int[]{0,1,2}, predInd2CPMIndices, new int[]{0}, 1, hasIntercept);
        // pred 2, 1 are added, want to add pred 0
        assertCorrectAddNewPred2CPM(allCPM, new int[]{0,1,2}, predInd2CPMIndices, new int[]{2,1}, 0, hasIntercept);
        // pred 0, 2 are added want to add pred 1
        assertCorrectAddNewPred2CPM(allCPM, new int[]{0,1,2,3}, predInd2CPMIndices, new int[]{0,2}, 1, hasIntercept);
        // with intercept
        predInd2CPMIndices = new int[][]{{0,1},{2},{3},{4}};
        hasIntercept = true;
        // pred 3,2 are chosen, add 0
        assertCorrectAddNewPred2CPM(allCPM, new int[]{0,1,2}, predInd2CPMIndices, new int[]{3,2}, 0, hasIntercept);
        // pred 2,3,1 are chosen, add 0
        assertCorrectAddNewPred2CPM(allCPM, new int[]{0,1,2,3}, predInd2CPMIndices, new int[]{2,3,1}, 0, hasIntercept);
        // pred 0, 1 are chosen, add 2
        assertCorrectAddNewPred2CPM(allCPM, new int[]{0,1,2,3}, predInd2CPMIndices, new int[]{0,1}, 2, hasIntercept);
    }

    public static void assertCorrectAddNewPred2CPM(double[][] allCPM, int[] sweepIndices, int[][] predInd2CPMIndices,
                                                   int[] pred2Include, int pred2Add, boolean hasIntercept) {
        // manually generated the cpm after new predictor is included
        int[] allPred = new int[pred2Include.length+1];
        System.arraycopy(pred2Include, 0, allPred, 0, pred2Include.length);
        allPred[pred2Include.length] = pred2Add;
        double[][] manualExtractCPM = extractPredSubsetsCPM(allCPM, allPred, predInd2CPMIndices, hasIntercept);
        sweepCPM(manualExtractCPM, sweepIndices, hasIntercept);
        // addNewPred2CPM generated from program
        double[][] smallCPM = extractPredSubsetsCPM(allCPM, pred2Include, predInd2CPMIndices, hasIntercept);
        sweepCPM(smallCPM, sweepIndices, hasIntercept);
        double[][] subsetCPM = addNewPred2CPM(allCPM, smallCPM, allPred, predInd2CPMIndices, hasIntercept);

        // program generated subsetCPM is correct if it equals to smallCPM on part of the matrix that is swept.
        int subsetCPMLastInd = subsetCPM.length-1;
        for (int rInd=0; rInd < sweepIndices.length; rInd++) {
            for (int cInd=0; cInd < sweepIndices.length; cInd++) {
                assertTrue("Expected: "+subsetCPM[rInd][cInd]+" Actual: "+manualExtractCPM[rInd][cInd]+ ". They" +
                        " are different.", Math.abs(subsetCPM[rInd][cInd]-manualExtractCPM[rInd][cInd])<1e-6);
            }
            // compare the last column and row too
            assertTrue("Expected: "+subsetCPM[subsetCPMLastInd][rInd]+" Actual: "+
                            manualExtractCPM[subsetCPMLastInd][rInd]+ ". They are different.",
                    Math.abs(subsetCPM[subsetCPMLastInd][rInd]-manualExtractCPM[subsetCPMLastInd][rInd])<1e-6);
            assertTrue("Expected: "+subsetCPM[rInd][subsetCPMLastInd]+". Actual: "+
                            manualExtractCPM[rInd][subsetCPMLastInd]+ ". They are different.",
                    Math.abs(subsetCPM[rInd][subsetCPMLastInd]-manualExtractCPM[rInd][subsetCPMLastInd])<1e-6);

        }
        // for the part of CPM that is not swept, it should equal to the original matrix
        double[][] origCPM = extractPredSubsetsCPM(allCPM, allPred, predInd2CPMIndices, hasIntercept);
        origCPM[subsetCPMLastInd][subsetCPMLastInd] = subsetCPM[subsetCPMLastInd][subsetCPMLastInd];
        for (int rInd=sweepIndices.length; rInd<subsetCPM.length; rInd++) {
            for (int cInd=sweepIndices.length; cInd<subsetCPM.length; cInd++) {
                assertTrue("Expected: "+subsetCPM[rInd][cInd]+". Actual: "+origCPM[rInd][cInd]+".  They are" +
                        " different.", Math.abs(subsetCPM[rInd][cInd]-origCPM[rInd][cInd])<1e-6);
            }
            // check last row/column
            assertTrue("Expected: "+subsetCPM[subsetCPMLastInd][rInd]+" Actual: "+
                            manualExtractCPM[subsetCPMLastInd][rInd]+ ". They are different.",
                    Math.abs(subsetCPM[subsetCPMLastInd][rInd]-origCPM[subsetCPMLastInd][rInd])<1e-6);
            assertTrue("Expected: "+subsetCPM[rInd][subsetCPMLastInd]+". Actual: "+
                            origCPM[rInd][subsetCPMLastInd]+ ". They are different.",
                    Math.abs(subsetCPM[rInd][subsetCPMLastInd]-origCPM[rInd][subsetCPMLastInd])<1e-6);
        }
    }

    @Test
    public void testPredIndex2CPMIndices() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            Arrays.stream(eCol).forEach(x -> origF.replace(x, origF.vec(x).toCategoricalVec()).remove());
            DKV.put(origF);
            Scope.track(origF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._family = gaussian;
            parms._train = origF._key;
            DataInfo dinfo =  new DataInfo(origF.clone(), null, 1, false,
                    DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false,
                    parms.makeImputer(), false, false, false, false, null);
            // manually generate predictor indices to CPM row/column indices
            String[] predictorNames = dinfo._adaptedFrame.names(); // predictor names plus response column
            int numPred = predictorNames.length-1;
            int[] catOffset = dinfo._catOffsets;
            int[] numOffset = dinfo._numOffsets;
            int[][] mPred2CPMMap = new int[numPred][];
            for (int catInd=0; catInd<dinfo._nums; catInd++) {
                int numRowCols = catOffset[catInd+1]-catOffset[catInd];
                mPred2CPMMap[catInd] = IntStream.iterate(catOffset[catInd], x->x+1).limit(numRowCols).toArray();
            }
            int totCol = dinfo._cats+dinfo._nums;
            for (int numInd=0; numInd<dinfo._nums; numInd++)
                mPred2CPMMap[numInd+dinfo._cats] = new int[]{numOffset[numInd]};

            // generated from model
            int[][] predictorIndex2CPMIndices = mapPredIndex2CPMIndices(dinfo, predictorNames.length-1);

            // compare manually generated and program generated predInd2MapIndices
            assertTrue(mPred2CPMMap.length==predictorIndex2CPMIndices.length);
            for (int index=0; index<numPred; index++) {
                assertArrayEquals("expected and extracted predInd2CPMIndices arrays are not equal",
                        mPred2CPMMap[index], predictorIndex2CPMIndices[index]);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testMaxRSweepEnumOnly() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            //Frame origF = Scope.track(parseTestFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            Arrays.stream(eCol).forEach(x -> origF.replace(x, origF.vec(x).toCategoricalVec()).remove());
            DKV.put(origF);
            Scope.track(origF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C11","C12","C13","C14","C15","C16","C17","C18","C19","C20"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweep;
            parms._build_glm_model = false;
            ModelSelectionModel modelMaxRSweep = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Frame resultFrameSweep = modelMaxRSweep.result();
            Scope.track(resultFrameSweep);
            Scope.track_generic(modelMaxRSweep);
            
            parms._build_glm_model = true;
            ModelSelectionModel modelMaxRSweepGLM = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Frame resultFrameSweepGLM = modelMaxRSweepGLM.result();
            Scope.track(resultFrameSweepGLM);
            Scope.track_generic(modelMaxRSweepGLM);

            parms._mode = maxr;
            ModelSelectionModel modelMaxR = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxR);
            Frame resultMaxR = modelMaxR.result();
            Scope.track(resultMaxR);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(1)), new Frame(resultMaxR.vec(2)), 1e-6);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepGLM.vec(2)), new Frame(resultMaxR.vec(2)), 1e-6);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(2)), new Frame(resultMaxR.vec(3)), 0);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepGLM.vec(3)), new Frame(resultMaxR.vec(3)), 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testMaxRSweepMixedColumns() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            int[] eCol = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            Arrays.stream(eCol).forEach(x -> origF.replace(x, origF.vec(x).toCategoricalVec()).remove());
            DKV.put(origF);
            Scope.track(origF);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C11","C12","C13","C14","C15","C16","C17","C18"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweep;
            parms._build_glm_model = false;
            ModelSelectionModel modelMaxRSweep = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Frame resultFrameSweep = modelMaxRSweep.result();
            Scope.track(resultFrameSweep);
            Scope.track_generic(modelMaxRSweep);
            
            parms._build_glm_model = true;
            ModelSelectionModel modelMaxRSweepGLM = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Frame resultFrameSweepGLM = modelMaxRSweepGLM.result();
            Scope.track(resultFrameSweepGLM);
            Scope.track_generic(modelMaxRSweepGLM);
            

            parms._mode = maxr;
            ModelSelectionModel modelMaxR = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxR);
            Frame resultMaxR = modelMaxR.result();
            Scope.track(resultMaxR);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepGLM.vec(2)), new Frame(resultMaxR.vec(2)), 1e-6);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepGLM.vec(3)), new Frame(resultMaxR.vec(3)), 0);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(1)), new Frame(resultMaxR.vec(2)), 1e-6);
            for (int rInd = 0; rInd < resultMaxR.numRows(); rInd++) {
                String sweepOne = resultFrameSweep.vec(2).stringAt(rInd);
                List<String> sweepOneList = Stream.of(sweepOne.split(", ")).collect(Collectors.toList());
                String maxrOne = resultMaxR.vec(3).stringAt(rInd);
                List<String> maxrOneList = Stream.of(maxrOne.split(", ")).collect(Collectors.toList());
                for (String oneWord : sweepOneList) {
                    assertTrue(maxrOneList.contains(oneWord));
                }
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testMaxRSweepNumericalOnly() {
        Scope.enter();
        try {
            Frame origF = Scope.track(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"));
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._ignored_columns = new String[]{"C1","C2","C3","C4","C5","C6","C7","C8","C9","C10"};
            parms._family = gaussian;
            parms._max_predictor_number = 5;
            parms._train = origF._key;
            parms._mode = maxrsweep;
            parms._build_glm_model = false;
            ModelSelectionModel modelMaxRSweep = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxRSweep);
            Frame resultFrameSweep = modelMaxRSweep.result();
            Scope.track(resultFrameSweep);
            
            parms._build_glm_model = true;
            ModelSelectionModel modelMaxRSweepGLM = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxRSweepGLM);
            Frame resultFrameSweepGLM = modelMaxRSweepGLM.result();
            Scope.track(resultFrameSweepGLM);
            
            parms._mode = maxr;
            ModelSelectionModel modelMaxR = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxR);
            Frame resultMaxR = modelMaxR.result();
            Scope.track(resultMaxR);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepGLM.vec(3)), new Frame(resultMaxR.vec(3)), 1e-6);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweepGLM.vec(4)), new Frame(resultMaxR.vec(4)), 0);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(2)), new Frame(resultMaxR.vec(3)), 1e-6);
            TestUtil.assertIdenticalUpToRelTolerance(new Frame(resultFrameSweep.vec(3)), new Frame(resultMaxR.vec(4)), 0);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Test and make sure the added and removed predictors are captured in both the result frame and the model summary.
     * In particular, I want to make sure that they agree.  The correctness of the added/removed predictors are tested
     * in Python unit test and won't be repeated here.
     */
    @Test
    public void testAddedRemovedCols() {
        Scope.enter();
        try {
            Frame train = Scope.track(massageFrame(parseTestFile("smalldata/glm_test/gaussian_20cols_10000Rows.csv"),
                    gaussian));
            DKV.put(train);
            ModelSelectionModel.ModelSelectionParameters parms = new ModelSelectionModel.ModelSelectionParameters();
            parms._response_column = "C21";
            parms._family = gaussian;
            parms._max_predictor_number = 3;
            parms._seed = 12345;
            parms._train = train._key;
            parms._mode = maxrsweep;
            parms._build_glm_model = false;
            ModelSelectionModel modelMaxrsweep = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxrsweep); //  model with validation dataset
            compareResultFModelSummary(modelMaxrsweep);

            parms._build_glm_model = true;
            ModelSelectionModel modelMaxrsweepGLM = new hex.modelselection.ModelSelection(parms).trainModel().get();
            Scope.track_generic(modelMaxrsweepGLM); //  model with validation dataset
            compareResultFModelSummary(modelMaxrsweepGLM);
        } finally {
            Scope.exit();
        }
    }
}
