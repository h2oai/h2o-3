package water.rapids.ast.prims.mungers;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.util.TwoDimTable;

import static org.junit.Assert.assertEquals;

public class AstImputeTest extends TestUtil {
    @BeforeClass
    static public void setup() { stall_till_cloudsize(1); }

    @Test
    public void MissingNumbersImputeWithMeanTest() {
        Scope.enter();
        try {
            Session sess = new Session();
            Frame fr = Scope.track(new TestFrameBuilder()
                    .withName("testFrame", sess)
                    .withColNames("C1")
                    .withVecTypes(Vec.T_CAT)
                    .withDataForCol(0, ar("1", null, "2"))
                    .build());

            // Need to do this CAT -> NUM trick since we can't create NUM column with array of integers because integers couldn't be null in Java.
            Vec nv = fr.vec(0).toNumericVec();
            fr.replace(0, nv);
            DKV.put(fr);
            DKV.put(nv);
            printOutFrameAsTable(fr, true, fr.numRows());

            Val val = Rapids.exec("(h2o.impute testFrame 0 'mean' 'interpolate' [] _ _)", sess);
            double[] res = val.getNums();

            // This assertion is green locally but on CI it returns 0.5 instead of 1.5
            assertEquals(1.5 , res[0], 1e-5);
            assertEquals( 1.5, fr.vec(0).at(1), 1e-5);
        } finally {
            Scope.exit();
        }
    }


    public void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
        assert limit <= Integer.MAX_VALUE;
        TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
        System.out.println(twoDimTable.toString(2, true));
    }
}
