package hex.deeplearning;

import hex.FrameTask;
import static junit.framework.Assert.failNotEquals;
import static org.junit.Assert.assertTrue;
import water.*;
import water.api.ConfusionMatrix;
import water.fvec.*;
import static water.util.ArrayUtils.subarray;
import static water.util.ModelUtils.getPrediction;

import org.junit.BeforeClass;
import org.junit.Test;
import water.util.Log;
import water.util.RandomUtils;

import java.util.Random;

public class DeepLearningIrisTest extends TestUtil {
  static final String PATH = "smalldata/iris/iris.csv";
  Frame _train, _test;


  /**
   * Create a new frame based on given row data.
   * @param names  names of frame columns
   * @param rows  data given in the form of rows
   * @return new frame which contains columns named according given names and including given data
   */
  public static Frame frame(String[] names, double[]... rows) {
    assert names == null || names.length == rows[0].length;
    Futures fs = new Futures();
    Vec[] vecs = new Vec[rows[0].length];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
    for( int c = 0; c < vecs.length; c++ ) {
      AppendableVec vec = new AppendableVec(keys[c]);
      NewChunk chunk = new NewChunk(vec, 0);
      for (double[] row : rows) chunk.addNum(row[c]);
      chunk.close(0, fs);
      vecs[c] = vec.close(fs);
    }
    fs.blockForPending();
    return new Frame(names, vecs);
  }

  // Default run is the long run
  @Test public void run() throws Exception { runFraction(1.0f); }

  @BeforeClass public static void stall() {
    stall_till_cloudsize(1);
  }

  void compareVal(double a, double b, double abseps, double releps) {
    // check for equality
    if (Double.compare(a, b) == 0) {
    }
    // check for small relative error
    else if (Math.abs(a-b)/Math.max(a,b) < releps) {
    }
    // check for small absolute error
    else if (Math.abs(a - b) <= abseps) {
    }
    // fail
    else failNotEquals("Not equal: ", a, b);
  }

