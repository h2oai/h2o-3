package hex.word2vec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.IcedLong;

import java.util.Map;

import static org.junit.Assert.*;

public class WordCountTaskTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testWordCount() {
    Frame fr = parse_test_file("bigdata/laptop/text8.gz", "NA", 0, new byte[]{Vec.T_STR});
    try {
      Map<BufferedString, IcedLong> counts = new WordCountTask().doAll(fr.vec(0))._counts;
      assertEquals(253854, counts.size());
      assertEquals(303L, counts.get(new BufferedString("anarchism"))._val);
      assertEquals(316376L, counts.get(new BufferedString("to"))._val);
      assertNotNull(counts);
    } finally {
      fr.remove();
    }
  }

}