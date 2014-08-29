package water

import java.io.File
import org.junit.Test
import org.junit.BeforeClass
import water.fvec.{DataFrame,MapReduce}

class BasicTest extends TestUtil {
  @Test def basicTest() = {
    //val fr = new DataFrame(new File("../smalldata/iris/iris_wheader.csv"))
    val fr = new DataFrame(new File("../smalldata/airlines/allyears2k_headers.zip"))
    try {
      println(fr.numRows)               // 150 rows
      println(fr.size)                  // 150 rows, but limited to an int's of rows
      println(fr.get(-1))               // None
      println(fr.get(150))              // None
      println(fr.get(0).get.deep.mkString(",")) // row 0, pretty printed
      // For loop over a frame; called with idx & row; row is a recycled array
      // filled with None or Option[Double]
      //val x = for( (idx,row) <- fr ) yield row(0)
      //println(x)                        // All of column 0

      // Linear Regression, Pass1
      class CalcSums(var X:Double =0, var Y:Double =0, var X2:Double =0, var nrows:Long=0) extends MapReduce[Array[Double],CalcSums] {
        override def map = (row : Array[Double]) => { X = row(0) ; Y = row(30); X2 = X*X; nrows=1 }
        override def reduce = (that : CalcSums) => { X += that.X ; Y += that.Y; X2 += that.X2; nrows += that.nrows }
      }
      val lr1 = new CalcSums.doAll(fr)
      val meanX = lr1.X/lr1.nrows
      val meanY = lr1.Y/lr1.nrows
      println("avg Year="+meanX+" avg IsDepDelayed="+meanY)

      class CalcErrs( var XXbar:Double =0, var YYbar:Double =0, var XYbar:Double =0 ) extends MapReduce[Array[Double],CalcErrs] {
        override def map = (row : Array[Double]) => { 
          val dx = row( 0)-meanX; val dy = row(30)-meanY; 
          XXbar = dx*dx;  YYbar = dy*dy;  XYbar = dx*dy
        }
        override def reduce = (that : CalcErrs) => { XXbar += that.XXbar ; YYbar += that.YYbar; XYbar += that.XYbar }
      }
      val lr2 = new CalcErrs.doAll(fr)
      println(lr2.XXbar+" "+lr2.YYbar+" "+lr2.XYbar)


    } finally {
      fr.delete
    }
  }
}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = water.TestUtil.stall_till_cloudsize(1)
}
