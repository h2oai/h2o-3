package hex.ensemble;

import hex.GLMHelper;
import hex.Model;
import hex.ModelMetrics;
import hex.StackedEnsembleModel;
import hex.StackedEnsembleModel.StackedEnsembleParameters;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static hex.StackedEnsembleModel.StackedEnsembleParameters.*;
import static org.junit.Assert.assertNotNull;

public class StackedEnsembleTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    private abstract class PrepData { abstract int prep(Frame fr); }

    static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn",
            "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
            "LateAircraftDelay", "IsDepDelayed"};

    @Test public void testBasicEnsembleAUTOMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, DistributionFamily.gaussian, MetalearnerAlgorithm.AUTO, false);
        
        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.AUTO, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.AUTO, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.AUTO, false);
    }


    @Test public void testBasicEnsembleGBMMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, MetalearnerAlgorithm.gbm, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.gbm, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.gbm, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.gbm, false);
    }

    @Test public void testBasicEnsembleDRFMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, MetalearnerAlgorithm.drf, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.drf, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.drf, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.drf, false);
    }

    @Test public void testBasicEnsembleDeepLearningMetalearner() {

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, MetalearnerAlgorithm.deeplearning, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.deeplearning, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.deeplearning, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.deeplearning, false);
    }

    

    @Test public void testBasicEnsembleGLMMetalearner() {

        // Regression tests
        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
                false, DistributionFamily.gaussian, MetalearnerAlgorithm.glm, false);

        // Binomial tests
        basicEnsemble("./smalldata/junit/test_tree_minmax.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("response"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.glm, false);

        basicEnsemble("./smalldata/logreg/prostate.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.glm, false);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.glm, false);

        basicEnsemble("./smalldata/gbm_test/alphabet_cattest.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("y"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.glm, false);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli, MetalearnerAlgorithm.glm, false);

        // Multinomial tests
        basicEnsemble("./smalldata/logreg/prostate.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("RACE"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.glm, false);

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("name").remove(); return fr.find("cylinders"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.glm, false);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial, MetalearnerAlgorithm.glm, false);

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
            
            final Frame training = new MRTask() {
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
            }.doAll(types, v).outputFrame(Key.<Frame>make(), null, domains);
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
                    false, DistributionFamily.multinomial, MetalearnerAlgorithm.glm, false);
        } finally {
            Scope.exit();
        }
    }
    
    @Test public void testBlending() {
        basicEnsemble("./smalldata/junit/cars.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, DistributionFamily.gaussian, MetalearnerAlgorithm.AUTO, true);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                for( String s : ignored_aircols ) fr.remove(s).remove();
                return fr.find("IsArrDelayed"); }
            },
            false, DistributionFamily.bernoulli, MetalearnerAlgorithm.AUTO, true);
        
        basicEnsemble("./smalldata/iris/iris_wheader.csv",
            null,
            new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
            },
            false, DistributionFamily.multinomial, MetalearnerAlgorithm.AUTO, true);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
            "./smalldata/logreg/prostate_test.csv",
            new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
            },
            false, DistributionFamily.bernoulli, MetalearnerAlgorithm.AUTO, true);
    }


    // ==========================================================================
    public StackedEnsembleModel.StackedEnsembleOutput basicEnsemble(String training_file,
                                                                    String validation_file,
                                                                    StackedEnsembleTest.PrepData prep,
                                                                    boolean dupeTrainingFrameToValidationFrame,
                                                                    DistributionFamily family,
                                                                    MetalearnerAlgorithm metalearner_algo,
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
}
