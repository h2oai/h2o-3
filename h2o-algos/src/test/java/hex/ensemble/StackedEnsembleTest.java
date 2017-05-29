package hex.ensemble;

import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import static hex.genmodel.utils.DistributionFamily.*;

public class StackedEnsembleTest extends TestUtil {

    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    private abstract class PrepData { abstract int prep(Frame fr); }

    static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn",
            "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
            "LateAircraftDelay", "IsDepDelayed"};

    @Test public void testBasicEnsemble() {
        // Regression tests
        basicEnsemble("./smalldata/junit/cars.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
                false, gaussian);

        basicEnsemble("./smalldata/junit/test_tree_minmax.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("response"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/logreg/prostate.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/gbm_test/alphabet_cattest.csv",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) { return fr.find("y"); }
                },
                false, DistributionFamily.bernoulli);

        basicEnsemble("./smalldata/airlines/allyears2k_headers.zip",
                new StackedEnsembleTest.PrepData() { int prep(Frame fr) {
                    for( String s : ignored_aircols ) fr.remove(s).remove();
                    return fr.find("IsArrDelayed"); }
                },
                false, DistributionFamily.bernoulli);
    }
    // ==========================================================================
    public StackedEnsembleModel.StackedEnsembleOutput basicEnsemble(String fname, StackedEnsembleTest.PrepData prep, boolean validation, DistributionFamily family) {
        GBMModel gbm = null;
        DRFModel drf = null;
        StackedEnsembleModel stackedEnsembleModel = null;
        Frame fr = null, fr2= null, vfr=null;
        try {
            Scope.enter();
            fr = parse_test_file(fname);
            int idx = prep.prep(fr); // hack frame per-test
            if (family == DistributionFamily.bernoulli || family == DistributionFamily.multinomial || family == DistributionFamily.modified_huber) {
                if (!fr.vecs()[idx].isCategorical()) {
                    Scope.track(fr.replace(idx, fr.vecs()[idx].toCategoricalVec()));
                }
            }
            DKV.put(fr);// Update frame after hacking it

            // Build GBM
            GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
            if( idx < 0 ) idx = ~idx;
            // Configure GBM
            gbmParameters._train = fr._key;
            gbmParameters._response_column = fr._names[idx];
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
            if( validation ) {        // Make a validation frame that's a clone of the training data
                vfr = new Frame(fr);
                DKV.put(vfr);
                gbmParameters._valid = vfr._key;
            }
            // Invoke GBM and block till the end
            GBM gbmJob = new GBM(gbmParameters);
            // Get the model
            gbm = gbmJob.trainModel().get();
            Assert.assertTrue(gbmJob.isStopped()); //HEX-1817

            // Build DRF
            DRFModel.DRFParameters drfParameters = new DRFModel.DRFParameters();
            // Configure DRF
            drfParameters._train = fr._key;
            drfParameters._response_column = fr._names[idx];
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
            stackedEnsembleParameters._train = fr._key;
            stackedEnsembleParameters._response_column = fr._names[idx];
            stackedEnsembleParameters._base_models = new Key[] {gbm._key,drf._key};
            // Invoke Stacked Ensemble and block till end
            StackedEnsemble stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters);
            // Get the stacked ensemble
            stackedEnsembleModel = stackedEnsembleJob.trainModel().get();
            Assert.assertTrue(stackedEnsembleJob.isStopped());

            //return
            return stackedEnsembleModel._output;

        } finally {
            if( fr  != null ) fr .remove();
            if( fr2 != null ) fr2.remove();
            if( vfr != null ) vfr.remove();
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
