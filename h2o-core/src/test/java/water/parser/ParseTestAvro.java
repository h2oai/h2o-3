package water.parser;

import org.junit.BeforeClass;
import org.junit.Test;

import water.TestUtil;
import water.fvec.Frame;

/**
 * Test suite for Avro parser.
 */
public class ParseTestAvro extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(3); }

  @Test
  public void testParseAirlines() {
    Frame f1 = null;
    try {
      //f1 = parse_test_file("/tmp/airline.avro");
      f1 = parse_test_file("/tmp/sequence100k.avro");
      System.err.println(String.format("cols = %d, rows = %d", f1.numCols(), f1.numRows()));
    } finally {
      if( f1 != null ) f1.delete();
    }
  }

}
