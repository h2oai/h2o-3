package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.udf.metric.BernoulliCustomDistribution;
import water.util.FrameUtils;

import java.io.IOException;

import static water.udf.JFuncUtils.loadTestFunc;

public class GBMCustomDistributionTest extends TestUtil {

    @BeforeClass
    static public void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testCustomDistribution() throws Exception {
        Frame fr = null, fr2 = null;
        GBMModel gbm_default = null;
        GBMModel gbm_custom = null;
        final CFuncRef func = bernoulliCustomDistribution();
        try {
            Scope.enter();
            fr = parseTestFile("./smalldata/gbm_test/alphabet_cattest.csv");
            int idx = fr.find("y");
            if (!fr.vecs()[idx].isCategorical()) {
                Scope.track(fr.replace(idx, fr.vecs()[idx].toCategoricalVec()));
            }

            System.out.println("Creating default model GBM...");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._response_column = "y";
            parms._distribution = DistributionFamily.bernoulli;
            gbm_default = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());

            fr2 = parseTestFile("./smalldata/gbm_test/alphabet_cattest.csv");
            int idx2 = fr2.find("y");
            if (!fr2.vecs()[idx2].isCategorical()) {
                Scope.track(fr2.replace(idx2, fr2.vecs()[idx2].toCategoricalVec()));
            }
            
            System.out.println("Creating custom distribution model GBM...");
            parms = new GBMModel.GBMParameters();
            parms._train = fr2._key;
            parms._distribution = DistributionFamily.custom;
            parms._custom_distribution_func = func.toRef();
            parms._response_column = "y";
            gbm_custom = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
            
            System.out.println("Test MSE is the same for default (" + gbm_default._output._training_metrics.mse() + ") and custom (" + gbm_custom._output._training_metrics.mse() + ")");
            Assert.assertEquals(gbm_default._output._training_metrics.mse(), gbm_custom._output._training_metrics.mse(), 1e-4);

            System.out.println("Test RMSE is the same for default (" + gbm_default._output._training_metrics.rmse() + ") and custom (" + gbm_custom._output._training_metrics.rmse() + ")");
            Assert.assertEquals(gbm_default._output._training_metrics.rmse(), gbm_custom._output._training_metrics.rmse(), 1e-4);

            System.out.println("Test AUC is the same for default (" + gbm_default._output._training_metrics.auc_obj()._auc + ") and custom (" + gbm_custom._output._training_metrics.auc_obj()._auc + ")");
            Assert.assertEquals(gbm_default._output._training_metrics.auc_obj()._auc, gbm_custom._output._training_metrics.auc_obj()._auc, 1e-4);

            System.out.println("Test accuracy is the same for default (" + gbm_default._output._training_metrics.cm().accuracy() + ") and custom (" + gbm_custom._output._training_metrics.cm().accuracy() + ")");
            Assert.assertEquals(gbm_default._output._training_metrics.cm().accuracy(), gbm_custom._output._training_metrics.cm().accuracy(), 1e-4);

            System.out.println("Test precision is the same for default (" + gbm_default._output._training_metrics.cm().precision() + ") and custom (" + gbm_custom._output._training_metrics.cm().precision() + ")");
            Assert.assertEquals(gbm_default._output._training_metrics.cm().precision(), gbm_custom._output._training_metrics.cm().precision(), 1e-4);

            System.out.println("Test recall is the same for default (" + gbm_default._output._training_metrics.cm().recall() + ") and custom (" + gbm_custom._output._training_metrics.cm().recall() + ")");
            Assert.assertEquals(gbm_default._output._training_metrics.cm().recall(), gbm_custom._output._training_metrics.cm().recall(), 1e-4);

            try {
                System.out.println("Creating custom distribution model GBM wrong setting...");
                parms = new GBMModel.GBMParameters();
                parms._train = fr._key;
                parms._response_column = "y"; // Train on the outcome
                parms._distribution = DistributionFamily.custom;
                parms._custom_distribution_func = null;
                gbm_custom = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
            } catch (H2OModelBuilderIllegalArgumentException ex) {
                System.out.println("Catch illegal argument exception.");
            }
        } finally {
            FrameUtils.delete(fr, fr2,  gbm_default, gbm_custom);
            DKV.remove(func.getKey());
            Scope.exit();
        }
    }

    private CFuncRef bernoulliCustomDistribution() throws IOException {
        return loadTestFunc("customDistribution.key", BernoulliCustomDistribution.class);
    }
}
