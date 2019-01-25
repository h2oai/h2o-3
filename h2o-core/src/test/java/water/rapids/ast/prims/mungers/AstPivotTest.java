package water.rapids.ast.prims.mungers;

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
public class AstPivotTest  extends TestUtil {
    @BeforeClass
    static public void setup() { stall_till_cloudsize(1); }

    @Test public void TestPivot() {
        Scope.enter();
        try {
            Session sess = new Session();
            Frame fr = Scope.track(new TestFrameBuilder()
                    .withName("$fr", sess)
                    .withColNames("index", "col", "value")
                    .withVecTypes(Vec.T_NUM,Vec.T_CAT,Vec.T_NUM)
                    .withDataForCol(0, ar(1, 2, 3, 4, 2, 4))
                    .withDataForCol(1, ar("a", "a", "a", "a", "b", "b"))
                    .withDataForCol(2, ard(10.1, 10.2, 10.3, 10.4, 20.1, 22.2))
                    .build());
            Val val = Rapids.exec("(pivot $fr 'index' 'col' 'value')", sess);
            Assert.assertTrue(val instanceof ValFrame);
            Frame res = Scope.track(val.getFrame());
            // check the first column. should be all unique indexes in order
            assertVecEquals(res.vec(0),dvec(1.0,2.0,3.0,4.0), 0.0);
            // next column is "a" values in correct order
            assertVecEquals(res.vec(1),dvec(10.1,10.2,10.3,10.4), 0.0);
            // last column is "b" values
            assertVecEquals(res.vec(2),dvec(Double.NaN, 20.1, Double.NaN,22.2), 0.0);
        } finally {
            Scope.exit();
        }



    }
}
