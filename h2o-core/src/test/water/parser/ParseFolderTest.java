package water.parser;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

public class ParseFolderTest extends TestUtil {

  @Test public void  testProstate() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/parse_folder" );
      k1 = parse_test_file  ("smalldata/junit/parse_folder_gold.csv");
      assertTrue("parsed values do not match!",isBitIdentical(k1,k2));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }
}
