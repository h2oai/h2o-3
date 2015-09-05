package hex.deeplearning;


import hex.DataInfo;
import hex.Distribution;
import hex.FrameTask;
import hex.ModelMetricsRegression;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.PrettyPrint;

import java.util.Random;

public class DeepLearningGradientCheck extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  static final float MAX_TOLERANCE = 1e-2f;
  static final float SAMPLE_RATE = 0.1f;

  @Test
  public void cancar() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parse_test_file("smalldata/glm_test/cancar_logIn.csv");
      for (String s : new String[]{
              "Merit", "Class"
      }) {
        Vec f = tfr.vec(s).toEnum();
        tfr.remove(s).remove();
        tfr.add(s, f);
      }
      DKV.put(tfr);

      Random rng = new Random(0xDECAF);
      int count=0;
      int failedcount=0;
      double maxRelErr = 0;
      double meanRelErr = 0;
      for (Distribution.Family dist : new Distribution.Family[]{
              Distribution.Family.gaussian,
              Distribution.Family.laplace,
              Distribution.Family.huber,
              Distribution.Family.gamma,
              Distribution.Family.poisson,
              Distribution.Family.tweedie,
              Distribution.Family.multinomial,
      }) {
        for (DeepLearningParameters.Activation act : new DeepLearningParameters.Activation[]{
                DeepLearningParameters.Activation.Tanh,
                DeepLearningParameters.Activation.Rectifier,
//                DeepLearningParameters.Activation.Maxout,
        }) {
          for (String response : new String[]{
                  "Class", //classification
                  "Cost", //regression
          }) {
            for (boolean adaptive : new boolean[]{
                    true,
                    false
            }) {
              for (int miniBatchSize : new int[]{
                      1,
              }) {
                boolean classification = response.equals("Class");
                if (classification && dist != Distribution.Family.multinomial) continue;
                if (!classification && dist == Distribution.Family.multinomial) continue;

                DeepLearningParameters parms = new DeepLearningParameters();
                parms._train = tfr._key;
                parms._epochs = 100; //converge to a reasonable model to avoid too large gradients
//            parms._l1 = 1e-3; //FIXME
//            parms._l2 = 1e-3; //FIXME
                parms._reproducible = true;
                parms._hidden = new int[]{10, 10, 10};
                parms._fast_mode = false; //otherwise we introduce small bprop errors
                parms._response_column = response;
                parms._distribution = dist;
                parms._max_w2 = 10;
                parms._seed = 0xaaabbb;
                parms._activation = act;
                parms._adaptive_rate = adaptive;
                parms._rate = 1e-4;
                parms._momentum_start = 0.9;
                parms._momentum_stable = 0.99;
                parms._model_id = Key.make();
                DeepLearningModelInfo.gradientCheck = null;

                // Build a first model; all remaining models should be equal
                DeepLearning job = new DeepLearning(parms);
                try {
                  dl = job.trainModel().get();

                  if (!classification) {
                    Frame p = dl.score(tfr);
                    hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(dl, tfr);
                    double resdev = ((ModelMetricsRegression) mm)._mean_residual_deviance;
                    Log.info("Mean residual deviance: " + resdev);
                    p.delete();
                  }

                  DeepLearningModelInfo modelInfo = dl.model_info().deep_clone(); //golden version
//                Log.info(modelInfo.toStringAll());
                  long before = dl.model_info().checksum_impl();

                  float meanLoss = 0;

                  // loop over every row in the dataset and check that the predictions
                  for (int rId = 0; rId < tfr.numRows(); rId+=1 /*miniBatchSize*/) {
                    // start from scratch - with a clean model
                    dl.set_model_info(modelInfo.deep_clone());

                    final DataInfo di = dl.model_info().data_info();

                    // populate miniBatch (consecutive rows)
                    final DataInfo.Row[] miniBatch = new DataInfo.Row[miniBatchSize];
                    for (int i=0; i<miniBatch.length; ++i) {
                      if (0 <= rId+i && rId+i < tfr.numRows()) {
                        miniBatch[i] = new FrameTask.ExtractDenseRow(di, rId+i).doAll(di._adaptedFrame)._row;
                      }
                    }

                    // loss at weight
                    long cs = dl.model_info().checksum_impl();
                    double loss = dl.loss(miniBatch);
                    assert(cs == before);
                    assert(before == dl.model_info().checksum_impl());
                    meanLoss += loss;

                    for (int layer = 0; layer <= parms._hidden.length; ++layer) {
                      int rows = dl.model_info().get_weights(layer).rows();
                      for (int row = 0; row < rows; ++row) {
                        int cols = dl.model_info().get_weights(layer).cols();
                        for (int col = 0; col < cols; ++col) {
                          if (rng.nextFloat() >= SAMPLE_RATE) continue;

                          // start from scratch - with a clean model
                          dl.set_model_info(modelInfo.deep_clone());

                          // do one forward propagation pass (and fill the mini-batch gradients -> set training=true)
                          Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(dl.model_info());
                          for (DataInfo.Row myRow : miniBatch) {
                            if (myRow == null) continue;
                            ((Neurons.Input) neurons[0]).setInput(-1, myRow.numVals, myRow.nBins, myRow.binIds);
                            DeepLearningTask.step(-1 /*seed doesn't matter*/, neurons, dl.model_info(), null, true /*training*/, new double[]{myRow.response[0]}, myRow.offset);
                          }

                          // check that we didn't change the model's weights/biases
                          long after = dl.model_info().checksum_impl();
                          assert (after == before);

                          // record the gradient since gradientChecking is enabled
                          DeepLearningModelInfo.gradientCheck = new DeepLearningModelInfo.GradientCheck(layer, row, col); //tell it what gradient to collect
                          DeepLearningTask.applyModelUpdates(neurons); //update the weights
                          assert (before != dl.model_info().checksum_impl());

                          // reset the model back to the trained model
                          dl.set_model_info(modelInfo.deep_clone());
                          assert (before == dl.model_info().checksum_impl());

                          double bpropGradient = DeepLearningModelInfo.gradientCheck.gradient;
                          DeepLearningModelInfo.gradientCheck = null;

                          // FIXME: re-enable this once the loss is computed from the de-standardized prediction/response
//                    double actualResponse=myRow.response[0];
//                    double predResponseLinkSpace = neurons[neurons.length-1]._a.get(0);
//                    if (di._normRespMul != null) {
//                      bpropGradient /= di._normRespMul[0]; //no shift for gradient
//                      actualResponse = (actualResponse / di._normRespMul[0] + di._normRespSub[0]);
//                      predResponseLinkSpace = (predResponseLinkSpace / di._normRespMul[0] + di._normRespSub[0]);
//                    }
//                    bpropGradient *= new Distribution(parms._distribution).gradient(actualResponse, predResponseLinkSpace);

                          final float weight = dl.model_info().get_weights(layer).get(row, col);

                          double eps = 1e-4 * Math.abs(weight); //don't make the weight deltas too small, or the float weights "won't notice"
                          if (eps == 0)
                            eps = 1e-6;

                          // loss at weight + eps
                          dl.model_info().get_weights(layer).set(row, col, (float)(weight + eps));
                          double up = dl.loss(miniBatch);

                          // loss at weight - eps
                          dl.model_info().get_weights(layer).set(row, col, (float)(weight - eps));
                          double down = dl.loss(miniBatch);

                          if (Math.abs(up-down)/Math.abs(up+down) < 1e-8) {
                            continue; //relative change in loss function is too small -> skip
                          }

                          double gradient = ((up - down) / (2. * eps));

                          double relError = 2 * Math.abs(bpropGradient - gradient) / (Math.abs(gradient) + Math.abs(bpropGradient));

                          count++;

                          // if either gradient is tiny, check if both are tiny
                          if (Math.abs(gradient) < 1e-8 || Math.abs(bpropGradient) < 1e-8) {
                            if (Math.abs(bpropGradient-gradient) < 1e-8) continue; //all good
                          }

                          meanRelErr += relError;

                          // if both gradients are tiny - numerically unstable relative error computation is not needed, since absolute error is small

                          if (relError > MAX_TOLERANCE) {
                            Log.info("\nRow: " + rId);
                            Log.info("weight (layer " + layer + ", row " + row + ", col " + col + "): " + weight + " +/- " + eps);
                            Log.info("loss: " + loss);
                            Log.info("losses up/down: " + up + " / " + down);
                            Log.info("=> Finite differences gradient: " + gradient);
                            Log.info("=> Back-propagation gradient  : " + bpropGradient);
                            Log.info("=> Relative error             : " + PrettyPrint.formatPct(relError));
                            failedcount++;
                          }

                          maxRelErr = Math.max(maxRelErr, relError);
                          assert(!Double.isNaN(maxRelErr));
                        }
                      }
                    }
                  }
                  meanLoss /= tfr.numRows();
                  Log.info("Mean loss: " + meanLoss);

//                  // FIXME: re-enable this
//                  if (parms._l1 == 0 && parms._l2 == 0) {
//                    assert(Math.abs(meanLoss-resdev)/Math.abs(resdev) < 1e-5);
//                  }
                } catch(RuntimeException ex) {
                  dl = DKV.getGet(parms._model_id);
                  if (dl != null)
                    Assert.assertTrue(dl.model_info().unstable());
                  else
                    Assert.assertTrue(job.isCancelledOrCrashed());
                } finally {
                  job.remove();
                  if (dl != null) dl.delete();
                }
              }
            }
          }
        }
      }
      Log.info("Number of tests: " + count);
      Log.info("Number of failed tests: " + failedcount);
      Log.info("Mean. relative error: " + meanRelErr/count);
      Log.info("Max. relative error: " + PrettyPrint.formatPct(maxRelErr));
      Assert.assertTrue("Error too large: " + maxRelErr + " >= " + MAX_TOLERANCE, maxRelErr < MAX_TOLERANCE);

    } finally {
      if (tfr != null) tfr.remove();
    }
  }

}
