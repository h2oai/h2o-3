package water.rapids.ast.prims.timeseries;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValRow;
import hex.CreateFrame;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class TimeSeriesTests extends TestUtil{
    private static Frame f = null;
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
        f = cf.execImpl().get();
    }

    @AfterClass public static void teardown() {
        f.delete();
    }

    @Test public void testIsax() {
        Val res = Rapids.exec("(isax (cumsum " + f._key + " 1) 10 10 0)"); // 10 words 10 max cardinality 0 optimize card
        Frame fr2 = res.getFrame();

    }
}
