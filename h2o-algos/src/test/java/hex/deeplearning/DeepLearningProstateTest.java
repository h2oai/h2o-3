package hex.deeplearning;

import org.junit.*;

import water.*;
import water.api.AUC;
import water.api.ConfusionMatrix;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset2;
import water.util.Log;

import java.util.Random;

public class DeepLearningProstateTest extends TestUtil {
  //Default: run 3%
  @Test @Ignore public void run() throws Exception { runFraction(0.003f); }

  public void runFraction(float fraction) {
    long seed = 0xDECAF;
    Random rng = new Random(seed);
    String[] datasets = new String[2];
    int[][] responses = new int[datasets.length][];
    datasets[0] = "smalldata/./logreg/prostate.csv"; responses[0] = new int[]{1,2,8};
    datasets[1] = "smalldata/iris/iris.csv";  responses[1] = new int[]{4};

    int testcount = 0;
    int count = 0;
    for (int i = 0; i < datasets.length; ++i) {
      final String dataset = datasets[i];
      NFSFileVec nfs = NFSFileVec.make(find_test_file(dataset));
      Frame frame = ParseDataset2.parse(Key.make(), nfs._key);

      NFSFileVec vnfs = NFSFileVec.make(find_test_file(dataset));
      Frame vframe = ParseDataset2.parse(Key.make(), vnfs._key);

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
                for (DeepLearning.ClassSamplingMethod csm : new DeepLearning.ClassSamplingMethod[] {
                        DeepLearning.ClassSamplingMethod.Stratified,
                        DeepLearning.ClassSamplingMethod.Uniform
                }) {
                  for (int scoretraining : new int[]{
                          200,
                          0,
                  }) {
                    for (int scorevalidation : new int[]{
                            200,
                            0,
                    }) {
                      for (int vf : new int[]{
                              0,  //no validation
                              1,  //same as source
                              -1, //different validation frame
                      }) {
                        for (int train_samples_per_iteration : new int[] {
                                -1, //N epochs per iteration
                                0, //1 epoch per iteration
                                rng.nextInt(100), // <1 epoch per iteration
                                500, //>1 epoch per iteration
                        })
                        {
                          for (Key best_model_key : new Key[]{null, Key.make()}) {
                            count++;
                            if (fraction < rng.nextFloat()) continue;
                            final double epochs = 7 + rng.nextDouble() + rng.nextInt(4);
                            final int[] hidden = new int[]{1 + rng.nextInt(4), 1 + rng.nextInt(6)};

                            Log.info("**************************)");
                            Log.info("Starting test #" + count);
                            Log.info("**************************)");
                            Frame valid = null; //no validation
                            if (vf == 1) valid = frame; //use the same frame for validation
                            else if (vf == -1) valid = vframe; //different validation frame (here: from the same file)

                            // build the model, with all kinds of shuffling/rebalancing/sampling
                            Key dest_tmp = Key.make();
                            {
                              Log.info("Using seed: " + seed);
                              DeepLearning p = new DeepLearning(dest_tmp);
                              p.checkpoint = null;

                              p.source = frame;
                              p.response = frame.vecs()[resp];
                              p.validation = valid;

                              p.hidden = hidden;
                              p.classification = !(i == 0 && resp == 2);
                              p.best_model_key = best_model_key;
                              p.epochs = epochs;
                              p.seed = seed;
                              p.train_samples_per_iteration = train_samples_per_iteration;
                              p.force_load_balance = load_balance;
                              p.replicate_training_data = replicate;
                              p.shuffle_training_data = shuffle;
                              p.score_training_samples = scoretraining;
                              p.score_validation_samples = scorevalidation;
                              p.balance_classes = balance_classes;
                              p.quiet_mode = true;
//                              p.quiet_mode = false;
                              p.score_validation_sampling = csm;
                              p.exec();
                            }

                            // Do some more training via checkpoint restart
                            Key dest = Key.make();
                            {
                              DeepLearning p = new DeepLearning(dest);
                              final DeepLearningModel tmp_model = DKV.get(dest_tmp).get(); //this actually *requires* frame to also still be in UKV (because of DataInfo...)
                              assert(tmp_model != null);
//                              assert(!Arrays.equals(p.job_key._kb, tmp_model.get_params().job_key._kb));
                              p.checkpoint = dest_tmp;

                              p.source = frame;
                              p.validation = valid;
                              p.response = frame.vecs()[resp];

                              if (i == 0 && resp == 2) p.classification = false;
                              p.best_model_key = best_model_key;
                              p.epochs = epochs;
                              p.seed = seed;
                              p.train_samples_per_iteration = train_samples_per_iteration;
                              p.exec();
                            }
                            // score and check result (on full data)
                            final DeepLearningModel mymodel = DKV.get(dest).get(); //this actually *requires* frame to also still be in UKV (because of DataInfo...)
                            // test HTML
                            {
                              StringBuilder sb = new StringBuilder();
                              mymodel.generateHTML("test", sb);
                            }
                            if (valid == null ) valid = frame;
//                            double threshold = 0;
                            if (mymodel.isClassifier()) {
                              Frame pred = mymodel.score(valid);
                              StringBuilder sb = new StringBuilder();

                              AUC auc = new AUC();
//                              double error = 0;
                              // binary
                              if (mymodel.nclasses()==2) {
                                auc.actual = valid;
                                auc.vactual = valid.vecs()[resp];
                                auc.predict = pred;
                                auc.vpredict = pred.vecs()[2];
                                auc.threshold_criterion = AUC.ThresholdCriterion.maximum_F1;
                                auc.execImpl();
                                auc.toASCII(sb);
//                                threshold = auc.threshold();
                                double error = auc.data().err();
                                Log.info(sb);

                                // check that auc.cm() is the right CM
                                Assert.assertEquals(new ConfusionMatrix2(auc.data().cm()).err(), error, 1e-15);

                                // check that calcError() is consistent as well (for CM=null, AUC!=null)
                                Assert.assertEquals(mymodel.calcError(valid, valid.lastVec(), pred, pred, "training", false, 0, null, auc, null), error, 1e-15);
                              }

                              // Compute CM
//                              double CMerrorOrig;
                              {
                                sb = new StringBuilder();
                                ConfusionMatrix CM = new ConfusionMatrix();
                                CM.actual = valid;
                                CM.vactual = valid.vecs()[resp];
                                CM.predict = pred;
                                CM.vpredict = pred.vecs()[0];
                                CM.execImpl();
                                sb.append("\n");
                                sb.append("Threshold: " + "default\n");
                                CM.toASCII(sb);
                                Log.info(sb);
//                                CMerrorOrig = new ConfusionMatrix2(CM.cm).err();
                              }

                              // confirm that orig CM was made with threshold 0.5
                              // put pred2 into UKV, and allow access
                              Frame pred2 = new Frame(Key.make("pred2"), pred.names(), pred.vecs());
                              pred2.delete_and_lock(null);
                              pred2.unlock(null);

                              //FIXME: Get Exec2 back into H2O-DEV
//                              if (mymodel.nclasses()==2) {
//                                // make labels with 0.5 threshold for binary classifier
//                                Env ev = Exec2.exec("pred2[,1]=pred2[,3]>=" + 0.5);
//                                pred2 = ev.popAry();
//                                ev.subRef(pred2, "pred2");
//                                ev.remove_and_unlock();
//
//                                water.api.ConfusionMatrix CM = new water.api.ConfusionMatrix();
//                                CM.actual = valid;
//                                CM.vactual = valid.vecs()[1];
//                                CM.predict = pred2;
//                                CM.vpredict = pred2.vecs()[0];
//                                CM.invoke();
//                                sb = new StringBuilder();
//                                sb.append("\n");
//                                sb.append("Threshold: " + 0.5 + "\n");
//                                CM.toASCII(sb);
//                                Log.info(sb);
//                                double threshErr = new ConfusionMatrix(CM.cm).err();
//                                Assert.assertEquals(threshErr, CMerrorOrig, 1e-15);
//
//                                // make labels with AUC-given threshold for best F1
//                                ev = Exec2.exec("pred2[,1]=pred2[,3]>=" + threshold);
//                                pred2 = ev.popAry();
//                                ev.subRef(pred2, "pred2");
//                                ev.remove_and_unlock();
//
//                                CM = new water.api.ConfusionMatrix();
//                                CM.actual = valid;
//                                CM.vactual = valid.vecs()[1];
//                                CM.predict = pred2;
//                                CM.vpredict = pred2.vecs()[0];
//                                CM.invoke();
//                                sb = new StringBuilder();
//                                sb.append("\n");
//                                sb.append("Threshold: " + threshold + "\n");
//                                CM.toASCII(sb);
//                                Log.info(sb);
//                                double threshErr2 = new ConfusionMatrix(CM.cm).err();
//                                Assert.assertEquals(threshErr2, error, 1e-15);
//                              }
                              pred2.delete();
                              pred.delete();
                            } //classifier

                            final boolean validation = (vf != 0); //p.validation != null -> DL scores based on validation data set (which can be the same as training data set)

                            if (mymodel.get_params().best_model_key != null) {
                              // get the actual best error on (potentially sampled) training data
                              float best_err = Float.MAX_VALUE;
                              long best_samples = 0;
                              for (DeepLearningModel.Errors err : mymodel.scoring_history()) {
                                float e;
                                if (mymodel.isClassifier()) {
                                  e = (float) (validation ? err.valid_err : err.train_err);
                                } else {
                                  e = (float) (validation ? err.valid_mse : err.train_mse);
                                }
                                if (e < best_err) {
                                  best_err = e;
                                  best_samples = err.training_samples;
                                }
                              }
                              Log.info("Actual best error : " + best_err + ".");
                              Log.info("Actual best training samples : " + best_samples + ".");

                              // get the error reported by the stored best model
                              DeepLearningModel bestmodel = DKV.get(mymodel.get_params().best_model_key).get();
//                              Log.info("Best model\n" + bestmodel.toString());
                              Frame bestPredict = bestmodel.score(valid);
                              double bestErr = bestmodel.calcError(valid, valid.vecs()[resp], bestPredict, bestPredict, validation ? "validation" : "training",
                                      true, bestmodel.get_params().max_confusion_matrix_size, new ConfusionMatrix(),
                                      bestmodel.nclasses() == 2 ? new AUC() : null, null); //presence of AUC object allows optimal threshold to be used for bestErr calculation

                              Log.info("Validation: " + validation);
                              Log.info("Best_model's samples : " + bestmodel.model_info().get_processed_total() + ".");
                              Log.info("Best_model's error : " + bestErr + ".");
                              Assert.assertEquals(bestmodel.model_info().get_processed_total(), best_samples); //check that the model was saved at the moment with the lowest error

                              if ((!validation || (scorevalidation == 0 && csm == DeepLearning.ClassSamplingMethod.Uniform))
                                      && (validation || (scoretraining == 0 && !balance_classes))) {
                                Assert.assertEquals(bestErr, best_err, 1e-5);
                              }

                              // clean up
                              bestmodel.delete();
                              bestPredict.delete();
                            }
                            mymodel.delete();
                            Keyed.remove(dest);
                            Keyed.remove(dest_tmp);
                            Log.info("Parameters combination " + count + ": PASS");
                            testcount++;
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

      frame.delete();
      vframe.delete();
    }
    Log.info("\n\n=============================================");
    Log.info("Tested " + testcount + " out of " + count + " parameter combinations.");
    Log.info("=============================================");
  }

  public static class Long extends DeepLearningProstateTest {
    @Test @Ignore public void run() throws Exception { runFraction(0.1f); }
  }

  public static class Short extends DeepLearningProstateTest {
    @Test @Ignore public void run() throws Exception { runFraction(0.0003f); }
  }
}
