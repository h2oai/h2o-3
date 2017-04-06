package water.userapi

import java.io.{File, FileWriter}

import org.junit.{Assert, Test, BeforeClass}
import org.scalatest.Matchers._
import org.scalatest._
import water.TestUtil._
import water.fvec.{Frame, Vec}
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

  private def whichOne(i: Long) =
    if (i%100 < 2) 0 else if (i%100 < 20) 1 else 2

  val RGB = Array("red", "white", "blue")

  val rgbGenerator: UFunction[Integer, String] = (i: Int) => RGB(whichOne(i))

  def buildTestVec(size: Int): Vec = cvec(RGB, size, rgbGenerator)
  def buildIdentityVec(size: Int): Vec = vec(size, Functions.identity[Integer])
  
  test("One Hot Encode") {

    val size = 1000

    val vec1 = buildTestVec(size)

    val sut = H2ODataset.onVecs("RGB" -> vec1)

    val actual: H2ODataset = sut.oneHotEncode
    
    actual.domain.get shouldBe Array("RGB.red", "RGB.white", "RGB.blue", "RGB.missing(NA)")

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

  def buildTwoVecDataset(size: Int): H2ODataset = {
    val vec1 = buildTestVec(size)
    val vec2 = buildIdentityVec(size)

    H2ODataset.onVecs("RGB" -> vec1, "Ausweis" -> vec2)
  }
  
  test("Add splitting column") {

    val size = 1000

    val sut = buildTwoVecDataset(size)

    val actual = sut.addSplittingColumn("RGB", 0.01, 7688714)
    
    actual flatMap (_.domain) match {
      case Some(dom) =>
        dom shouldBe Array("RGB", "Ausweis", "test_train_split")
      case None =>
        fail("Failed to split, oops")
    }
    
  }

  test("stratified split") {

    val size = 1000000
    val sut = buildTwoVecDataset(size)

    val stratifiedSplit: Option[(Frame, Frame)] = sut.stratifiedSplit("RGB", 0.01, 7688714)
    stratifiedSplit match {
      case Some((train, valid)) =>
        assert(Array[String]("RGB", "Ausweis").mkString(",") == train.names.mkString(","))
        assert(size == train.vec("Ausweis").length + valid.vec("Ausweis").length)
        assert( size/100 == valid.vec("Ausweis").length)
        assert(size/100*99 == train.vec("Ausweis").length)
        val vrgb: Vec = valid.vec("RGB")
        val counts = ((0 until 3 map (_ -> 0) toMap) /: (0 until vrgb.length.toInt)) {
          case (c, i) => {
            val k = vrgb.at8(i).toInt
            val v = c(k) + 1
            c + (k -> v)
          }
        }
        assert(counts(0) == 200)
        assert(counts(1) == 1800)
        assert(counts(2) == 8000)

      case None => fail("Failed to do stratified split")
    }
  }

  test("Chicago, end to end") {
    val path = "smalldata/chicago/chicagoCensus.csv"
    val sut = H2ODataset.readFile(path)
    val expectedDom: Array[String] = "Community Area Number,COMMUNITY AREA NAME,PERCENT OF HOUSING CROWDED,PERCENT HOUSEHOLDS BELOW POVERTY,PERCENT AGED 16+ UNEMPLOYED,PERCENT AGED 25+ WITHOUT HIGH SCHOOL DIPLOMA,PERCENT AGED UNDER 18 OR OVER 64,PER CAPITA INCOME ,HARDSHIP INDEX".split(",")
    sut.domain match {
      case Some(d) =>
        d shouldBe expectedDom
      case None => fail("Oops, no domain!")
    }
    
    val columnUT = "COMMUNITY AREA NAME"
    
    sut.makeCategorical(columnUT)
    
    sut.domainOf(columnUT) match {
      case Some(categories) => 
        assert(categories.length == 78)
      case None =>
        fail(s"Could not find domain of $columnUT")
    }

    val oneHot = sut.oneHotEncodeExcluding()
    assert(Some(87) == oneHot.domain.map(_.length))
    
  }

  /**
    * An end-to-end sample of categorification, one-hot encoding, and stratified split
    */
  test("Citibike, end to end") {
    // this is the path of the sample we use
    val path = "smalldata/demos/citibike_20k.csv"
    // we read the file here, and produce a dataset from it
    val dataset = H2ODataset.readFile(path)

    // removing these two column that we don't care about
    dataset.removeColumn("start station name", "end station name")

    // this is the expected number of rows in the dataset
    val expectedSize = 20000
    
    // checking that we got exactly the number of records we expected
    assert(expectedSize == dataset.length)

    // converting gender column to categorical type
    dataset.makeCategorical("gender")
    
    // the domain should be "male", "female", and "N/A"
    val categories = dataset.domainOf("gender")
    assert(Some(3) == categories.map(_.length))
    
    // apply oneHot encoding to all applicable columns except gender (we'll need it)
    val oneHot = dataset.oneHotEncodeExcluding("gender")
    
    // we expect 15 possible 
    assert(Some(15) == oneHot.domain.map(_.length))

    // Planning to do stratified split, so 0.75 go to train, 0.25 go to valid datasets
    val ratio = 0.25
    val expectedValidSize = (expectedSize * ratio).toInt
    val expectedTrainSize = expectedSize - expectedValidSize
    
    // do stratified split on gender column; 55555 is the random seed
    oneHot.stratifiedSplit("gender", ratio, 55555) match {
      case Some((train, valid)) =>
        assert(expectedTrainSize == ETL.length(train))
        assert(expectedValidSize == ETL.length(valid))

      case None =>
        fail("Failed to stratify by gender")
    }
  }
  
  test("SomethingElse") {
    assert(true)
  }
}

object H2ODatasetTest extends TestUtil {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(3)
}
