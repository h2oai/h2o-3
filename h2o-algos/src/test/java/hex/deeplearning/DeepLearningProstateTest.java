package hex.deeplearning;

import hex.ConfusionMatrix;
import hex.Distribution;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.ClassSamplingMethod;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;

import static hex.ConfusionMatrix.buildCM;

public class DeepLearningProstateTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() throws Exception { runFraction(0.00002f); }

  public void runFraction(float fraction) {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    String[] datasets = new String[2];
    int[][] responses = new int[datasets.length][];
    datasets[0] = "smalldata/logreg/prostate.csv"; responses[0] = new int[]{1,2,8}; //CAPSULE (binomial), AGE (regression), GLEASON (multi-class)
    datasets[1] = "smalldata/iris/iris.csv";  responses[1] = new int[]{4}; //Iris-type (multi-class)
    HashSet<Long> checkSums = new LinkedHashSet<>();

    int testcount = 0;
    int count = 0;
    for (int i = 0; i < datasets.length; ++i) {
      final String dataset = datasets[i];
      NFSFileVec  nfs = NFSFileVec.make(find_test_file(dataset));
      Frame  frame = ParseDataset.parse(Key.make(), nfs._key);
      NFSFileVec vnfs = NFSFileVec.make(find_test_file(dataset));
      Frame vframe = ParseDataset.parse(Key.make(), vnfs._key);

      try {
        for (int resp : responses[i]) {
          boolean classification = !(i == 0 && resp == 2);
          if (classification && !frame.vec(resp).isCategorical()) {
            DKV.remove(frame._key);
            String respname = frame.name(resp);
            Vec r = frame.vec(respname).toCategoricalVec();
            frame.remove(respname).remove();
            frame.add(respname, r);
            DKV.put(frame);

            DKV.remove(vframe._key);
            Vec vr = vframe.vec(respname).toCategoricalVec();
            vframe.remove(respname).remove();
            vframe.add(respname, vr);
            DKV.put(vframe);
          }
          for (DeepLearningParameters.Loss loss : new DeepLearningParameters.Loss[]{
                  DeepLearningParameters.Loss.Automatic,
                  DeepLearningParameters.Loss.CrossEntropy,
                  DeepLearningParameters.Loss.Huber,
                  DeepLearningParameters.Loss.Absolute,
                  DeepLearningParameters.Loss.Quadratic
          }) {
            if ( !classification && loss == DeepLearningParameters.Loss.CrossEntropy ) continue;
            for (Distribution.Family dist : new Distribution.Family[]{
                    Distribution.Family.AUTO,
                    Distribution.Family.laplace,
                    Distribution.Family.huber,
                    Distribution.Family.gaussian,
                    Distribution.Family.poisson,
                    Distribution.Family.tweedie,
                    Distribution.Family.gamma
            }) {
              if (classification && dist != Distribution.Family.multinomial && dist != Distribution.Family.bernoulli) continue;
              if (!classification) {
                if (dist == Distribution.Family.multinomial || dist == Distribution.Family.bernoulli) continue;
              }
              switch(dist) {
                case tweedie:
                case gamma:
                case poisson:
                  if (loss != DeepLearningParameters.Loss.Automatic)
                    continue;
                case huber:
                  if (loss != DeepLearningParameters.Loss.Huber && loss != DeepLearningParameters.Loss.Automatic)
                    continue;
                case laplace:
                  if (loss != DeepLearningParameters.Loss.Absolute && loss != DeepLearningParameters.Loss.Automatic)
                    continue;
              }

              for (boolean elastic_averaging : new boolean[]{
                      true,
                      false,
              }) {
                for (boolean replicate : new boolean[]{
                        true,
                        false,
                }) {
                  for (DeepLearningParameters.Activation activation : new DeepLearningParameters.Activation[]{
                          DeepLearningParameters.Activation.Tanh,
                          DeepLearningParameters.Activation.TanhWithDropout,
                          DeepLearningParameters.Activation.Rectifier,
                          DeepLearningParameters.Activation.RectifierWithDropout,
                          DeepLearningParameters.Activation.Maxout,
                          DeepLearningParameters.Activation.MaxoutWithDropout,
                  }) {
                    boolean reproducible=false;
                    switch (dist) {
                      case tweedie:
                      case gamma:
                      case poisson:
                        reproducible=true;
                      default:
                    }
                    for (boolean load_balance : new boolean[]{
                            true,
                            false,
                    }) {
                      for (boolean shuffle : new boolean[]{
                              true,
                              false,
                      }) {
                        for (boolean balance_classes : new boolean[]{
                                true,
                                false,
                        }) {
                          for (ClassSamplingMethod csm : new ClassSamplingMethod[]{
                                  ClassSamplingMethod.Stratified,
                                  ClassSamplingMethod.Uniform
                          }) {
                            for (int scoretraining : new int[]{
                                    200,
                                    20,
                                    0,
                            }) {
                              for (int scorevalidation : new int[]{
                                      200,
                                      20,
                                      0,
                              }) {
                                for (int vf : new int[]{
                                        0,  //no validation
                                        1,  //same as source
                                        -1, //different validation frame
                                }) {
                                  for (int n_folds : new int[]{
                                          0,
                                          2,
                                  }) {
                                    if (n_folds > 0 && balance_classes) continue; //FIXME: Add back

                                    for (boolean overwrite_with_best_model : new boolean[]{false, true}) {
                                      for (int train_samples_per_iteration : new int[]{
                                              -2, //auto-tune
                                              -1, //N epochs per iteration
                                              0, //1 epoch per iteration
                                              rng.nextInt(200), // <1 epoch per iteration
                                              500, //>1 epoch per iteration
                                      }) {
                                        DeepLearningModel model1 = null, model2 = null;
                                        count++;
                                        if (fraction < rng.nextFloat()) continue;

                                        try {
                                          Log.info("**************************)");
                                          Log.info("Starting test #" + count);
                                          Log.info("**************************)");
                                          final double epochs = 7 + rng.nextDouble() + rng.nextInt(4);
                                          final int[] hidden = new int[]{3 + rng.nextInt(4), 3 + rng.nextInt(6)};
                                          Frame valid = null; //no validation
                                          if (vf == 1) valid = frame; //use the same frame for validation
                                          else if (vf == -1) valid = vframe; //different validation frame (here: from the same file)
                                          long myseed = rng.nextLong();
                                          boolean replicate2 = rng.nextBoolean();
                                          boolean elastic_averaging2 = rng.nextBoolean();

                                          // build the model, with all kinds of shuffling/rebalancing/sampling
                                          DeepLearningParameters p = new DeepLearningParameters();
                                          {
                                            Log.info("Using seed: " + myseed);
                                            p._train = frame._key;
                                            p._response_column = frame._names[resp];
                                            p._valid = valid==null ? null : valid._key;

                                            p._hidden = hidden;
                                            p._input_dropout_ratio = 0.1;
                                            p._hidden_dropout_ratios = null;
                                            p._activation = activation;
//                                      p.best_model_key = best_model_key;
                                            p._overwrite_with_best_model = overwrite_with_best_model;
                                            p._epochs = epochs;
                                            p._loss = loss;
                                            p._distribution = dist;
                                            p._nfolds = n_folds;
                                            p._seed = myseed;
                                            p._train_samples_per_iteration = train_samples_per_iteration;
                                            p._force_load_balance = load_balance;
                                            p._replicate_training_data = replicate;
                                            p._reproducible = reproducible;
                                            p._shuffle_training_data = shuffle;
                                            p._score_training_samples = scoretraining;
                                            p._score_validation_samples = scorevalidation;
                                            p._classification_stop = -1;
                                            p._regression_stop = -1;
                                            p._balance_classes = classification && balance_classes;
                                            p._quiet_mode = true;
                                            p._score_validation_sampling = csm;
                                            p._elastic_averaging = elastic_averaging;
//                                      Log.info(new String(p.writeJSON(new AutoBuffer()).buf()).replace(",","\n"));
                                            DeepLearning dl = new DeepLearning(p, Key.<DeepLearningModel>make(Key.make().toString() + "first"));
                                            try {
                                              model1 = dl.trainModel().get();
                                              checkSums.add(model1.checksum());
                                              testcount++;
                                            } catch(Throwable t) {
                                              model1 = DKV.getGet(dl.dest());
                                              if (model1 != null)
                                                Assert.assertTrue(model1._output._job.isCrashed());
                                              throw t;
                                            }
                                            Log.info("Trained for " + model1.epoch_counter + " epochs.");
                                            assert( ((p._train_samples_per_iteration <= 0 || p._train_samples_per_iteration >= frame.numRows()) && model1.epoch_counter > epochs)
                                                    || Math.abs(model1.epoch_counter - epochs)/epochs < 0.20 );

                                            // check that iteration is of the expected length - check via when first scoring happens
                                            if (p._train_samples_per_iteration == 0) {
                                              // no sampling - every node does its share of the full data
                                              if (!replicate) assert((double)model1._output._scoring_history.get(1,3) == 1);
                                                // sampling on each node - replicated data
                                              else assert((double)model1._output._scoring_history.get(1,3) > 0.7 && (double)model1._output._scoring_history.get(1,3) < 1.3)
                                                      : ("First scoring at " + model1._output._scoring_history.get(1,3) + " epochs, should be closer to 1!" + "\n" + model1.toString());
                                            }
                                            else if (p._train_samples_per_iteration == -1) {
                                              // no sampling - every node does its share of the full data
                                              if (!replicate) assert ((double) model1._output._scoring_history.get(1, 3) == 1);
                                                // every node passes over the full dataset
                                              else {
                                                if (!reproducible)
                                                  assert ((double) model1._output._scoring_history.get(1, 3) == H2O.CLOUD.size());
                                              }
                                            }

                                            if (n_folds != 0) {
                                              assert(model1._output._cross_validation_metrics != null);
                                            } else {
                                              assert(model1._output._cross_validation_metrics == null);
                                            }

                                          }

                                          assert(model1.model_info().get_params()._l1 == 0);
                                          assert(model1.model_info().get_params()._l2 == 0);

                                          if (n_folds != 0) continue;
                                          // Do some more training via checkpoint restart
                                          // For n_folds, continue without n_folds (not yet implemented) - from now on, model2 will have n_folds=0...
                                          DeepLearningParameters p2 = new DeepLearningParameters();
                                          Assert.assertTrue(model1.model_info().get_processed_total() >= frame.numRows() * epochs);

                                          {
                                            p2._checkpoint = model1._key;
                                            p2._distribution = dist;
                                            p2._loss = loss;
                                            p2._nfolds = n_folds;
                                            p2._train = frame._key;
                                            p2._activation = activation;
                                            p2._hidden = hidden;
                                            p2._valid = valid == null ? null : valid._key;
                                            p2._l1 = 1e-3;
                                            p2._l2 = 1e-3;
                                            p2._reproducible = reproducible;
                                            p2._response_column = frame._names[resp];
                                            p2._overwrite_with_best_model = overwrite_with_best_model;
                                            p2._quiet_mode = true;
                                            p2._epochs = 2*epochs; //final amount of training epochs
                                            p2._replicate_training_data = replicate2;
                                            p2._seed = myseed;
//                                              p2._loss = loss; //fall back to default
//                                              p2._distribution = dist; //fall back to default
                                            p2._train_samples_per_iteration = train_samples_per_iteration;
                                            p2._balance_classes = classification && balance_classes;
                                            p2._elastic_averaging = elastic_averaging2;
                                            DeepLearning dl = new DeepLearning(p2);
                                            try {
                                              model2 = dl.trainModel().get();
                                            } catch(Throwable t) {
                                              model2 = DKV.getGet(dl.dest());
                                              if (model2 != null)
                                                Assert.assertTrue(model2._output._job.isCrashed());
                                              throw t;
                                            }
                                          }
                                          Assert.assertTrue(model1._output._job.isDone());
                                          Assert.assertTrue(model2._output._job.isDone());

                                          assert(model1._parms != p2);
                                          assert(model1.model_info().get_params() != model2.model_info().get_params());

                                          assert(model1.model_info().get_params()._l1 == 0);
                                          assert(model1.model_info().get_params()._l2 == 0);

                                          Assert.assertTrue(model2.model_info().get_processed_total() >= frame.numRows() * 2 * epochs);

                                          assert(p != p2);
                                          assert(p != model1.model_info().get_params());
                                          assert(p2 != model2.model_info().get_params());

                                          if (p._loss == DeepLearningParameters.Loss.Automatic) {
                                            assert(p2._loss == DeepLearningParameters.Loss.Automatic);
//                                              assert(model1.model_info().get_params()._loss != DeepLearningParameters.Loss.Automatic);
//                                              assert(model2.model_info().get_params()._loss != DeepLearningParameters.Loss.Automatic);
                                          }
                                          assert(p._hidden_dropout_ratios == null);
                                          assert(p2._hidden_dropout_ratios == null);
                                          if (p._activation.toString().contains("WithDropout")) {
                                            assert(model1.model_info().get_params()._hidden_dropout_ratios != null);
                                            assert(model2.model_info().get_params()._hidden_dropout_ratios != null);
                                            assert(Arrays.equals(
                                                    model1.model_info().get_params()._hidden_dropout_ratios,
                                                    model2.model_info().get_params()._hidden_dropout_ratios));
                                          }
                                          assert(p._l1 == 0);
                                          assert(p._l2 == 0);
                                          assert(p2._l1 == 1e-3);
                                          assert(p2._l2 == 1e-3);
                                          assert(model1.model_info().get_params()._l1 == 0);
                                          assert(model1.model_info().get_params()._l2 == 0);
                                          assert(model2.model_info().get_params()._l1 == 1e-3);
                                          assert(model2.model_info().get_params()._l2 == 1e-3);

                                          if (valid == null) valid = frame;
                                          double threshold;
                                          if (model2._output.isClassifier()) {
                                            Frame pred = null;
                                            Vec labels, predlabels, pred2labels;
                                            try {
                                              pred = model2.score(valid);
                                              // Build a POJO, validate same results
                                              Assert.assertTrue(model2.testJavaScoring(valid,pred,1e-6));

                                              hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(model2, valid);
                                              double error;
                                              // binary
                                              if (model2._output.nclasses() == 2) {
                                                assert (resp == 1);
                                                threshold = mm.auc_obj().defaultThreshold();
                                                error = mm.auc_obj().defaultErr();
                                                // check that auc.cm() is the right CM
                                                Assert.assertEquals(new ConfusionMatrix(mm.auc_obj().defaultCM(), model2._output._domains[resp]).err(), error, 1e-15);
                                                // check that calcError() is consistent as well (for CM=null, AUC!=null)
                                                Assert.assertEquals(mm.cm().err(), error, 1e-15);

                                                // check that the labels made with the default threshold are consistent with the CM that's reported by the AUC object
                                                labels = valid.vec(resp);
                                                predlabels = pred.vecs()[0];
                                                ConfusionMatrix cm = buildCM(labels, predlabels);
                                                Log.info("CM from pre-made labels:");
                                                Log.info(cm.toASCII());
//                                              Assert.assertEquals(cm.err(), error, 1e-4); //FIXME

                                                // confirm that orig CM was made with threshold 0.5
                                                // manually make labels with AUC-given default threshold
                                                String ast = "(= pred (> ([] pred 2) #"+threshold+") [0] [])";
                                                Frame tmp = water.rapids.Exec.exec(ast).getFrame();
                                                pred2labels = tmp.vecs()[0];
                                                cm = buildCM(labels, pred2labels);
                                                Log.info("CM from self-made labels:");
                                                Log.info(cm.toASCII());
                                                Assert.assertEquals(cm.err(), error, 1e-4); //AUC-given F1-optimal threshold might not reproduce AUC-given CM-error identically, but should match up to 1%
                                              }
                                            } finally {
                                              if (pred != null) pred.delete();
                                            }
                                          } //classifier
                                          else {
                                            Frame pred = null;
                                            try {
                                              pred = model2.score(valid);
                                              // Build a POJO, validate same results
                                              Assert.assertTrue(model2.testJavaScoring(frame,pred,1e-6));
                                            } finally {
                                              if (pred!=null) pred.delete();
                                            }
                                          }
                                          Log.info("Parameters combination " + count + ": PASS");
                                        } catch (H2OModelBuilderIllegalArgumentException | IllegalArgumentException ex) {
                                          throw H2O.fail("should not get here");
                                        } catch (RuntimeException t) {
                                          Assert.assertTrue(t.getMessage().contains("unstable"));
                                        } catch (Throwable t) {
                                          t.printStackTrace();
                                          throw new RuntimeException(t);
                                        } finally {
                                          if (model1 != null) {
                                            model1.deleteCrossValidationModels();
                                            model1.delete();
                                          }
                                          if (model2 != null) {
                                            model2.deleteCrossValidationModels();
                                            model2.delete();
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
                }
              }
            }
          }
        }
      } finally {
        frame.delete();
        vframe.delete();
      }
    }
    Log.info("\n\n=============================================");
    Log.info("Tested " + testcount + " out of " + count + " parameter combinations.");
    Log.info("=============================================");
    if (checkSums.size() != testcount) {
      Log.info("Only found " + checkSums.size() + " unique checksums.");
    }
    Assert.assertTrue(checkSums.size() == testcount);
    }

    public static class Mid extends DeepLearningProstateTest {
    @Test @Ignore public void run() throws Exception { runFraction(0.01f); } //for nightly tests
  }

  public static class Short extends DeepLearningProstateTest {
    @Test @Ignore public void run() throws Exception { runFraction(0.001f); }
  }
}
