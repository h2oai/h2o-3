package water;

import static org.junit.Assert.*;
import org.junit.*;


public class TestUtil {
  private static int _initial_keycnt = 0;

  // Stall test until we see at least X members of the Cloud
  public static void stall_till_cloudsize(int x) {
    H2O.waitForCloudSize(x, 100000);
  }

  @BeforeClass public static void setupCloud() {
    H2O.main(new String[] {});
    stall_till_cloudsize(1);
    _initial_keycnt = H2O.store_size();
  }

  @AfterClass public static void checkLeakedKeys() {
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    if( leaked_keys > 0 ) {
      for( Key k : H2O.localKeySet() ) {
        Value value = H2O.raw_get(k);
        System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
      }
    }
    assertTrue("No keys leaked", leaked_keys <= 0);
    _initial_keycnt = H2O.store_size();
  }

}

