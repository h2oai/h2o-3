package water.parser;

import java.io.File;
import java.util.Arrays;
import org.junit.*;
import water.Job;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;

public class ParseFolderTestBig extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(1); }

  // "dataset directory is not usually available"
  @Test @Ignore
  public void testCovtype() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("datasets/parse_folder_test");
      k1 = parse_test_file  ("datasets/UCI/UCI-large/covtype/covtype.data");
      Assert.assertTrue("parsed values do not match!",isBitIdentical(k1,k2));
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

  @Test @Ignore
  public void testBIGSVM() {
    String fname = "bigdata/cust_K/1m.svm";
    Frame k1 = null;
    try {
      File f = find_test_file(fname);
      assert f != null && f.exists():" file not found: " + fname;
      NFSFileVec nfs = NFSFileVec.make(f);
      Job<Frame> job = ParseDataset.parse(Key.make("BIGSVM.hex"),new Key[]{nfs._key},true,ParseSetup.guessSetup(new Key[]{nfs._key}, false, ParseSetup.GUESS_HEADER),false);
      while( job.progress() < 1.0 ) {
        System.out.print(((int)(job.progress()*1000.0))/10.0 + "% ");
        try { Thread.sleep(1000); } catch( InterruptedException ie ) { }
      }
      System.out.println();
      k1 = job.get();
      System.out.println(k1.toString());

    } finally {
      if( k1 != null ) k1.delete();
    }
  }
}
