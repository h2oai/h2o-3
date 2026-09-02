package hex.tree.dt;

import hex.tree.dt.binning.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.stream.Collectors;

import static hex.tree.dt.BinningTest.testBinningValidity;
import static hex.tree.dt.DT.getInitialFeaturesLimits;
import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class SplittingTest extends TestUtil {
    
    
    @Test
    public void testNumericSplitting() {
        try {
            Scope.enter();
            Frame basicData = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ar("two", "one", "three", "two", "two", "one", "one", "one", "three", "three"))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            DataFeaturesLimits wholeDataLimits = getInitialFeaturesLimits(basicData);

            Histogram histogram = new Histogram(basicData, wholeDataLimits, BinningStrategy.EQUAL_WIDTH, 2);
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
                    histogram.getFeatureBins(0).stream().map(b -> b._classesDistribution[0]).collect(Collectors.toList()));
            // feature 1, count all
            assertEquals(Arrays.asList(4, 3, 3),
                    histogram.getFeatureBins(1).stream().map(b -> b._count).collect(Collectors.toList()));
            // feature 1, count 0
            assertEquals(Arrays.asList(1, 1, 1),
                    histogram.getFeatureBins(1).stream().map(b -> b._classesDistribution[0]).collect(Collectors.toList()));
            
        } finally {
            Scope.exit();
        }
    }
    
}
