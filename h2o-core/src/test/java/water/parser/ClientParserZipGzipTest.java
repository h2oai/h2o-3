package water.parser;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class ClientParserZipGzipTest extends TestUtil {
  //
  // This JUnit test is used to verify that fixes for HEXDEV-497: parsing zip files
  // is working.  We are only testing it with a small dataset.  More comprehensive tests
  // can be found with Pyunit tests.
  //

  @BeforeClass static public void setup() {
    stall_till_cloudsize(1);
  }

  @Test public void testBasic() throws IOException {

      // airlines_small_csv.zip is a zip file that contains 4 csv files
    Frame one_zip_directory = parse_test_file("smalldata/parser/hexdev_497/airlines_small_csv.zip");

      // airlines_small_csv is a folder that contains the 4 csv files not compressed.
    Frame one_csv_directory = parse_test_file("smalldata/parser/hexdev_497/airlines_small_csv/all_airlines.csv");

      // H2O frames built from both sources should be equal.  Verify that here.
    assertTrue(TestUtil.isBitIdentical(one_zip_directory, one_csv_directory));

    if (one_zip_directory != null) one_zip_directory.delete();
    if (one_csv_directory != null) one_csv_directory.delete();

  }
}
