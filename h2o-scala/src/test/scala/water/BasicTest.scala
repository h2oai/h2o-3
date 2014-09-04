package water

import java.io.File
import org.junit.Test
import org.junit.BeforeClass
import water.fvec.{DataFrame,MapReduce}

class BasicTest extends TestUtil {
  @Test def basicTest() = {
    //val fr = new DataFrame(new File("../smalldata/iris/iris_wheader.csv"))
    val fr = new DataFrame(new File("../smalldata/junit/cars_nice_header.csv"))
    val fr2 = fr('cylinders,'displacement)   // Predict delay from year alone
    //val fr = new DataFrame(new File("../smalldata/airlines/allyears2k_headers.zip"))
    try {

      // Linear Regression, Pass1
      // Sums & sum squares
      class CalcSums(var X:Double =0, var Y:Double =0, var X2:Double =0, var nrows:Long=0) extends MapReduce[Array[Double],CalcSums] {
        override def map = (row : Array[Double]) => { X = row(0); Y = row(1); X2 = X*X; nrows=1 }
        override def reduce = (that : CalcSums) => { X += that.X ; Y += that.Y; X2 += that.X2; nrows += that.nrows }
      }
      val lr1 = new CalcSums().doAll(fr2)
      val meanX = lr1.X/lr1.nrows
      val meanY = lr1.Y/lr1.nrows

      // Linear Regression, Pass 2
      // Sum of squares of errors
      class CalcErrs( var XXbar:Double =0, var YYbar:Double =0, var XYbar:Double =0 ) extends MapReduce[Array[Double],CalcErrs] {
        override def map = (row : Array[Double]) => { 
          val dx = row(0)-meanX; val dy = row(1)-meanY
          XXbar = dx*dx;  YYbar = dy*dy;  XYbar = dx*dy
        }
        override def reduce = (that : CalcErrs) => { XXbar += that.XXbar ; YYbar += that.YYbar; XYbar += that.XYbar }
      }
      val lr2 = new CalcErrs().doAll(fr2)

      // Compute the regression
      val beta1 = lr2.XYbar / lr2.XXbar
      val beta0 = meanY - beta1 * meanX
      println(fr2._names(1)+" = "+beta1+"*"+fr2._names(0)+" + "+beta0)

      // Linear Regression, Pass 3
      class CalcRegression( var ssr:Double = 0, var rss:Double = 0 ) extends MapReduce[Array[Double],CalcRegression] {
        override def map = (row : Array[Double]) => { 
          val X = row(0); val Y = row(1)
          val fit = beta1*X + beta0
          rss = (fit-    Y)*(fit-    Y)
          ssr = (fit-meanY)*(fit-meanY)
        }
        override def reduce = (that : CalcRegression) => { ssr += that.ssr ; rss += that.rss }
      }
      val lr3 = new CalcRegression().doAll(fr2)

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
      fr2.delete()
    }
  }

  // test is off, until can pass around anon functions
  /*@Test*/ def biggerTest() = {
    val fr = new DataFrame(new File("../../datasets/UCI/UCI-large/covtype/covtype.data"))
    try {

      val start = System.currentTimeMillis
      (0 until 10)foreach( i => {
        class CalcSums(var X:Double =0, var Y:Double =0, var X2:Double =0, var nrows:Long=0) extends MapReduce[Array[Double],CalcSums] {
          val DEBUG_WEAVER=1
          override def map = (row : Array[Double]) => { X = row(0); Y = row(1); X2 = X*X; nrows=1 }
          override def reduce = (that : CalcSums) => { X += that.X ; Y += that.Y; X2 += that.X2; nrows += that.nrows }
        }
        val lr1 = new CalcSums().doAll(fr)
        val meanX = lr1.X/lr1.nrows
        val meanY = lr1.Y/lr1.nrows
      })
      val end = System.currentTimeMillis
      println("CalcSums iter over covtype: "+(end-start)/10)

    } finally {
      fr.delete()
    }
  }
}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = water.TestUtil.stall_till_cloudsize(2)
}
