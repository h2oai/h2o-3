package water;

import org.junit.*;
import org.junit.BeforeClass;

import water.fvec.Frame;

import static water.TestUtil.parse_test_file;

public class ConcurrentKeyTest {
  
  // Test rapid key create/delete during parse
  @Test public void testParse() {
    for( int i=0; i<25; i++ ) { // Small data to keep it fast
      Frame k1 = parse_test_file("smalldata/iris/iris.csv");
      k1.delete();
    }
  }
}