  void runFraction(float fraction) {
    long seed0 = 0xDECAF;
    int num_runs = 0;
    for (int repeat = 0; repeat < 5; ++repeat) {
      // Testing different things
      // Note: Microsoft reference implementation is only for Tanh + MSE, rectifier and MCE are implemented by 0xdata (trivial).
      // Note: Initial weight distributions are copied, but what is tested is the stability behavior.

      DeepLearning.Activation[] activations = {DeepLearning.Activation.Tanh, DeepLearning.Activation.Rectifier};
      DeepLearning.Loss[] losses = {DeepLearning.Loss.MeanSquare, DeepLearning.Loss.CrossEntropy};
      DeepLearning.InitialWeightDistribution[] dists = {
              DeepLearning.InitialWeightDistribution.Normal,
              DeepLearning.InitialWeightDistribution.Uniform,
              DeepLearning.InitialWeightDistribution.UniformAdaptive
      };
      final long seed = seed0 + repeat;
      Random rng = new Random(seed);

      double[] initial_weight_scales = {1e-4 + rng.nextDouble()};
      double[] holdout_ratios = {0.1 + rng.nextDouble() * 0.8};
      double[] momenta = {rng.nextDouble() * 0.99};
      int[] hiddens = {1, 2 + rng.nextInt(50)};
      int[] epochs = {1, 2 + rng.nextInt(50)};
      double[] rates = {0.01, 1e-5 + rng.nextDouble() * .1};

      for (DeepLearning.Activation activation : activations) {
        for (DeepLearning.Loss loss : losses) {
          for (DeepLearning.InitialWeightDistribution dist : dists) {
            for (double scale : initial_weight_scales) {
              for (double holdout_ratio : holdout_ratios) {
                for (double momentum : momenta) {
                  for (int hidden : hiddens) {
                    for (int epoch : epochs) {
                      for (double rate : rates) {
                        for (boolean sparse : new boolean[]{true,false}) {
                          for (boolean col_major : new boolean[]{false}) {
                            if (col_major && !sparse) continue;
                            num_runs++;
                            if (fraction < rng.nextFloat()) continue;
                            Log.info("");
                            Log.info("STARTING.");
                            Log.info("Running with " + activation.name() + " activation function and " + loss.name() + " loss function.");
                            Log.info("Initialization with " + dist.name() + " distribution and " + scale + " scale, holdout ratio " + holdout_ratio);
                            Log.info("Using " + hidden + " hidden layers and momentum: " + momentum);
                            Log.info("Using seed " + seed);

                            NFSFileVec nfs = NFSFileVec.make(find_test_file(PATH));
                            Frame frame = water.parser.ParseDataset2.parse(Key.make("iris_nn2"), nfs._key);

                            Frame fr = null;
                            DeepLearning p;
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
                              _train = frame(names, subarray(rows, 0, limit));
                              _test = frame(names, subarray(rows, limit, (int) frame.numRows() - limit));

                              p = new DeepLearning(Key.make());
                              p.source = _train;
                              p.response = _train.lastVec();
                              p.ignored_cols = null;
                              p.ignore_const_cols = true;
                              fr = FrameTask.DataInfo.prepareFrame(p.source, p.response, p.ignored_cols, true, p.ignore_const_cols);
                              dinfo = new FrameTask.DataInfo(fr, 1, false, true);
                            }
                            // must have all output classes in training data (since that's what the reference implementation has hardcoded)
                            while (dinfo._adaptedFrame.lastVec().domain().length < 3);

                            // use the same seed for the reference implementation
                            DeepLearningMLPReference ref = new DeepLearningMLPReference();
                            ref.init(activation, RandomUtils.getDeterRNG(seed), holdout_ratio, hidden);

//                            p.best_model_key = null;
                            p.best_model_key = Key.make("best_DLIris.hex");
                            p.seed = seed;
                            p.hidden = new int[]{hidden};
                            p.adaptive_rate = false;
                            p.rho = 0;
                            p.epsilon = 0;
                            p.rate = rate / (1 - momentum); //adapt to (1-m) correction that's done inside (only for constant momentum!)
                            p.activation = activation;
                            p.max_w2 = Float.POSITIVE_INFINITY;
                            p.epochs = epoch;
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
                            p.classification = true;
                            p.diagnostics = true;
                            p.validation = null;
                            p.quiet_mode = true;
                            p.fast_mode = false; //to be the same as reference
//                      p.fast_mode = true; //to be the same as old NeuralNet code
                            p.nesterov_accelerated_gradient = false; //to be the same as reference
//                        p.nesterov_accelerated_gradient = true; //to be the same as old NeuralNet code
                            p.train_samples_per_iteration = 0; //sync once per period
                            p.ignore_const_cols = false;
                            p.shuffle_training_data = false;
                            p.classification_stop = -1; //don't stop early -> need to compare against reference, which doesn't stop either
                            p.force_load_balance = false; //keep just 1 chunk for reproducibility
                            p.replicate_training_data = false;
                            p.single_node_mode = true;
                            p.sparse = sparse;
                            p.col_major = col_major;
                            DeepLearningModel mymodel = p.initModel(); //randomize weights, but don't start training yet

                            Neurons[] neurons = DeepLearningTask.makeNeuronsForTraining(mymodel.model_info());

                            // use the same random weights for the reference implementation
                            Neurons l = neurons[1];
                            for (int o = 0; o < l._a.size(); o++) {
                              for (int i = 0; i < l._previous._a.size(); i++) {
//                          System.out.println("initial weight[" + o + "]=" + l._w[o * l._previous._a.length + i]);
                                ref._nn.ihWeights[i][o] = l._w.get(o, i);
                              }
                              ref._nn.hBiases[o] = l._b.get(o);
//                        System.out.println("initial bias[" + o + "]=" + l._b[o]);
                            }
                            l = neurons[2];
                            for (int o = 0; o < l._a.size(); o++) {
                              for (int i = 0; i < l._previous._a.size(); i++) {
//                          System.out.println("initial weight[" + o + "]=" + l._w[o * l._previous._a.length + i]);
                                ref._nn.hoWeights[i][o] = l._w.get(o, i);
                              }
                              ref._nn.oBiases[o] = l._b.get(o);
//                        System.out.println("initial bias[" + o + "]=" + l._b[o]);
                            }

                            // Train the Reference
                            ref.train((int) p.epochs, rate, p.momentum_stable, loss);

                            // Train H2O
                            mymodel = p.trainModel(mymodel);

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
//                          System.out.println("weight[" + o + "]=" + b);
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
                              assertTrue(preds[0] == (int) fpreds.vecs()[0].at(i));
//                          // compare predicted probabilities
//                          for (int j=0; j<ref_preds.length; ++j) {
//                            compareVal((float)(ref_preds[j]), fpreds.vecs()[1+j].at(i), abseps, releps);
//                          }
                            }
                            fpreds.delete();
                            Log.info("Predicted values: PASS");

                            /**
                             * Compare (self-reported) scoring
                             */
                            final double trainErr = ref._nn.Accuracy(ref._trainData);
                            final double testErr = ref._nn.Accuracy(ref._testData);
                            final Frame trainPredict = mymodel.score(_train, false);
                            final double myTrainErr = mymodel.calcError(_train, _train.lastVec(), trainPredict, trainPredict, "Final training error:",
                                    true, p.max_confusion_matrix_size, new ConfusionMatrix(), null, null);
                            final Frame testPredict = mymodel.score(_test, false);
                            final double myTestErr = mymodel.calcError(_test, _test.lastVec(), testPredict, testPredict, "Final testing error:",
                                    true, p.max_confusion_matrix_size, new ConfusionMatrix(), null, null);
                            Log.info("H2O  training error : " + myTrainErr * 100 + "%, test error: " + myTestErr * 100 + "%");
                            Log.info("REF  training error : " + trainErr * 100 + "%, test error: " + testErr * 100 + "%");
                            compareVal(trainErr, myTrainErr, abseps, releps);
                            compareVal(testErr, myTestErr, abseps, releps);
                            Log.info("Scoring: PASS");

                            if (p.best_model_key != null) {
                              // get the actual best error on training data
                              float best_err = Float.MAX_VALUE;
                              for (DeepLearningModel.Errors err : mymodel.scoring_history()) {
                                best_err = Math.min(best_err, (float)err.train_err); //multi-class classification
                              }
                              Log.info("Actual best error : " + best_err * 100 + "%.");

                              // get the error reported by the stored best model
                              DeepLearningModel bestmodel = DKV.get(p.best_model_key).get();
                              final Frame bestPredict = bestmodel.score(_train, false);
                              final double bestErr = bestmodel.calcError(_train, _train.lastVec(), bestPredict, bestPredict, "Best error:",
                                      true, p.max_confusion_matrix_size, new ConfusionMatrix(), null, null);
                              Log.info("Best_model's error : " + bestErr * 100 + "%.");
                              compareVal(bestErr, best_err, abseps, releps);
                              bestmodel.delete();
                              bestPredict.delete();
                            }

                            // cleanup
                            mymodel.delete();
                            _train.delete();
                            _test.delete();
                            frame.delete();
                            fr.delete();
                            p.remove();
                            trainPredict.delete();
                            testPredict.delete();
                            Log.info("Parameters combination " + num_runs + ": PASS");
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

  public static class Long extends DeepLearningIrisTest {
    @Test public void run() throws Exception { runFraction(1.0f); }
  }

  public static class Short extends DeepLearningIrisTest {
    @Test public void run() throws Exception { runFraction(0.05f); }
  }
}

