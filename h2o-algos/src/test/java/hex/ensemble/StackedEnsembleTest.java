package hex.ensemble;

import hex.*;
import hex.ensemble.Metalearner.Algorithm;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class StackedEnsembleTest extends TestUtil {

  @Rule
  public transient ExpectedException expectedException = ExpectedException.none();

    private abstract class PrepData { abstract int prep(Frame fr); }

    static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn",
            "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
            "LateAircraftDelay", "IsDepDelayed"};

    @Test public void testBasicEnsembleAUTOMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return fr.find("economy (mpg)"); }},
            false, DistributionFamily.gaussian, Algorithm.AUTO, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.AUTO, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, Algorithm.AUTO, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.AUTO, false);
    }


    @Test public void testBasicEnsembleGBMMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, Algorithm.gbm, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.gbm, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, Algorithm.gbm, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.gbm, false);
    }

    @Test public void testBasicEnsembleDRFMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, Algorithm.drf, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.drf, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, Algorithm.drf, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.drf, false);
    }

    @Test public void testBasicEnsembleDeepLearningMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, Algorithm.deeplearning, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.deeplearning, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, Algorithm.deeplearning, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.deeplearning, false);
    }

    

    @Test public void testBasicEnsembleGLMMetalearner() {

        // Regression tests
        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, Algorithm.glm, false);

        // Binomial tests
        basicEnsemble("./smalldata/junit/test_tree_minmax.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("response"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.glm, false);

        basicEnsemble("./smalldata/logreg/prostate.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.glm, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.glm, false);

        basicEnsemble("./smalldata/gbm_test/alphabet_cattest.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("y"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.glm, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, Algorithm.glm, false);

        // Multinomial tests
        basicEnsemble("./smalldata/logreg/prostate.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("RACE"); }
                },
                false, DistributionFamily.multinomial, Algorithm.glm, false);

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("name").remove(); return fr.find("cylinders"); }
                },
                false, DistributionFamily.multinomial, Algorithm.glm, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, Algorithm.glm, false);

    }
    
    
    public static class Pubdev6157MRTask extends MRTask{
      
      private final int nclasses;

      Pubdev6157MRTask(int nclasses) {
        this.nclasses = nclasses;
      }

      @Override
      public void map(Chunk[] cs, NewChunk[] ncs) {
        Random r = new Random();
        NewChunk predictor = ncs[0];
        NewChunk response = ncs[1];
        for (int i = 0; i < cs[0]._len; i++) {
          long rowNum = (cs[0].start() + i);
          predictor.addNum(r.nextDouble()); // noise
          long respValue;
          if (rowNum % 2 == 0) {
            respValue = nclasses - 1;
          } else {
            respValue = rowNum % nclasses;
          }
          response.addNum(respValue); // more than 50% rows have last class as the response value
        }
      }
    }

    @Test
    public void testPubDev6157() {
        try {
            Scope.enter();

            // 1. Create synthetic training frame and train multinomial GLM
            Vec v = Vec.makeConN((long) 1e5, H2O.ARGS.nthreads * 4);
            Scope.track(v);
            final int nclasses = 4; // #of response classes in iris_wheader + 1
            
            byte[] types = new byte[]{Vec.T_NUM, Vec.T_CAT};
            String[][] domains = new String[types.length][];
            domains[domains.length - 1] = new String[nclasses];
            for (int i = 0; i < nclasses; i++)
                domains[domains.length - 1][i] = "Level" + i; 
            
            final Frame training = new Pubdev6157MRTask(nclasses)
                    .doAll(types, v).outputFrame(Key.<Frame>make(), null, domains);
            Scope.track(training);

            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = training._key;
            parms._response_column = training.lastVecName();
            parms._family = GLMModel.GLMParameters.Family.multinomial;
            parms._max_iterations = 1;
            parms._seed = 42;
            parms._auto_rebalance = false;

            GLM glm = new GLM(parms);
            final GLMModel model = glm.trainModelOnH2ONode().get();
            Scope.track_generic(model);
            assertNotNull(model);

            final Job j = new Job<>(Key.make(), parms.javaName(), parms.algoName());
            j.start(new H2O.H2OCountedCompleter() {
                @Override
                public void compute2() {
                    GLMHelper.runBigScore(model, training, false, false, j);
                    tryComplete();
                }
            }, 1).get();

            // 2. Train multinomial Stacked Ensembles with GLM metalearner - it should not crash 
            basicEnsemble("./smalldata/iris/iris_wheader.csv",
                    null,
                    new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                    },
                    false, DistributionFamily.multinomial, Algorithm.glm, false);
        } finally {
            Scope.exit();
        }
    }

    @Test public void testBlending() {
        basicEnsemble("./smalldata/junit/cars.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return fr.find("economy (mpg)"); }},
            false, DistributionFamily.gaussian, Algorithm.AUTO, true);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                for( String s : ignored_aircols ) fr.remove(s).remove();
                return fr.find("IsArrDelayed"); }
            },
            false, DistributionFamily.bernoulli, Algorithm.AUTO, true);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
            },
            false, DistributionFamily.multinomial, Algorithm.AUTO, true);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
            "./smalldata/logreg/prostate_test.csv",
            new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
            },
            false, DistributionFamily.bernoulli, Algorithm.AUTO, true);
    }

    @Test
    public void testBaseModelPredictionsCaching() {
      Grid grid = null;
      List<StackedEnsembleModel> seModels = new ArrayList<>();
      List<Frame> frames = new ArrayList<>();
      try {
        Scope.enter();
        long seed = 6 << 6 << 6;
        Frame train = parseTestFile("./smalldata/junit/cars.csv");
        String target = "economy (mpg)";
        Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(
            train,
            new Key[]{
                Key.make(train._key + "_train"),
                Key.make(train._key + "_blending"),
                Key.make(train._key + "_valid"),
            },
            new double[]{0.5, 0.3, 0.2},
            seed);
        train.remove();
        train = splits[0];
        Frame blend = splits[1];
        Frame valid = splits[2];
        frames.addAll(Arrays.asList(train, blend, valid));

        //generate a few base models
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._distribution = DistributionFamily.gaussian;
        params._train = train._key;
        params._response_column = target;
        params._seed = seed;
        Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
          put("_ntrees", new Integer[]{3, 5});
          put("_learn_rate", new Double[]{0.1, 0.2});
        }});
        grid = gridSearch.get();
        Model[] models = grid.getModels();
        Assert.assertEquals(4, models.length);

        StackedEnsembleParameters seParams = new StackedEnsembleParameters();
        seParams._train = train._key;
        seParams._blending = blend._key;
        seParams._response_column = target;
        seParams._base_models = grid.getModelKeys();
        seParams._seed = seed;
        //running a first blending SE without keeping predictions
        seParams._keep_base_model_predictions = false;
        StackedEnsemble seJob = new StackedEnsemble(seParams);
        StackedEnsembleModel seModel = seJob.trainModel().get();
        seModels.add(seModel);
        Assert.assertNull(seModel._output._base_model_predictions_keys);

        //running another SE, but this time caching predictions
        seParams._keep_base_model_predictions = true;
        seJob = new StackedEnsemble(seParams);
        seModel = seJob.trainModel().get();
        seModels.add(seModel);
        Assert.assertNotNull(seModel._output._base_model_predictions_keys);
        Assert.assertEquals(models.length, seModel._output._base_model_predictions_keys.length);

        Key<Frame>[] first_se_pred_keys = seModel._output._base_model_predictions_keys;
        for (Key k: first_se_pred_keys) {
          Assert.assertNotNull("prediction key is not stored in DKV", DKV.get(k));
        }

        //running again another SE, caching predictions, and checking that no new prediction is created
        seParams._keep_base_model_predictions = true;
        seJob = new StackedEnsemble(seParams);
        seModel = seJob.trainModel().get();
        seModels.add(seModel);
        Assert.assertNotNull(seModel._output._base_model_predictions_keys);
        Assert.assertEquals(models.length, seModel._output._base_model_predictions_keys.length);
        Assert.assertArrayEquals(first_se_pred_keys, seModel._output._base_model_predictions_keys);

        //running a last SE, with validation frame, and check that new predictions are added
        seParams._keep_base_model_predictions = true;
        seParams._valid = valid._key;
        seJob = new StackedEnsemble(seParams);
        seModel = seJob.trainModel().get();
        seModels.add(seModel);
        Assert.assertNotNull(seModel._output._base_model_predictions_keys);
        Assert.assertEquals(models.length * 2, seModel._output._base_model_predictions_keys.length);
        for (Key<Frame> first_pred_key: first_se_pred_keys) {
          Assert.assertTrue(ArrayUtils.contains(seModel._output._base_model_predictions_keys, first_pred_key));
        }

        seModel.deleteBaseModelPredictions();
        Assert.assertNull(seModel._output._base_model_predictions_keys);
        for (Key k: first_se_pred_keys) {
          Assert.assertNull(DKV.get(k));
        }
      } finally {
        Scope.exit();
        if (grid != null) {
          grid.delete();
        }
        for (Model m: seModels) m.delete();
        for (Frame f: frames) f.remove();
      }
    }

    @Test
    public void test_SE_scoring_with_blending() {
      List<Lockable> deletables = new ArrayList<>();
      try {
        final int seed = 62832;
        final Frame fr = parseTestFile("./smalldata/logreg/prostate_train.csv"); deletables.add(fr);
        final Frame test = parseTestFile("./smalldata/logreg/prostate_test.csv"); deletables.add(test);

        final String target = "CAPSULE";
        int tidx = fr.find(target);
        fr.replace(tidx, fr.vec(tidx).toCategoricalVec()).remove(); DKV.put(fr);
        test.replace(tidx, test.vec(tidx).toCategoricalVec()).remove(); DKV.put(test);

        SplitFrame sf = new SplitFrame(fr, new double[] { 0.7, 0.3 }, null);
        sf.exec().get();
        Key<Frame>[] ksplits = sf._destination_frames;
        final Frame train = ksplits[0].get(); deletables.add(train);
        final Frame blending = ksplits[1].get(); deletables.add(blending);

        //generate a few base models
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._train = train._key;
        params._response_column = target;
        params._seed = seed;

        Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
          put("_ntrees", new Integer[]{3, 5});
          put("_learn_rate", new Double[]{0.1, 0.2});
        }});
        Grid grid = gridSearch.get(); deletables.add(grid);
        Model[] gridModels = grid.getModels(); deletables.addAll(Arrays.asList(gridModels));
        Assert.assertEquals(4, gridModels.length);

        StackedEnsembleParameters seParams = new StackedEnsembleParameters();
        seParams._train = train._key;
        seParams._blending = blending._key;
        seParams._response_column = target;
        seParams._base_models = grid.getModelKeys();
        seParams._seed = seed;
        StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get(); deletables.add(se);

        // mainly ensuring that no exception is thrown due to unmet categorical in test dataset.
        Frame predictions = se.score(test); deletables.add(predictions);
        Assert.assertEquals(2+1, predictions.numCols()); // binomial: 2 probabilities + 1 response prediction
        Assert.assertEquals(test.numRows(), predictions.numRows());
      } finally {
        for (Lockable l: deletables) {
          if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
          l.delete();
        }
      }
    }

    @Test
    public void test_SE_with_GLM_can_do_predictions_on_frames_with_unseen_categorical_values() {
      // test for PUBDEV-6266
      List<Lockable> deletables = new ArrayList<>();
      try {
        final int seed = 62832;
        final Frame train = parseTestFile("./smalldata/testng/cars_train.csv"); deletables.add(train);
        final Frame test = parseTestFile("./smalldata/testng/cars_test.csv"); deletables.add(test);
        final String target = "economy (mpg)";
        int cyl_idx = test.find("cylinders");
        Assert.assertTrue(test.vec(cyl_idx).isInt());
        Vec cyl_test = test.vec(cyl_idx);
        cyl_test.set(cyl_test.length() - 1, 7); // that's a new engine concept
        test.replace(cyl_idx, cyl_test.toCategoricalVec()).remove();
        DKV.put(test);
        Assert.assertTrue(test.vec(cyl_idx).isCategorical());
        train.replace(cyl_idx, train.vec(cyl_idx).toCategoricalVec()).remove();
        DKV.put(train);

        //generate a few base models
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._train = train._key;
        params._response_column = target;
        params._seed = seed;
        params._keep_cross_validation_models = false;
        params._keep_cross_validation_predictions = true;
        params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
        params._nfolds = 5;

        Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
          put("_ntrees", new Integer[]{3, 5});
          put("_learn_rate", new Double[]{0.1, 0.2});
        }});
        Grid grid = gridSearch.get(); deletables.add(grid);
        Model[] gridModels = grid.getModels(); deletables.addAll(Arrays.asList(gridModels));
        Assert.assertEquals(4, gridModels.length);

        GLMModel.GLMParameters glm_params = new GLMModel.GLMParameters();
        glm_params._train = train._key;
        glm_params._response_column = target;
        glm_params._seed = seed;
        glm_params._keep_cross_validation_models = false;
        glm_params._keep_cross_validation_predictions = true;
        glm_params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
        glm_params._nfolds = 5;
        glm_params._alpha = new double[]{0.1, 0.2, 0.4};
        glm_params._lambda_search = true;
        GLMModel glm = new GLM(glm_params).trainModel().get(); deletables.add(glm);

        StackedEnsembleParameters seParams = new StackedEnsembleParameters();
        seParams._train = train._key;
        seParams._response_column = target;
        seParams._base_models = ArrayUtils.append(grid.getModelKeys(), (Key) glm._key);
        seParams._seed = seed;
        StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get(); deletables.add(se);

        // mainly ensuring that no exception is thrown due to unmet categorical in test dataset.
        Frame predictions = se.score(test); deletables.add(predictions);
        Assert.assertTrue(predictions.vec(0).at(cyl_test.length() - 1) > 0);
      } finally {
        for (Lockable l: deletables) {
          if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
          l.delete();
        }
      }
    }

    @Test public void testKeepLevelOneFrameCVMode() {
        testSEModelCanBeSafelyRemoved(true, false);
    }

    @Test public void testDoNotKeepLevelOneFrameCVMode() {
        testSEModelCanBeSafelyRemoved(false, false);
    }

    @Test public void testKeepLevelOneFrameBlendingMode() {
        testSEModelCanBeSafelyRemoved(true, true);
    }

    @Test public void testDoNotKeepLevelOneFrameBlendingMode() {
        testSEModelCanBeSafelyRemoved(false, true);
    }

    private void testSEModelCanBeSafelyRemoved(boolean keepLevelOneFrame, boolean blending) {
        List<Lockable> deletables = new ArrayList<>();
        try {
            final int seed = 1;
            final Frame train = parseTestFile("./smalldata/logreg/prostate_train.csv"); deletables.add(train);
            final Frame test = parseTestFile("./smalldata/logreg/prostate_test.csv"); deletables.add(test);

            final String target = "CAPSULE";
            int tidx = train.find(target);
            train.replace(tidx, train.vec(tidx).toCategoricalVec()).remove(); DKV.put(train);
            test.replace(tidx, test.vec(tidx).toCategoricalVec()).remove(); DKV.put(test);

            //generate a few base models
            GBMModel.GBMParameters params = new GBMModel.GBMParameters();
            params._train = train._key;
            params._response_column = target;
            params._seed = seed;
            if (!blending) {
                params._nfolds = 3;
                params._keep_cross_validation_models = false;
                params._keep_cross_validation_predictions = true;
                params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            }

            Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
                put("_ntrees", new Integer[]{3, 5});
                put("_learn_rate", new Double[]{0.1, 0.2});
            }});
            Grid grid = gridSearch.get(); deletables.add(grid);
            Model[] gridModels = grid.getModels(); deletables.addAll(Arrays.asList(gridModels));
            Assert.assertEquals(4, gridModels.length);

            StackedEnsembleModel.StackedEnsembleParameters seParams = new StackedEnsembleModel.StackedEnsembleParameters();
            seParams._train = train._key;
            seParams._response_column = target;
            seParams._base_models = grid.getModelKeys();
            seParams._seed = seed;
            seParams._keep_levelone_frame = keepLevelOneFrame;
            if (blending) seParams._blending = test._key;
            StackedEnsembleModel se1 = new StackedEnsemble(seParams).trainModel().get(); deletables.add(se1);
            Frame predictions = se1.score(test); deletables.add(predictions);

            if (keepLevelOneFrame) {
                Assert.assertEquals(gridModels.length + 1, se1._output._levelone_frame_id.numCols());
                if (blending) {
                    Assert.assertEquals(test.numRows(), se1._output._levelone_frame_id.numRows());
                    TestUtil.assertBitIdentical(new Frame(test.vec(target)), new Frame(se1._output._levelone_frame_id.vec(target)));
                } else {
                    Assert.assertEquals(train.numRows(), se1._output._levelone_frame_id.numRows());
                    TestUtil.assertBitIdentical(new Frame(train.vec(target)), new Frame(se1._output._levelone_frame_id.vec(target)));
                }
            } else {
                Assert.assertNull(se1._output._levelone_frame_id);
            }
            se1.delete();

            // building a new model would throw an exception if we deleted too much when deleting s1
            GBMModel gbm = new GBM(params).trainModel().get(); deletables.add(gbm);
            StackedEnsembleModel se2 = new StackedEnsemble(seParams).trainModel().get(); deletables.add(se2);
        } finally {
            for (Lockable l: deletables) {
                if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
                l.delete();
            }
        }
    }

    // ==========================================================================
    public StackedEnsembleModel.StackedEnsembleOutput basicEnsemble(String training_file,
                                                                    String validation_file,
                                                                    StackedEnsembleTest.PrepData prep,
                                                                    boolean dupeTrainingFrameToValidationFrame,
                                                                    DistributionFamily family,
                                                                    Algorithm metalearner_algo,
                                                                    boolean blending_mode) {
        Set<Frame> framesBefore = new HashSet<>();
        framesBefore.addAll(Arrays.asList( Frame.fetchAll()));

        GBMModel gbm = null;
        DRFModel drf = null;
        StackedEnsembleModel stackedEnsembleModel = null;
        Frame training_frame = null, validation_frame = null, blending_frame = null;
        Frame preds = null;
        long seed = 42*42;
        try {
            Scope.enter();
            training_frame = parseTestFile(training_file);
            if (null != validation_file)
                validation_frame = parseTestFile(validation_file);

            int idx = prep.prep(training_frame); // hack frame per-test
            if (null != validation_frame)
                prep.prep(validation_frame);

            if (blending_mode) {
                Frame[] splits = ShuffleSplitFrame.shuffleSplitFrame(
                    training_frame,
                    new Key[]{Key.make(training_frame._key + "_train"), Key.make(training_frame._key + "_blending")},
                    new double[] {0.6, 0.4},
                    seed);
                training_frame.remove();
                training_frame = splits[0];
                blending_frame = splits[1];
            }
            if (family == DistributionFamily.bernoulli || family == DistributionFamily.multinomial || family == DistributionFamily.modified_huber) {
                if (!training_frame.vecs()[idx].isCategorical()) {
                    Scope.track(training_frame.replace(idx, training_frame.vecs()[idx].toCategoricalVec()));
                    if (null != validation_frame)
                        Scope.track(validation_frame.replace(idx, validation_frame.vecs()[idx].toCategoricalVec()));
                    if (null != blending_frame)
                        Scope.track(blending_frame.replace(idx, blending_frame.vecs()[idx].toCategoricalVec()));
                }
            }

            DKV.put(training_frame); // Update frames after preparing
            if (null != validation_frame)
                DKV.put(validation_frame);
            if (null != blending_frame)
                DKV.put(blending_frame);

            // Build GBM
            GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
            // Configure GBM
            gbmParameters._train = training_frame._key;
            gbmParameters._valid = (validation_frame == null ? null : validation_frame._key);
            gbmParameters._response_column = training_frame._names[idx];
            gbmParameters._ntrees = 5;
            gbmParameters._distribution = family;
            gbmParameters._max_depth = 4;
            gbmParameters._min_rows = 1;
            gbmParameters._nbins = 50;
            gbmParameters._learn_rate = .2f;
            gbmParameters._score_each_iteration = true;
            gbmParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            gbmParameters._keep_cross_validation_predictions = true;
            gbmParameters._nfolds = 5;
            gbmParameters._seed = seed;
            if( dupeTrainingFrameToValidationFrame ) {        // Make a validation frame that's a clone of the training data
                validation_frame = new Frame(training_frame);
                DKV.put(validation_frame);
                gbmParameters._valid = validation_frame._key;
            }
            // Invoke GBM and block till the end
            GBM gbmJob = new GBM(gbmParameters);
            // Get the model
            gbm = gbmJob.trainModel().get();
            Assert.assertTrue(gbmJob.isStopped()); //HEX-1817

            // Build DRF
            DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
            // Configure DRF
            drfParameters._train = training_frame._key;
            drfParameters._valid = (validation_frame == null ? null : validation_frame._key);
            drfParameters._response_column = training_frame._names[idx];
            drfParameters._distribution = family;
            drfParameters._ntrees = 5;
            drfParameters._max_depth = 4;
            drfParameters._min_rows = 1;
            drfParameters._nbins = 50;
            drfParameters._score_each_iteration = true;
            drfParameters._seed = seed;
            if (!blending_mode) {
                drfParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
                drfParameters._keep_cross_validation_predictions = true;
                drfParameters._nfolds = 5;
            }
            // Invoke DRF and block till the end
            DRF drfJob = new DRF(drfParameters);
            // Get the model
            drf = drfJob.trainModel().get();
            Assert.assertTrue(drfJob.isStopped()); //HEX-1817

            // Build Stacked Ensemble of previous GBM and DRF
            StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleParameters();
            // Configure Stacked Ensemble
            stackedEnsembleParameters._train = training_frame._key;
            stackedEnsembleParameters._valid = (validation_frame == null ? null : validation_frame._key);
            stackedEnsembleParameters._blending = blending_frame == null ? null : blending_frame._key;
            stackedEnsembleParameters._response_column = training_frame._names[idx];
            stackedEnsembleParameters._metalearner_algorithm = metalearner_algo;
            stackedEnsembleParameters._base_models = new Key[] {gbm._key, drf._key};
            stackedEnsembleParameters._seed = seed;
            stackedEnsembleParameters._score_training_samples = 0; // don't subsample dataset for training metrics so we don't randomly fail the test
            stackedEnsembleParameters._auc_type = MultinomialAucType.MACRO_OVO;
            // Invoke Stacked Ensemble and block till end
            StackedEnsemble stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters);
            // Get the stacked ensemble
            stackedEnsembleModel = stackedEnsembleJob.trainModel().get();

            Frame training_clone = new Frame(training_frame);
            DKV.put(training_clone);
            Scope.track(training_clone);
            preds = stackedEnsembleModel.score(training_clone);
            final boolean predsTheSame = stackedEnsembleModel.testJavaScoring(training_clone, preds, 1e-15, 0.01);
            Assert.assertTrue(predsTheSame);
            Assert.assertTrue(stackedEnsembleJob.isStopped());

            ModelMetrics training_metrics = stackedEnsembleModel._output._training_metrics;
            ModelMetrics training_clone_metrics = ModelMetrics.getFromDKV(stackedEnsembleModel, training_clone);
            Assert.assertEquals(training_metrics.mse(), training_clone_metrics.mse(), 1e-15);
            training_clone.remove();

            if (validation_frame != null) {
                ModelMetrics validation_metrics = stackedEnsembleModel._output._validation_metrics;
                Frame validation_clone = new Frame(validation_frame);
                DKV.put(validation_clone);
                Scope.track(validation_clone);
                stackedEnsembleModel.score(validation_clone).remove();
                ModelMetrics validation_clone_metrics = ModelMetrics.getFromDKV(stackedEnsembleModel, validation_clone);
                Assert.assertEquals(validation_metrics.mse(), validation_clone_metrics.mse(), 1e-15);
                validation_clone.remove();
            }

            return stackedEnsembleModel._output;

        } finally {
            if( training_frame  != null ) training_frame.remove();
            if( validation_frame != null ) validation_frame.remove();
            if (blending_frame != null) blending_frame.remove();
            if( gbm != null ) {
                gbm.delete();
                for (Key k : gbm._output._cross_validation_predictions) Keyed.remove(k);
                Keyed.remove(gbm._output._cross_validation_holdout_predictions_frame_id);
                gbm.deleteCrossValidationModels();
            }
            if( drf != null ) {
                drf.delete();
                if (!blending_mode) {
                    for (Key k : drf._output._cross_validation_predictions) Keyed.remove(k);
                    Keyed.remove(drf._output._cross_validation_holdout_predictions_frame_id);
                    drf.deleteCrossValidationModels();
                }
            }

            if (preds != null) preds.delete();

            Set<Frame> framesAfter = new HashSet<>(framesBefore);
            framesAfter.removeAll(Arrays.asList( Frame.fetchAll()));

            Assert.assertEquals("finish with the same number of Frames as we started: " + framesAfter, 0, framesAfter.size());

            if( stackedEnsembleModel != null ) {
                stackedEnsembleModel.delete();
                stackedEnsembleModel._output._metalearner.delete();
            }
            Scope.exit();
        }
    }


    @Test public void test_SE_scoring_with_missing_response_column() {
        for (Algorithm algo: Algorithm.values()) {
            if (algo == Algorithm.xgboost) continue; // skipping XGBoost: not in UTs classpath.
            try {
                test_SE_scoring_with_missing_response_column(algo);
            } catch (Exception e) {
                Log.err(e);
                Assert.fail("StackedEnsemble scoring failed with algo "+algo+": "+e.getMessage());
            }
        }
    }

    private void test_SE_scoring_with_missing_response_column(Algorithm algo) {
        // test for PUBDEV-6376
        List<Lockable> deletables = new ArrayList<>();
        try {
            final int seed = 1;
            final Frame train = parseTestFile("./smalldata/testng/prostate_train.csv"); deletables.add(train);
            final Frame test = parseTestFile("./smalldata/testng/prostate_test.csv"); deletables.add(test);
            final String target = "CAPSULE";
            int target_idx = train.find(target);
            train.replace(target_idx, train.vec(target_idx).toCategoricalVec()).remove();
            DKV.put(train);
            test.remove(target_idx).remove();
            DKV.put(test);

            //generate a few base models
            GBMModel.GBMParameters params = new GBMModel.GBMParameters();
            params._train = train._key;
            params._response_column = target;
            params._seed = seed;
            params._keep_cross_validation_models = false;
            params._keep_cross_validation_predictions = true;
            params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            params._nfolds = 5;

            Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
                put("_ntrees", new Integer[]{3, 5});
                put("_learn_rate", new Double[]{0.1, 0.2});
            }});
            Grid grid = gridSearch.get(); deletables.add(grid);
            Model[] gridModels = grid.getModels(); deletables.addAll(Arrays.asList(gridModels));
            Assert.assertEquals(4, gridModels.length);

            GLMModel.GLMParameters glm_params = new GLMModel.GLMParameters();
            glm_params._train = train._key;
            glm_params._response_column = target;
            glm_params._family = GLMModel.GLMParameters.Family.binomial;
            glm_params._seed = seed;
            glm_params._keep_cross_validation_models = false;
            glm_params._keep_cross_validation_predictions = true;
            glm_params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            glm_params._nfolds = 5;
            glm_params._alpha = new double[]{0.1, 0.2, 0.4};
            glm_params._lambda_search = true;
            GLMModel glm = new GLM(glm_params).trainModel().get(); deletables.add(glm);
            Frame glmPredictions = glm.score(test); deletables.add(glmPredictions);

            StackedEnsembleParameters seParams = new StackedEnsembleParameters();
            seParams._train = train._key;
            seParams._response_column = target;
            seParams._base_models = ArrayUtils.append(grid.getModelKeys());
            seParams._metalearner_algorithm = algo;
            seParams._seed = seed;
            StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get(); deletables.add(se);

            // mainly ensuring that no exception is thrown due to unmet categorical in test dataset.
            Frame predictions = se.score(test); deletables.add(predictions);
            List<Double> allowedResponses = Arrays.asList(0.0, 1.0);
            double lastPrediction = predictions.vec(0).at(test.vec(0).length() - 1);
            Assert.assertTrue(allowedResponses.indexOf(lastPrediction) >= 0);
        } finally {
            for (Lockable l: deletables) {
                if (l instanceof Model) ((Model)l).deleteCrossValidationPreds();
                l.delete();
            }
        }

    }

  @Test
  public void testMissingFoldColumn_trainingFrame() {
    GBMModel gbmModel = null;
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);
      final Frame partialFrame = parseTestFile("./smalldata/iris/iris_wheader.csv", new int[]{4}); // Missing fold column
      Scope.track(partialFrame);

      GBMModel.GBMParameters parameters = new GBMModel.GBMParameters();
      parameters._train = trainingFrame._key;
      parameters._fold_column = "class";
      parameters._seed = 0xFEED;
      parameters._response_column = "petal_len";
      parameters._ntrees = 1;
      parameters._keep_cross_validation_predictions = true;

      GBM gbm = new GBM(parameters);
      gbmModel = gbm.trainModel().get();
      assertNotNull(gbmModel);
      

      final StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = partialFrame._key;
      seParams._response_column = "petal_len";
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._base_models = new Key[]{gbmModel._key};
      seParams._seed = 0xFEED;
      seParams._metalearner_fold_column = "class";

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Specified fold column 'class' not found in one of the supplied data frames. Available column names are: [sepal_len, sepal_wid, petal_wid, petal_len]");
      final StackedEnsemble stackedEnsemble = new StackedEnsemble(seParams);
      fail("Expected the Stack Ensemble Model never to be initialized successfully.");

    } finally {
      Scope.exit();
      
     
      if(gbmModel != null){
        gbmModel.deleteCrossValidationModels();
        gbmModel.deleteCrossValidationPreds();
        gbmModel.remove();
      }
    }
  }

  @Test
  public void testMissingFoldColumn_validationFrame() {
    GBMModel gbmModel = null;
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);
      final Frame partialFrame = parseTestFile("./smalldata/iris/iris_wheader.csv", new int[]{4}); // Missing fold column
      Scope.track(partialFrame);

      GBMModel.GBMParameters parameters = new GBMModel.GBMParameters();
      parameters._train = trainingFrame._key;
      parameters._valid = trainingFrame._key;
      parameters._fold_column = "class";
      parameters._seed = 0xFEED;
      parameters._response_column = "petal_len";
      parameters._ntrees = 1;
      parameters._keep_cross_validation_predictions = true;

      GBM gbm = new GBM(parameters);
      gbmModel = gbm.trainModel().get();
      assertNotNull(gbmModel);


      final StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = trainingFrame._key;
      seParams._valid = partialFrame._key;
      seParams._response_column = "petal_len";
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._base_models = new Key[]{gbmModel._key};
      seParams._seed = 0xFEED;
      seParams._metalearner_fold_column = "class";

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Specified fold column 'class' not found in one of the supplied data frames. Available column names are: [sepal_len, sepal_wid, petal_len, petal_wid]");
      final StackedEnsemble stackedEnsemble = new StackedEnsemble(seParams);
      fail("Expected the Stack Ensemble Model never to be initialized successfully.");

    } finally {
      Scope.exit();


      if(gbmModel != null){
        gbmModel.deleteCrossValidationModels();
        gbmModel.deleteCrossValidationPreds();
        gbmModel.remove();
      }
    }
  }

  @Test
  public void testMissingFoldColumn_blendingFrame() {
    GBMModel gbmModel = null;
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);
      final Frame partialFrame = parseTestFile("./smalldata/iris/iris_wheader.csv", new int[]{4}); // Missing fold column
      Scope.track(partialFrame);

      GBMModel.GBMParameters parameters = new GBMModel.GBMParameters();
      parameters._train = trainingFrame._key;
      parameters._fold_column = "class";
      parameters._seed = 0xFEED;
      parameters._response_column = "petal_len";
      parameters._ntrees = 1;
      parameters._keep_cross_validation_predictions = true;

      GBM gbm = new GBM(parameters);
      gbmModel = gbm.trainModel().get();
      assertNotNull(gbmModel);


      final StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = trainingFrame._key;
      seParams._blending = partialFrame._key;
      seParams._response_column = "petal_len";
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._base_models = new Key[]{gbmModel._key};
      seParams._seed = 0xFEED;
      seParams._metalearner_fold_column = "class";

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Specified fold column 'class' not found in one of the supplied data frames. Available column names are: [sepal_len, sepal_wid, petal_len, petal_wid]");
      final StackedEnsemble stackedEnsemble = new StackedEnsemble(seParams);
      fail("Expected the Stack Ensemble Model never to be initialized successfully.");

    } finally {
      Scope.exit();


      if(gbmModel != null){
        gbmModel.deleteCrossValidationModels();
        gbmModel.deleteCrossValidationPreds();
        gbmModel.remove();
      }
    }
  }

  @Test
  public void testBaseModelsWorkWithGrid() {
    GBMModel gbmModel = null;
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/junit/weather.csv");
      Scope.track(trainingFrame);
      trainingFrame.toCategoricalCol("RainTomorrow");

      GBMModel.GBMParameters parameters = new GBMModel.GBMParameters();
      parameters._train = trainingFrame._key;
      parameters._seed = 0xFEED;
      parameters._response_column = "RainTomorrow";
      parameters._ntrees = 1;
      parameters._keep_cross_validation_predictions = true;

      GBM gbm = new GBM(parameters);
      gbmModel = gbm.trainModel().get();
      assertNotNull(gbmModel);

      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.bernoulli});
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "RainTomorrow";
      params._seed = 42;

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      final StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = trainingFrame._key;
      seParams._response_column = "RainTomorrow";
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._base_models = new Key[]{gbmModel._key, grid._key};
      seParams._seed = 0xFEED;

      final StackedEnsemble stackedEnsemble = new StackedEnsemble(seParams);
      List<Key> expectedBaseModels = new ArrayList<Key>();
      expectedBaseModels.add(gbmModel._key);
      Collections.addAll(expectedBaseModels, grid.getModelKeys());

      assertArrayEquals(expectedBaseModels.toArray(new Key[0]), stackedEnsemble._parms._base_models);
    } finally {
      Scope.exit();

      if(gbmModel != null){
        gbmModel.deleteCrossValidationModels();
        gbmModel.deleteCrossValidationPreds();
        gbmModel.remove();
      }
    }
  }

  @Test
  public void testCanInferFromBaseModelsDistribution() {
    List<Lockable> deletables = new ArrayList<>();
    try {
      final int seed = 1;
      final Frame train = parseTestFile("./smalldata/testng/prostate_train.csv");
      deletables.add(train);
      final Frame test = parseTestFile("./smalldata/testng/prostate_test.csv");
      deletables.add(test);
      final String target = "CAPSULE";
      int target_idx = train.find(target);
      train.replace(target_idx, train.vec(target_idx).toCategoricalVec()).remove();
      DKV.put(train);
      test.remove(target_idx).remove();
      DKV.put(test);

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = train._key;
      params._response_column = target;
      params._seed = seed;
      params._keep_cross_validation_models = false;
      params._keep_cross_validation_predictions = true;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
      params._nfolds = 3;
      params._distribution = DistributionFamily.bernoulli;

      Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
        put("_ntrees", new Integer[]{3, 5});
      }});
      Grid grid = gridSearch.get();
      deletables.add(grid);
      Model[] gridModels = grid.getModels();
      deletables.addAll(Arrays.asList(gridModels));
      Assert.assertEquals(2, gridModels.length);

      StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = train._key;
      seParams._response_column = target;
      seParams._base_models = ArrayUtils.append(grid.getModelKeys());
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._seed = seed;
      StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
      deletables.add(se);

      Assert.assertTrue(((GLMModel.GLMParameters) se._output._metalearner._parms)._family == GLMModel.GLMParameters.Family.binomial);

      StackedEnsembleParameters seParams2 = new StackedEnsembleParameters();
      seParams2._train = train._key;
      seParams2._response_column = target;
      seParams2._base_models = ArrayUtils.append(grid.getModelKeys());
      seParams2._metalearner_algorithm = Algorithm.gbm;
      seParams2._seed = seed;
      StackedEnsembleModel se2 = new StackedEnsemble(seParams2).trainModel().get();
      deletables.add(se2);

      Assert.assertTrue(se2._output._metalearner._parms._distribution == DistributionFamily.bernoulli);
    } finally {
      for (Lockable l : deletables) {
        if (l instanceof Model) ((Model) l).deleteCrossValidationPreds();
        l.delete();
      }
    }
  }

  @Test
  public void testCanInferFromBaseModelsFamily() {
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.gaussian, GLMModel.GLMParameters.Link.identity, DistributionFamily.gaussian, false);
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.gamma, GLMModel.GLMParameters.Link.log, DistributionFamily.gamma, false);
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.gamma, GLMModel.GLMParameters.Link.identity, DistributionFamily.gamma, false);
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.tweedie, GLMModel.GLMParameters.Link.tweedie, DistributionFamily.tweedie, false);
  }

  @Test
  public void testCanInferFromBaseModelsMixedFamilyAndDistributions() {
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.gaussian, GLMModel.GLMParameters.Link.identity, DistributionFamily.gaussian, true);
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.gamma, GLMModel.GLMParameters.Link.log, DistributionFamily.gamma, true);
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.gamma, GLMModel.GLMParameters.Link.identity, DistributionFamily.gamma, true);
    familyAndLinkInferenceTestHelper(GLMModel.GLMParameters.Family.tweedie, GLMModel.GLMParameters.Link.tweedie, DistributionFamily.tweedie, true);
  }

  @Test
  public void testInferenceOfDistributionFallbacksToBasicDistribution() {
    List<Lockable> deletables = new ArrayList<>();
    try {
      final int seed = 1;
      final Frame train = parseTestFile("./smalldata/iris/iris_train.csv");
      deletables.add(train);
      final Frame test = parseTestFile("./smalldata/iris/iris_test.csv");
      deletables.add(test);
      final String target = "petal_wid";
      int target_idx = train.find(target);
      DKV.put(train);
      test.remove(target_idx).remove();
      DKV.put(test);

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = train._key;
      params._response_column = target;
      params._seed = seed;
      params._keep_cross_validation_models = false;
      params._keep_cross_validation_predictions = true;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
      params._nfolds = 3;
      params._distribution = DistributionFamily.tweedie;

      Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
        put("_ntrees", new Integer[]{3, 5});
      }});
      Grid grid = gridSearch.get();
      deletables.add(grid);
      Model[] gridModels = grid.getModels();
      deletables.addAll(Arrays.asList(gridModels));
      Assert.assertEquals(2, gridModels.length);

      StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = train._key;
      seParams._response_column = target;
      seParams._base_models = ArrayUtils.append(grid.getModelKeys());
      seParams._metalearner_algorithm = Algorithm.drf; // does NOT support tweedie
      seParams._seed = seed;
      StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
      deletables.add(se);

      Assert.assertTrue(se._output._metalearner._parms._distribution == DistributionFamily.gaussian);
    } finally {
      for (Lockable l : deletables) {
        if (l instanceof Model) ((Model) l).deleteCrossValidationPreds();
        l.delete();
      }
    }
  }


  private void familyAndLinkInferenceTestHelper(
          GLMModel.GLMParameters.Family family,
          GLMModel.GLMParameters.Link link,
          DistributionFamily distribution,
          boolean mixed) {
    List<Lockable> deletables = new ArrayList<>();
    try {
      final int seed = 1;
      final Frame train = parseTestFile("./smalldata/iris/iris_train.csv");
      deletables.add(train);
      final Frame test = parseTestFile("./smalldata/iris/iris_test.csv");
      deletables.add(test);
      final String target = "petal_wid";
      int target_idx = train.find(target);
      DKV.put(train);
      test.remove(target_idx).remove();
      DKV.put(test);

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._train = train._key;
      params._response_column = target;
      params._seed = seed;
      params._keep_cross_validation_models = false;
      params._keep_cross_validation_predictions = true;
      params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
      params._nfolds = 3;
      params._family = family;
      params._link = link;

      Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, new HashMap<String, Object[]>() {{
        put("_non_negative", new Boolean[]{false, true});
      }});
      Grid grid = gridSearch.get();
      deletables.add(grid);
      Model[] gridModels = grid.getModels();
      deletables.addAll(Arrays.asList(gridModels));
      Assert.assertEquals(2, gridModels.length);

      Grid gridGBM = null;
      if (mixed) {
        GBMModel.GBMParameters paramsGBM = new GBMModel.GBMParameters();
        paramsGBM._train = train._key;
        paramsGBM._response_column = target;
        paramsGBM._seed = seed;
        paramsGBM._keep_cross_validation_models = false;
        paramsGBM._keep_cross_validation_predictions = true;
        paramsGBM._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
        paramsGBM._nfolds = 3;
        paramsGBM._distribution = distribution;
        Job<Grid> gridSearchGBM = GridSearch.startGridSearch(null, paramsGBM, new HashMap<String, Object[]>() {{
          put("_ntrees", new Integer[]{3, 5});
        }});
        gridGBM = gridSearchGBM.get();
        deletables.add(gridGBM);
        Model[] gridModelsGBM = gridGBM.getModels();
        deletables.addAll(Arrays.asList(gridModelsGBM));
        Assert.assertEquals(2, gridModelsGBM.length);
      }

      StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = train._key;
      seParams._response_column = target;
      if (mixed) {
        // first glm and then gbm base models
        seParams._base_models = ArrayUtils.append(grid.getModelKeys(), gridGBM.getModelKeys());
      } else {
        seParams._base_models = ArrayUtils.append(grid.getModelKeys());
      }
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._seed = seed;
      StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
      deletables.add(se);

      Assert.assertTrue(((GLMModel.GLMParameters) se._output._metalearner._parms)._family == family);
      Assert.assertTrue(((GLMModel.GLMParameters) se._output._metalearner._parms)._link == link);


      StackedEnsembleParameters seParams2 = new StackedEnsembleParameters();
      seParams2._train = train._key;
      seParams2._response_column = target;
      if (mixed) {
        seParams2._base_models = ArrayUtils.append(grid.getModelKeys(), gridGBM.getModelKeys());
      } else {
        seParams2._base_models = ArrayUtils.append(grid.getModelKeys());
      }
      seParams2._metalearner_algorithm = Algorithm.gbm;
      seParams2._seed = seed;
      StackedEnsembleModel se2 = new StackedEnsemble(seParams2).trainModel().get();
      deletables.add(se2);

      Assert.assertTrue(se2._output._metalearner._parms._distribution == distribution);

      if (mixed) {
        // first gbm and then glm base models
        StackedEnsembleParameters seParams3 = new StackedEnsembleParameters();
        seParams3._train = train._key;
        seParams3._response_column = target;
        seParams3._base_models = ArrayUtils.append(gridGBM.getModelKeys(), grid.getModelKeys());
        seParams3._metalearner_algorithm = Algorithm.AUTO;
        seParams3._seed = seed;
        StackedEnsembleModel se3 = new StackedEnsemble(seParams3).trainModel().get();
        deletables.add(se3);

        Assert.assertTrue(((GLMModel.GLMParameters) se3._output._metalearner._parms)._family == family);
        Assert.assertTrue(((GLMModel.GLMParameters) se3._output._metalearner._parms)._link == link);
      }

    } finally {
      for (Lockable l : deletables) {
        if (l instanceof Model) ((Model) l).deleteCrossValidationPreds();
        l.delete();
      }
    }
  }


  @Test
  public void logitTransformWorks() {
        Scope.enter();
        try {
            Frame fr = new Frame();
            fr.add("first", Vec.makeCon(0.5, 10));
            fr.add("second", Vec.makeCon(0.1, 10));
            fr.add("third", Vec.makeCon(0.9, 10));

            Frame newFr = StackedEnsembleParameters.MetalearnerTransform.Logit.transform(null,fr, Key.make());
            Scope.track(newFr);

            Frame expected = new Frame();
            expected.add("first", Vec.makeCon(0, 10));
            expected.add("second", Vec.makeCon(-2.19722457, 10));
            expected.add("third", Vec.makeCon(2.19722457, 10));

            assertFrameEquals(expected, newFr, 1e-5);
        } finally {
            Scope.exit();
        }
  }


    @Test
    public void testMetalearnerTransformWorks() {
        try {
            Scope.enter();

            final Frame trainingFrame = parseTestFile("./smalldata/junit/weather.csv");
            Scope.track(trainingFrame);
            trainingFrame.toCategoricalCol("RainTomorrow");

            HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
                put("_max_depth", new Integer[]{2, 3, 4});
            }};

            GBMModel.GBMParameters params = new GBMModel.GBMParameters();
            params._train = trainingFrame._key;
            params._nfolds = 2;
            params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            params._response_column = "RainTomorrow";
            params._keep_cross_validation_predictions = true;
            params._seed = 0;
            params._col_sample_rate = 0.1; // so we don't have all the columns the same after percentile rank transform
            params._distribution = DistributionFamily.bernoulli;

            Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
            Scope.track_generic(gs);
            final Grid grid = gs.get();
            Scope.track_generic(grid);
            final StackedEnsembleParameters seParams = new StackedEnsembleParameters();
            seParams._train = trainingFrame._key;
            seParams._response_column = "RainTomorrow";
            seParams._metalearner_algorithm = Algorithm.AUTO;
            seParams._base_models = grid.getModelKeys();
            seParams._keep_levelone_frame = true;
            seParams._seed = 0xFEED;

            final StackedEnsembleParameters seParamsLogit = (StackedEnsembleParameters)seParams.clone();
            seParamsLogit._metalearner_transform = StackedEnsembleParameters.MetalearnerTransform.Logit;

            final StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
            final StackedEnsembleModel seLogit = new StackedEnsemble(seParamsLogit).trainModel().get();

            Scope.track_generic(se);
            Scope.track_generic(seLogit);

            final Frame vanillaLevelOneFrame = new Frame(se._output._levelone_frame_id).remove(new String[]{"RainTomorrow"});
            Scope.track(vanillaLevelOneFrame);

            Frame expectedLogit = StackedEnsembleParameters.MetalearnerTransform.Logit.transform(seLogit,vanillaLevelOneFrame,Key.make());
            Scope.track(expectedLogit);
            assertFrameEquals(expectedLogit,
                    new Frame(seLogit._output._levelone_frame_id).remove(new String[]{"RainTomorrow"}),
                    1e-5);

            Frame training_clone = new Frame(trainingFrame);
            DKV.put(training_clone);
            Scope.track(training_clone);
            Frame preds = se.score(training_clone);
            Scope.track(preds);
            final boolean predsTheSame = se.testJavaScoring(training_clone, preds, 1e-15, 0.01);
            Assert.assertTrue(predsTheSame);

            Frame predsLogit = seLogit.score(training_clone);
            Scope.track(predsLogit);
            final boolean predsLogitTheSame = seLogit.testJavaScoring(training_clone, predsLogit, 1e-15, 0.01);
            Assert.assertTrue(predsLogitTheSame);

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testMultinomialWithAUTOMetalearnerAndAlphaSearch() {
        final String training_file = "./smalldata/iris/iris_wheader.csv";
        Set<Frame> framesBefore = new HashSet<>();
        framesBefore.addAll(Arrays.asList(Frame.fetchAll()));

        GBMModel gbm = null;
        DRFModel drf = null;
        StackedEnsembleModel stackedEnsembleModel = null;
        Frame training_frame = null;
        Frame preds = null;
        long seed = 42 * 42;
        try {
            Scope.enter();
            training_frame = parseTestFile(training_file);

            int idx = 4;
            if (!training_frame.vecs()[idx].isCategorical()) {
                Scope.track(training_frame.replace(idx, training_frame.vecs()[idx].toCategoricalVec()));
            }

            DKV.put(training_frame); // Update frames after preparing

            // Build GBM
            GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
            // Configure GBM
            gbmParameters._train = training_frame._key;
            gbmParameters._response_column = training_frame._names[idx];
            gbmParameters._ntrees = 5;
            gbmParameters._distribution = DistributionFamily.multinomial;
            gbmParameters._max_depth = 4;
            gbmParameters._min_rows = 1;
            gbmParameters._nbins = 50;
            gbmParameters._learn_rate = .2f;
            gbmParameters._score_each_iteration = true;
            gbmParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            gbmParameters._keep_cross_validation_predictions = true;
            gbmParameters._nfolds = 5;
            gbmParameters._seed = seed;

            // Invoke GBM and block till the end
            GBM gbmJob = new GBM(gbmParameters);
            // Get the model
            gbm = gbmJob.trainModel().get();
            Assert.assertTrue(gbmJob.isStopped()); //HEX-1817

            // Build DRF
            DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
            // Configure DRF
            drfParameters._train = training_frame._key;
            drfParameters._response_column = training_frame._names[idx];
            drfParameters._distribution = DistributionFamily.multinomial;
            drfParameters._ntrees = 5;
            drfParameters._max_depth = 4;
            drfParameters._min_rows = 1;
            drfParameters._nbins = 50;
            drfParameters._score_each_iteration = true;
            drfParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            drfParameters._keep_cross_validation_predictions = true;
            drfParameters._nfolds = 5;
            drfParameters._seed = seed;

            // Invoke DRF and block till the end
            DRF drfJob = new DRF(drfParameters);
            // Get the model
            drf = drfJob.trainModel().get();
            Assert.assertTrue(drfJob.isStopped()); //HEX-1817

            // Build Stacked Ensemble of previous GBM and DRF
            StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleParameters();
            // Configure Stacked Ensemble
            stackedEnsembleParameters._train = training_frame._key;
            stackedEnsembleParameters._response_column = training_frame._names[idx];
            stackedEnsembleParameters._metalearner_algorithm = Algorithm.glm;
            stackedEnsembleParameters.initMetalearnerParams(Algorithm.glm);
            GLMModel.GLMParameters glmParams =  (GLMModel.GLMParameters) stackedEnsembleParameters._metalearner_parameters;
            glmParams._generate_scoring_history = true;
            glmParams._score_iteration_interval = (glmParams._valid == null) ? 5 : -1;
            glmParams._alpha = new double[] {0.5, 1.0};
            glmParams._standardize = false;

            stackedEnsembleParameters._base_models = new Key[]{gbm._key, drf._key};
            stackedEnsembleParameters._seed = seed;
            stackedEnsembleParameters._score_training_samples = 0; // don't subsample dataset for training metrics so we don't randomly fail the test
            // Invoke Stacked Ensemble and block till end
            StackedEnsemble stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters);
            // Get the stacked ensemble
            stackedEnsembleModel = stackedEnsembleJob.trainModel().get();

            Frame training_clone = new Frame(training_frame);
            DKV.put(training_clone);
            Scope.track(training_clone);
            preds = stackedEnsembleModel.score(training_clone);
            final boolean predsTheSame = stackedEnsembleModel.testJavaScoring(training_clone, preds, 1e-15, 0.01);
            Assert.assertTrue(predsTheSame);
            Assert.assertTrue(stackedEnsembleJob.isStopped());

            ModelMetrics training_metrics = stackedEnsembleModel._output._training_metrics;
            ModelMetrics training_clone_metrics = ModelMetrics.getFromDKV(stackedEnsembleModel, training_clone);
            Assert.assertEquals(training_metrics.mse(), training_clone_metrics.mse(), 1e-15);
            training_clone.remove();
        } finally {
            if (training_frame != null) training_frame.remove();
            if (gbm != null) gbm.delete();
            if (drf != null) drf.delete();
            if (preds != null) preds.delete();
            if (stackedEnsembleModel != null) stackedEnsembleModel.delete();
            Scope.exit();
        }
    }

  @Test
  public void testStackedEnsembleDoesntIgnoreColumnIfAnyBaseModelUsesIt() {
    try {
        Scope.enter();
        StackedEnsembleModel.StackedEnsembleOutput output = null;
        try {
            output = basicEnsemble("./smalldata/junit/cars.csv",
                    null,
                    new StackedEnsembleTest.PrepData() {
                        int prep(Frame fr) {
                            fr.remove("name").remove();
                            Vec acVec = fr.anyVec().makeCon(Math.PI); // this is what use to break SE (SE ignored this column, DRF did not)
                            Scope.track(acVec);
                            acVec.setNA(0);
                            fr.insertVec(0, "almost_constant", acVec);
                            return fr.find("economy (mpg)");
                        }
                    },
                    false, DistributionFamily.gaussian, Algorithm.glm, false);
        } finally { }
        Assert.assertNotNull(output);
    } finally {
      Scope.exit();
    }
  }

    @Test
    @SuppressWarnings("unchecked")
    public void testMOJOWorksWhenSubmodelIsIgnoringAColumn() {
        try (LockableCleaner cleaner = new LockableCleaner()) {
            final String response = "petal_wid";
            final Frame train = parseTestFile("./smalldata/iris/iris_train.csv");
            cleaner.add(train);

            Vec constVec = train.anyVec().makeCon(Math.PI);
            train.insertVec(0, "constant", constVec);
            DKV.put(train);
            
            GBMModel.GBMParameters ignoreConstParms = new GBMModel.GBMParameters();
            ignoreConstParms._train = train._key;
            ignoreConstParms._response_column = response;
            ignoreConstParms._ntrees = 5;
            ignoreConstParms._seed = 42;
            ignoreConstParms._keep_cross_validation_models = false;
            ignoreConstParms._keep_cross_validation_predictions = true;
            ignoreConstParms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            ignoreConstParms._nfolds = 3;
            ignoreConstParms._distribution = DistributionFamily.gaussian;

            GBMModel.GBMParameters keepConstParms = (GBMModel.GBMParameters) ignoreConstParms.clone();
            keepConstParms._ignore_const_cols = false;

            GBMModel ignoreConstModel = new GBM(ignoreConstParms).trainModel().get();
            cleaner.add(ignoreConstModel);
            GBMModel keepConstModel = new GBM(keepConstParms).trainModel().get();
            cleaner.add(keepConstModel);

            StackedEnsembleParameters seParams = new StackedEnsembleParameters();
            seParams._train = train._key;
            seParams._response_column = response;
            seParams._base_models = new Key[]{ignoreConstModel._key, keepConstModel._key};
            seParams._seed = 42;

            StackedEnsembleModel se = new StackedEnsemble(seParams).trainModel().get();
            cleaner.add(se);

            assertNotNull(se);

            Frame sePredict = se.score(train);
            cleaner.add(sePredict);

            assertTrue(se.testJavaScoring(train, sePredict, 1e-15, 0.01));
        }
    }

    private static class LockableCleaner extends ArrayList<Lockable<?>> implements AutoCloseable {
        @Override
        public void close() {
            for (Lockable<?> l : this) {
                if (l instanceof Model) 
                    ((Model<?, ?, ?>) l).deleteCrossValidationPreds();
                l.delete();
            }
        }
    }
    
}
