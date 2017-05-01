package hex.mli;

import hex.genmodel.utils.DistributionFamily;
import hex.mli.loco.LeaveOneCovarOut;
import hex.tree.gbm.GBM;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import hex.tree.gbm.GBMModel;
import static hex.genmodel.utils.DistributionFamily.gaussian;
import static hex.genmodel.utils.DistributionFamily.multinomial;
import static hex.genmodel.utils.DistributionFamily.bernoulli;

/**
 * This Junit is mainly used to detect leaks in Leave One Covariate Out (LOCO)
 */
public class LeaveOneCovarOutTest extends TestUtil {

    @BeforeClass()
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testLocoRegressionDefault() {
        //Regression case
        locoRun("./smalldata/junit/cars.csv", "economy (mpg)", gaussian,null);
    }

    @Test
    public void testLocoBernoulliDefault() {
        //Bernoulli case
        locoRun("./smalldata/logreg/prostate.csv", "CAPSULE", bernoulli,null);

    }

    @Test
    public void testLocoMultinomialDefault(){
        //Multinomial case
        locoRun("./smalldata/junit/cars.csv", "cylinders", multinomial,null);
    }

    @Test
    public void testLocoRegressionMean() {
        //Regression case
        locoRun("./smalldata/junit/cars.csv", "economy (mpg)", gaussian,"mean");
    }

    @Test
    public void testLocoBernoulliMean() {
        //Bernoulli case
        locoRun("./smalldata/logreg/prostate.csv", "CAPSULE", bernoulli,"mean");

    }

    @Test
    public void testLocoMultinomialMean(){
        //Multinomial case
        locoRun("./smalldata/junit/cars.csv", "cylinders", multinomial,"mean");
    }

    @Test
    public void testLocoRegressionMedian() {
        //Regression case
        locoRun("./smalldata/junit/cars.csv", "economy (mpg)", gaussian,"median");
    }

    @Test
    public void testLocoBernoulliMedian() {
        //Bernoulli case
        locoRun("./smalldata/logreg/prostate.csv", "CAPSULE", bernoulli,"median");

    }

    @Test
    public void testLocoMultinomialMedian(){
        //Multinomial case
        locoRun("./smalldata/junit/cars.csv", "cylinders", multinomial,"median");
    }

    public Frame locoRun(String fname, String response, DistributionFamily family, String method) {
        GBMModel gbm = null;
        Frame fr = null;
        Frame loco=null;
        try {
            Scope.enter();
            fr = parse_test_file(fname);
            int idx = fr.find(response);
            if (family == DistributionFamily.bernoulli || family == DistributionFamily.multinomial || family == DistributionFamily.modified_huber) {
                if (!fr.vecs()[idx].isCategorical()) {
                    Scope.track(fr.replace(idx, fr.vecs()[idx].toCategoricalVec()));
                }
            }
            DKV.put(fr);             // Update frame after hacking it

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            if( idx < 0 ) idx = ~idx;
            parms._train = fr._key;
            parms._response_column = fr._names[idx];
            parms._ntrees = 5;
            parms._distribution = family;
            parms._max_depth = 4;
            parms._min_rows = 1;
            parms._nbins = 50;
            parms._learn_rate = .2f;
            parms._score_each_iteration = true;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();

            if(method == null) {
                loco = LeaveOneCovarOut.leaveOneCovarOut(gbm, fr, job._job, null,null);
                assert DKV.get(loco._key) != null : "LOCO frame with default transform is not in DKV!";
            } else if(method == "mean"){
                loco = LeaveOneCovarOut.leaveOneCovarOut(gbm, fr, job._job, "mean",null);
                assert DKV.get(loco._key) != null : "LOCO frame with mean transform is not in DKV!";
            } else{
                loco = LeaveOneCovarOut.leaveOneCovarOut(gbm, fr, job._job, "median",null);
                assert DKV.get(loco._key) != null : "LOCO frame with median transform is not in DKV!";
            }
            return loco;

        } finally {
            if( fr  != null ) fr.remove();
            if( gbm != null ) gbm.delete();
            if( loco != null ) loco.remove();
            Scope.exit();
        }
    }

}
