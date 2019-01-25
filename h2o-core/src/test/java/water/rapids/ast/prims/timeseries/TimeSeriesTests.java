package water.rapids.ast.prims.timeseries;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.TestUtil;
import water.TestUtilSharedResources;
import water.fvec.Frame;
import water.parser.BufferedString;
import water.rapids.Rapids;
import water.rapids.Val;
import hex.CreateFrame;


public class TimeSeriesTests extends TestUtilSharedResources {
    private static Frame f = null, fr1=null, fr2=null;
    private static CreateFrame cf = null;

    @BeforeClass public static void setup() {
        stall_till_cloudsize(1);
        cf = new CreateFrame();
        cf.rows = 2;
        cf.cols = 256;
        cf.binary_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.categorical_fraction = 0.0;
        cf.integer_fraction = 0.0;
        cf.missing_fraction = 0.0;
        cf.seed = 123;
        f = cf.execImpl().get();
    }

    @AfterClass public static void teardown() {
        f.delete(); fr1.delete(); fr2.delete();
    }

    @Test public void testIsax() {
        Val res1 = Rapids.exec("(cumsum " + f._key + " 1)"); //
        fr1 = res1.getFrame();
        DKV.put(fr1);
        Val res2 = Rapids.exec("(isax " + fr1._key + " 10 10 0)"); // 10 words 10 max cardinality 0 optimize card
        fr2 = res2.getFrame();
        String expected = "0^10_0^10_0^10_0^10_5^10_7^10_8^10_9^10_9^10_8^10";
        final String actual = fr2.vec(0).atStr(new BufferedString(), 0).toString();
        Assert.assertEquals(expected, actual);
    }
}
