package hex.glm

import java.io.File

import hex.DataInfo
import hex.glm.GLMModel.GLMParameters.Family

import org.junit.Assert
import org.junit.Assert._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import water.fvec.{C0DChunk, Chunk, Frame, H2OFrame}
import water.{TestWithCloud, MRTask, Scope}

/**
  * Scala version of h2o's GLM test.
  */
@RunWith(classOf[JUnitRunner])
class GLM_Test extends TestWithCloud(1) {

  def testScoring(m: GLMModel, fr: Frame) {
    Scope.enter()
    val fr2: Frame = new Frame(fr)
    val preds: Frame = Scope.track(m.score(fr2))
    m.adaptTestForTrain(fr2, true, false)
    fr2.remove(fr2.numCols - 1)
    val dataInfo: DataInfo = m._output._dinfo
    val p: Int = dataInfo._cats + dataInfo._nums
    val p2: Int = fr2.numCols - (if (dataInfo._weights) 1 else 0) - (if (m._output._dinfo._offset) 1 else 0)
    assert(p == p2, p + " != " + p2)
    fr2.add(preds.names, preds.vecs)
    new TestScore0(m, dataInfo._weights, dataInfo._offset).doAll(fr2)

    if (!dataInfo._weights && !dataInfo._offset) Assert.assertTrue(m.testJavaScoring(fr, preds, 1e-15))
    Scope.exit()
  }

  class TestScore0(val model: GLMModel, val weights: Boolean, val offset: Boolean) extends MRTask {

    def veryClose(x: Double, y: Double) = Math.abs(x - y) < 1e-10

    private def checkScore(rowIndex: Long, predictions: Array[Double], expected: Array[Double]) {
      def maxOf(src: Seq[Double], default: Double) = if (src.isEmpty) default else src.max

      def maximumsAreClose: Boolean = {
        val max1: Double = maxOf(predictions, Double.MinValue)
        val max0: Double = maxOf(predictions.filter(_ < max1), max1)
        veryClose(max0, max1)
      }

      val start =
        if (model._parms._family == Family.binomial && veryClose(predictions(2), model.defaultThreshold)) 1
        else if (model._parms._family == Family.multinomial && maximumsAreClose) 1 else 0

      for {
        j <- start until predictions.length
      } {
        assertEquals(s"mismatch at row $rowIndex, p = $j:", expected(j), predictions(j), 1e-6)
      }
    }

    import java.util.Arrays._

    override def map(allChunks: Array[Chunk]) {
      val chunkSize: Int = allChunks(0)._len

      val nout: Int = if (model._parms._family eq Family.multinomial) model._output.nclasses + 1 else if (model._parms._family eq Family.binomial) 3 else 1

      val numInputChunks: Int = allChunks.length - nout
      val expectedOutputChunks: Array[Chunk] = copyOfRange(allChunks, numInputChunks, allChunks.length)
      val inputChunks = copyOf(allChunks, numInputChunks)
      val (off: Chunk, w: Chunk) = if (offset || weights) {
        (copyOf(inputChunks, numInputChunks - 1), inputChunks(numInputChunks - 1))
      } else {
        (new C0DChunk(0, chunkSize), new C0DChunk(1, chunkSize))
      }

      for {
        i <- 0 until chunkSize
      } {
        val tmp = new Array[Double](model._output._dinfo._cats + model._output._dinfo._nums)
        val predictions = new Array[Double](nout)

        if (weights || offset) model.score0(inputChunks, w.atd(i), off.atd(i), i, tmp, predictions)
        else model.score0(inputChunks, i, tmp, predictions)

        val expectedValues: Array[Double] = expectedOutputChunks map (_.atd(i))

        checkScore(i + inputChunks(0).start, predictions, expectedValues)
      }
    }
  }

  test("Abalone") {
    val foldername = "smalldata/glm_test"
    val folders = foldername :: s"../$foldername" :: Nil
    val folder = folders map (new File(_)) find (_.isDirectory) getOrElse {
      throw new IllegalArgumentException("Could not find smalldata folder")
    }

    val fr = new H2OFrame(new File(folder, "Abalone.gz"))
    val params: GLMModel.GLMParameters = new GLMModel.GLMParameters(Family.gaussian)
    params._train = fr._key
    params._response_column = fr._names(8)
    params._alpha = Array[Double](1.0)
    params._lambda_search = true
    val glm: GLM = new GLM(params)
    var model: GLMModel = glm.trainModel.get

    try {
      testScoring(model, fr)
    } finally {
      model.delete()
    }
  }
}
