package water.userapi

import java.io.{File, FileWriter}

import org.junit.BeforeClass
import org.scalatest.Matchers._
import org.scalatest._
import water.TestUtil._
import water.fvec.Vec
import water.udf.fp.{Function => UFunction, Functions}
import water.util.fp.JavaInterop
import water.{Test0, TestUtil}

import scala.language.postfixOps
import JavaInterop._


/**
  * Test for H2ODataset
  */
class H2ODatasetTest extends Test0  with BeforeAndAfterAll {
  
  
  val A_LOT: Int = 1 << 20
  val testFile1 = new File("DatasetTest.1.tmp")

  override def beforeAll: Unit = {
    startCloud(2)
    val fw = new FileWriter(testFile1)
    fw.write("A\tB\tC\na1\tb1\tc1\na2\tb2\tc2\n")
    fw.close()
    testFile1.deleteOnExit()
  }
  
  test("if file does not exist") {
    val notafile = "c:/System.ini"
    val ex = the [DataException] thrownBy {
      H2ODataset.read(new File(notafile))
    }
    val expected = s"Could not read $notafile"
    
    assert(ex.getMessage contains expected)
  }
  
  test("read test file") {
    val sut = H2ODataset.read(testFile1)
    sut.vec("C2") match {
      case Some(v1) =>
        assert(v1.stringAt(1) == "b1")

      case None =>
        fail("Vector V2 must have been there")
    }
  }
  
  val size = 1000

  private def whichOne(i: Long) =
    if (i%100 < 2) 0 else if (i%100 < 20) 1 else 2

  val RGB = Array("red", "white", "blue")

  val rgbGenerator: UFunction[Integer, String] = (i: Int) => RGB(whichOne(i))

  def buildTestVec: Vec = cvec(RGB, size, rgbGenerator)

  test("One Hot Encode") {
    
    val vec1 = buildTestVec

    val sut = H2ODataset.onVecs("RGB" -> vec1)

    val actual = sut.oneHotEncode()
    
    assert(actual.domain.get.mkString == Array("RGB.red", "RGB.white", "RGB.blue", "RGB.missing(NA)").mkString)

    val dom = actual.domain.get
    
    for { j <- 0 until 4 } {
      val name = dom(j)
      val vec: Option[Vec] = actual.vec(name)
      for { i <- 0 until size } {
        val expected = if (whichOne(i) == j) 1 else 0
        vec match {
          case Some(v) =>
            assert(expected == v.at8(i), s"@($i, $j: $name)")
          case None => fail(s"No vec at $j => ${dom(j)}")
        }
      }
    }
  }

  test("Add splitting column") {

    val vec1 = buildTestVec
    val vec2 = vec(size, Functions.identity[Integer])

    val sut = H2ODataset.onVecs("RGB" -> vec1, "Ausweis" -> vec2)

    val actual = sut.addSplittingColumn("RGB", 0.01, 7688714)
    
    actual flatMap (_.domain) match {
      case Some(dom) =>
        assert(Array("RGB", "Ausweis", "test_train_split").mkString(",") == dom.mkString(","))
      case None =>
        fail("Failed to split, oops")
    }
    
  }

  test("stratified split") {

  }
  
  /*

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
