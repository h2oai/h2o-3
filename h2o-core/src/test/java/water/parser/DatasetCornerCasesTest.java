package water.parser;

import org.junit.*;

import water.TestUtil;
import water.fvec.Frame;

public class DatasetCornerCasesTest extends TestUtil {

  /** HTWO-87 bug test
   *  - two lines dataset (one line is a comment) throws assertion java.lang.AssertionError: classOf no dists > 0? 1
   */
  @Test public void testTwoLineDataset() throws Exception {
    testOneLineDataset("./smalldata/junit/two-lines-dataset.csv");
  }

  /* The following tests deal with one line dataset ended by different number of newlines. */

  /*
   * HTWO-87-related bug test
   *
   *  - only one line dataset - guessing parser should recognize it.
   *  - this datasets are ended by different number of \n (0x0A):
   *    - one-line-dataset-0.csv    - the line is NOT ended by \n
   *    - one-line-dataset-1.csv    - the line is ended by 1 \n     (0x0A)
   *    - one-line-dataset-2.csv    - the line is ended by 2 \n     (0x0A 0x0A)
   *    - one-line-dataset-1dos.csv - the line is ended by \r\n     (0x0D 0x0A)
   *    - one-line-dataset-2dos.csv - the line is ended by 2 \r\n   (0x0D 0x0A 0x0D 0x0A)
   */
  @Test public void testOneLineDataset() {
    // max number of dataset files
    final String tests[] = {"0", "1unix", "2unix", "1dos", "2dos" };
    final String test_dir    = "smalldata/junit/";
    final String test_prefix = "one-line-dataset-";

    for( String test : tests ) {
      String datasetFilename = test_dir + test_prefix + test + ".csv";
      testOneLineDataset(datasetFilename);
    }
  }

  private void testOneLineDataset(String filename) {
    Frame fr = null;
    try {
      fr = parse_test_file(filename);
      Assert.assertEquals("Number of chunks == 1", 1, fr.anyVec().nChunks());
      Assert.assertEquals("Number of rows   == 2", 2, fr.numRows());
      Assert.assertEquals("Number of cols   == 9", 9, fr.numCols());
    } finally {
      if( fr != null ) fr.delete();
    }
  }
}
