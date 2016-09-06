package water.parser.parquet;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

import water.TestUtil;
import water.fvec.Frame;

/**
 * Test suite for Parquet parser.
 */
public class ParseTestParquet extends TestUtil {

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(1); }

  @Test
  public void testParseSimple() {
    Frame expected = null, actual = null;
    try {
      expected = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
      actual = TestUtil.parse_test_file("smalldata/parser/parquet/airlines-simple.snappy.parquet");

      assertEquals(Arrays.asList(expected._names), Arrays.asList(actual._names));
      assertEquals(Arrays.asList(expected.typesStr()), Arrays.asList(actual.typesStr()));
      assertTrue(isBitIdentical(expected, actual));
    } finally {
      if (expected != null) expected.delete();
      if (actual != null) actual.delete();
    }
  }

}
