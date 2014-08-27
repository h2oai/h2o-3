package water

import java.io.File
import org.junit.Test
import org.junit.BeforeClass
import water.fvec.DataFrame

class BasicTest extends TestUtil {
  @Test def basicTest() = {
    val fr = new DataFrame(new File("../smalldata/iris/iris_wheader.csv"))
    try {
      println(fr.numRows)               // 150 rows
      println(fr.size)                  // 150 rows, but limited to an int's of rows
      println(fr.get(-1))               // None
      println(fr.get(150))              // None
      println(fr.get(0).get.deep.mkString(",")) // row 0, pretty printed
      val x = for( (idx,row) <- fr ) yield row(0)
      println(x)
    } finally {
      fr.delete
    }
  }
}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = water.TestUtil.stall_till_cloudsize(1)
}
