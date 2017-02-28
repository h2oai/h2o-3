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
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static water.userapi.Etl.*;
import static water.userapi.Etl.Load.*;
import static water.userapi.Etl.Transform.*;
/**
 * Test suite for Frame functionality in UserAPI.
 * 
 * Created by vpatryshev on 2/22/17.
 */
public class EtlTest extends TestUtil {
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
      readFile(new File("c:/System.ini"));
  }

  @Test
  public void testRead() throws Exception {
    Frame sut = readFile(testFile1);
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

    Frame sut = createFrame(new HashMap<String, Vec>() {{put("RGB", vec1);}});
    
    Frame actual = oneHotEncode(sut);
    
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
    
    Frame sut = createFrame(
        new HashMap<String, Vec>() {{
          put("RGB", vec1); put("Ausweis", vec2);
        }});

    Frame actual = stratifiedSplitColumn(sut, "RGB", 0.01, 7688714);

    assertArrayEquals(new String[]{"Ausweis", "RGB", "testSplitCol"}, actual.names());

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

    Frame sut = createFrame(
        new HashMap<String, Vec>() {{
          put("RGB", vec1); put("Ausweis", vec2);
        }});

    Map<String, Frame> actual = null; //Extract.stratifiedSplit(sut, "RGB", 0.01, 7688714);

    final Frame train = actual.get("train");
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, train.names());

    final Frame valid = actual.get("valid");
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
  public void testEndToEndInChicago() throws IOException {
    String path = "smalldata/chicago/chicagoCensus.csv";
    Frame sut = readFile(path);
    String[] expectedDom = "Community Area Number,COMMUNITY AREA NAME,PERCENT OF HOUSING CROWDED,PERCENT HOUSEHOLDS BELOW POVERTY,PERCENT AGED 16+ UNEMPLOYED,PERCENT AGED 25+ WITHOUT HIGH SCHOOL DIPLOMA,PERCENT AGED UNDER 18 OR OVER 64,PER CAPITA INCOME ,HARDSHIP INDEX".split(",");
    assertArrayEquals(expectedDom, sut.names());
    makeCategorical(sut, "COMMUNITY AREA NAME");
    String[] categories = domainOf(sut, "COMMUNITY AREA NAME");
    assertEquals(78, categories.length);
    Frame oneHot = oneHotEncode(sut);
    assertEquals(88, oneHot.names().length);
  }
  
  @Test
  public void testEndToEndCitibike() throws IOException, InterruptedException {
    String path = "smalldata/demos/citibike_20k.csv";
    Frame inputFrame = Load.readFile(path);

    final int expectedSize = 20000;
    final double ratio = 0.25;
    final int expectedValidSize = (int)(expectedSize * ratio);
    final int expectedTrainSize = expectedSize - expectedValidSize;
    System.out.println(inputFrame.toString(0, 10));
    // Drop useless columns
    Frame frame1 = Transform.dropColumn(inputFrame, "start station name", "end station name");
    assertEquals(expectedSize, numRows(frame1));
    System.out.println("Step 1\n" + frame1.toString(0, 10));

    Frame frame2 = Transform.makeCategorical(frame1, "gender");
    System.out.println("Step 2\n" + frame2.toString(0, 10));

    Frame frame3 = Transform.oneHotEncode(frame2);
    System.out.println("Step 3\n" + frame3.toString(0, 10));

    Frame stratifiedSplitColumn = Transform.stratifiedSplitColumn(frame3, "gender", ratio, 42);
    System.out.println("Step 4\n" + stratifiedSplitColumn.toString(0, 10));

    Map<String, Frame> frame5 = Extract.splitByColumn(frame3, stratifiedSplitColumn);
    assertEquals(expectedTrainSize, numRows(frame5.get("train")));
    System.out.println("Step 5 - train part\n" + frame5.get("train").toString(0, 10));
    assertEquals(expectedValidSize, numRows(frame5.get("test")));
    System.out.println("Step 5 - test part\n" + frame5.get("test").toString(0, 10));
  }
}