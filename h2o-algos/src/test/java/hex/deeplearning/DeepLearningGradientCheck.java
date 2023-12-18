package hex.deeplearning;


import hex.*;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.genmodel.utils.DistributionFamily;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.PrettyPrint;

import java.util.Random;

public class DeepLearningGradientCheck extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  static final float MAX_TOLERANCE = 2e-2f;
  static final float MAX_FAILED_COUNT = 30;
  static final float SAMPLE_RATE = 0.01f;

  @Test
  public void gradientCheck() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("smalldata/glm_test/cancar_logIn.csv");
      for (String s : new String[]{
              "Merit", "Class"
      }) {
        Vec f = tfr.vec(s).toCategoricalVec();
        tfr.remove(s).remove();
        tfr.add(s, f);
      }
      DKV.put(tfr);
      tfr.add("Binary", tfr.anyVec().makeZero());
      new MRTask() {
        public void map(Chunk[] c) {
          for (int i=0;i<c[0]._len;++i)
            if (c[0].at8(i)==1) c[1].set(i,1);
        }
      }.doAll(tfr.vecs(new String[]{"Class","Binary"}));
      Vec cv = tfr.vec("Binary").toCategoricalVec();
      tfr.remove("Binary").remove();
      tfr.add("Binary", cv);
      DKV.put(tfr);

      Random rng = new Random(0xDECAF);
      int count=0;
      int failedcount=0;
      double maxRelErr = 0;
      double meanRelErr = 0;
      for (DistributionFamily dist : new DistributionFamily[]{
              DistributionFamily.gaussian,
              DistributionFamily.laplace,
              DistributionFamily.quantile,
              DistributionFamily.huber,
              // DistributionFamily.modified_huber,
              DistributionFamily.gamma,
              DistributionFamily.poisson,
              DistributionFamily.AUTO,
              DistributionFamily.tweedie,
              DistributionFamily.multinomial,
              DistributionFamily.bernoulli,
      }) {
        for (DeepLearningParameters.Activation act : new DeepLearningParameters.Activation[]{
//            DeepLearningParameters.Activation.ExpRectifier,
                DeepLearningParameters.Activation.Tanh,
                DeepLearningParameters.Activation.Rectifier,
//                DeepLearningParameters.Activation.Maxout,
        }) {
          for (String response : new String[]{
                  "Binary", //binary classification
                  "Class", //multi-class
                  "Cost", //regression
          }) {
            for (boolean adaptive : new boolean[]{
                    true,
                    false
            }) {
              for (int miniBatchSize : new int[]{
                      1
              }) {
                if (response.equals("Class")) {
                  if (dist != DistributionFamily.multinomial && dist != DistributionFamily.AUTO)
                    continue;
                }
                else if (response.equals("Binary")) {
                  if (dist != DistributionFamily.modified_huber && dist != DistributionFamily.bernoulli && dist != DistributionFamily.AUTO)
                    continue;
                }
                else {
                  if (dist == DistributionFamily.multinomial || dist == DistributionFamily.modified_huber || dist == DistributionFamily.bernoulli) continue;
                }

                DeepLearningParameters parms = new DeepLearningParameters();
                parms._huber_alpha = rng.nextDouble()+0.1;
                parms._tweedie_power = 1.01 + rng.nextDouble()*0.9;
                parms._quantile_alpha = 0.05 + rng.nextDouble()*0.9;
                parms._train = tfr._key;
                parms._epochs = 100; //converge to a reasonable model to avoid too large gradients
                parms._l1 = 1e-3;
                parms._l2 = 1e-3;
                parms._force_load_balance = false;
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
                parms._mini_batch_size = miniBatchSize;
//                DeepLearningModelInfo.gradientCheck = null;
                DeepLearningModelInfo.gradientCheck = new DeepLearningModelInfo.GradientCheck(0, 0, 0); //tell it what gradient to collect

                // Build a first model; all remaining models should be equal
                DeepLearning job = new DeepLearning(parms);
                try {
                  dl = job.trainModel().get();

                  boolean classification = response.equals("Class") || response.equals("Binary");
                  if (!classification) {
                    Frame p = dl.score(tfr);
                    hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(dl, tfr);
                    double resdev = ((ModelMetricsRegression) mm)._mean_residual_deviance;
                    Log.info("Mean residual deviance: " + resdev);
                    p.delete();
                  }

                  DeepLearningModelInfo modelInfo = IcedUtils.deepCopy(dl.model_info()); //golden version
//                Log.info(modelInfo.toStringAll());
                  long before = dl.model_info().checksum_impl();

                  float meanLoss = 0;

                  // loop over every row in the dataset and check that the predictions
                  for (int rId = 0; rId < tfr.numRows(); rId+=1 /*miniBatchSize*/) {
                    // start from scratch - with a clean model
                    dl.set_model_info(IcedUtils.deepCopy(modelInfo));

                    final DataInfo di = dl.model_info().data_info();

                    // populate miniBatch (consecutive rows)
                    final DataInfo.Row[] rowsMiniBatch = new DataInfo.Row[miniBatchSize];
                    for (int i=0; i<rowsMiniBatch.length; ++i) {
                      if (0 <= rId+i && rId+i < tfr.numRows()) {
                        rowsMiniBatch[i] = new FrameTask.ExtractDenseRow(di, rId+i).doAll(di._adaptedFrame)._row;
                      }
                    }

                    // loss at weight
                    long cs = dl.model_info().checksum_impl();
                    double loss = dl.meanLoss(rowsMiniBatch);
                    assert(cs == before);
                    assert(before == dl.model_info().checksum_impl());
                    meanLoss += loss;

                    for (int layer = 0; layer <= parms._hidden.length; ++layer) {
                      int rows = dl.model_info().get_weights(layer).rows();
                      assert(dl.model_info().get_biases(layer).size()==rows);
                      for (int row = 0; row < rows; ++row) {

                        //check bias
                        if (true) {

                          // start from scratch - with a clean model
                          dl.set_model_info(IcedUtils.deepCopy(modelInfo));

                          // do one forward propagation pass (and fill the mini-batch gradients -> set training=true)
                          Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(dl.model_info());
                          double[] responses = new double[miniBatchSize];
                          double[] offsets = new double[miniBatchSize];
                          int n = 0;
                          for (DataInfo.Row myRow : rowsMiniBatch) {
                            if (myRow == null) continue;
                            ((Neurons.Input) neurons[0]).setInput(-1, myRow.numIds, myRow.numVals, myRow.nBins, myRow.binIds, n);
                            responses[n] = myRow.response(0);
                            offsets[n] = myRow.offset;
                            n++;
                          }
                          DeepLearningTask.fpropMiniBatch(-1 /*seed doesn't matter*/, neurons, dl.model_info(), null, true /*training*/, responses, offsets, n);

                          // check that we didn't change the model's weights/biases
                          long after = dl.model_info().checksum_impl();
                          assert (after == before);

                          // record the gradient since gradientChecking is enabled
                          DeepLearningModelInfo.gradientCheck = new DeepLearningModelInfo.GradientCheck(layer, row, -1); //tell it what gradient to collect
                          DeepLearningTask.bpropMiniBatch(neurons, n); //update the weights and biases
                          assert (before != dl.model_info().checksum_impl());

                          // reset the model back to the trained model
                          dl.set_model_info(IcedUtils.deepCopy(modelInfo));
                          assert (before == dl.model_info().checksum_impl());

                          double bpropGradient = DeepLearningModelInfo.gradientCheck.gradient;

                          // FIXME: re-enable this once the loss is computed from the de-standardized prediction/response
//                    double actualResponse=myRow.response[0];
//                    double predResponseLinkSpace = neurons[neurons.length-1]._a.get(0);
//                    if (di._normRespMul != null) {
//                      bpropGradient /= di._normRespMul[0]; //no shift for gradient
//                      actualResponse = (actualResponse / di._normRespMul[0] + di._normRespSub[0]);
//                      predResponseLinkSpace = (predResponseLinkSpace / di._normRespMul[0] + di._normRespSub[0]);
//                    }
//                    bpropGradient *= new Distribution(parms._distribution).gradient(actualResponse, predResponseLinkSpace);

                          final double bias = dl.model_info().get_biases(layer).get(row);

                          double eps = 1e-4 * Math.abs(bias); //don't make the weight deltas too small, or the float weights "won't notice"
                          if (eps == 0)
                            eps = 1e-6;

                          // loss at bias + eps
                          dl.model_info().get_biases(layer).set(row, bias + eps);
                          double up = dl.meanLoss(rowsMiniBatch);

                          // loss at bias - eps
                          dl.model_info().get_biases(layer).set(row, bias - eps);
                          double down = dl.meanLoss(rowsMiniBatch);

                          if (Math.abs(up - down) / Math.abs(up + down) < 1e-8) {
                            continue; //relative change in loss function is too small -> skip
                          }

                          double gradient = ((up - down) / (2. * eps));

                          double relError = 2 * Math.abs(bpropGradient - gradient) / (Math.abs(gradient) + Math.abs(bpropGradient));

                          count++;

                          // if either gradient is tiny, check if both are tiny
                          if (Math.abs(gradient) < 1e-7 || Math.abs(bpropGradient) < 1e-7) {
                            if (Math.abs(bpropGradient - gradient) < 1e-7) continue; //all good
                          }

                          meanRelErr += relError;

                          // if both gradients are tiny - numerically unstable relative error computation is not needed, since absolute error is small

                          if (relError > MAX_TOLERANCE) {
                            Log.info("\nDistribution: " + dl._parms._distribution);
                            Log.info("\nRow: " + rId);
                            Log.info("bias (layer " + layer + ", row " + row + "): " + bias + " +/- " + eps);
                            Log.info("loss: " + loss);
                            Log.info("losses up/down: " + up + " / " + down);
                            Log.info("=> Finite differences gradient: " + gradient);
                            Log.info("=> Back-propagation gradient  : " + bpropGradient);
                            Log.info("=> Relative error             : " + PrettyPrint.formatPct(relError));
                            failedcount++;
                          }
                        }

                        int cols = dl.model_info().get_weights(layer).cols();
                        for (int col = 0; col < cols; ++col) {
                          if (rng.nextFloat() >= SAMPLE_RATE) continue;

                          // start from scratch - with a clean model
                          dl.set_model_info(IcedUtils.deepCopy(modelInfo));

                          // do one forward propagation pass (and fill the mini-batch gradients -> set training=true)
                          Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(dl.model_info());
                          double [] responses = new double[miniBatchSize];
                          double [] offsets = new double[miniBatchSize];
                          int n=0;
                          for (DataInfo.Row myRow : rowsMiniBatch) {
                            if (myRow == null) continue;
                            ((Neurons.Input) neurons[0]).setInput(-1, myRow.numIds, myRow.numVals, myRow.nBins, myRow.binIds, n);
                            responses[n] = myRow.response(0);
                            offsets[n] = myRow.offset;
                            n++;
                          }
                          DeepLearningTask.fpropMiniBatch(-1 /*seed doesn't matter*/, neurons, dl.model_info(), null, true /*training*/, responses, offsets, n);

                          // check that we didn't change the model's weights/biases
                          long after = dl.model_info().checksum_impl();
                          assert (after == before);

                          // record the gradient since gradientChecking is enabled
                          DeepLearningModelInfo.gradientCheck = new DeepLearningModelInfo.GradientCheck(layer, row, col); //tell it what gradient to collect
                          DeepLearningTask.bpropMiniBatch(neurons, n); //update the weights
                          assert (before != dl.model_info().checksum_impl());

                          // reset the model back to the trained model
                          dl.set_model_info(IcedUtils.deepCopy(modelInfo));
                          assert (before == dl.model_info().checksum_impl());

                          double bpropGradient = DeepLearningModelInfo.gradientCheck.gradient;

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
                          double up = dl.meanLoss(rowsMiniBatch);

                          // loss at weight - eps
                          dl.model_info().get_weights(layer).set(row, col, (float)(weight - eps));
                          double down = dl.meanLoss(rowsMiniBatch);

                          if (Math.abs(up-down)/Math.abs(up+down) < 1e-8) {
                            continue; //relative change in loss function is too small -> skip
                          }

                          double gradient = ((up - down) / (2. * eps));

                          double relError = 2 * Math.abs(bpropGradient - gradient) / (Math.abs(gradient) + Math.abs(bpropGradient));

                          count++;

                          // if either gradient is tiny, check if both are tiny
                          if (Math.abs(gradient) < 1e-7 || Math.abs(bpropGradient) < 1e-7) {
                            if (Math.abs(bpropGradient-gradient) < 1e-7) continue; //all good
                          }

                          meanRelErr += relError;

                          // if both gradients are tiny - numerically unstable relative error computation is not needed, since absolute error is small

                          if (relError > MAX_TOLERANCE) {
                            Log.info("\nDistribution: " + dl._parms._distribution);
                            Log.info("\nRow: " + rId);
                            Log.info("weight (layer " + layer + ", row " + row + ", col " + col + "): " + weight + " +/- " + eps);
                            Log.info("loss: " + loss);
                            Log.info("losses up/down: " + up + " / " + down);
                            Log.info("=> Finite differences gradient: " + gradient);
                            Log.info("=> Back-propagation gradient  : " + bpropGradient);
                            Log.info("=> Relative error             : " + PrettyPrint.formatPct(relError));
                            failedcount++;
                          }
//                          Assert.assertTrue(failedcount==0);

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
                  dl = DKV.getGet(job.dest());
                  if (dl != null)
                    Assert.assertTrue(dl.model_info().isUnstable());
                  else
                    Assert.assertTrue(job.isStopped());
                } finally {
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
      Assert.assertTrue("Failed count too large: " + failedcount + " > " + MAX_FAILED_COUNT, failedcount <= MAX_FAILED_COUNT);

    } finally {
      if (tfr != null) tfr.remove();
    }
  }

  @Test public void checkDistributionGradients() {
    Random rng = new Random(0xDECAF);
    for (DistributionFamily dist : new DistributionFamily[]{
            DistributionFamily.AUTO,
            DistributionFamily.gaussian,
            DistributionFamily.laplace,
            DistributionFamily.quantile,
            DistributionFamily.huber,
            DistributionFamily.gamma,
            DistributionFamily.poisson,
            DistributionFamily.tweedie,
            DistributionFamily.bernoulli,
//            DistributionFamily.modified_huber,
//              DistributionFamily.multinomial, //no gradient/deviance implemented
    }) {
      DeepLearningParameters p = new DeepLearningParameters();
      p._distribution = dist;
      int N=1000;
      double eps=1./(10.*N);
      for (double y : new double[]{dist == DistributionFamily.gamma ? 0.5 : 0, 1}) { //actual - taylored for binomial, but should work for regression too
        // y has to be positive (>0) for gamma distribution
        // scan the range -2..2 in function approximation space (link space)
        for (int i=-5*N; i<5*N; ++i) {
          p._huber_alpha = rng.nextDouble()+0.1;
          p._tweedie_power = 1.01 + rng.nextDouble()*0.9;
          p._quantile_alpha = 0.05 + rng.nextDouble()*0.9;
          Distribution d = DistributionFactory.getDistribution(p);
          double f = (i+0.5)/N; // avoid issues at 0
          double grad = -2*d.negHalfGradient(y, f); //f in link space (model space)
          double w = rng.nextDouble()*10;
          double approxgrad = (d.deviance(w,y,d.linkInv(f+eps)) - d.deviance(w,y,d.linkInv(f-eps)))/(2*eps*w); //deviance in real space
          assert(Math.abs(grad - approxgrad) <= 1e-4);
        }
      }
    }
  }
}
