package ai.h2o.cascade.stdlib.core;

import ai.h2o.cascade.Cascade;
import ai.h2o.cascade.CascadeParserTest;
import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.CascadeSession;
import ai.h2o.cascade.core.WorkFrame;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValFrame;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test module for {@link FnFromDkv}.
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
    water.Scope.enter();
    try {
      Frame originalFrame = parse_test_file("smalldata/iris/iris2.csv");
      water.Scope.track(originalFrame);

      // Import frame {@code f} from DKV into the Cascade session
      Val res = exec("(fromDkv `iris` '" + originalFrame._key + "')");
      assertTrue(res instanceof ValFrame);
      WorkFrame cff = res.getFrame();
      assertTrue("WorkFrame object is supposed to be in the 'stone' mode", cff.isStoned());
      Frame importedFrame = cff.getStoneFrame();
      water.Scope.track(importedFrame);

      // Verify that the imported frame is stored in the global scope
      Scope global = session.globalScope();
      Val vs = global.lookupVariable("iris");
      assertTrue(vs instanceof ValFrame);
      assertTrue(vs.getFrame().isStoned());
      assertEquals(importedFrame._key, vs.getFrame().getStoneFrame()._key);

      // Verify that it's a deep copy of the original frame
      assertEquals(importedFrame.numCols(), originalFrame.numCols());
      assertNotEquals("Key of the imported frame should be different from the original frame",
          importedFrame._key, originalFrame._key);
      for (int i = 0; i < originalFrame.numCols(); i++) {
        assertNotEquals("vec(" + i + ")-th keys should be different in the imported and original frames",
            importedFrame.vec(i)._key, originalFrame.vec(i)._key);
      }

      // Verify that the name {@code iris} can now be used from Cascade
      Val nrows = Cascade.eval("(nrows iris)", session);
      assertEquals(150, nrows.getInt());
      Val ncols = Cascade.eval("(ncols iris)", session);
      assertEquals(5, ncols.getInt());
    } finally {
      water.Scope.exit();
    }
  }


  @Test
  public void testBadImports() {
    try {
      exec("(fromDkv 1)");
    } catch (Cascade.TypeError e) {
      assertEquals("Wrong number of arguments: expected 2, received 1", e.getCause().getMessage());
      assertEquals(0, e.location);
      assertEquals(11, e.length);
    }

    try {
      exec("(fromDkv irrrris 'iris.hex')");
    } catch (Cascade.NameError e) {
      assertEquals("Name lookup of irrrris failed", e.getMessage());
      assertEquals(9, e.location);
      assertEquals(7, e.length);
    }

    try {
      exec("(fromDkv ?iris 'iris.hex')");
    } catch (Cascade.TypeError e) {
      assertEquals("Expected argument of type IDS but instead got AST", e.getCause().getMessage());
      assertEquals(9, e.location);
      assertEquals(5, e.length);
    }

    try {
      exec("(fromDkv `iris` 'irrris.hex')");
    } catch (Cascade.ValueError e) {
      assertEquals("Key not found in the DKV", e.getCause().getMessage());
      assertEquals(16, e.location);
      assertEquals(12, e.length);
    }

    try {
      exec("(fromDkv `iris1 iris2` 'iris.hex')");
    } catch (Cascade.ValueError e) {
      assertEquals("Only one id should be supplied", e.getCause().getMessage());
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
