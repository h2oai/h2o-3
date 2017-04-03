package water.parser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.*;
import static org.junit.Assert.*;
import water.Job;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.util.FileUtils;

public class ParseFolderTestBig extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

  // "dataset directory is not usually available"
  @Test @Ignore
  public void testCovtype() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("datasets/parse_folder_test");
      k1 = parse_test_file  ("datasets/UCI/UCI-large/covtype/covtype.data");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  // "bigdata directory is not usually available"
  @Test @Ignore
  public void testKDDCup() {
    Frame k1 = null, k2 = null;
    try {
      k1 = parse_test_file("bigdata/laptop/usecases/cup98LRN_z.csv");
      Vec v1 = k1.vec("RDATE_5");
      System.out.println(v1.toString());
      System.out.printf("%b %e %d %b %s\n", v1.isCategorical(), v1.min(), v1.naCnt(), v1.isCategorical(), Arrays.toString(v1.domain()));

      k2 = parse_test_file("bigdata/laptop/usecases/cup98VAL_z.csv");
      Vec v2 = k2.vec("SOLIH");
      System.out.println(v2.toString());
      System.out.printf("%b %e %d %b %s\n",v2.isCategorical(),v2.min(),v2.naCnt(),v2.isCategorical(),Arrays.toString(v2.domain()));

    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  @Test
  public void testPUBDEV4026() {
    Scope.enter();
    String fname = "bigdata/PUBDEV4026.svm";
    try {
      File f = FileUtils.getFile(fname);
      NFSFileVec nfs = NFSFileVec.make(f);
      final ParseSetup globalSetup = ParseSetup.guessSetup(new Key[]{nfs._key}, false, ParseSetup.GUESS_HEADER);
      ParseWriter.ParseErr[] errors = globalSetup._errs;
      
      assertTrue("Got errors: " + Arrays.toString(errors), errors.length == 0);
      Job<Frame> job = ParseDataset.parse(Key.make("PUBDEV4026.hex"), new Key[]{nfs._key}, true, globalSetup, false)._job;
      int i = 0;
      while (job.progress() < 1.0) {
        System.out.print(((int) (job.progress() * 1000.0)) / 10.0 + "% ");
        try {
          Thread.sleep(1000);
          i++;
        } catch (InterruptedException ignore) { /*comment to disable ideaJ warning*/}
      }
      System.out.println();
      Frame k1 = Scope.track(job.get()); // will throw on error
      System.out.println(k1.toString());
      assertEquals(776211, k1.vecs().length);
      assertEquals(47754, k1.vec(0).length());
    } catch (IOException ignore) {
      System.out.println("\nFile not found: " + fname + " - not running the test.");
    } finally {
      Scope.exit();
    }
  }

  @Test @Ignore
  public void testBIGSVM() {
    Scope.enter();
    String fname = "bigdata/cust_K/1m.svm";
    try {
      File f = FileUtils.getFile(fname);
      NFSFileVec nfs = NFSFileVec.make(f);
      Job<Frame> job = ParseDataset.parse(Key.make("BIGSVM.hex"), new Key[]{nfs._key}, true, ParseSetup.guessSetup(new Key[]{nfs._key}, false, ParseSetup.GUESS_HEADER), false)._job;
      while (job.progress() < 1.0) {
        System.out.print(((int) (job.progress() * 1000.0)) / 10.0 + "% ");
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignore) { /*comment to disable ideaJ warning*/}
      }
      System.out.println();
      Frame k1 = Scope.track(job.get());
      System.out.println(k1.toString());
    } catch (IOException ioe) {
        Assert.fail("File not found: " + fname + " - " + ioe.getMessage());
    } finally {
      Scope.exit();
    }
  }
}
