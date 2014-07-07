package water.parser;

import org.testng.AssertJUnit;
import org.testng.annotations.*;

import java.io.File;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.FileIntegrityChecker;

public class ParseProgressTest extends TestUtil {
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
    AssertJUnit.assertEquals(1,c.size());   // Exactly 1 file
    Key k = c.syncDirectory(null,null,null,null);
    AssertJUnit.assertEquals(true,k!=null);

    Frame fr = ParseDataset2.parse(Key.make(),k);
    AssertJUnit.assertEquals( 55, fr.numCols() );
    AssertJUnit.assertEquals( 581012, fr.numRows() );
    fr.delete();
  }
}
