package hex.ensemble;

import hex.Model;
import hex.StackedEnsembleModel;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static hex.genmodel.utils.DistributionFamily.gaussian;

public class StackedEnsembleTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    private abstract class PrepData { abstract int prep(Frame fr); }

    static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn",
            "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
            "LateAircraftDelay", "IsDepDelayed"};

    @Test public void testBasicEnsemble() {
        // Regression tests
        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
                false, gaussian);

        // Binomial tests
        basicEnsemble("./smalldata/junit/test_tree_minmax.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("response"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/logreg/prostate.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/logreg/prostate_train.csv",
                "./smalldata/logreg/prostate_test.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/gbm_test/alphabet_cattest.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("y"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli);

        // Multinomial tests
        basicEnsemble("./smalldata/logreg/prostate.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("RACE"); }
                },
                false, DistributionFamily.multinomial);

        basicEnsemble("./smalldata/junit/cars.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("name").remove(); return fr.find("cylinders"); }
                },
                false, DistributionFamily.multinomial);

        basicEnsemble("./smalldata/iris/iris_wheader.csv",
                null,
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {return fr.find("class"); }
                },
                false, DistributionFamily.multinomial);
    }
    // ==========================================================================
    public StackedEnsembleModel.StackedEnsembleOutput basicEnsemble(String training_file, String validation_file, StackedEnsembleTest.PrepData prep, boolean dupeTrainingFrameToValidationFrame, DistributionFamily family) {
        Set<Frame> framesBefore = new HashSet<>();
        framesBefore.addAll(Arrays.asList( Frame.fetchAll()));

        GBMModel gbm = null;
        DRFModel drf = null;
        StackedEnsembleModel stackedEnsembleModel = null;
        Frame training_frame = null, validation_frame = null;
        try {
            Scope.enter();
            training_frame = parse_test_file(training_file);
            if (null != validation_file)
                validation_frame = parse_test_file(validation_file);

            int idx = prep.prep(training_frame); // hack frame per-test
            if (null != validation_frame)
                prep.prep(validation_frame);

            if (family == DistributionFamily.bernoulli || family == DistributionFamily.multinomial || family == DistributionFamily.modified_huber) {
                if (!training_frame.vecs()[idx].isCategorical()) {
                    Scope.track(training_frame.replace(idx, training_frame.vecs()[idx].toCategoricalVec()));
                    if (null != validation_frame)
                        Scope.track(validation_frame.replace(idx, validation_frame.vecs()[idx].toCategoricalVec()));
                }
            }
            DKV.put(training_frame); // Update frames after preparing
            if (null != validation_frame)
                DKV.put(validation_frame);

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
            drfParameters._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
            drfParameters._keep_cross_validation_predictions = true;
            drfParameters._nfolds = 5;
            // Invoke DRF and block till the end
            DRF drfJob = new DRF(drfParameters);
            // Get the model
            drf = drfJob.trainModel().get();
            Assert.assertTrue(drfJob.isStopped()); //HEX-1817

            // Build Stacked Ensemble of previous GBM and DRF
            StackedEnsembleModel.StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters();
            // Configure Stacked Ensemble
            stackedEnsembleParameters._train = training_frame._key;
            stackedEnsembleParameters._valid = (validation_frame == null ? null : validation_frame._key);
            stackedEnsembleParameters._response_column = training_frame._names[idx];
            stackedEnsembleParameters._base_models = new Key[] {gbm._key,drf._key};
            // Invoke Stacked Ensemble and block till end
            StackedEnsemble stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters);
            // Get the stacked ensemble
            stackedEnsembleModel = stackedEnsembleJob.trainModel().get();
            Assert.assertTrue(stackedEnsembleJob.isStopped());

            //return
            return stackedEnsembleModel._output;

        } finally {
            if( training_frame  != null ) training_frame .remove();
            if( validation_frame != null ) validation_frame.remove();
            if( gbm != null ) {
                gbm.delete();
                for (Key k : gbm._output._cross_validation_predictions) k.remove();
                gbm._output._cross_validation_holdout_predictions_frame_id.remove();
                gbm.deleteCrossValidationModels();
            }
            if( drf != null ) {
                drf.delete();
                for (Key k : drf._output._cross_validation_predictions) k.remove();
                drf._output._cross_validation_holdout_predictions_frame_id.remove();
                drf.deleteCrossValidationModels();
            }

            Set<Frame> framesAfter = new HashSet<>(framesBefore);
            framesAfter.removeAll(Arrays.asList( Frame.fetchAll()));

            Assert.assertEquals("finish with the same number of Frames as we started: " + framesAfter, 0, framesAfter.size());

            if( stackedEnsembleModel != null ) {
                stackedEnsembleModel.delete();
                stackedEnsembleModel.remove();
                stackedEnsembleModel._output._metalearner._output._training_metrics.remove();
                stackedEnsembleModel._output._metalearner.remove();
            }

            Scope.exit();
        }
    }

}
