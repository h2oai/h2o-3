package water.parser;

import java.util.Arrays;
import org.junit.*;
import water.TestUtil;
import water.fvec.Frame;
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
      Vec v = k1.vec("RDATE_5");
      System.out.println(v.toString());
      System.out.println(v.isEnum());
      System.out.println(v.min());
      System.out.println(v.isEnum());
      System.out.println(Arrays.toString(v.domain()));

    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

}
