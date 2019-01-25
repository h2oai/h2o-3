package water.network;

import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.Ignore;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.util.Date;

import static hex.genmodel.utils.DistributionFamily.gaussian;

/**
 * This class is used to capture TCP packets while training a model
 * The result is then used to check if SSL encryption is working properly
 */
@Ignore
public class SSLEncryptionTest extends TestUtil {

    public static void main(String[] args) {
        if (args.length == 1) {
            testGBMRegressionGaussianSSL(args[0]);
        } else {
            testGBMRegressionGaussianNonSSL();
        }

        System.exit(0);
    }

    public static void testGBMRegressionGaussianNonSSL() {
        stall_till_cloudsize(4);
        testGBMRegressionGaussian();
    }

    public static void testGBMRegressionGaussianSSL(String prop) {
        stall_till_cloudsize(new String[] {"-internal_security_conf", prop}, 4);
        testGBMRegressionGaussian();
    }

    private static void testGBMRegressionGaussian() {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
            Date start = new Date();

            fr = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._distribution = gaussian;
            parms._response_column = fr._names[1]; // Row in col 0, dependent in col 1, predictor in col 2
            parms._ntrees = 1;
            parms._max_depth = 1;
            parms._min_rows = 1;
            parms._nbins = 20;
            // Drop ColV2 0 (row), keep 1 (response), keep col 2 (only predictor), drop remaining cols
            String[] xcols = parms._ignored_columns = new String[fr.numCols()-2];
            xcols[0] = fr._names[0];
            System.arraycopy(fr._names,3,xcols,1,fr.numCols()-3);
            parms._learn_rate = 1.0f;
            parms._score_each_iteration=true;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();

            Log.info(">>> GBM parsing and training took: " + (new Date().getTime() - start.getTime())  + " ms.");

            Assert.assertTrue(job.isStopped()); //HEX-1817

            // Done building model; produce a score column with predictions

            Date scoringStart = new Date();

            fr2 = gbm.score(fr);

            Log.info(">>> GBM scoring took: " + (new Date().getTime() - scoringStart.getTime())  + " ms.");
        } finally {
            if( fr  != null ) fr .remove();
            if( fr2 != null ) fr2.remove();
            if( gbm != null ) gbm.remove();
        }
    }
}
