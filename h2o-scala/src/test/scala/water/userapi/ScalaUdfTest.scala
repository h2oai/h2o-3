package water.userapi

import java.io.{File, FileWriter}

import org.junit.BeforeClass
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import water.{Test0, TestUtil}

import scala.language.postfixOps

/**
  * Test for H2ODataset
  */
class H2ODatasetTest extends Test0 with BeforeAndAfter with BeforeAndAfterAll {
  val A_LOT: Int = 1 << 20
  val testFile1 = new File("DatasetTest.1.tmp")

  override def beforeAll: Unit = {
    startCloud(2)
    val fw = new FileWriter(testFile1)
    fw.write("A\tB\tC\na1\tb1\tc1\na2\tb2\tc2\n")
    fw.close()
    testFile1.deleteOnExit()
    
  }

  test("IsNA") {
    assert(1 == 2)
  }
  /*

import static org.junit.Assert.*

/**
 * Test suite for Dataset functionality in UserAPI.
 * 
 * Created by vpatryshev on 2/22/17.
 */
public class DatasetTest extends TestUtil {
  static File testFile1 = new File("DatasetTest.1.tmp")

  @BeforeClass
  static public void setup() throws IOException { 
    stall_till_cloudsize(2)
    Writer fw = new FileWriter(testFile1)
    fw.write("A\tB\tC\na1\tb1\tc1\na2\tb2\tc2\n")
    fw.close()
    testFile1.deleteOnExit()
  }
  
  @Before
  public void hi() { Scope.enter() }

  @After
  public void bye() { Scope.exit() }

  @Test(expected=DataException.class)
  public void testNoReadFile() throws Exception {
      Dataset.read(new File("c:/System.ini"))
  }

  @Test
  public void testRead() throws Exception {
    Dataset sut = Dataset.read(testFile1)
    Vec v1 = sut.vec("C2")
    assertEquals("b1", String.valueOf(v1.atStr(new BufferedString(), 1)))
  }

  final int size = 1000

  private static int whichOne(int i) {
    return i%100 < 2 ? 0 : i%100 < 20 ? 1 : 2
  }
  
  @Test
  public void testOneHotEncode() throws Exception {
    final String[] domain = {"red", "white", "blue"}

    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)]
      }
    })

    Dataset sut = Dataset.onVecs(new HashMap<String, Vec>() {{put("RGB", vec1)}})
    
    Dataset actual = sut.oneHotEncode()
    
    assertArrayEquals(new String[]{"RGB", "RGB.red", "RGB.white", "RGB.blue", "RGB.missing(NA)"}, actual.domain())
    
    for (int i = 0 i < size i++) {
      for (int j = 1 j < 4 j++) {
        assertEquals("@(" + i + "," + j + ")", 
            (j-1) == whichOne(i) ? 1 : 0,
            actual.vec(actual.domain()[j]).at8(i))
      }
    }
    
  }

  @Test
  public void testAddSplittingColumn() throws Exception {
    final String[] domain = {"red", "white", "blue"}

    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)]
      }
    })

    final Vec vec2 = vec(size, Functions.<Integer>identity())
    
    Dataset sut = Dataset.onVecs(
        new HashMap<String, Vec>() {{
          put("RGB", vec1) put("Ausweis", vec2)
        }})

    Dataset actual = sut.addSplittingColumn("RGB", 0.01, 7688714)

    assertArrayEquals(new String[]{"RGB", "Ausweis", "test_train_split"}, actual.domain())

    // it is hard to test specific values
  }

  @Test
  public void testStratifiedSplit() throws Exception {
    final String[] domain = {"red", "white", "blue"}

    final int size = 1000000
    final Vec vec1 = cvec(domain, size, new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return domain[whichOne(i)]
      }
    })

    final Vec vec2 = vec(size, Functions.<Integer>identity())

    Dataset sut = Dataset.onVecs(
        new HashMap<String, Vec>() {{
          put("RGB", vec1) put("Ausweis", vec2)
        }})

    TrainAndValid actual = sut.stratifiedSplit("RGB", 0.01, 7688714)

    final Frame train = actual.train
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, train.names())

    final Frame valid = actual.valid
    assertArrayEquals(new String[]{"RGB", "Ausweis"}, valid.names())

    assertEquals(size, train.vec("Ausweis").length() + valid.vec("Ausweis").length())

    assertEquals( size/100, valid.vec("Ausweis").length())
    assertEquals(size/100*99, train.vec("Ausweis").length())
    
    Vec trgb = train.vec("RGB")
    
    int[] counts = new int[]{0,0,0}
    
    for (int i = 0 i < 10000 i++) {
      counts[(int)trgb.at8(i)]++
    }
    assertArrayEquals(new int[]{200, 1802, 7998}, counts)
  }
  
  @Test
  public void testEndToEndInChicago() {
    String path = "smalldata/chicago/chicagoCensus.csv"
    Dataset sut = Dataset.readFile(path)
    String[] expectedDom = "Community Area Number,COMMUNITY AREA NAME,PERCENT OF HOUSING CROWDED,PERCENT HOUSEHOLDS BELOW POVERTY,PERCENT AGED 16+ UNEMPLOYED,PERCENT AGED 25+ WITHOUT HIGH SCHOOL DIPLOMA,PERCENT AGED UNDER 18 OR OVER 64,PER CAPITA INCOME ,HARDSHIP INDEX".split(",")
    assertArrayEquals(expectedDom, sut.domain())
    sut.makeCategorical("COMMUNITY AREA NAME")
    String[] categories = sut.domainOf("COMMUNITY AREA NAME")
    assertEquals(78, categories.length)
    Dataset oneHot = sut.oneHotEncode()
    assertEquals(88, oneHot.domain().length)
  }
  
  @Test
  public void testEndToEndCitibike() {
    String path = "smalldata/demos/citibike_20k.csv"
    Dataset sut = Dataset.readFile(path)

    final int expectedSize = 20000
    final double ratio = 0.25
    final int expectedValidSize = (int)(expectedSize * ratio)
    final int expectedTrainSize = expectedSize - expectedValidSize
    sut.removeColumn("start station name", "end station name")
    assertEquals(expectedSize, sut.length())
    sut.makeCategorical("gender")
    String[] categories = sut.domainOf("gender")
    assertEquals(3, categories.length)
    Dataset oneHot = sut.oneHotEncode()
    assertEquals(20, oneHot.domain().length)
    TrainAndValid tav = oneHot.stratifiedSplit("gender", ratio, 55555)
    assertEquals(expectedTrainSize, ETL.length(tav.train))
    assertEquals(expectedValidSize, ETL.length(tav.valid))
  }
}   */
}

object H2ODatasetTest extends TestUtil {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(3)
}
