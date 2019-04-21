package water.fvec

import java.net.URI

import org.junit.{Assert, BeforeClass, Test}
import water.util.FileUtils
import water.TestUtil
import water.parser.ParseSetup

/**
 * Tests for exposed frame operations.
 */
class FrameOpsTest extends TestUtil {

  @Test
  def testImportFromHTTP(): Unit = {
    val fr = new H2OFrame(URI.create("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip"))
    try {
      assert(fr.numCols() == 12)
      assert(fr.numRows() == 2691)
    } finally {
      fr.delete()
    }
  }

  /** Test importing from HTTP together with import from file://
    * The purpose of this test is to test that we can specify multiple sources as part of one single frame creation call
    */
  @Test
  def testImportFromHTTPWithFile(): Unit = {
    val fr = new H2OFrame(URI.create("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip"), FileUtils.getFile("smalldata/airlines/AirlinesTest.csv.zip").toURI)
    try {
      assert(fr.numCols() == 12)
      assert(fr.numRows() == 5382)
    } finally {
      fr.delete()
    }
  }


  @Test
  def testParserSetup(): Unit = {
    val irisFile = FileUtils.getFile("smalldata/iris/iris.csv")
    val f1 = new H2OFrame(irisFile)
    // Default setup
    val parserSetup = H2OFrame.defaultParserSetup()
    val f2 = new H2OFrame(parserSetup, irisFile)
    // Modify parser setup hint
    parserSetup.setSeparator(',').setCheckHeader(ParseSetup.HAS_HEADER).setNumberColumns(5)
    val f3 = new H2OFrame(parserSetup, irisFile)

    try {
      Assert.assertEquals(f1.numCols(), f2.numCols())
      Assert.assertEquals(f1.numRows(), f2.numRows())
      Assert.assertEquals(f1.numCols(), f3.numCols())
      Assert.assertEquals(f1.numRows(), f3.numRows() + 1 /* one line should be use as header */)
    } finally {
      f1.delete()
      f2.delete()
      f3.delete()
    }
  }

  @Test
  def testApplyMethod1(): Unit = {
    val carsFile = FileUtils.getFile("smalldata/junit/cars.csv")
    val f1 = new H2OFrame(carsFile)
    val subframe = f1('name)
    try {
      Assert.assertEquals(1, subframe.numCols())
      Assert.assertEquals(f1.numRows(), subframe.numRows())

      // Transform 'name' column to string type
      Assert.assertEquals(Vec.T_CAT, f1.vec("name").get_type())
      f1((name, vec) => vec.toStringVec, (name, vec) => name == "name" && vec.get_type() == Vec.T_CAT)
      Assert.assertEquals(Vec.T_STR, f1.vec("name").get_type())
    } finally {
      f1.delete()
      subframe.delete()
    }
  }
}

object FrameOpsTest extends TestUtil {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(1)
}

