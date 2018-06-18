package hex.deeplearning;

import hex.ScoringInfo;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.Activation;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.InitialWeightDistribution;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.Loss;
import hex.genmodel.GenModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;
import water.util.RandomUtils;

import java.util.Random;

public class DeepLearningIrisTest extends TestUtil {
  static final String PATH = "smalldata/iris/iris.csv";
  Frame _train, _test;

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  // Default run is the short run
  @Test public void run() throws Exception { runFraction(0.05f); }

  private void compareVal(double a, double b, double abseps, double releps) {
    if( !MathUtils.compare(a,b,abseps,releps) ) // Complex test does not fit JUnit Assert very well
      Assert.assertEquals("Not equal: ", a, b, 0.0); // always fails if we get here, and prints nice msg
  }

  void runFraction(float fraction) {
    long seed0 = 0xDECAF;
    int num_runs = 0;

    Frame frame = null;
    try {
      frame = parse_test_file(Key.make("iris.hex"),PATH);

      for (int repeat = 0; repeat < 5; ++repeat) {
        // Testing different things
        // Note: Microsoft reference implementation is only for Tanh + MSE.
        // Note: Rectifier and MCE are implemented by H2O.ai (trivial).
        // Note: Initial weight distributions are copied, but what is tested is the stability behavior.

        Activation[] activations = {Activation.Tanh, Activation.Rectifier};
        Loss[] losses = {Loss.Quadratic, Loss.CrossEntropy};
        InitialWeightDistribution[] dists = {
                InitialWeightDistribution.Normal,
                InitialWeightDistribution.Uniform,
                InitialWeightDistribution.UniformAdaptive
        };
        final long seed = seed0 + repeat;
        Random rng = new Random(seed);

        double[] initial_weight_scales = {1e-4 + rng.nextDouble()};
        double[] holdout_ratios = {0.1 + rng.nextDouble() * 0.8};
        double[] momenta = {rng.nextDouble() * 0.99};
        int[] hiddens = {1, 2 + rng.nextInt(50)};
        int[] epochs = {1, 2 + rng.nextInt(50)};
        double[] rates = {0.01, 1e-5 + rng.nextDouble() * .1};

        for (Activation activation : activations) {
          for (Loss loss : losses) {
            for (InitialWeightDistribution dist : dists) {
              for (double scale : initial_weight_scales) {
                for (double holdout_ratio : holdout_ratios) {
                  for (double momentum : momenta) {
                    for (int hidden : hiddens) {
                      for (int epoch : epochs) {
                        for (double rate : rates) {
                          DeepLearningModel mymodel = null;
                          Frame trainPredict = null;
                          Frame testPredict = null;
                          try {
                            num_runs++;
                            if (fraction < rng.nextFloat()) continue;
                            Log.info("");
                            Log.info("STARTING.");
                            Log.info("Running with " + activation.name() + " activation function and " + loss.name() + " loss function.");
                            Log.info("Initialization with " + dist.name() + " distribution and " + scale + " scale, holdout ratio " + holdout_ratio);
                            Log.info("Using " + hidden + " hidden layer neurons and momentum: " + momentum);
                            Log.info("Using seed " + seed);

                            Random rand;

                            int trial = 0;
                            do {
                              Log.info("Trial #" + ++trial);
                              if (_train != null) _train.delete();
                              if (_test  != null) _test .delete();

                              rand = RandomUtils.getRNG(seed);

                              double[][] rows = new double[(int) frame.numRows()][frame.numCols()];
                              String[] names = new String[frame.numCols()];
                              for (int c = 0; c < frame.numCols(); c++) {
                                names[c] = "ColumnName" + c;
                                for (int r = 0; r < frame.numRows(); r++)
                                  rows[r][c] = frame.vecs()[c].at(r);
                              }

                              for (int i = rows.length - 1; i >= 0; i--) {
                                int shuffle = rand.nextInt(i + 1);
                                double[] row = rows[shuffle];
                                rows[shuffle] = rows[i];
                                rows[i] = row;
                              }

                              int limit = (int) (frame.numRows() * holdout_ratio);
                              _train = ArrayUtils.frame(names, water.util.ArrayUtils.subarray(rows, 0, limit));
                              _test  = ArrayUtils.frame(names, water.util.ArrayUtils.subarray(rows, limit, (int) frame.numRows() - limit));

                              // Must have all output classes in training
                              // data (since that's what the reference
                              // implementation has hardcoded).  But count
                              // of classes is not known unless we visit
                              // all the response data - force that now.
                              String respname = _train.lastVecName();
                              Vec resp = _train.lastVec().toCategoricalVec();
                              _train.remove(respname).remove();
                              _train.add(respname, resp);
                              DKV.put(_train);

                              Vec vresp = _test.lastVec().toCategoricalVec();
                              _test.remove(respname).remove();
                              _test.add(respname, vresp);
                              DKV.put(_test);
                            }
                            while( _train.lastVec().cardinality() < 3);

                            // use the same seed for the reference implementation
                            DeepLearningMLPReference ref = new DeepLearningMLPReference();
                            ref.init(activation, RandomUtils.getRNG(seed), holdout_ratio, hidden);

                            DeepLearningParameters p = new DeepLearningParameters();
                            p._train = _train._key;
                            p._response_column = _train.lastVecName();
                            assert _train.lastVec().isCategorical();
                            p._ignored_columns = null;

                            p._seed = seed;
                            p._hidden = new int[]{hidden};
                            p._adaptive_rate = false;
                            p._rho = 0;
                            p._epsilon = 0;
                            p._rate = rate / (1 - momentum); //adapt to (1-m) correction that's done inside (only for constant momentum!)
                            p._activation = activation;
                            p._max_w2 = Float.POSITIVE_INFINITY;
                            p._input_dropout_ratio = 0;
                            p._rate_annealing = 0; //do not change - not implemented in reference
                            p._l1 = 0;
                            p._loss = loss;
                            p._l2 = 0;
                            p._momentum_stable = momentum; //reference only supports constant momentum
                            p._momentum_start = p._momentum_stable; //do not change - not implemented in reference
                            p._momentum_ramp = 0; //do not change - not implemented in reference
                            p._initial_weight_distribution = dist;
                            p._initial_weight_scale = scale;
                            p._valid = null;
                            p._quiet_mode = true;
                            p._fast_mode = false; //to be the same as reference
//                            p._fast_mode = true; //to be the same as old NeuralNet code
                            p._nesterov_accelerated_gradient = false; //to be the same as reference
//                            p._nesterov_accelerated_gradient = true; //to be the same as old NeuralNet code
                            p._train_samples_per_iteration = 0; //sync once per period
                            p._ignore_const_cols = false;
                            p._shuffle_training_data = false;
                            p._classification_stop = -1; //don't stop early -> need to compare against reference, which doesn't stop either
                            p._force_load_balance = false; //keep just 1 chunk for reproducibility
                            p._overwrite_with_best_model = false;
                            p._replicate_training_data = false;
                            p._mini_batch_size = 1;
                            p._single_node_mode = true;
                            p._epochs = 0;
                            p._elastic_averaging = false;
                            mymodel = new DeepLearning(p).trainModel().get();
                            p._epochs = epoch;

                            Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(mymodel.model_info());

                            // use the same random weights for the reference implementation
                            Neurons l = neurons[1];
                            for (int o = 0; o < l._a[0].size(); o++) {
                              for (int i = 0; i < l._previous._a[0].size(); i++) {
//                                System.out.println("initial weight[" + o + "]=" + l._w[o * l._previous._a.length + i]);
                                ref._nn.ihWeights[i][o] = l._w.get(o, i);
                              }
                              ref._nn.hBiases[o] = l._b.get(o);
//                              System.out.println("initial bias[" + o + "]=" + l._b[o]);
                            }
                            l = neurons[2];
                            for (int o = 0; o < l._a[0].size(); o++) {
                              for (int i = 0; i < l._previous._a[0].size(); i++) {
//                                System.out.println("initial weight[" + o + "]=" + l._w[o * l._previous._a.length + i]);
                                ref._nn.hoWeights[i][o] = l._w.get(o, i);
                              }
                              ref._nn.oBiases[o] = l._b.get(o);
//                              System.out.println("initial bias[" + o + "]=" + l._b[o]);
                            }

                            // Train the Reference
                            ref.train((int) p._epochs, rate, p._momentum_stable, loss, seed);

                            // Train H2O
                            mymodel.delete();
                            DeepLearning dl = new DeepLearning(p);
                            mymodel = dl.trainModel().get();
                            Assert.assertTrue(mymodel.model_info().get_processed_total() == epoch * dl.train().numRows());

                            /**
                             * Tolerances (should ideally be super tight -> expect the same double/float precision math inside both algos)
                             */
                            final double abseps = 1e-6;
                            final double releps = 1e-6;

                            /**
                             * Compare weights and biases in hidden layer
                             */
                            neurons = DeepLearningTask.makeNeuronsForTesting(mymodel.model_info()); //link the weights to the neurons, for easy access
                            l = neurons[1];
                            for (int o = 0; o < l._a[0].size(); o++) {
                              for (int i = 0; i < l._previous._a[0].size(); i++) {
                                double a = ref._nn.ihWeights[i][o];
                                double b = l._w.get(o, i);
                                compareVal(a, b, abseps, releps);
//                                System.out.println("weight[" + o + "]=" + b);
                              }
                              double ba = ref._nn.hBiases[o];
                              double bb = l._b.get(o);
                              compareVal(ba, bb, abseps, releps);
                            }
                            Log.info("Weights and biases for hidden layer: PASS");

                            /**
                             * Compare weights and biases for output layer
                             */
                            l = neurons[2];
                            for (int o = 0; o < l._a[0].size(); o++) {
                              for (int i = 0; i < l._previous._a[0].size(); i++) {
                                double a = ref._nn.hoWeights[i][o];
                                double b = l._w.get(o, i);
                                compareVal(a, b, abseps, releps);
                              }
                              double ba = ref._nn.oBiases[o];
                              double bb = l._b.get(o);
                              compareVal(ba, bb, abseps, releps);
                            }
                            Log.info("Weights and biases for output layer: PASS");

                            /**
                             * Compare predictions
                             * Note: Reference and H2O each do their internal data normalization,
                             * so we must use their "own" test data, which is assumed to be created correctly.
                             */
                            // H2O predictions
                            Frame fpreds = mymodel.score(_test); //[0] is label, [1]...[4] are the probabilities

                            try {
                              for (int i = 0; i < _test.numRows(); ++i) {
                                // Reference predictions
                                double[] xValues = new double[neurons[0]._a[0].size()];
                                System.arraycopy(ref._testData[i], 0, xValues, 0, xValues.length);
                                double[] ref_preds = ref._nn.ComputeOutputs(xValues);

                                // find the label
                                // do the same as H2O here (compare float values and break ties based on row number)
                                double[] preds = new double[ref_preds.length + 1];
                                for (int j = 0; j < ref_preds.length; ++j) preds[j + 1] = ref_preds[j];
                                preds[0] = GenModel.getPrediction(preds, null, xValues, 0.5);

                                // compare predicted label
                                Assert.assertTrue(preds[0] == (int) fpreds.vecs()[0].at(i));
//                                // compare predicted probabilities
//                                for (int j=0; j<ref_preds.length; ++j) {
//                                  compareVal((float)(ref_preds[j]), fpreds.vecs()[1+j].at(i), abseps, releps);
//                                }
                              }
                            } finally {
                              if (fpreds != null) fpreds.delete();
                            }
                            Log.info("Predicted values: PASS");

                            /**
                             * Compare (self-reported) scoring
                             */
                            final double trainErr = ref._nn.Accuracy(ref._trainData);
                            final double  testErr = ref._nn.Accuracy(ref. _testData);
                            trainPredict = mymodel.score(_train);
                            testPredict  = mymodel.score(_test );
                            hex.ModelMetrics mmtrain = hex.ModelMetrics.getFromDKV(mymodel,_train);
                            hex.ModelMetrics mmtest  = hex.ModelMetrics.getFromDKV(mymodel,_test );
                            final double myTrainErr = mmtrain.cm().err();
                            final double  myTestErr = mmtest .cm().err();
                            Log.info("H2O  training error : " + myTrainErr * 100 + "%, test error: " + myTestErr * 100 + "%");
                            Log.info("REF  training error : " +   trainErr * 100 + "%, test error: " +   testErr * 100 + "%");
                            compareVal(trainErr, myTrainErr, abseps, releps);
                            compareVal( testErr,  myTestErr, abseps, releps);
                            Log.info("Scoring: PASS");

                            // get the actual best error on training data
                            float best_err = Float.MAX_VALUE;
                            for (ScoringInfo e : mymodel.scoring_history()) {
                              DeepLearningScoringInfo err = (DeepLearningScoringInfo) e;
                              best_err = Math.min(best_err, (float) (Double.isNaN(err.scored_train._classError) ? best_err : err.scored_train._classError)); //multi-class classification
                            }
                            Log.info("Actual best error : " + best_err * 100 + "%.");

                            // this is enabled by default
                            if (p._overwrite_with_best_model) {
                              Frame bestPredict = null;
                              try {
                                bestPredict = mymodel.score(_train);
                                hex.ModelMetrics mmbest = hex.ModelMetrics.getFromDKV(mymodel,_train);
                                final double bestErr = mmbest.cm().err();
                                Log.info("Best_model's error : " + bestErr * 100 + "%.");
                                compareVal(bestErr, best_err, abseps, releps);
                              } finally {
                                if (bestPredict != null) bestPredict.delete();
                              }
                            }
                            Log.info("Parameters combination " + num_runs + ": PASS");

                          } finally {
                            // cleanup
                            if (mymodel != null) {
                              mymodel.delete();
                            }
                            if (_train != null) _train.delete();
                            if (_test != null) _test.delete();
                            if (trainPredict != null) trainPredict.delete();
                            if (testPredict != null) testPredict.delete();
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } catch(Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (frame != null) frame.delete();
    }
  }

  public static class Long extends DeepLearningIrisTest {
    @Test
    @Ignore
    public void run() throws Exception { runFraction(0.1f); }
  }

  public static class Short extends DeepLearningIrisTest {
    @Test @Ignore public void run() throws Exception { runFraction(0.05f); }
  }
    @Test
  public void test_so_dl_params() {
      try {
        Scope.enter();
        Frame fr = parse_test_file("smalldata/iris/iris_wheader.csv");
        Scope.track(fr);

        //Use DeepLearningParameters to invoke to the deep learning parameters (don't use the interim parameter DeepLearningParametersV3, because it can change under the hood)
        DeepLearningParameters p = new DeepLearningParameters();

        //Please use the following to set your deeplearning parameter arguments
        p._train = fr._key;
        p._response_column = fr.lastVecName();

        p._hidden = new int[]{200, 200};

        DeepLearningModel dlmodel = new DeepLearning(p).trainModel().get();

        Frame predict = dlmodel.score(fr);
        Scope.track(predict);
        Scope.track_generic(dlmodel);
        System.out.println("predictions" + predict);
      } finally {
        Scope.exit();
      }
    }
}

