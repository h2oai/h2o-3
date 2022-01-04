package water.parser;

import org.junit.*;

import java.io.File;

import org.junit.runner.RunWith;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FileIntegrityChecker;
import water.util.FileUtils;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ParseProgressTest {

  // Attempt a multi-jvm parse of covtype.
  // Silently exits if it cannot find covtype.
  @Test public void testCovtype() {
    String[] covtype_locations = new String[]{"../datasets/UCI/UCI-large/covtype/covtype.data", "../../datasets/UCI/UCI-large/covtype/covtype.data", "../datasets/UCI/UCI-large/covtype/covtype.data.gz",  "../demo/UCI-large/covtype/covtype.data", };
    File f = null;
    for( String covtype_location : covtype_locations ) {
      f = FileUtils.locateFile(covtype_location);
      if( f != null && f.exists() )
        break;
    }
    if( f == null || !f.exists() ) {
      System.out.println("Could not find covtype.data, skipping ParseProgressTest.testCovtype()");
      return;
    }

    FileIntegrityChecker c = FileIntegrityChecker.check(f, false);
    Assert.assertEquals(1,c.size());   // Exactly 1 file
    Key k = c.syncDirectory(null,null,null,null);
    Assert.assertNotNull(k);

    Frame fr = ParseDataset.parse(Key.make(), k);
    Assert.assertEquals( 55, fr.numCols() );
    Assert.assertEquals( 581012, fr.numRows() );
    fr.delete();
  }
}
