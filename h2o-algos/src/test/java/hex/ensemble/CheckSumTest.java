package hex.ensemble;

import hex.genmodel.utils.DistributionFamily;
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
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.StackedEnsembleModel;

/***
 * The main purpose of this test is to ensure the checksum() of a training frame is preserved throughout the
 * stacked ensemble process. An issue is known with fold_column causing checksum() issues and also calling the
 * wrong score method. However, stacked ensembles does not need fold column to be passed in explicitly. Rather, a user
 * just needs to pass in the training frame, response name, and the base models. 
 */
public class CheckSumTest extends TestUtil {
    
    @BeforeClass public static void stall() { stall_till_cloudsize(1); }

    @Test public void checkSumTest() {
        Frame fr = null;
        Frame frAfterGbm = null;
        Frame frAfterDrf = null;
        GBMModel gbm = null;
        GBMModel.GBMParameters parmsGbm = new GBMModel.GBMParameters();
        DRFModel drf = null;
        DRFModel.DRFParameters parmsDrf = new DRFModel.DRFParameters();
        StackedEnsembleModel stackedEnsembleModel = null;

        String fname = "./smalldata/stackedensembles/stacking_fold.csv";
        try {
            Scope.enter();
            Frame train = parse_test_file(fname);
            DKV.put(train);
            int resp = train.find("response");
            Scope.track(train.replace(resp, train.vecs()[resp].toCategoricalVec()));
            DKV.put(train._key,train);

            //Build GBM
            parmsGbm._train = train._key;
            parmsGbm._response_column = "response"; // Train on the outcome
            parmsGbm._ntrees = 10;
            parmsGbm._max_depth = 3;
            parmsGbm._min_rows = 2;
            parmsGbm._learn_rate = .2f;
            parmsGbm._distribution = DistributionFamily.bernoulli;
            parmsGbm._fold_column = "fold_column";
            parmsGbm._keep_cross_validation_predictions = true;
            parmsGbm._seed = 1;
            gbm = new GBM(parmsGbm).trainModel().get();
            frAfterGbm = gbm._parms.train();
            //Compare original train checksum to GBM train checksum
            Assert.assertEquals(train.checksum(),frAfterGbm.checksum());

            //Build DRF
            parmsDrf._train = train._key;
            parmsDrf._response_column = "response"; // Train on the outcome
            parmsDrf._distribution = DistributionFamily.bernoulli;
            parmsDrf._fold_column = "fold_column";
            parmsDrf._keep_cross_validation_predictions = true;
            parmsDrf._seed = 1;
            drf = new DRF(parmsDrf).trainModel().get();
            frAfterDrf= drf._parms.train();
            //Compare original train checksum to DRF train checksum
            Assert.assertEquals(train.checksum(),frAfterDrf.checksum());

            // Build Stacked Ensemble of previous GBM and DRF
            StackedEnsembleModel.StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters();
            stackedEnsembleParameters._train = train._key;
            stackedEnsembleParameters._response_column = "response";
            stackedEnsembleParameters._base_models = new Key[] {gbm._key,drf._key};
            StackedEnsemble stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters);
            stackedEnsembleModel = stackedEnsembleJob.trainModel().get();

        } finally {
            if( fr != null ) fr.remove();
            if( frAfterGbm != null) frAfterGbm.remove();
            if(frAfterDrf != null) frAfterDrf.remove();
            if( gbm != null ) {
                gbm.delete();
                parmsGbm._train.remove();
                for (Key k : gbm._output._cross_validation_predictions) k.remove();
                gbm._output._cross_validation_holdout_predictions_frame_id.remove();
                gbm.deleteCrossValidationModels();
            }
            if( drf != null ) {
                drf.delete();
                parmsDrf._train.remove();
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
