package water;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import water.fvec.Vec;

import java.util.Arrays;

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
    Vec v1 = Vec.makeCon(111.0, 11);
    Vec v2 = Vec.makeCon(112.0, 12);
    Vec v3 = Vec.makeCon(113.0, 13);
    Scope.track(v1);
    Scope.track(v2);
    Scope.track(v3);
    Scope.exit();
  }

  @Test
  public void testEnterAndExit_with_leakage() throws Exception {
    Scope.enter();
    Vec v1 = Vec.makeCon(121.0, 21);
    Vec v2 = Vec.makeCon(122.0, 22);
    Vec v3 = Vec.makeCon(123.0, 23);
    Scope.track(v1);
    Scope.track(v2);
    Scope.track(v3);
    Scope.exit(Arrays.asList(v3._key, v2._key));
    assertTrue(numberOfLeakedKeys() > 0);
    Futures fs = new Futures();
    Keyed.remove(v2._key);
    Keyed.remove(v3._key);
    fs.blockForPending();
  }
}