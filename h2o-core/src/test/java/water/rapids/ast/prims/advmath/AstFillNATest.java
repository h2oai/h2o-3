package water.rapids.ast.prims.advmath;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.TestFrameBuilder;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.Session;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static water.TestUtil.ar;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AstFillNATest{
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  
  @Test
  public void forwardFillNAInCategoricalColumnTest() {
    Scope.enter();
    try {
      Session sess = new Session();
      Frame fr = Scope.track(new TestFrameBuilder()
              .withName("testFrame", sess)
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar( "a", "b", null))
              .build());

      Val val = Rapids.exec("(h2o.fillna testFrame 'forward' 0 1)", sess);
      Assert.assertTrue(val instanceof ValFrame);
      Frame res = Scope.track(val.getFrame());

      System.out.println(res.toTwoDimTable().toString());
      assertEquals(1, res.vec(0).at(2), 1e-5);
    } finally {
      Scope.exit();
    }
  }
}
