package water.parser.parquet;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Test suite for Parquet parser.
 */
public class ParseTestParquet extends TestUtil {

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(5); }

  @Test
  public void testParseSimple() {
    Frame parsed = null, expected = null, actual = null;
    try {
      parsed = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");
      // convert all categorical vecs to String vec (we don't support categoricals in Parquet parser yet)
      expected = new Frame();
      for (String name : parsed.names()) {
        Vec v = parsed.vec(name);
        if (v.isCategorical()) {
          v = v.toStringVec();
        }
        expected.add(name, v);
      }
      actual = TestUtil.parse_test_file("smalldata/parser/parquet/airlines-simple.snappy.parquet");

      assertEquals(Arrays.asList(expected._names), Arrays.asList(actual._names));
      assertTrue(isBitIdentical(expected, actual));
    } finally {
      if (parsed != null) parsed.delete();
      if (expected != null) expected.delete();
      if (actual != null) actual.delete();
    }
  }

}
