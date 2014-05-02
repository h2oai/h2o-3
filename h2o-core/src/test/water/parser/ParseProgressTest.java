package water.parser;

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.FileIntegrityChecker;

public class ParseProgressTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  // Attempt a multi-jvm parse of covtype.
  // Silently exits if it cannot find covtype.
  @Test public void testCovtype() {
    String[] covtype_locations = new String[]{"../datasets/UCI/UCI-large/covtype/covtype.data", "../../datasets/UCI/UCI-large/covtype/covtype.data", "../datasets/UCI/UCI-large/covtype/covtype.data.gz",  "../demo/UCI-large/covtype/covtype.data", };
    File f = null;
    for( String covtype_location : covtype_locations ) {
      f = find_test_file( covtype_location );
      if( f != null && f.exists() )
        break;
    }
    if( f == null || !f.exists() ) {
      System.out.println("Could not find covtype.data, skipping ParseProgressTest.testCovtype()");
      return;
    }

    FileIntegrityChecker c = FileIntegrityChecker.check(f);
    assertEquals(1,c.size());   // Exactly 1 file
    Key k = c.syncDirectory(null,null,null,null);
    assertEquals(true,k!=null);

    Frame fr = ParseDataset2.parse(Key.make(),k);
    assertEquals( 55, fr.numCols() );
    assertEquals( 581012, fr.numRows() );
    fr.delete();
  }
}
