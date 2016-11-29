package water.rapids.ast.prims.reducers;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.rapids.vals.ValFrame;

/**
 * Test for the {@link AstCumu} class and its subclasses.
 */
public class AstCumuTest extends TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testCumuSimple() {
    Scope.enter();
    try {
      Session sess = new Session();
      new TestFrameBuilder()
          .withName("$fr", sess)
          .withColNames("c1", "c2", "c3", "c4")
          .withDataForCol(0, ard(1, 1))
          .withDataForCol(1, ard(2, 2))
          .withDataForCol(2, ard(3, 3))
          .withDataForCol(3, ard(4, 4))
          .build();

      Val val = Rapids.exec("(cumsum $fr 1)", sess);
      Assert.assertTrue(val instanceof ValFrame);
      Frame res = Scope.track(val.getFrame());
      Assert.assertEquals(res.vec(0).at8(0L), 1);
      Assert.assertEquals(res.vec(1).at8(0L), 3);
      Assert.assertEquals(res.vec(2).at8(0L), 6);
      Assert.assertEquals(res.vec(3).at8(0L), 10);

      val = Rapids.exec("(cumsum $fr 0)", sess);
      Assert.assertTrue(val instanceof ValFrame);
      res = Scope.track(val.getFrame());
      Assert.assertEquals(res.vec(0).at8(1L), 2);
      Assert.assertEquals(res.vec(1).at8(1L), 4);
      Assert.assertEquals(res.vec(2).at8(1L), 6);
      Assert.assertEquals(res.vec(3).at8(1L), 8);

      val = Rapids.exec("(cummax $fr 1)", sess);
      Assert.assertTrue(val instanceof ValFrame);
      res = Scope.track(val.getFrame());
      Assert.assertEquals(res.vec(0).at8(0L), 1);
      Assert.assertEquals(res.vec(1).at8(0L), 2);
      Assert.assertEquals(res.vec(2).at8(0L), 3);
      Assert.assertEquals(res.vec(3).at8(0L), 4);
    } finally {
      Scope.exit();
    }
  }

}
