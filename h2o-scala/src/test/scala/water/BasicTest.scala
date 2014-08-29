package water

import java.io.File
import org.junit.Test
import org.junit.BeforeClass
import water.fvec.{DataFrame,MapReduce}

class BasicTest extends TestUtil {
  @Test def basicTest() = {
    //val fr = new DataFrame(new File("../smalldata/iris/iris_wheader.csv"))
    //val fr = new DataFrame(new File("../smalldata/junit/cars.csv"))
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
      // Sums & sum squares
      val colX=0
      val colY=30
      class CalcSums(var X:Double =0, var Y:Double =0, var X2:Double =0, var nrows:Long=0) extends MapReduce[Array[Double],CalcSums] {
        override def map = (row : Array[Double]) => { X = row(colX); Y = row(colY); X2 = X*X; nrows=1 }
        override def reduce = (that : CalcSums) => { X += that.X ; Y += that.Y; X2 += that.X2; nrows += that.nrows }
      }
      val lr1 = new CalcSums().doAll(fr)
      val meanX = lr1.X/lr1.nrows
      val meanY = lr1.Y/lr1.nrows

      // Linear Regression, Pass 2
      // Sum of squares of errors
      class CalcErrs( var XXbar:Double =0, var YYbar:Double =0, var XYbar:Double =0 ) extends MapReduce[Array[Double],CalcErrs] {
        override def map = (row : Array[Double]) => { 
          val dx = row(colX)-meanX; val dy = row(colY)-meanY
          XXbar = dx*dx;  YYbar = dy*dy;  XYbar = dx*dy
        }
        override def reduce = (that : CalcErrs) => { XXbar += that.XXbar ; YYbar += that.YYbar; XYbar += that.XYbar }
      }
      val lr2 = new CalcErrs().doAll(fr)

      // Compute the regression
      val beta1 = lr2.XYbar / lr2.XXbar
      val beta0 = meanY - beta1 * meanX
      println("Y = "+beta1+"*X + "+beta0)

      // Linear Regression, Pass 3
      class CalcRegression( var ssr:Double = 0, var rss:Double = 0 ) extends MapReduce[Array[Double],CalcRegression] {
        override def map = (row : Array[Double]) => { 
          val X = row(colX); val Y = row(colY)
          val fit = beta1*X + beta0
          rss = (fit-    Y)*(fit-    Y)
          ssr = (fit-meanY)*(fit-meanY)
        }
        override def reduce = (that : CalcRegression) => { ssr += that.ssr ; rss += that.rss }
      }
      val lr3 = new CalcRegression().doAll(fr)

      // Compute goodness of fit
      val r2 = lr3.ssr / lr2.YYbar
      val df = lr1.nrows - 2
      val svar = lr3.rss / df
      val svar1 = svar / lr2.XXbar
      val svar0 = svar/lr1.nrows + meanX*meanX*svar1
      val beta0stderr = Math.sqrt(svar0)
      val beta1stderr = Math.sqrt(svar1)
      println("r2 = "+r2+", beta0_stderr="+beta0stderr+", beta1_stderr="+beta1stderr+", nrows="+lr1.nrows)

    } finally {
      fr.delete()
    }
  }
}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = water.TestUtil.stall_till_cloudsize(1)
}
