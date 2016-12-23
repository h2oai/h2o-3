package ai.h2o.cascade.stdlib.core;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.CascadeParserTest;
import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.CascadeSession;
import ai.h2o.cascade.core.CFrame;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test module for {@link FnFromdkv}.
 */
public class FnFromdkvTest extends TestUtil {
  private static CascadeSession session;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    session = new CascadeSession("test");
  }


  @Test
  public void basicTest() {
    Scope.enter();
    try {
      Frame f = parse_test_file("smalldata/iris/iris2.csv");
      Scope.track(f);

      // Import frame {@code f} from DKV into the Cascade session
      Val res = exec("(fromdkv `iris` '" + f._key + "')");
      assertTrue(res instanceof ValFrame);
      CFrame cff = res.getFrame();
      assertTrue("CFrame object is supposed to be lightweight", cff.isStoned());
      Frame ff = cff.getStoneFrame();
      Scope.track(ff);

      // Verify that the imported frame is stored in the global scope, and
      // that its key remains unchanged (i.e. no copy is made).
      CascadeScope global = session.globalScope();
      Val vs = global.lookup("iris");
      assertTrue(vs instanceof ValFrame);
      assertTrue(vs.getFrame().isStoned());
      assertEquals(ff._key, vs.getFrame().getStoneFrame()._key);
      assertEquals(ff._key, f._key);

      // Verify that the name {@code iris} can now be used from Cascade
      Val nrows = Cascade.eval("(nrows iris)", session);
      assertEquals(150, nrows.getInt());

      Val ncols = Cascade.eval("(ncols iris)", session);
      assertEquals(5, ncols.getInt());
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void testBadImports() {
    try {
      exec("(fromdkv 1)");
    } catch (Cascade.TypeError e) {
      assertEquals("Expected 2 arguments but got 1 argument", e.getMessage());
      assertEquals(0, e.location);
      assertEquals(11, e.length);
    }

    try {
      exec("(fromdkv irrrris 'iris.hex')");
    } catch (Cascade.ValueError e) {
      assertEquals("Name lookup of irrrris failed", e.getMessage());
      assertEquals(9, e.location);
      assertEquals(7, e.length);
    }

    try {
      exec("(fromdkv ?iris 'iris.hex')");
    } catch (Cascade.TypeError e) {
      assertEquals("Expected argument of type IDS but instead got AST", e.getMessage());
      assertEquals(9, e.location);
      assertEquals(5, e.length);
    }

    try {
      exec("(fromdkv `iris` 'irrris.hex')");
    } catch (Cascade.ValueError e) {
      assertEquals("Key irrris.hex was not found in DKV", e.getMessage());
      assertEquals(16, e.location);
      assertEquals(12, e.length);
    }

    try {
      exec("(fromdkv `iris1 iris2` 'iris.hex')");
    } catch (Cascade.ValueError e) {
      assertEquals("Only one id should be supplied", e.getMessage());
      assertEquals(9, e.location);
      assertEquals(13, e.length);
    }
  }



  private static Val exec(String expr) {
    try {
      return Cascade.eval(expr, session);
    } catch (Cascade.Error e) {
      CascadeParserTest.reportCascadeError(e, expr);
      throw e;
    }
  }
}
