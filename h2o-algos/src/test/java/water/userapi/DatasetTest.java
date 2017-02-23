package water.userapi;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.udf.fp.Function;
import water.udf.fp.Functions;

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
    stall_till_cloudsize(3);
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

  final int size = 1000;

  private static int whichOne(int i) {
    return i%100 < 2 ? 0 : i%100 < 20 ? 1 : 2;
  }
  
  @Test
  public void testOneHotEncode() throws Exception {
    final String[] domain = {"red", "white", "blue"};

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
  public void testAddSplittingColumn() throws Exception {
    final String[] domain = {"red", "white", "blue"};

    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)];
      }
    });

    final Vec vec2 = vec(size, Functions.<Integer>identity());
    
    Dataset sut = Dataset.onVecs(
        new HashMap<String, Vec>() {{
          put("RGB", vec1); put("Ausweis", vec2);
        }});

    Dataset actual = sut.addSplittingColumn("RGB", 0.01, 7688714);

    assertArrayEquals(new String[]{"RGB", "Ausweis", "test_train_split"}, actual.domain());

    // it is hard to test specific values
  }

  @Test
  public void testStratifiedSplit() throws Exception {
    final String[] domain = {"red", "white", "blue"};

    final int size = 1000;
    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)];
      }
    });

    final Vec vec2 = vec(size, Functions.<Integer>identity());

    Dataset sut = Dataset.onVecs(
        new HashMap<String, Vec>() {{
          put("RGB", vec1); put("Ausweis", vec2);
        }});

    Dataset.TrainAndValid actual = sut.stratifiedSplit("RGB", 0.01, 7688714);

    final Dataset train = actual.train;
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, train.domain());

    final Dataset valid = actual.valid;
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, valid.domain());

    assertEquals(size, train.vec("Ausweis").length() + valid.vec("Ausweis").length());

    assertEquals( size/100, valid.vec("Ausweis").length());
    assertEquals(size/100*99, train.vec("Ausweis").length());
    
    Vec trgb = train.vec("RGB");
    
    int[] counts = new int[]{0,0,0};
    
    for (int i = 0; i < 10000; i++) {
      counts[(int)trgb.at8(i)]++;
    }
    assertArrayEquals(new int[]{218, 1858, 7924}, counts);
  }
}