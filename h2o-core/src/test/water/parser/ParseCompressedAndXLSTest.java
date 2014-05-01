package water.parser;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

public class ParseCompressedAndXLSTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(2); }

  @Test public void testIris(){
    Frame k1 = null,k2 = null,k3 = null, k4 = null;
    try {
      k1 = parse_test_file("smalldata/junit/iris.csv");
      k2 = parse_test_file("smalldata/junit/iris.xls");
      k3 = parse_test_file("smalldata/junit/iris.csv.gz");
      k4 = parse_test_file("smalldata/junit/iris.csv.zip");
      assertTrue(isBitIdentical(k1,k2));
      assertTrue(isBitIdentical(k2,k3));
      assertTrue(isBitIdentical(k3,k4));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
      if( k3 != null ) k3.delete();
      if( k4 != null ) k4.delete();
    }
  }

  @Test public void  testXLS(){
    Frame k1 = null;
    try {
      k1 = parse_test_file("smalldata/junit/benign.xls");
      assertEquals( 14,k1.numCols());
      assertEquals(203,k1.numRows());
      k1.delete();
      k1 = parse_test_file("smalldata/junit/pros.xls");
      assertEquals(  9,k1.numCols());
      assertEquals(380,k1.numRows());
    } finally {
      if( k1 != null ) k1.delete();
    }
  }

}
