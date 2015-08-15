package hex.deeplearning;


import hex.DataInfo;
import hex.Distribution;
import hex.ModelMetricsRegression;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.Log;
import water.util.PrettyPrint;

import java.util.Random;

public class DeepLearningGradientCheck extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test
  @Ignore
  public void cancar() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/glm_test/cancar_logIn.csv");
      for (String s : new String[]{
              "Merit", "Class"
      }) {
        Scope.track(tfr.replace(tfr.find(s), tfr.vec(s).toEnum())._key);
      }
      DKV.put(tfr);

      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 100; //converge to a reasonable model to avoid too large gradients
//      parms._l1 = 1e-2;
//      parms._l2 = 1e-2;
      parms._reproducible = true;
      parms._hidden = new int[]{20,20,20};
      parms._response_column = "Cost"; //regression
//      parms._distribution = Distribution.Family.huber;
      parms._seed = 0xdecaf+1;
      parms._activation = DeepLearningParameters.Activation.Tanh;

      // Build a first model; all remaining models should be equal
      DeepLearning job = new DeepLearning(parms);
      dl = job.trainModel().get();

      // set parameters for gradient checking
//      long seed = new Random(0xdecaf).nextLong();
      long seed = new Random(parms._seed).nextLong();
      final Random rng = new Random(seed);

      dl.score(tfr);
      hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(dl, tfr);
      double resdev = ((ModelMetricsRegression)mm)._mean_residual_deviance;
      Log.info("Mean residual deviance: " + resdev);

      DeepLearningModelInfo modelInfo = dl.model_info().deep_clone(); //golden version
      long before = dl.model_info().checksum_impl();

      float maxRelErr = 0;
      float meanLoss = 0;
      for (int loop=0; loop<tfr.numRows(); ++loop) {
        dl.set_model_info(modelInfo.deep_clone());

        // pick one random row from the dataset
        final DataInfo.Row myRow = dl.model_info().data_info().newDenseRow();
        final DataInfo di = dl.model_info().data_info();
        final int rId = loop; //rng.nextInt((int)tfr.numRows());
        new MRTask(){
          @Override
          public void map(Chunk[] cs) {
            di.extractDenseRow(cs,rId,myRow);
          }
        }.doAll(di._adaptedFrame);

        // do one forward propagation pass
        Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(dl.model_info());
        ((Neurons.Input)neurons[0]).setInput(-1, myRow.numVals, myRow.nBins, myRow.binIds);
        DeepLearningTask.step(-1 /*seed doesn't matter*/, neurons, dl.model_info(), null, true /*training*/, new double[]{myRow.response[0]}, myRow.offset);
        long after = dl.model_info().checksum_impl();
        assert(after==before);

        // pick a random weight to compute the gradient for
        int layer = 1 + rng.nextInt(parms._hidden.length);
        int row = rng.nextInt(dl.model_info().get_weights(layer).rows());
        int col = rng.nextInt(dl.model_info().get_weights(layer).cols());

        // record the gradient since gradientChecking is enabled
        DeepLearningModelInfo.gradientCheck = new DeepLearningModelInfo.GradientCheck(layer, row, col); //tell it what gradient to collect
        DeepLearningTask.applyModelUpdates(neurons); //actually record the gradient - THIS CHANGES THE MODEL
        assert(before != dl.model_info().checksum_impl());

        // reset the model back to the trained model
        dl.set_model_info(modelInfo.deep_clone());
        assert(before == dl.model_info().checksum_impl());

        float bpropGradient = DeepLearningModelInfo.gradientCheck.gradient;

        // FIXME: re-enable this once the loss is computed from the de-standardized prediction/response
//        double actualResponse=myRow.response[0];
//        double predResponseLinkSpace = neurons[neurons.length-1]._a.get(0);
//        if (di._normRespMul != null) {
//          bpropGradient /= di._normRespMul[0]; //no shift for gradient
//          actualResponse = (actualResponse / di._normRespMul[0] + di._normRespSub[0]);
//          predResponseLinkSpace = (predResponseLinkSpace / di._normRespMul[0] + di._normRespSub[0]);
//        }
//        bpropGradient *= new Distribution(parms._distribution).gradient(actualResponse, predResponseLinkSpace);

        // Now change some weights
        final float weight = dl.model_info().get_weights(layer).get(row, col);

        float eps = 1e-2f*weight;

        double loss = dl.loss(myRow);
        meanLoss += loss;


        long cs = dl.model_info().checksum_impl();
        dl.model_info().get_weights(layer).set(row, col, weight + eps);
        assert(dl.model_info().get_weights(layer).get(row,col) != weight);

        assert(cs != dl.model_info().checksum_impl());
        double up = dl.loss(myRow);

        dl.model_info().get_weights(layer).set(row, col, weight - eps);
        assert(dl.model_info().get_weights(layer).get(row,col) != weight);
        assert(cs != dl.model_info().checksum_impl());
        double down = dl.loss(myRow);
        float gradient = (float) ((up - down) / (2. * (double)eps));

        gradient /=2; //HACK - since deviance and gradient are off by 2x
        if (gradient < 1e-5) continue;

        float relError = (Math.abs(bpropGradient - gradient) / Math.abs(gradient));

        if (false) {
          Log.info("\nRow: " + (loop + 1));
          Log.info("loss: " + loss);
          Log.info("weight (layer " + layer + ", row " + row + ", col " + col + "): " + weight + " +/- " + eps);
          Log.info("losses up/down: " + up + " / " + down);
          Log.info("=> Finite differences gradient: " + gradient);
          Log.info("=> Back-propagation gradient  : " + bpropGradient);
          Log.info("=> Relative error             : " + PrettyPrint.formatPct(relError));
        }

        maxRelErr = Math.max(maxRelErr, relError);
      }
      meanLoss /= tfr.numRows();
      Log.info("Mean loss: " + meanLoss);

      // FIXME: re-enable this once the loss is computed from the de-standardized prediction/response
//      if (parms._l1 == 0 && parms._l2 == 0) {
//        assert(Math.abs(meanLoss-resdev)/Math.abs(resdev) < 1e-5);
//      }

      Log.info("Max. relative error: " + PrettyPrint.formatPct(maxRelErr));
      float tol = 1e-2f;
      Assert.assertTrue("Error too large: " + maxRelErr + " >= " + tol, maxRelErr < tol);

      job.remove();
    } finally {
      if (tfr != null) tfr.remove();
      if (dl != null) dl.delete();
      Scope.exit();
    }
  }

}
