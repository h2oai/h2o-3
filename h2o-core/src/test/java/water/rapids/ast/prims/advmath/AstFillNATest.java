package water.rapids.ast.prims.advmath;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.TestFrameBuilder;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;
import water.rapids.Session;
public class AstFillNATest extends TestUtil {
    @BeforeClass
    static public void setup() { stall_till_cloudsize(1); }

    @Test public void TestFillNA() {
        Scope.enter();
        try {
            Session sess = new Session();
            Frame fr = Scope.track(new TestFrameBuilder()
                    .withName("$fr", sess)
                    .withColNames("C1", "C2", "C3")
                    .withVecTypes(Vec.T_NUM,Vec.T_NUM,Vec.T_NUM)
                    .withDataForCol(0, ard( 1,Double.NaN, Double.NaN, Double.NaN, Double.NaN))
                    .withDataForCol(1, ard( Double.NaN, 1, Double.NaN, Double.NaN, Double.NaN))
                    .withDataForCol(2, ard( Double.NaN, Double.NaN,1, Double.NaN, Double.NaN))
                    .build());
            Val val = Rapids.exec("(h2o.fillna $fr 'forward' 0 2)", sess);
            Assert.assertTrue(val instanceof ValFrame);
            Frame res = Scope.track(val.getFrame());
            // check the first column. should be all unique indexes in order
            assertVecEquals(res.vec(0),dvec(1.0,1.0,1.0,Double.NaN,Double.NaN), 0.0);
            assertVecEquals(res.vec(1),dvec(Double.NaN,1.0,1.0,1.0,Double.NaN), 0.0);
            assertVecEquals(res.vec(2),dvec(Double.NaN,Double.NaN,1.0,1.0,1.0), 0.0);
        } finally {
            Scope.exit();
        }



    }
}
