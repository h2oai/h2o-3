package water.etl;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import static water.etl.prims.advmath.AdvMath.StratifiedSplit;
import static water.etl.prims.mungers.Mungers.OneHotEncoder;
import static water.etl.prims.mungers.Mungers.Rows;

import static water.etl.prims.operators.Operators.Eq;
import water.fvec.Frame;
import water.util.FrameUtils;
import water.util.VecUtils;

public class EtlTest extends TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @AfterClass
    public static void teardown() { }

    @Test
    public void TestETL() {
        Frame fr = parse_test_file(Key.<Frame>make(), "path_to_file");
        Frame frOH = OneHotEncoder(fr, "cat_col");
        Frame trainTestCol = StratifiedSplit(fr,"response",0.2,123);
        Frame train = Rows(frOH, Eq(trainTestCol,"train"));
        Frame test = Rows(frOH, Eq(trainTestCol,"test"));


    }

}

