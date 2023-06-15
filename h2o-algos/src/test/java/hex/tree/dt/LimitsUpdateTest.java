package hex.tree.dt;

import org.apache.commons.math3.util.Precision;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.tree.dt.binning.Histogram.getFeaturesLimitsForConditions;
import static org.junit.Assert.*;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class LimitsUpdateTest extends TestUtil {
    
    @Test
    public void testBinningBasicData() {
        try {
            Scope.enter();
            Frame basicData = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
                    .withDataForCol(2, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))

                    .withColNames("First", "Prediction", "Second")
                    .build();

            Vec response = basicData.remove("Prediction");
            basicData.add("Prediction", response);
            
            Scope.track_generic(basicData);

            DataFeaturesLimits wholeDataLimits = DT.getInitialFeaturesLimits(basicData);

            // count of features
            assertEquals(basicData.numCols() - 1, wholeDataLimits.featuresCount());

            // min and max values of features
            for (int i = 0; i < wholeDataLimits.featuresCount(); i++) {
                // check that lower limit is lower than the minimum value
                assertTrue(basicData.vec(i).min() > ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._min);
                assertEquals(basicData.vec(i).max(),
                        ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._max,
                        Precision.EPSILON);
            }
            
            // update data limits - set 8.1 as new max for feature 0
            DataFeaturesLimits newDataLimits = wholeDataLimits.updateMax(0, 8.1);
            
            // check that new max was set
            assertEquals(8.1, ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._max, Precision.EPSILON);
            assertNotEquals( ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(0))._max,
                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._max, Precision.EPSILON);
            // check that min din not change
            assertEquals(((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(0))._min,
                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._min, Precision.EPSILON);
            
            DataFeaturesLimits newRealDataLimits = getFeaturesLimitsForConditions(basicData, newDataLimits);
            
            // test that real limits are more concrete that the limitations. 
            // For specific data new max of feature 1 will be 8 instead of 9.
            assertEquals(8.0, ((NumericFeatureLimits) newRealDataLimits.getFeatureLimits(1))._max, Precision.EPSILON);
            assertTrue(((NumericFeatureLimits) newRealDataLimits.getFeatureLimits(1))._max <
                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(1))._max);
            
            // test when limitations are null
            DataFeaturesLimits dataLimitsWithoutLimitations = getFeaturesLimitsForConditions(basicData,
                    null);
            // must be equal to original whole data limits
            assertTrue(dataLimitsWithoutLimitations.equals(wholeDataLimits));
        } finally {
            Scope.exit();
        }

    }

    @Test
    public void testBinningSmallData() {
        try {
            Scope.enter();
            Frame data = Scope.track(parseTestFile("smalldata/testng/airlines_train_preprocessed.csv"));
            data.replace(0, data.vec(0).toCategoricalVec()).remove();
            // manually put prediction column as the last one
            Vec response = data.remove("IsDepDelayed");
            data.add("IsDepDelayed", response);
            DKV.put(data);

            Scope.track_generic(data);

            DataFeaturesLimits wholeDataLimits = DT.getInitialFeaturesLimits(data);

            // count of features
            assertEquals(data.numCols() - 1, wholeDataLimits.featuresCount());

            // min and max values of features
            for (int i = 0; i < wholeDataLimits.featuresCount(); i++) {
                // check that lower limit is lower than the minimum value
                assertTrue(data.vec(i).min() > ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._min);
                assertEquals(data.vec(i).max(),
                        ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(i))._max,
                        Precision.EPSILON);
            }

            // update data limits - set 1988 as new max for feature 0 (fYear)
            DataFeaturesLimits newDataLimits = wholeDataLimits.updateMax(0, 1988);

            // check that new max was set
            assertEquals(1988, ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._max, Precision.EPSILON);
            assertNotEquals( ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(0))._max,
                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._max, Precision.EPSILON);
            // check that min din not change
            assertEquals(((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(0))._min,
                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(0))._min, Precision.EPSILON);

            DataFeaturesLimits newRealDataLimits = getFeaturesLimitsForConditions(data, newDataLimits);

            // test that real limits are more concrete that the limitations. 
            // For airlines data new max of feature 7 (Distance) is 954 for new limitations on feature 0 (fYear <= 1988).
            // data[(data.fYear <= 1988)].Distance.max() -> 954
            // data.Distance.max() -> 3365
            assertEquals(954, ((NumericFeatureLimits) newRealDataLimits.getFeatureLimits(7))._max, Precision.EPSILON);
            assertEquals(3365, ((NumericFeatureLimits) wholeDataLimits.getFeatureLimits(7))._max, Precision.EPSILON);
            assertTrue(((NumericFeatureLimits) newRealDataLimits.getFeatureLimits(7))._max <
                    ((NumericFeatureLimits) newDataLimits.getFeatureLimits(7))._max);

            // test when limitations are null
            DataFeaturesLimits dataLimitsWithoutLimitations = getFeaturesLimitsForConditions(data, null);
            // must be equal to original whole data limits
            assertTrue(dataLimitsWithoutLimitations.equals(wholeDataLimits));
        } finally {
            Scope.exit();
        }
    }
}
