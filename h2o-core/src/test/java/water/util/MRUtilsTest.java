package water.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

/**
 * Test VecUtils interface.
 */
public class MRUtilsTest extends TestUtil {
    @BeforeClass
    static public void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testSampleWithWeight() {
        Frame f = parseTestFile("bigdata/laptop/lending-club/loan.csv");
        Scope.enter();
        Scope.track(f);
        String column = "loan_amnt";
        try {
            Frame f1 = MRUtils.sampleFrame(f, 10000, 1);
            Frame f2 = MRUtils.sampleFrame(f, 10000, 2);
            Frame f3 = MRUtils.sampleFrame(f, 10000, 3);
            Frame f4 = MRUtils.sampleFrame(f, 10000, 4);
            Frame f5 = MRUtils.sampleFrame(f, 10000, 5);
            Frame f6 = MRUtils.sampleFrame(f, 10000, 6);
            Scope.track(f1, f2, f3, f4, f5, f6);

            double mean_weight = (
                    f1.vec(column).mean() * f1.numRows() +
                            f2.vec(column).mean() * f2.numRows() +
                            f3.vec(column).mean() * f3.numRows() +
                            f4.vec(column).mean() * f4.numRows() +
                            f5.vec(column).mean() * f5.numRows() +
                            f6.vec(column).mean() * f6.numRows()) / 6;

            Frame f1_weighted = MRUtils.sampleFrame(f, 10000, column, 1);
            Frame f2_weighted = MRUtils.sampleFrame(f, 10000, column, 2);
            Frame f3_weighted = MRUtils.sampleFrame(f, 10000, column, 3);
            Frame f4_weighted = MRUtils.sampleFrame(f, 10000, column, 4);
            Frame f5_weighted = MRUtils.sampleFrame(f, 10000, column, 5);
            Frame f6_weighted = MRUtils.sampleFrame(f, 10000, column, 6);
            Scope.track(f1_weighted, f2_weighted, f3_weighted, f4_weighted, f5_weighted, f6_weighted);

            double mean_weight_weighted = (
                    f1_weighted.vec(column).mean() * f1_weighted.numRows() +
                            f2_weighted.vec(column).mean() * f2_weighted.numRows() +
                            f3_weighted.vec(column).mean() * f3_weighted.numRows() +
                            f4_weighted.vec(column).mean() * f4_weighted.numRows() +
                            f5_weighted.vec(column).mean() * f5_weighted.numRows() +
                            f6_weighted.vec(column).mean() * f6_weighted.numRows()) / 6;

            // Sampling when considering the weights should have significantly higher sums of the weights than without considering them.
            assert 0.8 * mean_weight_weighted > mean_weight;

        } finally {
            Scope.exit();
        }
    }

}
