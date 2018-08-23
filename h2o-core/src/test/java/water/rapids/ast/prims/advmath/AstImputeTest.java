package water.rapids.ast.prims.advmath;

import org.junit.BeforeClass;
import org.junit.Test;
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
    public void MissingNumbersImputeTest() {
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
            fr.replace(0, fr.vec(0).toNumericVec());

            Val val = Rapids.exec("(h2o.impute testFrame 0 'mean' 'interpolate' [] _ _)", sess);
            double[] res = val.getNums();

            assertEquals(res[0], 1.5, 1e-5);
            assertEquals(fr.vec(0).at(1), 1.5, 1e-5);
        } finally {
            Scope.exit();
        }
    }
}
