package hex.deeplearning;

import org.junit.*;

import water.*;
import water.api.AUC;
import water.api.AUCData;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset2;
import water.util.Log;
import static hex.deeplearning.DeepLearningModel.*;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.ClassSamplingMethod;

import java.util.Random;

public class DeepLearningProstateTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  @Test public void run() throws Exception { runFraction(0.001f); }

  public void runFraction(float fraction) {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    String[] datasets = new String[2];
    int[][] responses = new int[datasets.length][];
    datasets[0] = "smalldata/./logreg/prostate.csv"; responses[0] = new int[]{1,2,8}; //CAPSULE (binomial), AGE (regression), GLEASON (multi-class)
    datasets[1] = "smalldata/iris/iris.csv";  responses[1] = new int[]{4}; //Iris-type (multi-class)

    int testcount = 0;
    int count = 0;
    for (int i = 0; i < datasets.length; ++i) {
      final String dataset = datasets[i];
      NFSFileVec nfs = NFSFileVec.make(find_test_file(dataset));
      Frame frame = ParseDataset2.parse(Key.make(), nfs._key);
      NFSFileVec vnfs = NFSFileVec.make(find_test_file(dataset));
      Frame vframe = ParseDataset2.parse(Key.make(), vnfs._key);

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
//                                        -2, //auto-tune
                                        -1, //N epochs per iteration
                                        0, //1 epoch per iteration
                                        rng.nextInt(100), // <1 epoch per iteration
                                        500, //>1 epoch per iteration
                                }) {
                                  DeepLearningModel model1 = null, model2 = null, tmp_model = null;
                                  Key dest = null, dest_tmp = null;
                                  count++;
                                  if (fraction < rng.nextFloat()) continue;

                                  try {
                                    Log.info("**************************)");
                                    Log.info("Starting test #" + count);
                                    Log.info("**************************)");
                                    final double epochs = 7 + rng.nextDouble() + rng.nextInt(4);
                                    final int[] hidden = new int[]{1 + rng.nextInt(4), 1 + rng.nextInt(6)};
                                    Frame valid = null; //no validation
                                    if (vf == 1) valid = frame; //use the same frame for validation
                                    else if (vf == -1) valid = vframe; //different validation frame (here: from the same file)

                                    // build the model, with all kinds of shuffling/rebalancing/sampling
                                    dest_tmp = Key.make(Key.make().toString() + "first");
                                    {
                                      Log.info("Using seed: " + seed);
                                      DeepLearningParameters p = new DeepLearningParameters();
                                      p.checkpoint = null;

                                      p.source = frame;
                                      p.response = frame.vecs()[resp];
                                      p.validation = valid;

                                      p.hidden = hidden;
                                      if (i == 0 && resp == 2) p.classification = false;
//                                      p.best_model_key = best_model_key;
                                      p.override_with_best_model = override_with_best_model;
                                      p.epochs = epochs;
                                      p.n_folds = n_folds;
                                      p.keep_cross_validation_splits = keep_cv_splits;
                                      p.seed = seed;
                                      p.train_samples_per_iteration = train_samples_per_iteration;
                                      p.force_load_balance = load_balance;
                                      p.replicate_training_data = replicate;
                                      p.shuffle_training_data = shuffle;
                                      p.score_training_samples = scoretraining;
                                      p.score_validation_samples = scorevalidation;
                                      p.classification_stop = -1;
                                      p.regression_stop = -1;
                                      p.balance_classes = balance_classes;
                                      p.quiet_mode = true;
                                      p.score_validation_sampling = csm;
                                      try {
                                        model1 = new DeepLearning(dest_tmp, p, 1).train().get();
                                      } catch (Throwable t) {
                                        t.printStackTrace();
                                        throw new RuntimeException(t);
                                      }

                                      if (n_folds != 0)
                                      // test HTML of cv models
                                      {
                                        throw H2O.unimpl();
//                                        for (Key k : model1.get_params().xval_models) {
//                                          DeepLearningModel cv_model = UKV.get(k);
//                                          StringBuilder sb = new StringBuilder();
//                                          cv_model.generateHTML("cv", sb);
//                                          cv_model.delete_best_model();
//                                          cv_model.delete();
//                                        }
                                      }
                                    }

                                    // Do some more training via checkpoint restart
                                    // For n_folds, continue without n_folds (not yet implemented) - from now on, model2 will have n_folds=0...
                                    dest = Key.make();
                                    DeepLearningParameters p = new DeepLearningParameters();
                                    tmp_model = DKV.get(dest_tmp).get(); //this actually *requires* frame to also still be in UKV (because of DataInfo...)
                                    Assert.assertTrue(tmp_model.model_info().get_processed_total() >= frame.numRows() * epochs);
                                    assert (tmp_model != null);

                                    p.checkpoint = dest_tmp;
                                    p.n_folds = 0;

                                    p.source = frame;
                                    p.validation = valid;
                                    p.response = frame.vecs()[resp];
                                    if (i == 0 && resp == 2) p.classification = false;
                                    p.override_with_best_model = override_with_best_model;
                                    p.epochs = epochs;
                                    p.seed = seed;
                                    p.train_samples_per_iteration = train_samples_per_iteration;
                                    try {
                                      model1 = new DeepLearning(dest, p, 1).train().get();
                                    } catch (Throwable t) {
                                      t.printStackTrace();
                                      throw new RuntimeException(t);
                                    }

                                    // score and check result (on full data)
                                    model2 = DKV.get(dest).get(); //this actually *requires* frame to also still be in UKV (because of DataInfo...)

                                    // score and check result of the best_model
                                    if (model2.actual_best_model_key != null) {
                                      final DeepLearningModel best_model = DKV.get(model2.actual_best_model_key).get();
                                      if (override_with_best_model) {
                                        Assert.assertEquals(best_model.error(), model2.error(), 0);
                                      }
                                    }

                                    if (valid == null) valid = frame;
                                    double threshold = 0;
                                    if (model2._output.isClassifier()) {
                                      Frame pred = null, pred2 = null;
                                      try {
                                        pred = model2.score(valid);
                                        StringBuilder sb = new StringBuilder();

                                        AUC auc = new AUC();
                                        double error = 0;
                                        // binary
                                        if (model2._output.nclasses() == 2) {
                                          auc.actual = valid;
                                          assert (resp == 1);
                                          auc.vactual = valid.vecs()[resp];
                                          auc.predict = pred;
                                          auc.vpredict = pred.vecs()[2];
                                          auc.execImpl();
                                          auc.toASCII(sb);
                                          AUCData aucd = auc.data();
                                          threshold = aucd.threshold();
                                          error = aucd.err();
                                          Log.info(sb);

                                          // check that auc.cm() is the right CM
                                          Assert.assertEquals(new ConfusionMatrix2(auc.data().cm()).err(), error, 1e-15);

                                          // check that calcError() is consistent as well (for CM=null, AUC!=null)
                                          Assert.assertEquals(model2.calcError(valid, valid.lastVec(), pred, pred, "training", false, 0, null, auc, null), error, 1e-15);
                                        }

                                        // Compute CM
                                        double CMerrorOrig;
                                        {
                                          sb = new StringBuilder();
                                          water.api.ConfusionMatrix CM = new water.api.ConfusionMatrix();
                                          CM.actual = valid;
                                          CM.vactual = valid.vecs()[resp];
                                          CM.predict = pred;
                                          CM.vpredict = pred.vecs()[0];
                                          CM.execImpl();
                                          sb.append("\n");
                                          sb.append("Threshold: " + "default\n");
                                          CM.toASCII(sb);
                                          Log.info(sb);
//                                        CMerrorOrig = new ConfusionMatrix2(CM.cm).err();
                                        }

                                        // confirm that orig CM was made with threshold 0.5
                                        // put pred2 into UKV, and allow access
                                        pred2 = new Frame(Key.make("pred2"), pred.names(), pred.vecs());
                                        pred2.delete_and_lock(null);
                                        pred2.unlock(null);

//                                        if (model2._output.nclasses() == 2) {
//                                          // make labels with 0.5 threshold for binary classifier
//                                          Env ev = Exec2.exec("pred2[,1]=pred2[,3]>=" + 0.5);
//                                          try {
//                                            pred2 = ev.popAry();
//                                            String skey = ev.key();
//                                            ev.subRef(pred2, skey);
//                                          } finally {
//                                            if (ev!=null) ev.remove_and_unlock();
//                                          }
//
//                                          water.api.ConfusionMatrix CM = new water.api.ConfusionMatrix();
//                                          CM.actual = valid;
//                                          CM.vactual = valid.vecs()[1];
//                                          CM.predict = pred2;
//                                          CM.vpredict = pred2.vecs()[0];
//                                          CM.invoke();
//                                          sb = new StringBuilder();
//                                          sb.append("\n");
//                                          sb.append("Threshold: " + 0.5 + "\n");
//                                          CM.toASCII(sb);
//                                          Log.info(sb);
//                                          double threshErr = new ConfusionMatrix(CM.cm).err();
//                                          Assert.assertEquals(threshErr, CMerrorOrig, 1e-15);
//
//                                          // make labels with AUC-given threshold for best F1
//                                          ev = Exec2.exec("pred2[,1]=pred2[,3]>=" + threshold);
//                                          try {
//                                            pred2 = ev.popAry();
//                                            String skey = ev.key();
//                                            ev.subRef(pred2, skey);
//                                          } finally {
//                                            if (ev != null) ev.remove_and_unlock();
//                                          }
//
//                                          CM = new water.api.ConfusionMatrix();
//                                          CM.actual = valid;
//                                          CM.vactual = valid.vecs()[1];
//                                          CM.predict = pred2;
//                                          CM.vpredict = pred2.vecs()[0];
//                                          CM.invoke();
//                                          sb = new StringBuilder();
//                                          sb.append("\n");
//                                          sb.append("Threshold: ").append(threshold).append("\n");
//                                          CM.toASCII(sb);
//                                          Log.info(sb);
//                                          double threshErr2 = new ConfusionMatrix(CM.cm).err();
//                                          Assert.assertEquals(threshErr2, error, 1e-15);
//                                        }
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
                                      model1.delete_best_model();
                                      model1.delete();
                                    }
                                    if (model2 != null) {
                                      model2.delete_xval_models();
                                      model2.delete_best_model();
                                      model2.delete();
                                    }
                                    if (tmp_model != null) {
                                      tmp_model.delete_xval_models();
                                      tmp_model.delete_best_model();
                                      tmp_model.delete();
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
  }

  public static class Long extends DeepLearningProstateTest {
    @Test
    @Ignore
    public void run() throws Exception { runFraction(1f); }
  }

  public static class Mid extends DeepLearningProstateTest {
    @Test
    public void run() throws Exception { runFraction(0.01f); } //for nightly tests
  }

  public static class Short extends DeepLearningProstateTest {
    @Test public void run() throws Exception { runFraction(0.001f); }
  }
}
