package water

import java.io.File
import org.junit.{Test,BeforeClass,Ignore}
import water.fvec.{DataFrame,MapReduce}
import scala.reflect.ClassTag

class BasicTest extends TestUtil {
  @Test def basicTest() = {
    //val fr = new DataFrame(new File("../smalldata/iris/iris_wheader.csv"))
    val fr = new DataFrame(new File("../smalldata/junit/cars_nice_header.csv"))
    val fr2 = fr('cylinders,'displacement)   // Predict delay from year alone
    //val fr = new DataFrame(new File("../smalldata/airlines/allyears2k_headers.zip"))
    try {

      // Linear Regression, Pass1
      // Sums & sum squares
      class Pass1 extends MapReduce[Array[Double],Pass1] { var X, Y, X2 =0.0; var nrows=0L
        override def map(row : maptype) = { X = row(0); Y = row(1); X2 = X*X; nrows=1 }
        override def reduce(@@ : self) = { X += @@.X ; Y += @@.Y; X2 += @@.X2; nrows += @@.nrows }
      }
      val lr1 = new Pass1().doAll(fr2)
      val meanX = lr1.X/lr1.nrows
      val meanY = lr1.Y/lr1.nrows

      // Linear Regression, Pass 2
      // Sum of squares of errors
      class Pass2 extends MapReduce[Array[Double],Pass2] { var XXbar, YYbar, XYbar = 0.0
        override def map(row : maptype) = { 
          val dx = row(0)-meanX; val dy = row(1)-meanY
          XXbar = dx*dx;  YYbar = dy*dy;  XYbar = dx*dy
        }
        override def reduce(@@ : self) = { XXbar += @@.XXbar ; YYbar += @@.YYbar; XYbar += @@.XYbar }
      }
      val lr2 = new Pass2().doAll(fr2)

      // Compute the regression
      val beta1 = lr2.XYbar / lr2.XXbar
      val beta0 = meanY - beta1 * meanX
      println(fr2._names(1)+" = "+beta1+"*"+fr2._names(0)+" + "+beta0)

      // Linear Regression, Pass 3
      class Pass3 extends MapReduce[Array[Double],Pass3] { var ssr, rss = 0.0
        override def map(row : maptype) = { 
          val X = row(0); val Y = row(1)
          val fit = beta1*X + beta0
          rss = (fit-    Y)*(fit-    Y)
          ssr = (fit-meanY)*(fit-meanY)
        }
        override def reduce(@@ : self) = { ssr += @@.ssr ; rss += @@.rss }
      }
      val lr3 = new Pass3().doAll(fr2)

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

  // test is off because of its size
  @Test @Ignore def biggerTest() = {
    //val fr = new DataFrame(new File("../smalldata/junit/cars_nice_header.csv"))
    val fr = new DataFrame(new File("../../datasets/UCI/UCI-large/covtype/covtype.data"))
    val fr2 = fr('C1,'C2)
    try {
      val iters = 100
      var meanX, meanY=0.0
      var nrows=0L
      val start = System.currentTimeMillis
      (0 until iters) foreach( i => {
        class Pass1 extends MapReduce[Array[Double],Pass1] { var X, Y, X2 =0.0; var nrows=0L
          override def map(row : maptype) = { X = row(0); Y = row(1); X2 = X*X; nrows=1 }
          override def reduce(@@ : self) = { X += @@.X ; Y += @@.Y; X2 += @@.X2; nrows += @@.nrows }
        }
        val lr1 = new Pass1().doAll(fr2)
        meanX = lr1.X/lr1.nrows
        meanY = lr1.Y/lr1.nrows
        nrows = lr1.nrows
      })
      val end = System.currentTimeMillis
      println("CalcSums iter over covtype: " + (end - start) / iters + "ms, meanX=" + meanX + ", meanY=" + meanY+", nrows="+nrows)
    } finally {
      fr.delete()
      fr2.delete()
    }
  }

}

object BasicTest extends TestUtil {
  @BeforeClass def setup() = TestUtil.stall_till_cloudsize(5)
}
