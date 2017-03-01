package water.etl;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;

import static water.etl.prims.advmath.AdvMath.StratifiedSplit;
import static water.etl.prims.mungers.Mungers.OneHotEncoder;
import static water.etl.prims.operators.Operators.Eq;
import water.fvec.Frame;
import water.Scope;

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
        fr.add(frOH);
        Frame trainTestCol = StratifiedSplit(fr,"IsDepDelayed",0.2,123);
        Frame idx = Eq(trainTestCol,"train");
        Frame train = fr.deepSlice(idx,null);
        Frame idx2 = Eq(trainTestCol,"test");
        Frame test = fr.deepSlice(idx2,null);

        System.out.println(fr.toString(0L,10));
        System.out.println(train.toString(0L,10));
        System.out.println(test.toString(0L,10));
        fr.delete();
        frOH.delete();
        trainTestCol.delete();
        idx.delete();
        idx2.delete();
        train.delete();
        test.delete();


    }

}

