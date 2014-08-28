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
      // For loop over a frame; called with idx & row; row is a recycled array
      // filled with None or Option[Double]
      val x = for( (idx,row) <- fr ) yield row(0)
      println(x)                        // All of column 0
      // Column-wise addition, allowing for Nones
      val y = fr.reduce( (l,r) => (l._1, for( (ll,rr) <- l._2.zip(r._2) ) yield add(ll,rr)))
      println(y._2.deep.mkString(","))
    } finally {
      fr.delete
    }
  }

  // Add values "the R way" - NA's ignored
  private def add(l : Option[Any], r : Option[Any] ):Option[Any] = {
    (l,r) match {
      case (None,x) => x
      case (x,None) => x
      case (Some(ld : Double),Some(rd : Double)) => Some(ld+rd)
      case _ => ???
    }
  }
}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = water.TestUtil.stall_till_cloudsize(1)
}
