//package hex.tree.sdt;
//
//import hex.tree.dt.NumericFeatureLimits;
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
//import static hex.tree.sdt.binning.Histogram.getFeaturesLimitsForConditions;
//import static org.junit.Assert.*;
//
//
//@CloudSize(1)
//@RunWith(H2ORunner.class)
//public class LimitsUpdateTest extends TestUtil {
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
//            Scope.track_generic(basicData);
//
//            DataFeaturesLimits wholeDataLimits = SDT.getInitialFeaturesLimits(basicData);
//
//            // count of features
//            assertEquals(basicData.numCols() - 1, wholeDataLimits.featuresCount());
//
//            // min and max values of features
//            for (int i = 0; i < wholeDataLimits.featuresCount(); i++) {
//                // check that lower limit is lower than the minimum value
//                assertTrue(basicData.vec(i).min() > ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._min);
//                assertEquals(basicData.vec(i).max(),
//                        ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._max,
//                        Precision.EPSILON);
//            }
//            
//            // update data limits - set 8.1 as new max for feature 0
//            DataFeaturesLimits newDataLimits = wholeDataLimits.updateMax(0, 8.1);
//            
//            // check that new max was set
//            assertEquals(8.1, ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._max, Precision.EPSILON);
//            assertNotEquals( ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(0))._max,
//                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._max, Precision.EPSILON);
//            // check that min din not change
//            assertEquals(((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(0))._min,
//                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._min, Precision.EPSILON);
//            
//            DataFeaturesLimits newRealDataLimits = getFeaturesLimitsForConditions(basicData, newDataLimits);
//            
//            // test that real limits are more concrete that the limitations. 
//            // For specific data new max of feature 1 will be 8 instead of 9.
//            assertEquals(8.0, ((NumericFeatureLimits) newRealDataLimits.getFeatureLimits(1))._max, Precision.EPSILON);
//            assertTrue(((NumericFeatureLimits) newRealDataLimits.getFeatureLimits(1))._max <
//                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(1))._max);
//            
//            // test when limitations are null
//            DataFeaturesLimits dataLimitsWithoutLimitations = getFeaturesLimitsForConditions(basicData,
//                    null);
//            // must be equal to original whole data limits
//            assertTrue(dataLimitsWithoutLimitations.equals(wholeDataLimits));
//        } finally {
//            Scope.exit();
//        }
//
//    }
//}
