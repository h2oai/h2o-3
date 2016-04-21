package water.parser;


import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import water.TestUtil;
import water.fvec.Frame;

/**
 * Test suite for Avro parser.
 */
public class ParseTestAvro extends TestUtil {

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(5); }

  @Test
  public void testParseSimple() {
    String[] files = TestUtil.ar("smalldata/parser/avro/sequence100k.avro",
                                 "smalldata/parser/avro/episodes.avro");
                                 //"smalldata/parser/avro/airline.avro");

    int[][] dims = TestUtil.ar(TestUtil.ari(1, 100000),  // cols X rows
                               TestUtil.ari(3, 8));
    int limit = files.length;
    assertEquals("Test configuration error - number of files has to match dimensions ",
                 files.length, dims.length);

    for (int i = 0; i < limit; ++i) {
      String f = files[i];
      int[] dim = dims[i];
      Frame frame = null;
      try {
        frame = TestUtil.parse_test_file(f);
        assertEquals("Frame has to have expected number of columns", dim[0], frame.numCols());
        assertEquals("Frame has to have expected number of rows", dim[1], frame.numRows());
      } finally {
        if (frame != null)
          frame.delete();
      }
    }
  }

}
