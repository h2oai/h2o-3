package water;

import org.junit.*;

import water.fvec.Frame;

public class ConcurrentKeyTest extends TestUtil {
  public ConcurrentKeyTest() { super(2); }
  
  // Test rapid key create/delete during parse
  @Test public void testParse() {
    for( int i=0; i<25; i++ ) { // Small data to keep it fast
      Frame k1 = parse_test_file("smalldata/iris/iris.csv");
      k1.delete();
    }
  }
}
