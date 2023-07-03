package hex.tree.dt;

import hex.tree.dt.binning.Bin;
import hex.tree.dt.binning.BinningStrategy;
import hex.tree.dt.binning.Histogram;
import hex.tree.dt.mrtasks.CountBinsSamplesCountsMRTask;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hex.tree.dt.DT.getInitialFeaturesLimits;
import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class BinningTest extends TestUtil {
    
    
    private void testBinningValidity(Histogram histogram, int numRows) {
        // min and max values
        for (int i = 0; i < histogram.featuresCount(); i++) {
            List<Bin> featureBins = histogram.getFeatureBins(i);
            assertFalse(featureBins.isEmpty());
            // at least first and last bins are not empty
            assertNotEquals(0, featureBins.get(0)._count);
            assertNotEquals(0, featureBins.get(featureBins.size() - 1)._count);
            // all values are distributed to bins
            assertEquals(numRows, featureBins.stream().map(b -> b._count).reduce(0, Integer::sum).intValue());
        }
    }
    
    @Test
    public void testBinningBasicData() {
        try {
            Scope.enter();
            Frame basicData = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            DataFeaturesLimits wholeDataLimits = getInitialFeaturesLimits(basicData);

            Histogram histogram = new Histogram(basicData, wholeDataLimits, BinningStrategy.EQUAL_WIDTH);
            // count of features
            assertEquals(basicData.numCols() - 1, histogram.featuresCount());
            int numRows = (int) basicData.numRows();
            testBinningValidity(histogram, numRows);
            
            // test counts of samples in each bin. Specific for given data
            // feature 0, count all
            assertEquals(Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1, 1, 1), 
                    histogram.getFeatureBins(0).stream().map(b -> b._count).collect(Collectors.toList()));
            // feature 0, count 0
            assertEquals(Arrays.asList(0, 0, 1, 0, 1, 0, 1, 0, 0, 0), 
                    histogram.getFeatureBins(0).stream().map(b -> b._count0).collect(Collectors.toList()));
            // feature 1, count all
            assertEquals(Arrays.asList(7, 1, 0, 0, 0, 0, 0, 0, 1, 1),
                    histogram.getFeatureBins(1).stream().map(b -> b._count).collect(Collectors.toList()));
            // feature 1, count 0
            assertEquals(Arrays.asList(3, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                    histogram.getFeatureBins(1).stream().map(b -> b._count0).collect(Collectors.toList()));
            
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testBinSamplesCountBasicData() {
        try {
            Scope.enter();
            Frame basicData = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            DataFeaturesLimits dataLimits = getInitialFeaturesLimits(basicData);
            Histogram histogram = new Histogram(basicData, dataLimits, BinningStrategy.EQUAL_WIDTH);
            
            double[][] binsArray = histogram.getFeatureBins(0).stream()
                    .map(bin -> new double[]{bin._min, bin._max, 0, 0}).toArray(double[][]::new);
            
            CountBinsSamplesCountsMRTask task = new CountBinsSamplesCountsMRTask(0, 
                    dataLimits.toDoubles(), binsArray).doAll(basicData);
            
            assertEquals(10, task._bins.length);
                    
            assert(task._bins[0][0] < basicData.vec(0).min());
            assert(task._bins[0][1] < 1 && task._bins[0][1] > 0.8);
            assert(task._bins[0][2] == 1.0);
            assert(task._bins[0][3] == 0.0);

            assert(task._bins[1][0] == task._bins[0][1]);
            assert(task._bins[1][1] < 2);
            assert(task._bins[1][2] == 1.0);
            assert(task._bins[1][3] == 0.0);

            assert(task._bins[2][0] == task._bins[1][1]);
            assert(task._bins[2][1] < 3);
            assert(task._bins[2][2] == 1.0);
            assert(task._bins[2][3] == 1.0);


            binsArray = histogram.getFeatureBins(1).stream()
                    .map(bin -> new double[]{bin._min, bin._max, 0, 0}).toArray(double[][]::new);

            task = new CountBinsSamplesCountsMRTask(1, dataLimits.toDoubles(), binsArray).doAll(basicData);

            assertEquals(10, task._bins.length);

            assert(task._bins[0][0] < basicData.vec(1).min());
            assert(task._bins[0][1] == 1.69);
            assert(task._bins[0][2] == 7.0);
            assert(task._bins[0][3] == 3.0);

            assert(task._bins[1][0] == task._bins[0][1]);
            assert(task._bins[1][1] == 2.5);
            assert(task._bins[1][2] == 1.0);
            assert(task._bins[1][3] == 0.0);

            assert(task._bins[2][0] == task._bins[1][1]);
            assert(task._bins[2][1] == 3.32);
            assert(task._bins[2][2] == 0.0);
            assert(task._bins[2][3] == 0.0);
            
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBinningProstateData() {
        try {
            Scope.enter();

            Frame prostateData = Scope.track(parseTestFile("smalldata/testng/prostate_train.csv"));

            // manually put prediction column as the last one
            Vec response = prostateData.remove("CAPSULE");
            prostateData.add("CAPSULE", response);
            
            Scope.track_generic(prostateData);

            DataFeaturesLimits wholeDataLimits = getInitialFeaturesLimits(prostateData);

            // count of features
            assertEquals(prostateData.numCols() - 1, wholeDataLimits.featuresCount());

            // min and max values of features
            for (int i = 0; i < wholeDataLimits.featuresCount(); i++) {
                // check that lower limit is lower than the minimum value
                assertTrue(prostateData.vec(i).min() > wholeDataLimits.getFeatureLimits(i)._min);
                assertEquals(prostateData.vec(i).max(),
                        wholeDataLimits.getFeatureLimits(i)._max,
                        Precision.EPSILON);
            }


            Histogram histogram = new Histogram(prostateData, wholeDataLimits, BinningStrategy.EQUAL_WIDTH);
            // count of features
            assertEquals(prostateData.numCols() - 1, histogram.featuresCount());
            int numRows = (int) prostateData.numRows();
            testBinningValidity(histogram, numRows);

        } finally {
            Scope.exit();
        }
    }


    @Test
    public void testBinningAirlinesData() {
        try {
            Scope.enter();
            Frame data = Scope.track(parseTestFile("smalldata/testng/airlines_train_preprocessed.csv"));
            data.replace(0, data.vec(0).toCategoricalVec()).remove();
            // manually put prediction column as the last one
            Vec response = data.remove("IsDepDelayed");
            data.add("IsDepDelayed", response);
            DKV.put(data);

            Scope.track_generic(data);

            DataFeaturesLimits wholeDataLimits = getInitialFeaturesLimits(data);

            // count of features
            assertEquals(data.numCols() - 1, wholeDataLimits.featuresCount());

            // min and max values of features
            for (int i = 0; i < wholeDataLimits.featuresCount(); i++) {
                // check that lower limit is lower than the minimum value
                assertTrue(data.vec(i).min() > wholeDataLimits.getFeatureLimits(i)._min);
                assertEquals(data.vec(i).max(), wholeDataLimits.getFeatureLimits(i)._max, Precision.EPSILON);
            }

            Histogram histogram = new Histogram(data, wholeDataLimits, BinningStrategy.EQUAL_WIDTH);
            // count of features
            assertEquals(data.numCols() - 1, histogram.featuresCount());
            int numRows = (int) data.numRows();
            testBinningValidity(histogram, numRows);

        } finally {
            Scope.exit();
        }
    }

}
