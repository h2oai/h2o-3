package hex.word2vec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.BufferedString;
import static water.util.FileUtils.*;
import water.util.IcedLong;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeThat;

public class WordCountTaskTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testWordCount() {
    String[] strData = new String[10000];
    for (int i = 0; i < strData.length; i++) {
      int b = i % 10;
      if (b < 3)
        strData[i] = "A";
      else if (b < 5)
        strData[i] = "B";
      else
        strData[i] = "C";
    }
    Frame fr = new TestFrameBuilder()
            .withName("data")
            .withColNames("Str")
            .withVecTypes(Vec.T_STR)
            .withDataForCol(0, strData)
            .withChunkLayout(100, 900, 5000, 4000)
            .build();
    try {
      Map<BufferedString, IcedLong> counts = new WordCountTask().doAll(fr.vec(0))._counts;
      assertEquals(3, counts.size());
      assertEquals(3000L, counts.get(new BufferedString("A"))._val);
      assertEquals(2000L, counts.get(new BufferedString("B"))._val);
      assertEquals(5000L, counts.get(new BufferedString("C"))._val);
      System.out.println(counts);
    } finally {
      fr.remove();
    }
  }

  @Test
  public void testWordCountText8() {
    String fName = "bigdata/laptop/text8.gz";
    assumeThat("text8 data available", locateFile(fName), is(notNullValue())); // only run if text8 is present
    Frame fr = parse_test_file(fName, "NA", 0, new byte[]{Vec.T_STR});
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