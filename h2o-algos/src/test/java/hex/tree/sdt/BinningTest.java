//package hex.tree.sdt;
//
//import hex.tree.dt.NumericFeatureLimits;
//import hex.tree.dt.binning.NumericBin;
//import hex.tree.sdt.binning.BinningStrategy;
//import hex.tree.sdt.binning.Histogram;
//import hex.tree.sdt.mrtasks.CountBinSamplesCountMRTask;
//import org.apache.commons.math3.util.Precision;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import water.Scope;
//import water.TestUtil;
//import water.fvec.Frame;
//import water.fvec.TestFrameBuilder;
//import water.fvec.Vec;
//import water.runner.CloudSize;
//import water.runner.H2ORunner;
//
//import java.util.Arrays;
//import java.util.List;
//import java.util.stream.Collectors;
//
//import static hex.tree.sdt.SDT.getInitialFeaturesLimits;
//import static org.junit.Assert.*;
//
//@CloudSize(1)
//@RunWith(H2ORunner.class)
//public class BinningTest extends TestUtil {
//    
//    private void testBinSamplesCount(Frame data, double[][] initialLimits) {
//
//        CountBinSamplesCountMRTask task =
//                new CountBinSamplesCountMRTask(0, 1.0, 2.0, initialLimits);
//        task.doAll(data);
//        assertEquals(1, task._count);
//        assertEquals(1, task._count0);
//
//        task = new CountBinSamplesCountMRTask(0, 0.9, 2.0, initialLimits);
//        task.doAll(data);
//        assertEquals(2, task._count);
//        assertEquals(1, task._count0);
//
//        task = new CountBinSamplesCountMRTask(1, 1.0, 6.0, initialLimits);
//        task.doAll(data);
//        assertEquals(5, task._count);
//        assertEquals(0, task._count0);
//
//        task = new CountBinSamplesCountMRTask(1, 0.88, 6.0, initialLimits);
//        task.doAll(data);
//        assertEquals(5, task._count);
//        assertEquals(0, task._count0);
//    }
//    
//    private void testBinningValidity(Histogram histogram, int numRows) {
//        // min and max values
//        for (int i = 0; i < histogram.featuresCount(); i++) {
//            List<NumericBin> featureBins = histogram.getFeatureBins(i);
//            assertFalse(featureBins.isEmpty());
//            // at least first and last bins are not empty
//            assertNotEquals(0, featureBins.get(0)._count);
//            assertNotEquals(0, featureBins.get(featureBins.size() - 1)._count);
//            // all values are distributed to bins
//            assertEquals(numRows,
//                    featureBins.stream().map(b -> b._count).reduce(0, Integer::sum).intValue());
//        }
//    }
//    
//    @Test
//    public void testBinningBasicData() {
//        try {
//            Scope.enter();
//            Frame basicData = new TestFrameBuilder()
//                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
//                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                    .withDataForCol(1, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
//                    .withDataForCol(2, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
//
//                    .withColNames("First", "Prediction", "Second")
//                    .build();
//
//            Vec response = basicData.remove("Prediction");
//            basicData.add("Prediction", response);
//
//            DataFeaturesLimits wholeDataLimits = getInitialFeaturesLimits(basicData);
//
//            Histogram histogram = new Histogram(basicData, wholeDataLimits, BinningStrategy.EQUAL_WIDTH);
//            // count of features
//            assertEquals(basicData.numCols() - 1, histogram.featuresCount());
//            int numRows = (int) basicData.numRows();
//            testBinningValidity(histogram, numRows);
//            
//            // test counts of samples in each bin. Specific for given data
//            // feature 0, count all
//            assertEquals(Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1, 1, 1), 
//                    histogram.getFeatureBins(0).stream().map(b -> b._count).collect(Collectors.toList()));
//            // feature 0, count 0
//            assertEquals(Arrays.asList(0, 0, 1, 0, 1, 0, 1, 0, 0, 0), 
//                    histogram.getFeatureBins(0).stream().map(b -> b._count0).collect(Collectors.toList()));
//            // feature 1, count all
//            assertEquals(Arrays.asList(7, 1, 0, 0, 0, 0, 0, 0, 1, 1),
//                    histogram.getFeatureBins(1).stream().map(b -> b._count).collect(Collectors.toList()));
//            // feature 1, count 0
//            assertEquals(Arrays.asList(3, 0, 0, 0, 0, 0, 0, 0, 0, 0),
//                    histogram.getFeatureBins(1).stream().map(b -> b._count0).collect(Collectors.toList()));
//
//            // test samples count in bins 
//            testBinSamplesCount(basicData, wholeDataLimits.toDoubles());
//            
//        } finally {
//            Scope.exit();
//        }
//    }
//    
//    
//
//    @Test
//    public void testBinningProstateData() {
//        try {
//            Scope.enter();
//
//            Frame prostateData = Scope.track(parseTestFile("smalldata/prostate/prostate_train.csv"));
//
//            Vec response = prostateData.remove("CAPSULE");
//            prostateData.add("CAPSULE", response);
//            
//            Scope.track_generic(prostateData);
//
//            DataFeaturesLimits wholeDataLimits = getInitialFeaturesLimits(prostateData);
//
//            // count of features
//            assertEquals(prostateData.numCols() - 1, wholeDataLimits.featuresCount());
//
//            // min and max values of features
//            for (int i = 0; i < wholeDataLimits.featuresCount(); i++) {
//                // check that lower limit is lower than the minimum value
//                assertTrue(prostateData.vec(i).min() > ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._min);
//                assertEquals(prostateData.vec(i).max(),
//                        ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._max,
//                        Precision.EPSILON);
//            }
//
//
//            Histogram histogram = new Histogram(prostateData, wholeDataLimits, BinningStrategy.EQUAL_WIDTH);
//            // count of features
//            assertEquals(prostateData.numCols() - 1, histogram.featuresCount());
//            int numRows = (int) prostateData.numRows();
//            testBinningValidity(histogram, numRows);
//
//        } finally {
//            Scope.exit();
//        }
//    }
//
//}
