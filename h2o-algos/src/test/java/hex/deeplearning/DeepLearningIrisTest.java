package hex.deeplearning;

import hex.FrameTask;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.Activation;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.InitialWeightDistribution;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.Loss;
import org.junit.*;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Random;

import static hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import static hex.deeplearning.DeepLearningModel.Errors;
import static water.util.ModelUtils.getPrediction;

public class DeepLearningIrisTest extends TestUtil {
  static final String PATH = "smalldata/iris/iris.csv";
  Frame _train, _test;

  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  // Default run is the short run
  @Test public void run() throws Exception { runFraction(0.05f); }

  private void compareVal(double a, double b, double abseps, double releps) {
    if( !compare(a,b,abseps,releps) ) // Complex test does not fit JUnit Assert very well
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
        // Note: Microsoft reference implementation is only for Tanh + MSE, rectifier and MCE are implemented by 0xdata (trivial).
        // Note: Initial weight distributions are copied, but what is tested is the stability behavior.

        Activation[] activations = {Activation.Tanh, Activation.Rectifier};
        Loss[] losses = {Loss.MeanSquare, Loss.CrossEntropy};
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
                          for (boolean sparse : new boolean[]{true, false}) {
                            for (boolean col_major : new boolean[]{false}) {
                              Scope.enter();
                              DeepLearningModel mymodel = null;
                              Frame fr = null;
                              DeepLearningParameters p;
                              Frame trainPredict = null;
                              Frame testPredict = null;
                              try {
                                if (col_major && !sparse) continue;
                                num_runs++;
                                if (fraction < rng.nextFloat()) continue;
                                Log.info("");
                                Log.info("STARTING.");
                                Log.info("Running with " + activation.name() + " activation function and " + loss.name() + " loss function.");
                                Log.info("Initialization with " + dist.name() + " distribution and " + scale + " scale, holdout ratio " + holdout_ratio);
                                Log.info("Using " + hidden + " hidden layers and momentum: " + momentum);
                                Log.info("Using seed " + seed);

                                Random rand;

                                int trial = 0;
                                FrameTask.DataInfo dinfo;
                                do {
                                  Log.info("Trial #" + ++trial);
                                  if (_train != null) _train.delete();
                                  if (_test != null) _test.delete();
                                  if (fr != null) fr.delete();

                                  rand = RandomUtils.getDeterRNG(seed);

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
                                  _train = frame(names, water.util.ArrayUtils.subarray(rows, 0, limit));
                                  _test  = frame(names, water.util.ArrayUtils.subarray(rows, limit, (int) frame.numRows() - limit));
                                  // Convert response to an enum
                                  String resp_name = _train.lastVecName();
                                  _train.add(resp_name, _train.remove(resp_name).toEnum());
                                  _test .add(resp_name, _test .remove(resp_name).toEnum());
                                  DKV.put(_train._key, _train);
                                  DKV.put(_test ._key, _test );

                                  p = new DeepLearningParameters();
                                  p._training_frame = _train._key;
                                  p._response_column = resp_name;
                                  p.ignored_columns = null;
                                  p.ignore_const_cols = true;
                                  fr = FrameTask.DataInfo.prepareFrame(_train, _train.vec(p._response_column), _train.find(p.ignored_columns), true, p.ignore_const_cols);
                                  dinfo = new FrameTask.DataInfo(Key.make(),fr, 1, false, FrameTask.DataInfo.TransformType.STANDARDIZE);
                                }
                                // must have all output classes in training data (since that's what the reference implementation has hardcoded)
                                while (dinfo._adaptedFrame.lastVec().domain().length < 3);

                                // use the same seed for the reference implementation
                                DeepLearningMLPReference ref = new DeepLearningMLPReference();
                                ref.init(activation, RandomUtils.getDeterRNG(seed), holdout_ratio, hidden);

                                p.seed = seed;
                                p.hidden = new int[]{hidden};
                                p.adaptive_rate = false;
                                p.rho = 0;
                                p.epsilon = 0;
                                p.rate = rate / (1 - momentum); //adapt to (1-m) correction that's done inside (only for constant momentum!)
                                p.activation = activation;
                                p.max_w2 = Float.POSITIVE_INFINITY;
                                p.input_dropout_ratio = 0;
                                p.rate_annealing = 0; //do not change - not implemented in reference
                                p.l1 = 0;
                                p.loss = loss;
                                p.l2 = 0;
                                p.momentum_stable = momentum; //reference only supports constant momentum
                                p.momentum_start = p.momentum_stable; //do not change - not implemented in reference
                                p.momentum_ramp = 0; //do not change - not implemented in reference
                                p.initial_weight_distribution = dist;
                                p.initial_weight_scale = scale;
                                p._classification = true;
                                p.diagnostics = true;
                                p._validation_frame = null;
                                p.quiet_mode = true;
                                p.fast_mode = false; //to be the same as reference
//                            p.fast_mode = true; //to be the same as old NeuralNet code
                                p.nesterov_accelerated_gradient = false; //to be the same as reference
//                            p.nesterov_accelerated_gradient = true; //to be the same as old NeuralNet code
                                p.train_samples_per_iteration = 0; //sync once per period
                                p.ignore_const_cols = false;
                                p.shuffle_training_data = false;
                                p.classification_stop = -1; //don't stop early -> need to compare against reference, which doesn't stop either
                                p.force_load_balance = false; //keep just 1 chunk for reproducibility
                                p.override_with_best_model = false; //keep just 1 chunk for reproducibility
                                p.replicate_training_data = false;
                                p.single_node_mode = true;
                                p.sparse = sparse;
                                p.col_major = col_major;
                                p.epochs = 0;
                                DeepLearning dl = new DeepLearning(p);
                                try {
                                  mymodel = dl.train().get();
                                } finally {
                                  dl.remove();
                                }
                                p.epochs = epoch;

                                Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(mymodel.model_info());

                                // use the same random weights for the reference implementation
                                Neurons l = neurons[1];
                                for (int o = 0; o < l._a.size(); o++) {
                                  for (int i = 0; i < l._previous._a.size(); i++) {
//                                System.out.println("initial weight[" + o + "]=" + l._w[o * l._previous._a.length + i]);
                                    ref._nn.ihWeights[i][o] = l._w.get(o, i);
                                  }
                                  ref._nn.hBiases[o] = l._b.get(o);
//                              System.out.println("initial bias[" + o + "]=" + l._b[o]);
                                }
                                l = neurons[2];
                                for (int o = 0; o < l._a.size(); o++) {
                                  for (int i = 0; i < l._previous._a.size(); i++) {
//                                System.out.println("initial weight[" + o + "]=" + l._w[o * l._previous._a.length + i]);
                                    ref._nn.hoWeights[i][o] = l._w.get(o, i);
                                  }
                                  ref._nn.oBiases[o] = l._b.get(o);
//                              System.out.println("initial bias[" + o + "]=" + l._b[o]);
                                }

                                // Train the Reference
                                ref.train((int) p.epochs, rate, p.momentum_stable, loss);

                                // Train H2O
                                mymodel.delete_best_model();
                                mymodel.delete();
                                dl = new DeepLearning(p);
                                try {
                                  mymodel = dl.train().get();
                                } finally {
                                  dl.remove();
                                }
                                Assert.assertTrue(mymodel.model_info().get_processed_total() == epoch * fr.numRows());

                                /**
                                 * Tolerances (should ideally be super tight -> expect the same double/float precision math inside both algos)
                                 */
                                final double abseps = 1e-4;
                                final double releps = 1e-4;

                                /**
                                 * Compare weights and biases in hidden layer
                                 */
                                neurons = DeepLearningTask.makeNeuronsForTesting(mymodel.model_info()); //link the weights to the neurons, for easy access
                                l = neurons[1];
                                for (int o = 0; o < l._a.size(); o++) {
                                  for (int i = 0; i < l._previous._a.size(); i++) {
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
                                for (int o = 0; o < l._a.size(); o++) {
                                  for (int i = 0; i < l._previous._a.size(); i++) {
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
                                    double[] xValues = new double[neurons[0]._a.size()];
                                    System.arraycopy(ref._testData[i], 0, xValues, 0, xValues.length);
                                    double[] ref_preds = ref._nn.ComputeOutputs(xValues);

                                    // find the label
                                    // do the same as H2O here (compare float values and break ties based on row number)
                                    float[] preds = new float[ref_preds.length + 1];
                                    for (int j = 0; j < ref_preds.length; ++j) preds[j + 1] = (float) ref_preds[j];
                                    preds[0] = getPrediction(preds, i);

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
                                final double testErr = ref._nn.Accuracy(ref._testData);
                                trainPredict = mymodel.score(_train, false);
                                final double myTrainErr = mymodel.calcError(_train, _train.lastVec(), trainPredict, trainPredict, "Final training error:",
                                        true, p.max_confusion_matrix_size, new hex.ConfusionMatrix(), null, null);
                                testPredict = mymodel.score(_test, false);
                                final double myTestErr = mymodel.calcError(_test, _test.lastVec(), testPredict, testPredict, "Final testing error:",
                                        true, p.max_confusion_matrix_size, new hex.ConfusionMatrix(), null, null);
                                Log.info("H2O  training error : " + myTrainErr * 100 + "%, test error: " + myTestErr * 100 + "%");
                                Log.info("REF  training error : " + trainErr * 100 + "%, test error: " + testErr * 100 + "%");
                                compareVal(trainErr, myTrainErr, abseps, releps);
                                compareVal(testErr, myTestErr, abseps, releps);
                                Log.info("Scoring: PASS");

                                // get the actual best error on training data
                                float best_err = Float.MAX_VALUE;
                                for (Errors err : mymodel.scoring_history()) {
                                  best_err = Math.min(best_err, (float) err.train_err); //multi-class classification
                                }
                                Log.info("Actual best error : " + best_err * 100 + "%.");

                                // this is enabled by default
                                if (p.override_with_best_model) {
                                  Frame bestPredict = null;
                                  try {
                                    bestPredict = mymodel.score(_train, false);
                                    final double bestErr = mymodel.calcError(_train, _train.lastVec(), bestPredict, bestPredict, "Best error:",
                                            true, p.max_confusion_matrix_size, new hex.ConfusionMatrix(), null, null);
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
                                  mymodel.delete_best_model();
                                  mymodel.delete();
                                }
                                if (_train != null) _train.delete();
                                if (_test != null) _test.delete();
                                if (fr != null) fr.delete();
                                if (trainPredict != null) trainPredict.delete();
                                if (testPredict != null) testPredict.delete();
                                Scope.exit();
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
}

