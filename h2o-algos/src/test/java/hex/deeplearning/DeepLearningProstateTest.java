package hex.deeplearning;

import hex.ConfusionMatrix;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.ClassSamplingMethod;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.rapids.Env;
import water.rapids.Exec;
import water.util.Log;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;

import static hex.ConfusionMatrix.buildCM;
import static hex.deeplearning.DeepLearningModel.DeepLearningParameters;

public class DeepLearningProstateTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  @Test public void run() throws Exception { runFraction(0.0007f); }

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
        for (boolean replicate : new boolean[]{
                true,
                false,
        }) {
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
                for (int resp : responses[i]) {
                  /* boolean classification = !(i == 0 && resp == 2);
                     if (classification && !frame.vec(resp).isEnum()) {
                        frame.replace(resp, frame.vec(resp).toEnum());
                        DKV.put(frame._key, frame);
                  } */
                  boolean classification = false;   // TODO: Test currently limited to regression only

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
//                                  2,
                          }) {
                            if (n_folds != 0 && vf != 0) continue;
                            for (boolean keep_cv_splits : new boolean[]{false}) { //otherwise it leaks
                              for (boolean override_with_best_model : new boolean[]{false, true}) {
                                for (int train_samples_per_iteration : new int[]{
                                        -2, //auto-tune
                                        -1, //N epochs per iteration
                                        0, //1 epoch per iteration
                                        rng.nextInt(200), // <1 epoch per iteration
                                        500, //>1 epoch per iteration
                                }) {
                                  DeepLearningModel model1 = null, model2 = null, tmp_model = null;
                                  Key dest, dest_tmp;
                                  count++;
                                  if (fraction < rng.nextFloat()) continue;

                                  try {
                                    Scope.enter();
                                    Log.info("**************************)");
                                    Log.info("Starting test #" + count);
                                    Log.info("**************************)");
                                    final double epochs = 7 + rng.nextDouble() + rng.nextInt(4);
                                    final int[] hidden = new int[]{1 + rng.nextInt(4), 1 + rng.nextInt(6)};
                                    Frame valid = null; //no validation
                                    if (vf == 1) valid = frame; //use the same frame for validation
                                    else if (vf == -1) valid = vframe; //different validation frame (here: from the same file)

                                    // build the model, with all kinds of shuffling/rebalancing/sampling
                                    {
                                      Log.info("Using seed: " + seed);
                                      DeepLearningParameters p = new DeepLearningParameters();
                                      p._destination_key = Key.make(Key.make().toString() + "first");
                                      dest_tmp = p._destination_key;
                                      p._checkpoint = null;

                                      p._train = frame._key;
                                      p._response_column = frame._names[resp];
                                      p._valid = valid==null ? null : valid._key;

                                      p._hidden = hidden;
//                                      p.best_model_key = best_model_key;
                                      p._override_with_best_model = override_with_best_model;
                                      p._epochs = epochs;
                                      p._n_folds = n_folds;
                                      p._keep_cross_validation_splits = keep_cv_splits;
                                      p._seed = seed;
                                      p._train_samples_per_iteration = train_samples_per_iteration;
                                      p._force_load_balance = load_balance;
                                      p._replicate_training_data = replicate;
                                      p._shuffle_training_data = shuffle;
                                      p._score_training_samples = scoretraining;
                                      p._score_validation_samples = scorevalidation;
                                      p._classification_stop = -1;
                                      p._regression_stop = -1;
                                      p._balance_classes = classification && balance_classes;
                                      p._quiet_mode = true;
                                      p._score_validation_sampling = csm;
//                                      Log.info(new String(p.writeJSON(new AutoBuffer()).buf()).replace(",","\n"));
                                      DeepLearning dl = new DeepLearning(p);
                                      try {
                                        model1 = dl.trainModel().get();
                                        checkSums.add(model1.checksum());
                                      } catch (Throwable t) {
                                        t.printStackTrace();
                                        throw new RuntimeException(t);
                                      } finally {
                                        dl.remove();
                                      }
                                      Log.info("Trained for " + model1.epoch_counter + " epochs.");
                                      assert( ((p._train_samples_per_iteration <= 0 || p._train_samples_per_iteration >= frame.numRows()) && model1.epoch_counter > epochs)
                                              || Math.abs(model1.epoch_counter - epochs)/epochs < 0.20 );

                                      if (n_folds != 0)
                                      // test HTML of cv models
                                      {
                                        throw H2O.unimpl();
//                                        for (Key k : model1.get_params().xval_models) {
//                                          DeepLearningModel cv_model = UKV.get(k);
//                                          StringBuilder sb = new StringBuilder();
//                                          cv_model.generateHTML("cv", sb);
//                                          cv_model.delete();
//                                        }
                                      }
                                    }

                                    // Do some more training via checkpoint restart
                                    // For n_folds, continue without n_folds (not yet implemented) - from now on, model2 will have n_folds=0...
                                    DeepLearningParameters p = new DeepLearningParameters();
                                    tmp_model = DKV.get(dest_tmp).get(); //this actually *requires* frame to also still be in UKV (because of DataInfo...)
                                    Assert.assertTrue(tmp_model.model_info().get_processed_total() >= frame.numRows() * epochs);
                                    assert (tmp_model != null);

                                    p._destination_key = Key.make();
                                    dest = p._destination_key;
                                    p._checkpoint = dest_tmp;
                                    p._n_folds = 0;

                                    p._valid = valid == null ? null : valid._key;
                                    p._response_column = frame._names[resp];
                                    p._override_with_best_model = override_with_best_model;
                                    p._epochs = epochs;
                                    p._seed = seed;
                                    p._train_samples_per_iteration = train_samples_per_iteration;
                                    p._balance_classes = classification && balance_classes;
                                    p._train = frame._key;
                                    DeepLearning dl = new DeepLearning(p);
                                    try {
                                      model1 = dl.trainModel().get();
                                    } catch (Throwable t) {
                                      t.printStackTrace();
                                      throw new RuntimeException(t);
                                    } finally {
                                      dl.remove();
                                    }

                                    // score and check result (on full data)
                                    model2 = DKV.get(dest).get(); //this actually *requires* frame to also still be in DKV (because of DataInfo...)

                                    if (valid == null) valid = frame;
                                    double threshold = 0;
                                    if (model2._output.isClassifier()) {
                                      Frame pred = null, pred2 = null;
                                      try {
                                        pred = model2.score(valid);
                                        hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(model2, valid);
                                        double error = 0;
                                        // binary
                                        if (model2._output.nclasses() == 2) {
                                          assert (resp == 1);
                                          threshold = mm.auc().threshold();
                                          error = mm.auc().err();
                                          // check that auc.cm() is the right CM
                                          Assert.assertEquals(new ConfusionMatrix(mm.auc().cm(), new String[]{"0", "1"}).err(), error, 1e-15);
                                          // check that calcError() is consistent as well (for CM=null, AUC!=null)
                                          Assert.assertEquals(mm.cm().err(), error, 1e-15);
                                        }
                                        double CMerrorOrig = buildCM(valid.vecs()[resp].toEnum(), pred.vecs()[0].toEnum()).err();

                                        // confirm that orig CM was made with threshold 0.5
                                        // put pred2 into DKV, and allow access
                                        pred2 = new Frame(Key.make("pred2"), pred.names(), pred.vecs());
                                        pred2.delete_and_lock(null);
                                        pred2.unlock(null);

                                        if (model2._output.nclasses() == 2) {
                                          // make labels with 0.5 threshold for binary classifier
                                          // ast is from this expression pred2[,1] = (pred2[,3]>=0.5)
                                          String ast = "(= ([ %pred2 \"null\" #0) (G ([ %pred2 \"null\" #2) #"+0.5+"))";
                                          Env ev = Exec.exec(ast);
                                          try {
                                            pred2 = ev.popAry(); // pop0 pops w/o lowering refs, let remove_and_unlock handle cleanup
                                          } finally {
                                            if (ev!=null) ev.remove_and_unlock();
                                          }

                                          double threshErr = buildCM(valid.vecs()[resp].toEnum(), pred2.vecs()[0].toEnum()).err();
                                          Assert.assertEquals(threshErr, CMerrorOrig, 1e-15);

                                          // make labels with AUC-given threshold for best F1
                                          // similar ast to the above
                                          ast = "(= ([ %pred2 \"null\" #0) (G ([ %pred2 \"null\" #2) #"+threshold+"))";
                                          ev = Exec.exec(ast);
                                          try {
                                            pred2 = ev.popAry();  // pop0 pops w/o lowering refs, let remove_and_unlock handle cleanup
                                          } finally {
                                            if (ev != null) ev.remove_and_unlock();
                                          }

                                          double threshErr2 = buildCM(valid.vecs()[resp].toEnum(), pred2.vecs()[0].toEnum()).err();
                                          Assert.assertEquals(threshErr2, error, 1e-15);
                                        }
                                      } finally {
                                        if (pred != null) pred.delete();
                                        if (pred2 != null) pred2.delete();
                                      }
                                    } //classifier
                                    Log.info("Parameters combination " + count + ": PASS");
                                    testcount++;
                                  } catch (Throwable t) {
                                    t.printStackTrace();
                                    throw new RuntimeException(t);
                                  } finally {
                                    if (model1 != null) {
                                      model1.delete_xval_models();
                                      model1.delete();
                                    }
                                    if (model2 != null) {
                                      model2.delete_xval_models();
                                      model2.delete();
                                    }
                                    if (tmp_model != null) {
                                      tmp_model.delete_xval_models();
                                      tmp_model.delete();
                                    }
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
        }
      } finally {
        frame.delete();
        vframe.delete();
      }
    }
    Assert.assertTrue(checkSums.size() == testcount);
    Log.info("\n\n=============================================");
    Log.info("Tested " + testcount + " out of " + count + " parameter combinations.");
    Log.info("=============================================");
  }

  public static class Mid extends DeepLearningProstateTest {
    @Test @Ignore
    public void run() throws Exception { runFraction(0.01f); } //for nightly tests
  }

  public static class Short extends DeepLearningProstateTest {
    @Test @Ignore public void run() throws Exception { runFraction(0.001f); }
  }
}
