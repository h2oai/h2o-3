package water.etl;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static water.etl.prims.advmath.AdvMath.StratifiedSplit;
import static water.etl.prims.mungers.Mungers.OneHotEncoder;
import static water.etl.prims.mungers.Mungers.Rows;

import static water.etl.prims.operators.Operators.Eq;
import water.fvec.Frame;


public class EtlTest extends TestUtil {

    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @AfterClass
    public static void teardown() { }

    @Test
    public void TestETL() {
        Frame fr = parse_test_file(Key.<Frame>make(), "smalldata/airlines/AirlinesTest.csv.zip");
        Frame frOH = OneHotEncoder(fr, "Origin");
        Frame trainTestCol = StratifiedSplit(fr,"IsDepDelayed",0.2,123);
        Frame train = Rows(frOH, Eq(trainTestCol,"train"));
        Frame test = Rows(frOH, Eq(trainTestCol,"test"));
        train.delete();
        test.delete();
        fr.delete();
        frOH.delete();
        trainTestCol.delete();


    }

}

