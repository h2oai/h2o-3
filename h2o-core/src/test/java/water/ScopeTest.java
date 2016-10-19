package water;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for Scope
 */
public class ScopeTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testEnterAndExit() throws Exception {
    Scope.enter();
    Scope.exit(); // not supposed to leak anything
  }

  @Test
  public void testEnterAndExit_with_something_to_keep() throws Exception {
    Scope.enter();
    Key k1 = Key.make("k11");
    Key k2 = Key.make("k12");
    Key k3 = Key.make("k13");
    Scope.exit(k3, k1, k2); // not supposed to leak anything
  }

  @Test
  public void testEnterAndExit_with_leakage() throws Exception {
    Scope.enter();
    Key k1 = Key.make("k21");
    Key k2 = Key.make("k22");
    Key k3 = Key.make("k23");
    Value v1 = new Value(k1, "v1");
    DKV.put(k1, v1);
    Scope.exit(k3, k2);
    assertEquals(v1, DKV.get(k1));
    assertEquals(1, numberOfLeakedKeys());
    removeKeysRegardless();
  }

  @Test
  public void testEnterAndExit_with_no_leakage() throws Exception {
    Scope.enter();
    Key k1 = Key.make("k21");
    Key k2 = Key.make("k22");
    Key k3 = Key.make("k23");
    Value v1 = new Value(k1, "v1");
    DKV.put(k1, v1);
    Scope.exit(k3, k1);
    assertEquals(v1, DKV.get(k1));
    assertEquals(1, numberOfLeakedKeys());
    removeKeysRegardless();
  }
}