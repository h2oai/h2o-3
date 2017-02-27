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
import water.udf.fp.Functions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test suite for Frame functionality in UserAPI.
 * 
 * Created by vpatryshev on 2/22/17.
 */
public class ETLTest extends TestUtil {
  static File testFile1 = new File("DatasetTest.1.tmp");

  @BeforeClass
  static public void setup() throws IOException { 
    stall_till_cloudsize(2);
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
      ETL.read(new File("c:/System.ini"));
  }

  @Test
  public void testRead() throws Exception {
    Frame sut = ETL.read(testFile1);
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

    Frame sut = ETL.onVecs(new HashMap<String, Vec>() {{put("RGB", vec1);}});
    
    Frame actual = ETL.oneHotEncode(sut);
    
    assertArrayEquals(new String[]{"RGB", "RGB.red", "RGB.white", "RGB.blue", "RGB.missing(NA)"}, actual.names());
    
    for (int i = 0; i < size; i++) {
      for (int j = 1; j < 4; j++) {
        assertEquals("@(" + i + "," + j + ")", 
            (j-1) == whichOne(i) ? 1 : 0,
            actual.vec(actual.names()[j]).at8(i));
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
    
    Frame sut = ETL.onVecs(
        new HashMap<String, Vec>() {{
          put("RGB", vec1); put("Ausweis", vec2);
        }});

    Frame actual = ETL.addSplittingColumn(sut, "RGB", 0.01, 7688714);

    assertArrayEquals(new String[]{"RGB", "Ausweis", "test_train_split"}, actual.names());

    // it is hard to test specific values
  }

  @Test
  public void testStratifiedSplit() throws Exception {
    final String[] domain = {"red", "white", "blue"};

    final int size = 1000000;
    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)];
      }
    });

    final Vec vec2 = vec(size, Functions.<Integer>identity());

    Frame sut = ETL.onVecs(
        new HashMap<String, Vec>() {{
          put("RGB", vec1); put("Ausweis", vec2);
        }});

    TrainAndValid actual = ETL.stratifiedSplit(sut, "RGB", 0.01, 7688714);

    final Frame train = actual.train;
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, train.names());

    final Frame valid = actual.valid;
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, valid.names());

    assertEquals(size, train.vec("Ausweis").length() + valid.vec("Ausweis").length());

    assertEquals( size/100, valid.vec("Ausweis").length());
    assertEquals(size/100*99, train.vec("Ausweis").length());
    
    Vec trgb = train.vec("RGB");
    
    int[] counts = new int[]{0,0,0};
    
    for (int i = 0; i < 10000; i++) {
      counts[(int)trgb.at8(i)]++;
    }
    assertArrayEquals(new int[]{200, 1802, 7998}, counts);
  }
  
  @Test
  public void testEndToEndInChicago() {
    String path = "smalldata/chicago/chicagoCensus.csv";
    Frame sut = ETL.readFile(path);
    String[] expectedDom = "Community Area Number,COMMUNITY AREA NAME,PERCENT OF HOUSING CROWDED,PERCENT HOUSEHOLDS BELOW POVERTY,PERCENT AGED 16+ UNEMPLOYED,PERCENT AGED 25+ WITHOUT HIGH SCHOOL DIPLOMA,PERCENT AGED UNDER 18 OR OVER 64,PER CAPITA INCOME ,HARDSHIP INDEX".split(",");
    assertArrayEquals(expectedDom, sut.names());
    ETL.makeCategorical(sut, "COMMUNITY AREA NAME");
    String[] categories = ETL.domainOf(sut, "COMMUNITY AREA NAME");
    assertEquals(78, categories.length);
    Frame oneHot = ETL.oneHotEncode(sut);
    assertEquals(88, oneHot.names().length);
  }
  
  @Test
  public void testEndToEndCitibike() {
    String path = "smalldata/demos/citibike_20k.csv";
    Frame sut = ETL.readFile(path);

    final int expectedSize = 20000;
    final double ratio = 0.25;
    final int expectedValidSize = (int)(expectedSize * ratio);
    final int expectedTrainSize = expectedSize - expectedValidSize;
    ETL.removeColumn(sut, "start station name", "end station name");
    assertEquals(expectedSize, ETL.length(sut));
    ETL.makeCategorical(sut, "gender");
    String[] categories = ETL.domainOf(sut, "gender");
    assertEquals(3, categories.length);
    Frame oneHot = ETL.oneHotEncode(sut);
    assertEquals(20, oneHot.names().length);
    TrainAndValid tav = ETL.stratifiedSplit(oneHot, "gender", ratio, 55555);
    assertEquals(expectedTrainSize, ETL.length(tav.train));
    assertEquals(expectedValidSize, ETL.length(tav.valid));
  }
}