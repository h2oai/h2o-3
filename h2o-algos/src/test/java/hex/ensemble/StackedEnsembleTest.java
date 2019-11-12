package hex.ensemble;

import hex.GLMHelper;
import hex.Model;
import hex.ModelMetrics;
import hex.SplitFrame;
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
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class StackedEnsembleTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

    private abstract class PrepData { abstract int prep(Frame fr); }

    static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn",
            "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
            "LateAircraftDelay", "IsDepDelayed"};

    @Test public void testBasicEnsembleAUTOMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
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
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
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
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
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
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
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
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
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
            new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
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
        Frame train = parse_test_file("./smalldata/junit/cars.csv");
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
        seParams._distribution = DistributionFamily.bernoulli;
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
        final Frame fr = parse_test_file("./smalldata/logreg/prostate_train.csv"); deletables.add(fr);
        final Frame test = parse_test_file("./smalldata/logreg/prostate_test.csv"); deletables.add(test);

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
        final Frame train = parse_test_file("./smalldata/testng/cars_train.csv"); deletables.add(train);
        final Frame test = parse_test_file("./smalldata/testng/cars_test.csv"); deletables.add(test);
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
            final Frame train = parse_test_file("./smalldata/logreg/prostate_train.csv"); deletables.add(train);
            final Frame test = parse_test_file("./smalldata/logreg/prostate_test.csv"); deletables.add(test);

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
                    TestUtil.isBitIdentical(new Frame(test.vec(target)), new Frame(se1._output._levelone_frame_id.vec(target)));
                } else {
                    Assert.assertEquals(train.numRows(), se1._output._levelone_frame_id.numRows());
                    TestUtil.isBitIdentical(new Frame(train.vec(target)), new Frame(se1._output._levelone_frame_id.vec(target)));
                }
            } else {
                Assert.assertNull(se1._output._levelone_frame_id);
            }
            se1.delete();

//            building a new model would throw an exception if we deleted too much when deleting s1
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
            training_frame = parse_test_file(training_file);
            if (null != validation_file)
                validation_frame = parse_test_file(validation_file);

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
            if( idx < 0 ) idx = ~idx;
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
            // Invoke Stacked Ensemble and block till end
            StackedEnsemble stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters);
            // Get the stacked ensemble
            stackedEnsembleModel = stackedEnsembleJob.trainModel().get();

            Frame training_clone = new Frame(training_frame);
            DKV.put(training_clone);
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
                for (Key k : gbm._output._cross_validation_predictions) k.remove();
                gbm._output._cross_validation_holdout_predictions_frame_id.remove();
                gbm.deleteCrossValidationModels();
            }
            if( drf != null ) {
                drf.delete();
                if (!blending_mode) {
                    for (Key k : drf._output._cross_validation_predictions) k.remove();
                    drf._output._cross_validation_holdout_predictions_frame_id.remove();
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
            final Frame train = parse_test_file("./smalldata/testng/prostate_train.csv"); deletables.add(train);
            final Frame test = parse_test_file("./smalldata/testng/prostate_test.csv"); deletables.add(test);
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

      final Frame trainingFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);
      final Frame partialFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv", new int[]{4}); // Missing fold column
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

      final Frame trainingFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);
      final Frame partialFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv", new int[]{4}); // Missing fold column
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

      final Frame trainingFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);
      final Frame partialFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv", new int[]{4}); // Missing fold column
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
  public void testInvalidFoldColumn_trainingFrame() {
    GBMModel gbmModel = null;
    try {
      Scope.enter();

      final Frame trainingFrame = TestUtil.parse_test_file("./smalldata/iris/iris_wheader.csv");
      Scope.track(trainingFrame);

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

      final Frame seTrain = new Frame(Key.<Frame>make(), trainingFrame.names(), trainingFrame.vecs());
      Vec foldVec = seTrain.remove("class");
      seTrain.add("class", foldVec.toStringVec());
      DKV.put(seTrain);
      Scope.track(seTrain);

      final StackedEnsembleParameters seParams = new StackedEnsembleParameters();
      seParams._train = seTrain._key;
      seParams._response_column = "petal_len";
      seParams._metalearner_algorithm = Algorithm.AUTO;
      seParams._base_models = new Key[]{gbmModel._key};
      seParams._seed = 0x5EED;
      seParams._metalearner_fold_column = "class";

      expectedException.expect(IllegalArgumentException.class);
      expectedException.expectMessage("Specified fold column 'class' not found in one of the supplied data frames. Available column names are: [sepal_len, sepal_wid, petal_wid, petal_len]");

      new StackedEnsemble(seParams);
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


}
