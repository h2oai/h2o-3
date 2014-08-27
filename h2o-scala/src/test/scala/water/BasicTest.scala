package water

import java.io.File
import org.junit._
import water.TestUtil
import water.fvec.DataFrame

class BasicTest extends water.TestUtil {
  @Test def basicTest() = {
    val fr = new DataFrame(new File("../smalldata/iris/iris_wheader.csv"))
    println(fr.numRows)
  }
}

object BasicTest extends water.TestUtil {
  @BeforeClass def setup() = water.TestUtil.stall_till_cloudsize(1)
}
