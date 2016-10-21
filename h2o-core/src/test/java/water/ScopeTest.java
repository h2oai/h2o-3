package water;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * Tests for Scope
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ScopeTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testEnterAndExit() throws Exception {
    Scope.enter();
    Scope.exit(); // not supposed to leak anything
  }

  @Test
  public void testEnterAndExit_with_no_leakage() throws Exception {
    Scope.enter();
    Vec v1 = Vec.makeCon(11.0, 10);
    Vec v2 = Vec.makeCon(12.0, 10);
    Vec v3 = Vec.makeCon(13.0, 10);
    Scope.track(v1);
    Scope.track(v2);
    Scope.track(v3);
    Scope.exit();
  }

  @Test
  public void testEnterAndExit_with_leakage() throws Exception {
    Scope.enter();
    Vec v1 = Vec.makeCon(21.0, 10);
    Vec v2 = Vec.makeCon(22.0, 10);
    Vec v3 = Vec.makeCon(23.0, 10);
    Scope.track(v1);
    Scope.track(v2);
    Scope.track(v3);
    Scope.exit(v3._key, v2._key);
    assertEquals(6, numberOfLeakedKeys());
    v2.remove();
    v3.remove();
  }
}