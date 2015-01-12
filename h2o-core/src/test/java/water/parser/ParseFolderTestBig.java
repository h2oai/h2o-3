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
      Vec v1 = k1.vec("RDATE_5");
      System.out.println(v1.toString());
      System.out.printf("%b %e %d %b %s\n", v1.isEnum(), v1.min(), v1.naCnt(), v1.isEnum(), Arrays.toString(v1.domain()));

      k2 = parse_test_file("bigdata/laptop/usecases/cup98VAL_z.csv");
      Vec v2 = k2.vec("SOLIH");
      System.out.println(v2.toString());
      System.out.printf("%b %e %d %b %s\n",v2.isEnum(),v2.min(),v2.naCnt(),v2.isEnum(),Arrays.toString(v2.domain()));

    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

}
