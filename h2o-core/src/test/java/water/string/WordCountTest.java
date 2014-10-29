package water.string;

import org.junit.*;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

public class WordCountTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(3); }

  @Test public void testText8() {
    Key wca = null;
    Frame fr = null;
    try {
      long start = System.currentTimeMillis();
      fr = parse_test_file("smalldata/text/text8");
      System.out.println("Done Parse: "+(float)(System.currentTimeMillis()-start)/1000+"s");

      start = System.currentTimeMillis();
      wca = (new WordCountTask(5)).doAll(fr)._wordCountKey;
      Log.info("Test: post doAll");
      System.out.println("Done training: "+(float)(System.currentTimeMillis()-start)/1000+"s");
      Assert.assertEquals(100038l, ((Frame)wca.get()).numRows());
    } finally {
      if( fr  != null ) fr.remove();
      if( wca != null ) wca.remove();
    }
  }
}
