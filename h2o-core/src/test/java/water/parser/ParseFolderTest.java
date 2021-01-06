package water.parser;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.util.FileUtils;

import java.io.File;
import java.io.IOException;

public class ParseFolderTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(5); }

  @Test public void testProstate() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parseTestFolder("smalldata/junit/parse_folder" );
      k1 = parseTestFile("smalldata/junit/parse_folder_gold.csv");
      TestUtil.assertBitIdentical(k1, k2);
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // test skipped some columns
  @Test public void testFolderSkipColumnsSome() {
    Scope.enter();
    Frame k1 = null, k2 = null, k3=null, k4=null;
    int[] skippedColumns = new int[] {0,1};

    try {
      k1 = parseTestFile("smalldata/junit/parse_folder_gold.csv");
      k2 = parseTestFolder("smalldata/junit/parse_folder" );
      Scope.track(k1,k2);
      TestUtil.assertBitIdentical(k1,k2);

      k3 = parseTestFile("smalldata/junit/parse_folder_gold.csv", skippedColumns);
      k4 = parseTestFolder("smalldata/junit/parse_folder", skippedColumns);
      Scope.track(k3,k4);
      TestUtil.assertBitIdentical(k3,k4);
    } finally {
      Scope.exit();
    }
  }


  // test skipped some columns
  @Test public void testFolderSkipColumnsAll() {
    Scope.enter();
    Frame k1 = null;
    int[] skippedColumns = new int[] {0,1,2,3,4,5,6,7,8};

    try {
      k1 = parseTestFolder("smalldata/junit/parse_folder", skippedColumns);
      Assert.assertTrue("Error:  Should have thrown an exception but did not!", 1 == 2);
    } catch (Exception ex) {
      System.out.println(ex);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testSameFile() throws IOException {
    File f = FileUtils.getFile("smalldata/iris/iris_wheader.csv");
    NFSFileVec nfs1 = NFSFileVec.make(f);
    NFSFileVec nfs2 = NFSFileVec.make(f);
    Frame fr = null;
    try {
      fr = ParseDataset.parse(Key.make(), new Key[]{nfs1._key, nfs2._key}, false/*delete on done*/, false, ParseSetup.GUESS_HEADER);
    } finally {
      if( fr != null ) fr.delete();
      if( nfs1 != null ) nfs1.remove();
    }
  }
}
