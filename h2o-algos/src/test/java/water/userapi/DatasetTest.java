package water.userapi;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.udf.fp.Function;
import water.udf.specialized.EnumColumn;
import water.udf.specialized.Enums;
import water.util.FrameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Test suite for Dataset functionality in UserAPI.
 * 
 * Created by vpatryshev on 2/22/17.
 */
public class DatasetTest extends TestUtil {
  static File testFile1 = new File("DatasetTest.1.tmp");

  @BeforeClass
  static public void setup() throws IOException { 
    stall_till_cloudsize(1);
    Writer fw = new FileWriter(testFile1);
    fw.write("A\tB\tC\na1\tb1\tc1\na2\tb2\tc2\n");
    fw.close();
    testFile1.deleteOnExit();
  }
  
  @Before
  public void hi() { Scope.enter(); }

  @After
  public void bye() { Scope.exit(); }

  @Test(expected=DataException.class)
  public void testNoReadFile() throws Exception {
      Dataset.read(new File("c:/System.ini"));
  }

  @Test
  public void testRead() throws Exception {
    Dataset sut = Dataset.read(testFile1);
    Vec v1 = sut.vec("C2");
    assertEquals("b1", String.valueOf(v1.atStr(new BufferedString(), 1)));
  }

  private static int whichOne(int i) {
    return i < 1000 ? 0 : i < 11000 ? 1 : 2;
  }
  
  @Test
  public void testOneHotEncode() throws Exception {
    final String[] domain = {"red", "white", "blue"};

    final int size = 1000000;
    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)];
      }
    });

    Dataset sut = Dataset.onVecs(new HashMap<String, Vec>() {{put("RGB", vec1);}});
    
    Dataset actual = sut.oneHotEncode();
    
    assertArrayEquals(new String[]{"RGB.red", "RGB.white", "RGB.blue", "RGB.missing(NA)"}, actual.domain());
    
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < 3; j++) {
        assertEquals("@(" + i + "," + j + ")", 
            j == whichOne(i) ? 1 : 0,
            actual.vec(actual.domain()[j]).at8(i));
      }
    }
    
  }

  @Test
  public void testStratifiedSplit() throws Exception {

  }
}