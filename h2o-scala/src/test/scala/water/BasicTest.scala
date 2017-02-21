package water

import org.junit.{Assert, BeforeClass, Test}
import water.fvec.H2OFrame
import water.util.FileUtils._

class BasicTest extends TestUtil {

  @Test def testDataFrameLoadAPI(): Unit = {
    val filename1 = "../smalldata/iris/iris_wheader.csv"
    val filename2 = "../smalldata/iris/iris.csv"
    val file1 = getFile(filename1)
    val file2 = getFile(filename2)
    val uri1 = file1.toURI
    val uri2 = file2.toURI
    // Create frames
    val fr1 = new H2OFrame(file1)
    val fr2 = new H2OFrame(uri1)
    val fr3 = new H2OFrame(uri1, uri2)
    val fr4 = new H2OFrame(fr1)
    val fr5 = new H2OFrame("iris_wheader.hex")

    Assert.assertEquals(5, fr1.numCols())
    Assert.assertEquals(150, fr1.numRows())
    Assert.assertEquals(fr1.numCols(), fr2.numCols())
    Assert.assertEquals(fr1.numCols(), fr3.numCols())
    Assert.assertEquals(fr1.numCols(), fr4.numCols())
    Assert.assertEquals(fr1.numCols(), fr5.numCols())
    Assert.assertEquals(fr1.numRows(), fr2.numRows())
    Assert.assertEquals(2*fr1.numRows(), fr3.numRows())
    Assert.assertEquals(fr1.numRows(), fr5.numRows())
    // Cleanup
    fr1.delete()
    fr2.delete()
    fr3.delete()
    // We do not need cleanup fr4,fr5 since they are just referencing fr1
  }
}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(5)
}
