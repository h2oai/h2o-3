package hex.glm;

import hex.DataInfo;
import hex.GLMMetrics;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsBinomialGLM;
import hex.api.MakeGLMModelHandler;
import hex.genmodel.utils.DistributionFamily;
import hex.schemas.GLMModelV3;
import hex.schemas.MakeDerivedGLMModelV3;
import hex.schemas.MakeGLMModelV3;
import hex.schemas.MakeUnrestrictedGLMModelV3;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Keyed;
import water.Scope;
import water.TestUtil;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelMetricsBaseV3;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.DistributedException;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMControlVariablesAndRemoveOffsetTest extends TestUtil {
    
    @Test
    public void compareModelWithControlVariablesEnabledAndDisabled() {
        Frame train = null;
        Frame test = null;
        Frame preds = null;
        GLMModel glm = null;
        Frame preds2 = null;
        GLMModel glm2 = null;
        try {
            Scope.enter();
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;

            String responseColumn = "C21";

            // set cat columns
            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            int response_index = numCols - 1;

            train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();

            DKV.put(train);
            Scope.track_generic(train);

            test = new Frame(train);
            test.remove(responseColumn);
            
            String[] control_variables = new String[]{"C1", "C13", "C20"};

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(family);
            params._response_column = responseColumn;
            params._train = train._key;
            params._control_variables = control_variables;
            params._score_each_iteration = true;

            // train model with control variables enabled
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            System.out.println("_________________________________");
            System.out.println(glm);
            System.out.println("______");
            
            preds = glm.score(test);
            Scope.track_generic(preds);

            // train model with control variables disabled
            params._control_variables = null;

            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            preds2 = glm2.score(test);
            Scope.track_generic(preds2);

            // check result training metrics are not the same
            double delta = 10e-10;
            assertNotEquals(glm.auc(), glm2.auc(), delta);
            assertNotEquals(glm.mse(), glm2.mse(), delta);
            assertNotEquals(glm.logloss(), glm2.logloss(), delta);
            
            double tMse = glm._output._training_metrics._MSE;
            double tMse2 = glm2._output._training_metrics._MSE;
            System.out.println(tMse+" "+tMse2);
            assertNotEquals(tMse, tMse2, delta);

            // check result training metrics unrestricted model and glm model with control variables disabled are the same
            assertEquals(glm2._output._training_metrics.auc_obj()._auc, glm._output._training_metrics_unrestricted_model.auc_obj()._auc, delta);
            assertEquals(glm2._output._training_metrics.mse(), glm._output._training_metrics_unrestricted_model.mse(), delta);
            assertEquals(glm2._output._training_metrics.rmse(), glm._output._training_metrics_unrestricted_model.rmse(), delta);
            
            // check preds differ
            int differ = 0;
            int testRowNumber = 100;
            double threshold = (2 * testRowNumber)/1.1;
            for (int i = 0; i < testRowNumber; i++) {
                if(preds.vec(1).at(i) != preds2.vec(1).at(i)) differ++;
                if(preds.vec(2).at(i) != preds2.vec(2).at(i)) differ++;
            }
            System.out.println(differ + " " + threshold);
            assertTrue(differ > threshold);

            System.out.println("Scoring history control val enabled");
            TwoDimTable glmSH = glm._output._scoring_history;
            System.out.println(glmSH);
            System.out.println("Scoring history control val disabled");
            TwoDimTable glm2SH = glm2._output._scoring_history;
            System.out.println(glm2SH);
            System.out.println("Scoring history control val enabled unrestricted model");
            TwoDimTable glmSHCV = glm._output._scoring_history_unrestricted_model;
            System.out.println(glmSHCV);
            System.out.println("Scoring history control val disabled unrestricted model");
            TwoDimTable glm2SHCV = glm2._output._scoring_history_unrestricted_model;
            System.out.println(glm2SHCV);
            
            
            // check scoring history is the same (instead of timestamp and duration column)
            // change table header because it contains " unrestricted model"
            glm2SH.setTableHeader(glmSHCV.getTableHeader());
            assertTwoDimTableEquals(glmSHCV, glm2SH, new int[]{0,1});
            
            // check control val scoring history is not null when control vals is enabled
            assertNotNull(glmSHCV);

            // check control val scoring history is null when control vals is disabled
            assertNull(glm2SHCV);
            
            //check variable importance
            TwoDimTable vi = glm._output._variable_importances;
            TwoDimTable vi_unrestricted = glm._output._variable_importances_unrestricted_model;
            TwoDimTable vi_unrestristed_2 = glm2._output._variable_importances;

            // Restricted and unrestricted varimp contain the same variables but in different order
            // (control variables have zero importance in restricted model, so they sort to the bottom)
            assertEquals(new HashSet<>(Arrays.asList(vi.getRowHeaders())),
                    new HashSet<>(Arrays.asList(vi_unrestricted.getRowHeaders())));
            assertArrayEquals(vi_unrestricted.getRowHeaders(), vi_unrestristed_2.getRowHeaders());

        } finally {
            if(train != null) train.remove();
            if(test != null) test.remove();
            if(preds != null) preds.remove();
            if(glm != null) glm.remove();
            if(preds2 != null) preds2.remove();
            if(glm2 != null) glm2.remove();
            Scope.exit();
        }
    }
    
    @Test
    public void testTrainScoreDifferFromScore0() {
        Frame train = null;
        Frame test = null;
        Frame preds = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            // set cat columns
            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            int response_index = numCols - 1;

            train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();

            DKV.put(train);
            Scope.track_generic(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(family);
            params._response_column = responseColumn;
            params._train = train._key;
            params._control_variables = new String[]{"C1", "C13", "C20"};

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            test = new Frame(train);
            test.remove(glm._output.responseName());
            preds = glm.score(test);
            Scope.track_generic(preds);

            glm.adaptTestForTrain(test, true, false);
            test.remove(test.numCols() - 1); // remove response
            test.add(preds.names(), preds.vecs());

            DKV.put(test);
            Scope.track_generic(test);

            new GLMTest.TestScore0(glm, false, false).doAll(test);
        } catch(DistributedException e){
            System.out.println("This test should failed. Score should differ from score0, because of control variables.");
            System.out.println(e);
        } finally {
            if(train != null) train.remove();
            if(test != null) test.remove();
            if(preds != null) preds.remove();
            if(glm != null) glm.remove();
            Scope.exit();
        }
    }
    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testIncorrectControlVariable(){
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"bla"};
            glm = new GLM(params).trainModel().get();
        } finally {
            if(train != null) train.remove();
            if(glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariablePresentInFrame(){
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._ignored_columns = new String[]{"x1"};
            params._control_variables = new String[]{"x1"};
            glm = new GLM(params).trainModel().get();
        } finally {
            if(train != null) train.remove();
            if(glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableWithInteraction() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            
            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"x2"};
            params._interactions = new String[]{"x1", "x2"};
            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableAsWeightsColumn() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);


            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._weights_column = "x1";
            glm = new GLM(params).trainModel().get();
            
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableAsOffsetColumn() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._offset_column = "x1";
            glm = new GLM(params).trainModel().get();

        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableAsResponseColumn() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"y"};
            glm = new GLM(params).trainModel().get();

        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableInIgnoredColumns() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._ignored_columns = new String[]{"x1"};
            glm = new GLM(params).trainModel().get();

        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableMultinomial() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,2,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._distribution = DistributionFamily.multinomial;
            glm = new GLM(params).trainModel().get();

        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariableOrdinal() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,2,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._distribution = DistributionFamily.ordinal;
            glm = new GLM(params).trainModel().get();

        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }
    
    @Test
    public void testBasicDataGaussianControlVariables(){
        /** Test against GLM in R 
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * res <- c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1)
         * data <- data.frame(cat1, cat2, res)
         * glm <- glm(res ~ cat1 + cat2, data=data)
         * summary(glm)
         * predict(glm)
         *
         * Call:
         * glm(formula = res ~ cat1 + cat2, data = data)
         *
         * Deviance Residuals: 
         *     Min       1Q   Median       3Q      Max  
         * -0.7586  -0.4655   0.2759   0.3103   0.5345  
         *
         * Coefficients:
         *             Estimate Std. Error t value Pr(>|t|)  
         * (Intercept)  0.46552    0.17694   2.631   0.0149 *
         * cat11        0.22414    0.20011   1.120   0.2742  
         * cat21        0.06897    0.20192   0.342   0.7358  
         * ---
         * Signif. codes:  0 ‘***’ 0.001 ‘**’ 0.01 ‘*’ 0.05 ‘.’ 0.1 ‘ ’ 1
         *
         * (Dispersion parameter for gaussian family taken to be 0.2533733)
         *
         *     Null deviance: 6.1538  on 25  degrees of freedom
         * Residual deviance: 5.8276  on 23  degrees of freedom
         * AIC: 42.902
         *
         *         1         2         3         4         5         6         7         8 
         * 0.7586207 0.6896552 0.7586207 0.4655172 0.4655172 0.6896552 0.6896552 0.5344828 
         *         9        10        11        12        13        14        15        16 
         * 0.5344828 0.6896552 0.5344828 0.6896552 0.4655172 0.7586207 0.6896552 0.7586207 
         *        17        18        19        20        21        22        23        24 
         * 0.4655172 0.4655172 0.5344828 0.5344828 0.6896552 0.6896552 0.7586207 0.6896552 
         *        25        26 
         * 0.5344828 0.4655172 
         */
        
        Frame train = null;
        GLMModel glm = null;
        GLMModel glmControl = null;
        Frame preds = null;
        Frame predsControl = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._non_negative = true;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = DistributionFamily.gaussian;
            params._link = GLMModel.GLMParameters.Link.identity;
            params._max_iterations = 2;
            params._dispersion_epsilon = 0.2533733;
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);
            System.out.println(preds.toTwoDimTable().toString());
            
            System.out.println(glm._output._variable_importances);
            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);

            params._control_variables = new String[]{"cat1"};
            glmControl = new GLM(params).trainModel().get();
            predsControl = glmControl.score(train);
            System.out.println(predsControl.toTwoDimTable().toString());
            System.out.println(glmControl._output._variable_importances);
            System.out.println(glmControl.coefficients().toString());
            Double[] coefficientsControl = glmControl.coefficients().values().toArray(new Double[0]);
            
            Double[] coefficientsR = new Double[]{0.22414, 0.06897, 0.46552};

            Vec predsRVec = Vec.makeVec(new double[]{0.7586207, 0.6896552, 0.7586207, 0.4655172, 0.4655172, 0.6896552, 0.6896552,
                    0.5344828, 0.5344828, 0.6896552, 0.5344828, 0.6896552, 0.4655172, 0.7586207, 0.6896552, 0.7586207, 0.4655172,
                    0.4655172, 0.5344828, 0.5344828, 0.6896552, 0.6896552, 0.7586207, 0.6896552, 0.5344828, 0.4655172},Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});
            
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR");
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, train, "manualPredsH2o");
            Frame manualPredsControl = scoreManualWithCoefficients(coefficientsControl, train, "manualPredsControl", new int[]{0});
            Frame manualPredsRControl = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR", new int[]{0});
            
            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(0).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                double r = predsR.vec(0).at(i);
                double manualR = manualPredsR.vec(0).at(i);
                double h2oControl = predsControl.vec(0).at(i);
                double manualH2oControl = manualPredsControl.vec(0).at(i);
                double manualRControl = manualPredsRControl.vec(0).at(i);
                
                System.out.println("h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o control: "+h2oControl+" h2o control manual "+manualH2oControl+
                        " R control manual: "+manualRControl);
                
                // glm score calculation check
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);
                
                // control values calculation check
                Assert.assertEquals(h2oControl, manualH2oControl, tol);
                Assert.assertEquals(h2oControl, manualRControl, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmControl != null) glmControl.remove();
            if (preds != null) preds.remove();
            if (predsControl != null) predsControl.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataBinomialControlVariables(){
        /** Test against GLM in R 
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * res <- factor(c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1))
         * data <- data.frame(cat1, cat2, res)
         * glm <- glm(res ~ cat1 + cat2, data=data, family=binomial)
         * summary(glm)
         * predict(glm)
         *
         * Call:
         * glm(formula = res ~ cat1 + cat2, family = binomial, data = data)
         *
         * Deviance Residuals: 
         *     Min       1Q   Median       3Q      Max  
         * -1.6744  -1.1127   0.8047   0.8576   1.2435  
         *
         * Coefficients:
         *             Estimate Std. Error z value Pr(>|z|)
         * (Intercept)  -0.1542     0.7195  -0.214    0.830
         * cat11         0.9651     0.8419   1.146    0.252
         * cat21         0.3083     0.8541   0.361    0.718
         *
         * (Dispersion parameter for binomial family taken to be 1)
         *
         *     Null deviance: 34.646  on 25  degrees of freedom
         * Residual deviance: 33.256  on 23  degrees of freedom
         * AIC: 39.256
         *
         * Number of Fisher Scoring iterations: 4
         *
         *          1          2          3          4          5          6          7 
         *  1.1192316  0.8109302  1.1192316 -0.1541507 -0.1541507  0.8109302  0.8109302 
         *          8          9         10         11         12         13         14 
         *  0.1541507  0.1541507  0.8109302  0.1541507  0.8109302 -0.1541507  1.1192316 
         *         15         16         17         18         19         20         21 
         *  0.8109302  1.1192316 -0.1541507 -0.1541507  0.1541507  0.1541507  0.8109302 
         *         22         23         24         25         26 
         *  0.8109302  1.1192316  0.8109302  0.1541507 -0.1541507 
         */

        Frame train = null;
        GLMModel glm = null;
        GLMModel glmControl = null;
        Frame preds = null;
        Frame predsControl = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);
            
            DistributionFamily family = DistributionFamily.bernoulli;

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._non_negative = true;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = family;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._max_iterations = 4;
            params._dispersion_epsilon = 1;
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);
            System.out.println(preds.toTwoDimTable().toString());

            System.out.println(glm._output._variable_importances);
            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);

            params._control_variables = new String[]{"cat1"};
            glmControl = new GLM(params).trainModel().get();
            predsControl = glmControl.score(train);
            System.out.println(predsControl.toTwoDimTable().toString());
            System.out.println(glmControl._output._variable_importances);
            System.out.println(glmControl.coefficients().toString());
            Double[] coefficientsControl = glmControl.coefficients().values().toArray(new Double[0]);

            Double[] coefficientsR = new Double[]{0.9651, 0.3083, -0.1542};
            Vec predsRVec = Vec.makeVec(new double[]{1.1192316, 0.8109302, 1.1192316,-0.1541507,-0.1541507, 0.8109302, 0.8109302, 
                    0.1541507, 0.1541507, 0.8109302, 0.1541507, 0.8109302, -0.1541507, 1.1192316, 0.8109302, 1.1192316, -0.1541507, 
                    -0.1541507, 0.1541507, 0.1541507, 0.8109302, 0.8109302, 1.1192316, 0.8109302, 0.1541507, -0.1541507},Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});

            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR", family);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, train, "manualPredsH2o", family);
            Frame manualPredsControl = scoreManualWithCoefficients(coefficientsControl, train, "manualPredsControl", new int[]{0}, family);
            Frame manualPredsRControl = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR", new int[]{0}, family);

            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(2).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                // for some reason the predict output from glm in R is not in logit
                double r = 1.0 / (Math.exp(-predsR.vec(0).at(i)) + 1.0);
                double manualR = manualPredsR.vec(0).at(i);
                double h2oControl = predsControl.vec(2).at(i);
                double manualH2oControl = manualPredsControl.vec(0).at(i);
                double manualRControl = manualPredsRControl.vec(0).at(i);

                System.out.println(i+" h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o control: "+h2oControl+" h2o control manual "+manualH2oControl+
                        " R control manual: "+manualRControl);

                // glm score calculation check
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);

                // control values calculation check
                Assert.assertEquals(h2oControl, manualH2oControl, tol);
                Assert.assertEquals(h2oControl, manualRControl, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmControl != null) glmControl.remove();
            if (preds != null) preds.remove();
            if (predsControl != null) predsControl.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName){
        return scoreManualWithCoefficients(coefficients, data, frameName, null, null, null);
    }

    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, DistributionFamily family){
        return scoreManualWithCoefficients(coefficients, data, frameName, null, family, null);
    }

    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, int[] controlVariablesIdx){
        return scoreManualWithCoefficients(coefficients, data, frameName, controlVariablesIdx, null, null);
    }

    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, int[] controlVariablesIdx, DistributionFamily family){
        return scoreManualWithCoefficients(coefficients, data, frameName, controlVariablesIdx, family, null);
    }

    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, DistributionFamily family, Vec offset){
        return scoreManualWithCoefficients(coefficients, data, frameName, null, family, offset);
    }
    
    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, int[] controlVariablesIdx, DistributionFamily family, Vec offset){
        Vec predictions = Vec.makeZero(data.numRows(), Vec.T_NUM);
        for (long i = 0; i < data.numRows(); i++) {
            double prediction = 0;
            for (int j = 0; j < data.numCols()-1; j++) {
                if(controlVariablesIdx == null || Arrays.binarySearch(controlVariablesIdx, j) < 0) {
                    double coefficient = coefficients[j];
                    double datapoint = data.vec(j).at(i);
                    prediction += coefficient * datapoint;
                }
            }
            prediction += coefficients[coefficients.length-1];
            if (offset != null) prediction += offset.at(i);
            if (DistributionFamily.bernoulli.equals(family)) {
                prediction = 1.0 / (Math.exp(-prediction) + 1.0);
            } else if(DistributionFamily.tweedie.equals(family)) {
                prediction = Math.exp(prediction);
            }
            predictions.set(i, prediction);
        }
        return new Frame(Key.<Frame>make(frameName),new String[]{"predict"},new Vec[]{predictions});
    }

    @Test
    public void compareModelWithOffsetEnabledAndDisabled() {
        Frame train = null;
        Frame test = null;
        Frame preds = null;
        GLMModel glm = null;
        Frame preds2 = null;
        GLMModel glm2 = null;
        try {
            Scope.enter();
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            // set cat columns
            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            int response_index = numCols - 1;

            train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();

            DKV.put(train);
            Scope.track_generic(train);

            test = new Frame(train);
            test.remove(responseColumn);


            GLMModel.GLMParameters params = new GLMModel.GLMParameters(family);
            params._response_column = responseColumn;
            params._train = train._key;
            params._score_each_iteration = true;
            params._offset_column = "C20";
            params._remove_offset_effects = true;

            // train model with remove offset effects enabled
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            System.out.println("_________________________________");
            System.out.println(glm);
            System.out.println("______");

            preds = glm.score(test);
            Scope.track_generic(preds);

            // train model with offset effect removed
            params._remove_offset_effects = false;

            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            preds2 = glm2.score(test);
            Scope.track_generic(preds2);

            // check result training metrics are not the same
            double delta = 10e-10;
            assertNotEquals(glm.auc(), glm2.auc(), delta);
            assertNotEquals(glm.mse(), glm2.mse(), delta);
            //assertNotEquals(glm.logloss(), glm2.logloss(), delta);

            double tMse = glm._output._training_metrics._MSE;
            double tMse2 = glm2._output._training_metrics._MSE;
            System.out.println(tMse+" "+tMse2);
            assertNotEquals(tMse, tMse2, delta);

            // check result training metrics unrestricted model and glm model with remove offset effects disabled are the same
            assertEquals(glm2._output._training_metrics.auc_obj()._auc, glm._output._training_metrics_unrestricted_model.auc_obj()._auc, delta);
            assertEquals(glm2._output._training_metrics.mse(), glm._output._training_metrics_unrestricted_model.mse(), delta);
            assertEquals(glm2._output._training_metrics.rmse(), glm._output._training_metrics_unrestricted_model.rmse(), delta);

            // check preds differ
            int differ = 0;
            int testRowNumber = 100;
            double threshold = (2 * testRowNumber)/1.1;
            for (int i = 0; i < testRowNumber; i++) {
                if(preds.vec(1).at(i) != preds2.vec(1).at(i)) differ++;
                if(preds.vec(2).at(i) != preds2.vec(2).at(i)) differ++;
            }
            
            assertTrue("Expected number of differing predictions to exceed threshold", differ > threshold);

            System.out.println("Scoring history remove offset enabled");
            TwoDimTable glmSH = glm._output._scoring_history;
            System.out.println(glmSH);
            System.out.println("Scoring history remove offset disabled");
            TwoDimTable glm2SH = glm2._output._scoring_history;
            System.out.println(glm2SH);
            System.out.println("Scoring history remove offset enabled unrestricted model");
            TwoDimTable glmSHROE = glm._output._scoring_history_unrestricted_model;
            System.out.println(glmSHROE);
            System.out.println("Scoring history remove offset disabled unrestricted model");
            TwoDimTable glm2SHROE = glm2._output._scoring_history_unrestricted_model;
            System.out.println(glm2SHROE);
            
            // check scoring history is the same (instead of timestamp and duration column)
            // change table header because it contains " unrestricted model"
            glm2SH.setTableHeader(glmSHROE.getTableHeader());
            assertTwoDimTableEquals(glmSHROE, glm2SH, new int[]{0,1});

            // check control val scoring history is not null when remove offset effects feature is enabled
            assertNotNull(glmSHROE);

            // check control val scoring history is null when remove offset effects feature is disabled
            assertNull(glm2SHROE);

            //check variable importance
            TwoDimTable vi = glm._output._variable_importances;
            TwoDimTable vi_unrestricted = glm._output._variable_importances_unrestricted_model;
            TwoDimTable vi_unrestristed_2 = glm2._output._variable_importances;

            // Restricted and unrestricted varimp contain the same variables but in different order
            // (control variables have zero importance in restricted model, so they sort to the bottom)
            assertEquals(new HashSet<>(Arrays.asList(vi.getRowHeaders())),
                    new HashSet<>(Arrays.asList(vi_unrestricted.getRowHeaders())));
            assertArrayEquals(vi_unrestricted.getRowHeaders(), vi_unrestristed_2.getRowHeaders());

        } finally {
            if(train != null) train.remove();
            if(test != null) test.remove();
            if(preds != null) preds.remove();
            if(glm != null) glm.remove();
            if(preds2 != null) preds2.remove();
            if(glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    @Test
    public void compareModelWithOffsetAndControlVariablesEnabledAndDisabled() {
        Frame train = null;
        Frame test = null;
        Frame preds = null;
        GLMModel glm = null;
        Frame preds2 = null;
        GLMModel glm2 = null;
        try {
            Scope.enter();
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            // set cat columns
            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            int response_index = numCols - 1;

            train.replace((response_index), train.vec(response_index).toCategoricalVec()).remove();

            DKV.put(train);
            Scope.track_generic(train);

            test = new Frame(train);
            test.remove(responseColumn);


            GLMModel.GLMParameters params = new GLMModel.GLMParameters(family);
            params._response_column = responseColumn;
            params._train = train._key;
            params._score_each_iteration = true;
            params._offset_column = "C20";
            params._remove_offset_effects = true;
            params._control_variables = new String[]{"C5"};

            // train model with remove offset effects enabled
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            System.out.println("_________________________________");
            System.out.println(glm);
            System.out.println("______");

            preds = glm.score(test);
            Scope.track_generic(preds);

            // train model with offset effect removed
            params._remove_offset_effects = false;
            params._control_variables = null;

            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            preds2 = glm2.score(test);
            Scope.track_generic(preds2);

            // check result training metrics are not the same
            double delta = 10e-10;
            assertNotEquals(glm.auc(), glm2.auc(), delta);
            assertNotEquals(glm.mse(), glm2.mse(), delta);
            //assertNotEquals(glm.logloss(), glm2.logloss(), delta);

            double tMse = glm._output._training_metrics._MSE;
            double tMse2 = glm2._output._training_metrics._MSE;
            System.out.println(tMse+" "+tMse2);
            assertNotEquals(tMse, tMse2, delta);

            // check result training metrics unrestricted model and glm model with remove offset effects disabled are the same
            assertEquals(glm2._output._training_metrics.auc_obj()._auc, glm._output._training_metrics_unrestricted_model.auc_obj()._auc, delta);
            assertEquals(glm2._output._training_metrics.mse(), glm._output._training_metrics_unrestricted_model.mse(), delta);
            assertEquals(glm2._output._training_metrics.rmse(), glm._output._training_metrics_unrestricted_model.rmse(), delta);

            // check preds differ
            int differ = 0;
            int testRowNumber = 100;
            double threshold = (2 * testRowNumber)/1.1;
            for (int i = 0; i < testRowNumber; i++) {
                if(preds.vec(1).at(i) != preds2.vec(1).at(i)) differ++;
                if(preds.vec(2).at(i) != preds2.vec(2).at(i)) differ++;
            }
            System.out.println(differ + " " + threshold);
            assertTrue(differ > threshold);

            System.out.println("Scoring history remove offset enabled");
            TwoDimTable glmSH = glm._output._scoring_history;
            System.out.println(glmSH);
            System.out.println("Scoring history remove offset disabled");
            TwoDimTable glm2SH = glm2._output._scoring_history;
            System.out.println(glm2SH);
            System.out.println("Scoring history remove offset enabled unrestricted model");
            TwoDimTable glmSHCV = glm._output._scoring_history_unrestricted_model;
            System.out.println(glmSHCV);
            System.out.println("Scoring history remove offset disabled unrestricted model");
            TwoDimTable glm2SHCV = glm2._output._scoring_history_unrestricted_model;
            System.out.println(glm2SHCV);

            // check scoring history is the same (instead of timestamp and duration column)
            // change table header because it contains " unrestricted model"
            glm2SH.setTableHeader(glmSHCV.getTableHeader());
            assertTwoDimTableEquals(glmSHCV, glm2SH, new int[]{0,1});

            // check control val scoring history is not null when control vals is enabled
            assertNotNull(glmSHCV);

            // check control val scoring history is null when control vals is disabled
            assertNull(glm2SHCV);

            //check variable importance
            TwoDimTable vi = glm._output._variable_importances;
            TwoDimTable vi_unrestricted = glm._output._variable_importances_unrestricted_model;
            TwoDimTable vi_unrestristed_2 = glm2._output._variable_importances;

            // Restricted and unrestricted varimp contain the same variables but in different order
            // (control variables have zero importance in restricted model, so they sort to the bottom)
            assertEquals(new HashSet<>(Arrays.asList(vi.getRowHeaders())),
                    new HashSet<>(Arrays.asList(vi_unrestricted.getRowHeaders())));
            assertArrayEquals(vi_unrestricted.getRowHeaders(), vi_unrestristed_2.getRowHeaders());
        } finally {
            if(train != null) train.remove();
            if(test != null) test.remove();
            if(preds != null) preds.remove();
            if(glm != null) glm.remove();
            if(preds2 != null) preds2.remove();
            if(glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testRemoveOffsetEffectsMissingOffsetColumn() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"a","b"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,2,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            glm = new GLM(params).trainModel().get();

        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }


    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testRemoveOffsetEffectsMultinomial() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0},new String[]{"black","red"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new double[]{1,1,1,0,0}, cat1.group().addVec());
            Vec res = Vec.makeVec(new double[]{1,1,2,0,0},cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{cat1, cat2, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._remove_offset_effects = true;
            params._offset_column = "x2";
            params._distribution = DistributionFamily.multinomial;
            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * Guard added in init(): _family=multinomial (the GLM-specific enum, not _distribution) must
     * also be blocked. The old validate() check used _distribution which stays AUTO for GLM, so
     * a 3-class response with _family=multinomial + remove_offset_effects would silently pass
     * validation and run a wasteful dual-pass CV before this fix.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveOffsetEffectsMultinomialViaFamily() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            // 3-class categorical response so Family.multinomial is valid on its own.
            Vec x = Vec.makeVec(new double[]{1,2,3,1,2,3,1,2,3,1}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{.1,.2,.1,.2,.1,.2,.1,.2,.1,.2}, Vec.newKey());
            Vec y = Vec.makeVec(new long[]{0,1,2,0,1,2,0,1,2,0}, new String[]{"a","b","c"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("test_ro_multinomial_family"),
                    new String[]{"x", "offset", "y"}, new Vec[]{x, offset, y});
            DKV.put(train);
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.multinomial);
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._remove_offset_effects = true;
            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataBinomialOffset(){
        /** Test against GLM in R 
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * offset <- c(0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0)
         * res <- factor(c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1))
         * data <- data.frame(cat1, cat2, offset, res)
         * glm <- glm(res ~ cat1 + cat2 + offset(offset), data=data, family = binomial)
         * summary(glm)
         * predict(glm)
         *
         * Call:
         * glm(formula = res ~ cat1 + cat2 + offset(offset), family = binomial, 
         *     data = data)
         *
         * Coefficients:
         *             Estimate Std. Error z value Pr(>|z|)
         * (Intercept)  -0.3310     0.7256  -0.456    0.648
         * cat11         0.9780     0.8467   1.155    0.248
         * cat21         0.2295     0.8586   0.267    0.789
         *
         * (Dispersion parameter for binomial family taken to be 1)
         *
         *     Null deviance: 33.557  on 25  degrees of freedom
         * Residual deviance: 32.173  on 23  degrees of freedom
         * AIC: 38.173
         *
         * Number of Fisher Scoring iterations: 4
         *
         *            1            2            3            4            5            6 
         *  0.976506946  0.847045758  1.076506946 -0.130997049 -0.230997049  0.647045758 
         *            7            8            9           10           11           12 
         *  0.647045758  0.098464139  0.198464139  1.147045758  0.198464139  1.047045758 
         *           13           14           15           16           17           18 
         *  0.469002951  1.276506946  1.047045758  1.376506946 -0.330997049 -0.330997049 
         *           19           20           21           22           23           24 
         *  0.398464139 -0.001535861  0.647045758  0.647045758  0.976506946  0.647045758 
         *           25           26 
         * -0.001535861 -0.330997049
         **/
        Frame train = null;
        GLMModel glm = null;
        GLMModel glmOffset = null;
        Frame preds = null;
        Frame predsOffset = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            DistributionFamily family = DistributionFamily.bernoulli;
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = family;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._max_iterations = 4;
            params._dispersion_epsilon = 1;
            params._offset_column = "offset";
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);
            System.out.println(preds.toTwoDimTable().toString());

            System.out.println(glm._output._variable_importances);
            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);
            
            params._remove_offset_effects = true;
            glmOffset = new GLM(params).trainModel().get();
            predsOffset = glmOffset.score(train);
            System.out.println(predsOffset.toTwoDimTable().toString());
            Double[] coefficientsOffset = glmOffset.coefficients().values().toArray(new Double[0]);

            Double[] coefficientsR = new Double[]{0.9780, 0.2295, -0.3310};
            Vec predsRVec = Vec.makeVec(new double[]{0.976506946, 0.847045758, 1.076506946, -0.130997049, -0.230997049, 
                    0.647045758, 0.647045758, 0.098464139, 0.198464139, 1.147045758, 0.198464139, 1.047045758, 
                    0.469002951, 1.276506946, 1.047045758, 1.376506946, -0.330997049, -0.330997049, 0.398464139,
                    -0.001535861, 0.647045758, 0.647045758, 0.976506946, 0.647045758, -0.001535861, -0.330997049},
                    Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});

            Frame trainWithoutOffset = train.deepCopy("trainWithoutOffset");
            Vec offsetVec = trainWithoutOffset.remove("offset");
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", family, offsetVec);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, trainWithoutOffset, "manualPredsH2o", family, offsetVec);
            Frame manualPredsRemoveOffset = scoreManualWithCoefficients(coefficientsOffset, trainWithoutOffset, "manualPredsRemoveOffset", family);
            Frame manualPredsRRemoveOffset = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", family);

            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(2).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                // predict output from glm in R is not in logit
                double r = (1.0 / (Math.exp(-predsR.vec(0).at(i)) + 1.0));
                double manualR = manualPredsR.vec(0).at(i);
                double h2oOffset = predsOffset.vec(2).at(i);
                double manualH2oOffset = manualPredsRemoveOffset.vec(0).at(i);
                double manualROffset = manualPredsRRemoveOffset.vec(0).at(i);

                System.out.println(i+" h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o remove offset: "+h2oOffset+" h2o remove offset manual "+manualH2oOffset+
                        " R remove offset manual: "+manualROffset);

                // glm score calculation check
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);

                // offset calculation check
                Assert.assertEquals(h2oOffset, manualH2oOffset, tol);
                Assert.assertEquals(h2oOffset, manualROffset, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmOffset != null) glmOffset.remove();
            if (preds != null) preds.remove();
            if (predsOffset != null) predsOffset.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataGaussianOffset(){
        /** Test against GLM in R 
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * offset <- c(0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0)
         * res <- c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1)
         * data <- data.frame(cat1, cat2, res, offset)
         * glm <- glm(res ~ cat1 + cat2 + offset(offset), data=data)
         * summary(glm)
         * predict(glm)
         *
         * Call:
         * glm(formula = res ~ cat1 + cat2 + offset(offset), data = data)
         *
         * Coefficients:
         *             Estimate Std. Error t value Pr(>|t|)
         * (Intercept)  0.28908    0.17334   1.668    0.109
         * cat11        0.22931    0.19604   1.170    0.254
         * cat21       -0.01149    0.19782  -0.058    0.954
         *
         * (Dispersion parameter for gaussian family taken to be 0.2431734)
         *
         *     Null deviance: 5.9385  on 25  degrees of freedom
         * Residual deviance: 5.5930  on 23  degrees of freedom
         * AIC: 41.834
         *
         * Number of Fisher Scoring iterations: 2
         *
         *         1         2         3         4         5         6         7         8 
         * 0.6068966 0.7183908 0.7068966 0.4890805 0.3890805 0.5183908 0.5183908 0.4775862 
         *         9        10        11        12        13        14        15        16 
         * 0.5775862 1.0183908 0.5775862 0.9183908 1.0890805 0.9068966 0.9183908 1.0068966 
         *        17        18        19        20        21        22        23        24 
         * 0.2890805 0.2890805 0.7775862 0.3775862 0.5183908 0.5183908 0.6068966 0.5183908 
         *        25        26 
         * 0.3775862 0.2890805
         * */

        Frame train = null;
        GLMModel glm = null;
        GLMModel glmOffset = null;
        Frame preds = null;
        Frame predsOffset = null;
        Frame predsR = null;
        try {
            Scope.enter();
            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1},cat1.group().addVec());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = DistributionFamily.gaussian;
            params._link = GLMModel.GLMParameters.Link.identity;
            params._max_iterations = 2;
            params._dispersion_epsilon = 0.2431734;
            params._offset_column = "offset";
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);
            
            System.out.println(preds.toTwoDimTable().toString());
            System.out.println(glm._output._variable_importances);
            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);

            params._remove_offset_effects = true;
            glmOffset = new GLM(params).trainModel().get();
            predsOffset = glmOffset.score(train);
            System.out.println(predsOffset.toTwoDimTable().toString());
            System.out.println(glmOffset._output._variable_importances);
            System.out.println(glmOffset.coefficients().toString());
            Double[] coefficientsOffset = glmOffset.coefficients().values().toArray(new Double[0]);

            Double[] coefficientsR = new Double[]{0.22931, -0.01149, 0.28908};

            Vec predsRVec = Vec.makeVec(new double[]{0.6068966, 0.7183908, 0.7068966, 0.4890805, 0.3890805, 0.5183908, 
                    0.5183908, 0.4775862, 0.5775862, 1.0183908, 0.5775862, 0.9183908, 1.0890805, 0.9068966, 0.9183908,
                    1.0068966, 0.2890805, 0.2890805, 0.7775862, 0.3775862, 0.5183908, 0.5183908, 0.6068966, 0.5183908, 
                    0.3775862, 0.2890805},Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});

            System.out.println("GLM offset ceoef:       " + glm.coefficients().toString());
            System.out.println("GLM remove offset coef: " + glmOffset.coefficients().toString());
            System.out.println("GLM R offset coef:      " + Arrays.toString(coefficientsR));

            Frame trainWithoutOffset = train.deepCopy("trainWithoutOffset");
            Vec offsetVec = trainWithoutOffset.remove("offset");
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", null, offsetVec);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, trainWithoutOffset, "manualPredsH2o", null, offsetVec);
            Frame manualPredsOffset = scoreManualWithCoefficients(coefficientsOffset, trainWithoutOffset, "manualPredsOffset");
            Frame manualPredsROffset = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsROffset");

            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(0).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                double r = predsR.vec(0).at(i);
                double manualR = manualPredsR.vec(0).at(i);
                double h2oOffset = predsOffset.vec(0).at(i);
                double manualH2oOffset = manualPredsOffset.vec(0).at(i);
                double manualROffset = manualPredsROffset.vec(0).at(i);

                System.out.println("h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o offset: "+h2oOffset+" h2o offset manual "+manualH2oOffset+
                        " R offset manual: "+manualROffset);

                // glm score calculation check
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);

                // offset values calculation check
                Assert.assertEquals(h2oOffset, manualH2oOffset, tol);
                Assert.assertEquals(h2oOffset, manualROffset, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmOffset != null) glmOffset.remove();
            if (preds != null) preds.remove();
            if (predsOffset != null) predsOffset.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataTweedieOffset(){
        /** Test against GLM in R
         * library(statmod)
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * offset <- c(0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0)
         * res <- c(2.1, 3.5, 1.2, 0.8, 0.5, 2.8, 1.5, 1.9, 0.7, 3.2, 1.8, 2.5, 2.0, 2.7, 3.0, 1.1, 0.4, 0.6, 1.9, 0.9, 2.3, 1.0, 2.6, 3.1, 1.4, 0.7)
         * data <- data.frame(cat1, cat2, res, offset)
         * glm <- glm(res ~ cat1 + cat2 + offset(offset), data=data, family=tweedie(var.power=1.5, link.power=0))
         * summary(glm)
         * predict(glm)
         *
         * Coefficients:
         *             Estimate Std. Error t value Pr(>|t|)
         * (Intercept) -0.13946    0.15587  -0.895 0.380193
         * cat11        0.77632    0.16762   4.631 0.000117 ***
         * cat21        0.01003    0.16399   0.061 0.951756
         *
         * (Dispersion parameter for Tweedie family taken to be 0.2186502)
         *
         * Number of Fisher Scoring iterations: 4
         *
         *          1          2          3          4          5          6
         * 0.74688735 0.83685691 0.84688735 0.06054050 -0.03945950 0.63685691
         *          7          8          9         10         11         12
         * 0.63685691 0.07057094 0.17057094 1.13685691 0.17057094 1.03685691
         *         13         14         15         16         17         18
         * 0.66054050 1.04688735 1.03685691 1.14688735 -0.13945950 -0.13945950
         *         19         20         21         22         23         24
         * 0.37057094 -0.02942906 0.63685691 0.63685691 0.74688735 0.63685691
         *         25         26
         * -0.02942906 -0.13945950
         * */

        Frame train = null;
        GLMModel glm = null;
        GLMModel glmOffset = null;
        Frame preds = null;
        Frame predsOffset = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{2.1,3.5,1.2,0.8,0.5,2.8,1.5,1.9,0.7,3.2,1.8,2.5,2.0,2.7,3.0,1.1,0.4,0.6,1.9,0.9,2.3,1.0,2.6,3.1,1.4,0.7}, cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            DistributionFamily family = DistributionFamily.tweedie;
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = family;
            params._family = GLMModel.GLMParameters.Family.tweedie;
            params._link = GLMModel.GLMParameters.Link.tweedie;
            params._tweedie_variance_power = 1.5;
            params._tweedie_link_power = 0;
            params._max_iterations = 20;
            params._offset_column = "offset";
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);

            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);
            
            params._remove_offset_effects = true;
            glmOffset = new GLM(params).trainModel().get();
            predsOffset = glmOffset.score(train);
            Double[] coefficientsOffset = glmOffset.coefficients().values().toArray(new Double[0]);

            Double[] coefficientsR = new Double[]{0.77632, 0.01003, -0.13946};

            // R predictions on link scale (log)
            Vec predsRVec = Vec.makeVec(new double[]{0.74688735, 0.83685691, 0.84688735, 0.06054050, -0.03945950, 
                    0.63685691, 0.63685691, 0.07057094, 0.17057094, 1.13685691, 0.17057094, 1.03685691, 0.66054050, 
                    1.04688735, 1.03685691, 1.14688735, -0.13945950, -0.13945950, 0.37057094, -0.02942906, 0.63685691, 
                    0.63685691, 0.74688735, 0.63685691, -0.02942906, -0.13945950}, Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});

            Frame trainWithoutOffset = train.deepCopy("trainWithoutOffset");
            Vec offsetVec = trainWithoutOffset.remove("offset");

            // Manual scoring on link scale (no inverse link applied)
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", family, offsetVec);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, trainWithoutOffset, "manualPredsH2o", family, offsetVec);
            Frame manualPredsOffset = scoreManualWithCoefficients(coefficientsOffset, trainWithoutOffset, "manualPredsOffset", family);
            Frame manualPredsROffset = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsROffset", family);

            System.out.println("GLM offset coef:        " + glm.coefficients().toString());
            System.out.println("GLM remove offset coef: " + glmOffset.coefficients().toString());
            System.out.println("GLM R offset coef:      " + Arrays.toString(coefficientsR));

            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(0).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                //  predictions in R is not in exponential values
                double r = Math.exp(predsR.vec(0).at(i));
                double manualR = manualPredsR.vec(0).at(i);
                double h2oOffset = predsOffset.vec(0).at(i);
                double manualH2oOffset = manualPredsOffset.vec(0).at(i);
                double manualROffset = manualPredsROffset.vec(0).at(i);

                System.out.println("h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o offset: "+h2oOffset+" h2o offset manual "+manualH2oOffset+
                        " R offset manual: "+manualROffset);

                // glm score calculation check
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);

                // offset values calculation check
                Assert.assertEquals(h2oOffset, manualH2oOffset, tol);
                Assert.assertEquals(h2oOffset, manualROffset, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmOffset != null) glmOffset.remove();
            if (preds != null) preds.remove();
            if (predsOffset != null) predsOffset.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataTweedieControlValuesAndOffset(){
        /** Test against GLM in R (same model as testBasicDataTweedieOffset)
         * library(statmod)
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * offset <- c(0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0)
         * res <- c(2.1, 3.5, 1.2, 0.8, 0.5, 2.8, 1.5, 1.9, 0.7, 3.2, 1.8, 2.5, 2.0, 2.7, 3.0, 1.1, 0.4, 0.6, 1.9, 0.9, 2.3, 1.0, 2.6, 3.1, 1.4, 0.7)
         * data <- data.frame(cat1, cat2, res, offset)
         * glm <- glm(res ~ cat1 + cat2 + offset(offset), data=data, family=tweedie(var.power=1.5, link.power=0))
         * summary(glm)
         *
         * Coefficients:
         *             Estimate Std. Error t value Pr(>|t|)
         * (Intercept) -0.13946    0.15587  -0.895 0.380193
         * cat11        0.77632    0.16762   4.631 0.000117 ***
         * cat21        0.01003    0.16399   0.061 0.951756
         * */

        Frame train = null;
        GLMModel glm = null;
        GLMModel glmCVOffset = null;
        Frame preds = null;
        Frame predsCVOffset = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{2.1,3.5,1.2,0.8,0.5,2.8,1.5,1.9,0.7,3.2,1.8,2.5,2.0,2.7,3.0,1.1,0.4,0.6,1.9,0.9,2.3,1.0,2.6,3.1,1.4,0.7}, cat1.group().addVec());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            DistributionFamily family = DistributionFamily.tweedie;
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._non_negative = false;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = family;
            params._family = GLMModel.GLMParameters.Family.tweedie;
            params._link = GLMModel.GLMParameters.Link.tweedie;
            params._tweedie_variance_power = 1.5;
            params._tweedie_link_power = 0;
            params._max_iterations = 20;
            params._offset_column = "offset";
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);

            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);
            
            params._control_variables = new String[]{"cat1"};
            params._remove_offset_effects = true;
            glmCVOffset = new GLM(params).trainModel().get();
            predsCVOffset = glmCVOffset.score(train);
            Double[] coefficientsCVOffset = glmCVOffset.coefficients().values().toArray(new Double[0]);

            Double[] coefficientsR = new Double[]{0.77632, 0.01003, -0.13946};

            Vec predsRVec = Vec.makeVec(new double[]{0.74688735, 0.83685691, 0.84688735, 0.06054050, -0.03945950, 0.63685691,
                    0.63685691, 0.07057094, 0.17057094, 1.13685691, 0.17057094, 1.03685691,
                    0.66054050, 1.04688735, 1.03685691, 1.14688735, -0.13945950, -0.13945950,
                    0.37057094, -0.02942906, 0.63685691, 0.63685691, 0.74688735, 0.63685691,
                    -0.02942906, -0.13945950}, Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});

            Frame trainWithoutOffset = train.deepCopy("trainWithoutOffset");
            Vec offsetVec = trainWithoutOffset.remove("offset");

            // Manual scoring: unrestricted model (with offset)
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", family, offsetVec);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, trainWithoutOffset, "manualPredsH2o", family, offsetVec);
            // Manual scoring: restricted model (cat1 zeroed out, no offset)
            Frame manualPredsCVOffset = scoreManualWithCoefficients(coefficientsCVOffset, trainWithoutOffset, "manualPredsCVRemoveOffset", new int[]{0}, family);
            Frame manualPredsRCVOffset = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsRCVRemoveOffset", new int[]{0}, family);

            System.out.println("GLM coef:                         " + glm.coefficients().toString());
            System.out.println("GLM CV + remove offset coef:      " + glmCVOffset.coefficients().toString());
            System.out.println("GLM R coef:                       " + Arrays.toString(coefficientsR));

            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(0).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                //  predictions in R is not in exponential values
                double r = Math.exp(predsR.vec(0).at(i));
                double manualR = manualPredsR.vec(0).at(i);
                double h2oCVOffset = predsCVOffset.vec(0).at(i);
                double manualH2oCVOffset = manualPredsCVOffset.vec(0).at(i);
                double manualRCVOffset = manualPredsRCVOffset.vec(0).at(i);

                System.out.println(i+" h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o CV+offset: "+h2oCVOffset+" h2o CV+offset manual "+manualH2oCVOffset+
                        " R CV+offset manual: "+manualRCVOffset);

                // glm score calculation check
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);

                // control variables + remove offset calculation check
                Assert.assertEquals(h2oCVOffset, manualH2oCVOffset, tol);
                Assert.assertEquals(h2oCVOffset, manualRCVOffset, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmCVOffset != null) glmCVOffset.remove();
            if (preds != null) preds.remove();
            if (predsCVOffset != null) predsCVOffset.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataBinomialControlValuesAndOffset(){
        /** Test against GLM in R 
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * offset <- c(0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0)
         * res <- factor(c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1))
         * data <- data.frame(cat1, cat2, offset, res)
         * glm <- glm(res ~ cat1 + cat2 + offset(offset), data=data, family = binomial)
         * summary(glm)
         * predict(glm)
         *
         * Call:
         * glm(formula = res ~ cat1 + cat2 + offset(offset), family = binomial, 
         *     data = data)
         *
         * Coefficients:
         *             Estimate Std. Error z value Pr(>|z|)
         * (Intercept)  -0.3310     0.7256  -0.456    0.648
         * cat11         0.9780     0.8467   1.155    0.248
         * cat21         0.2295     0.8586   0.267    0.789
         *
         * (Dispersion parameter for binomial family taken to be 1)
         *
         *     Null deviance: 33.557  on 25  degrees of freedom
         * Residual deviance: 32.173  on 23  degrees of freedom
         * AIC: 38.173
         *
         * Number of Fisher Scoring iterations: 4
         *
         *            1            2            3            4            5            6 
         *  0.976506946  0.847045758  1.076506946 -0.130997049 -0.230997049  0.647045758 
         *            7            8            9           10           11           12 
         *  0.647045758  0.098464139  0.198464139  1.147045758  0.198464139  1.047045758 
         *           13           14           15           16           17           18 
         *  0.469002951  1.276506946  1.047045758  1.376506946 -0.330997049 -0.330997049 
         *           19           20           21           22           23           24 
         *  0.398464139 -0.001535861  0.647045758  0.647045758  0.976506946  0.647045758 
         *           25           26 
         * -0.001535861 -0.330997049
         **/
        Frame train = null;
        GLMModel glm = null;
        GLMModel glmCVOffset = null;
        Frame preds = null;
        Frame predsCVOffset = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            DistributionFamily family = DistributionFamily.bernoulli;
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._non_negative = true;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = family;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._max_iterations = 4;
            params._dispersion_epsilon = 1;
            params._offset_column = "offset";
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);
            System.out.println(preds.toTwoDimTable().toString());

            System.out.println(glm._output._variable_importances);
            System.out.println(glm.coefficients().toString());
            Double[] coefficients = glm.coefficients().values().toArray(new Double[0]);

            params._control_variables = new String[]{"cat1"};
            params._remove_offset_effects = true;
            
            glmCVOffset = new GLM(params).trainModel().get();
            predsCVOffset = glmCVOffset.score(train);
            System.out.println(predsCVOffset.toTwoDimTable().toString());
            Double[] coefficientsOffset = glmCVOffset.coefficients().values().toArray(new Double[0]);

            Double[] coefficientsR = new Double[]{0.9780, 0.2295, -0.3310};
            Vec predsRVec = Vec.makeVec(new double[]{0.976506946, 0.847045758, 1.076506946, -0.130997049, -0.230997049,
                            0.647045758, 0.647045758, 0.098464139, 0.198464139, 1.147045758, 0.198464139, 1.047045758,
                            0.469002951, 1.276506946, 1.047045758, 1.376506946, -0.330997049, -0.330997049, 0.398464139,
                            -0.001535861, 0.647045758, 0.647045758, 0.976506946, 0.647045758, -0.001535861, -0.330997049},
                    Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict"},new Vec[]{predsRVec});

            Frame trainWithoutOffset = train.deepCopy("trainWithoutOffset");
            Vec offsetVec = trainWithoutOffset.remove("offset");
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", family, offsetVec);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, trainWithoutOffset, "manualPredsH2o", family, offsetVec);
            Frame manualPredsRemoveCVOffset = scoreManualWithCoefficients(coefficientsOffset, trainWithoutOffset, "manualPredsCVRemoveOffset", new int[]{0}, family);
            Frame manualPredsRRemoveCVOffset = scoreManualWithCoefficients(coefficientsR, trainWithoutOffset, "manualPredsR", new int[]{0}, family);

            double tol = 1e-3;
            for (long i = 0; i < manualPredsH2o.numRows(); i++) {
                double h2o = preds.vec(2).at(i);
                double manualH2o = manualPredsH2o.vec(0).at(i);
                // predict output from glm in R is not in logit
                double r = (1.0 / (Math.exp(-predsR.vec(0).at(i)) + 1.0));
                double manualR = manualPredsR.vec(0).at(i);
                double h2oCVOffset = predsCVOffset.vec(2).at(i);
                double manualH2oCVOffset = manualPredsRemoveCVOffset.vec(0).at(i);
                double manualRCVOffset = manualPredsRRemoveCVOffset.vec(0).at(i);

                System.out.println(i+" h2o: "+h2o+ " h2o manual:" +manualH2o+
                        " R: "+r+" R manual: "+manualR +
                        " h2o control and remove offset: "+h2oCVOffset+" h2o control variables and remove offset manual "+manualH2oCVOffset+
                        " R control variables and remove offset manual: "+manualRCVOffset);

                // glm score calculation checkmanualROffset
                Assert.assertEquals(h2o, manualH2o, tol);
                Assert.assertEquals(h2o, r, tol);
                Assert.assertEquals(h2o, manualR, tol);

                // offset calculation check
                Assert.assertEquals(h2oCVOffset, manualH2oCVOffset, tol);
                Assert.assertEquals(h2oCVOffset, manualRCVOffset, tol);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmCVOffset != null) glmCVOffset.remove();
            if (preds != null) preds.remove();
            if (predsCVOffset != null) predsCVOffset.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testRemoveOffsetWithInteraction() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._remove_offset_effects = true;
            params._offset_column = "offset";
            params._interactions = new String[]{"x1", "x2"};
            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test
    public void testRemoveOffsetWithCrossValidation() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._intercept = false;
            params._remove_offset_effects = true;
            params._offset_column = "offset";
            params._nfolds = 3;
            glm = new GLM(params).trainModel().get();
            assertNotNull("Model should train successfully with remove_offset_effects=true and nfolds=3", glm);
            assertNotNull("CV metrics should be populated", glm._output._cross_validation_metrics);
            assertNotNull("Training metrics should be populated", glm._output._training_metrics);
            System.out.println("CV metrics");
            System.out.println(glm._output._cross_validation_metrics);
            System.out.println("Scoring history");
            System.out.println(glm.scoring_history().toString());
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    // Confirms no regression: training metrics are identical between a CV run and a no-CV run
    // on the same data with the same fixed lambda, because the override's if-guard is never entered.
    @Test
    public void testCrossValidationWithoutRemoveOffsetNoRegression() {
        Frame train = null;
        GLMModel glmCV = null;
        GLMModel glmNoCV = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("train_regression");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._remove_offset_effects = false;

            params._nfolds = 0;
            glmNoCV = new GLM(params).trainModel().get();

            params._nfolds = 3;
            params._fold_assignment = Model.Parameters.FoldAssignmentScheme.AUTO;
            glmCV = new GLM(params).trainModel().get();
            
            assertNotNull("CV model should train with remove_offset_effects=false", glmCV);
            assertNotNull("CV metrics should be populated", glmCV._output._cross_validation_metrics);
            // Training metrics on the full dataset must be unaffected by the cv_scoreCVModels override.
            assertEquals("Training MSE must be identical with and without CV when remove_offset_effects=false",
                    glmNoCV._output._training_metrics._MSE,
                    glmCV._output._training_metrics._MSE, 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glmCV != null) glmCV.remove();
            if (glmNoCV != null) glmNoCV.remove();
            Scope.exit();
        }
    }

    // Remove_offset_effects must not alter beta coefficients — offset zeroing is scoring-only.
    // The IRLSM solver path is unchanged by the flag; only GLMResDevTask and cv_scoreCVModels read it.
    @Test
    public void testRemoveOffsetDoesNotChangeBetaCoefficients() {
        Frame train = null;
        GLMModel glmWithROE = null;
        GLMModel glmWithoutROE = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("train_beta");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 0;

            params._remove_offset_effects = true;
            glmWithROE = new GLM(params).trainModel().get();

            params._remove_offset_effects = false;
            glmWithoutROE = new GLM(params).trainModel().get();

            assertNotNull(glmWithROE);
            assertNotNull(glmWithoutROE);
            assertEquals("Number of coefficients must match", glmWithROE.coefficients().size(), glmWithoutROE.coefficients().size());
            for (String name : glmWithROE.coefficients().keySet()) {
                assertEquals("Beta for '" + name + "' must be identical regardless of remove_offset_effects",
                        glmWithoutROE.coefficients().get(name),
                        glmWithROE.coefficients().get(name), 1e-10);
            }
        } finally {
            if (train != null) train.remove();
            if (glmWithROE != null) glmWithROE.remove();
            if (glmWithoutROE != null) glmWithoutROE.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBasicDataBinomialOffsetValidation(){
        Frame train = null;
        Frame valid = null;
        GLMModel glm = null;
        GLMModel glmOffset = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            Vec cat1V = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,0,1,1,0,1,1,1,0,1,0,0,0,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2V = Vec.makeVec(new long[]{1,0,1,0,1,0,0,0,1,0,1,0,0,1,0,1,0,0,1,1,1,0,0,0,0,0},new String[]{"0","1"},Vec.newKey());
            Vec offsetV = Vec.makeVec(new double[]{0.1,0.2,0.3,0.2,0.3,0,0,0.1,0.3,0.3,0.2,0.4,0.1,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec resV = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,1,1,0,1,0,1,0,1,1,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"},Vec.newKey());
            valid = new Frame(Key.<Frame>make("valid"),new String[]{"cat1", "cat2", "offset", "y"},new Vec[]{cat1V, cat2V, offsetV, resV});
            DKV.put(valid);

            DistributionFamily family = DistributionFamily.bernoulli;
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._valid = valid._key;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._standardize = false;
            params._intercept = true;
            params._objective_epsilon = 1e-10;
            params._gradient_epsilon = 1e-6;
            params._response_column = "y";
            params._distribution = family;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._max_iterations = 4;
            params._dispersion_epsilon = 1;
            params._offset_column = "offset";
            glm = new GLM(params).trainModel().get();

            System.out.println(glm._output._variable_importances);
            System.out.println(glm.coefficients().toString());

            params._remove_offset_effects = true;
            glmOffset = new GLM(params).trainModel().get();
            
            ModelMetricsBinomial mmVal = (ModelMetricsBinomial) glm._output._validation_metrics;
            System.out.println(mmVal.toString());
            ModelMetricsBinomial mmOffsetValUnrestricted = (ModelMetricsBinomial) glmOffset._output._validation_metrics_unrestricted_model;
            System.out.println(mmOffsetValUnrestricted.toString());
            
            assertEquals("MSE is not the same. ", mmVal._MSE, mmOffsetValUnrestricted._MSE, 0);
            assertEquals("AUC is not the same. ", mmVal._auc._auc, mmOffsetValUnrestricted._auc._auc, 0);
            assertEquals("Logloss is not the same. ", mmVal._logloss, mmOffsetValUnrestricted._logloss, 0);
            assertEquals("Loglikelihood is not the same. ", mmVal._loglikelihood, mmOffsetValUnrestricted._loglikelihood, 0);
        }
        finally {
            if (train != null) train.remove();
            if (valid != null) valid.remove();
            if (glm != null) glm.remove();
            if (glmOffset != null) glmOffset.remove();
            Scope.exit();
        }
    }

    // =========================================================================
    // GH-16676: Additional tests for control_variables and remove_offset_effects
    // =========================================================================

    /** Returns the index of the first row whose header equals {@code name}, or -1 if not found. */
    private static int findRowIndex(TwoDimTable table, String name) {
        String[] rowHeaders = table.getRowHeaders();
        for (int i = 0; i < rowHeaders.length; i++)
            if (name.equals(rowHeaders[i])) return i;
        return -1;
    }

    /** Creates a small binomial frame with categorical predictors, offset, and response. */
    private static Frame makeBinomialOffsetFrame(String key) {
        Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
        Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
        Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
        DKV.put(f);
        return f;
    }

    /** Creates a small binomial frame with numeric predictors, offset, and response.
     *  Use params._offset_column="offset" to treat the offset as an offset column,
     *  or params._ignored_columns={"offset"} to exclude it from predictors entirely. */
    private static Frame makeNumericBinomialOffsetFrame(String key) {
        Vec x1 = Vec.makeVec(new double[]{100,200,300,400,500,600,700,800,900,1000,
                150,250,350,450,550,650,750,850,950,1050,120,220,320,420,520,620}, Vec.newKey());
        Vec x2 = Vec.makeVec(new double[]{0.01,0.02,0.03,0.04,0.05,0.06,0.07,0.08,0.09,0.10,
                0.015,0.025,0.035,0.045,0.055,0.065,0.075,0.085,0.095,0.105,
                0.012,0.022,0.032,0.042,0.052,0.062}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
        Vec resp = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1","x2","offset","y"}, new Vec[]{x1,x2,offset,resp});
        DKV.put(f);
        return f;
    }

    /** Creates a small Poisson frame with a numeric predictor, a non-zero log-offset, and count response. */
    private static Frame makePoissonOffsetFrame(String key) {
        Vec x1 = Vec.makeVec(new double[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0.3,0.2,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0.2,0.3,0.5,0.1,0.2,0.3,0.1,0.2,0.1,0.3}, Vec.newKey());
        Vec y = Vec.makeVec(new double[]{1,2,0,3,1,0,2,4,1,0,2,3,1,5,2,0,1,0,3,2,1,0,4,1,0,2}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1", "offset", "y"}, new Vec[]{x1, offset, y});
        DKV.put(f);
        return f;
    }

    /** Creates a small Gaussian frame with a numeric predictor, offset, weights, and continuous response. */
    private static Frame makeGaussianOffsetFrame(String key) {
        Vec x1 = Vec.makeVec(new double[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0.3,0.2,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0.2,0.3,0.5,0.1,0.2,0.3,0.1,0.2,0.1,0.3}, Vec.newKey());
        Vec weights = Vec.makeVec(new double[]{1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}, Vec.newKey());
        Vec y = Vec.makeVec(new double[]{2.1,3.9,5.2,8.1,9.8,11.5,14.2,15.9,17.8,20.1,
                22.3,24.8,27.1,29.9,31.8,34.2,36.9,39.1,41.8,43.9,
                46.2,48.8,51.1,53.9,56.2,58.8}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1", "offset", "weights", "y"}, new Vec[]{x1, offset, weights, y});
        DKV.put(f);
        return f;
    }

    /** Creates makeBinomialOffsetFrame's data with an explicit fold column instead of relying on nfolds-driven splitting. */
    private static Frame makeBinomialOffsetFoldColumnFrame(String key) {
        Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
        Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
        Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
        Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
        Vec fold = Vec.makeVec(new long[]{0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1,2,0,1}, new String[]{"0","1","2"}, Vec.newKey());
        Frame f = new Frame(Key.<Frame>make(key), new String[]{"x1", "x2", "offset", "y", "fold"}, new Vec[]{cat1, cat2, offset, res, fold});
        DKV.put(f);
        return f;
    }

    /** Prepares the binomial_20_cols_10KRows dataset with categorical columns. */
    private Frame prepareBinomial20ColsFrame() {
        Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
        int numCols = train.numCols();
        int enumCols = (numCols - 1) / 2;
        for (int cindex = 0; cindex < enumCols; cindex++) {
            train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
        }
        train.replace(numCols - 1, train.vec(numCols - 1).toCategoricalVec()).remove();
        DKV.put(train);
        Scope.track_generic(train);
        return train;
    }

    /**
     * Checkpoint resume must keep RO-only and CV-only training metrics distinct.
     * Verifies that the checkpoint restore path doesn't mix up the two restricted model outputs.
     */
    @Test
    public void testCvRoCheckpointPreservesDistinctMetrics() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel glm2 = null;
        try {
            Scope.enter();

            train = makeBinomialOffsetFrame("p0_1_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._score_each_iteration = true;
            params._max_iterations = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Verify RO and CV training metrics are distinct before checkpoint
            ModelMetrics mmRO = glm._output._training_metrics_restricted_model_ro;
            ModelMetrics mmCV = glm._output._training_metrics_restricted_model_contr_vals;
            assertNotNull("RO training metrics should exist before checkpoint", mmRO);
            assertNotNull("CV training metrics should exist before checkpoint", mmCV);
            double devRO = ((ModelMetricsBinomialGLM) mmRO).residual_deviance();
            double devCV = ((ModelMetricsBinomialGLM) mmCV).residual_deviance();
            assertTrue("RO and CV deviance should differ before checkpoint",
                    Math.abs(devRO - devCV) > 1e-10);

            // Resume from checkpoint
            GLMModel.GLMParameters params2 = new GLMModel.GLMParameters();
            params2._train = train._key;
            params2._alpha = new double[]{0};
            params2._response_column = "y";
            params2._offset_column = "offset";
            params2._control_variables = new String[]{"x1"};
            params2._remove_offset_effects = true;
            params2._score_each_iteration = true;
            params2._max_iterations = 6;
            params2._distribution = DistributionFamily.bernoulli;
            params2._link = GLMModel.GLMParameters.Link.logit;
            params2._solver = GLMModel.GLMParameters.Solver.IRLSM;
            params2._checkpoint = glm._key;

            glm2 = new GLM(params2).trainModel().get();
            Scope.track_generic(glm2);

            // After checkpoint resume, RO and CV training metrics must still be distinct
            ModelMetrics mmRO2 = glm2._output._training_metrics_restricted_model_ro;
            ModelMetrics mmCV2 = glm2._output._training_metrics_restricted_model_contr_vals;
            assertNotNull("RO training metrics should exist after checkpoint resume", mmRO2);
            assertNotNull("CV training metrics should exist after checkpoint resume", mmCV2);
            double devRO2 = ((ModelMetricsBinomialGLM) mmRO2).residual_deviance();
            double devCV2 = ((ModelMetricsBinomialGLM) mmCV2).residual_deviance();
            assertTrue("RO and CV deviance should differ after checkpoint " +
                    "(if identical, checkpoint restore mixed up the restricted models). " +
                    "RO=" + devRO2 + ", CV=" + devCV2,
                    Math.abs(devRO2 - devCV2) > 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glm2 != null) glm2.remove();
            Scope.exit();
        }
    }

    /**
     * make_derived_model must not mutate the source model's DataInfo predictor transform.
     */
    @Test
    public void testDerivedModelDoesNotMutateSourceDataInfo() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("p0_2_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._standardize = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Capture the DataInfo predictor transform BEFORE calling make_derived_model
            hex.DataInfo dinfoBefore = glm.dinfo();
            assertNotNull("DataInfo should exist", dinfoBefore);
            // With standardize=true on numeric data, _normMul should be non-null
            double[] normMulBefore = dinfoBefore._normMul != null ? dinfoBefore._normMul.clone() : null;

            // Call make_derived_model (the unrestricted variant, both flags false)
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "p0_2_derived";
            args.remove_offset_effects = false;
            args.remove_control_variables_effects = false;
            handler.make_derived_model(3, args);
            derived = DKV.getGet(Key.make("p0_2_derived"));
            Scope.track_generic(derived);

            // After make_derived_model, the source model's DataInfo should be unchanged
            hex.DataInfo dinfoAfter = glm.dinfo();
            // Check that the predictor transform was not wiped to NONE
            // With standardize=true, _normMul should still be non-null and unchanged
            if (normMulBefore != null) {
                assertNotNull("Source model DataInfo _normMul should not be null after make_derived_model " +
                        "(setPredictorTransform(NONE) would null it out)", dinfoAfter._normMul);
                assertArrayEquals("Source model DataInfo _normMul should be unchanged after make_derived_model",
                        normMulBefore, dinfoAfter._normMul, 0);
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /** Helper: verify scoring history deviance matches training metrics. */
    private void assertScoringHistoryDevianceMatchesMetrics(String[] controlVariables) {
        String key = controlVariables != null ? "p0_4_train" : "p0_5_train";
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame(key);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._control_variables = controlVariables;
            params._remove_offset_effects = true;
            params._score_each_iteration = true;
            params._generate_scoring_history = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            TwoDimTable sh = glm._output._scoring_history;
            assertNotNull("Scoring history should exist", sh);
            ModelMetrics mm = glm._output._training_metrics;
            assertNotNull("Training metrics should exist", mm);
            double metricsDeviance = ((ModelMetricsBinomialGLM) mm).residual_deviance() / mm._nobs;

            int devianceCol = Arrays.asList(sh.getColHeaders()).indexOf("deviance_train");
            assertTrue("Scoring history should have deviance_train column", devianceCol >= 0);
            double shDeviance = (double) sh.get(sh.getRowDim() - 1, devianceCol);

            assertEquals("Scoring history deviance should match training metrics mean residual deviance",
                    metricsDeviance, shDeviance, metricsDeviance * 0.01);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test
    public void testCvRoScoringHistoryDevianceMatchesMetrics() {
        assertScoringHistoryDevianceMatchesMetrics(new String[]{"x1"});
    }

    @Test
    public void testRoScoringHistoryDevianceMatchesMetrics() {
        assertScoringHistoryDevianceMatchesMetrics(null);
    }

    /**
     * With remove_offset_effects, final deviance and scoring history NLL should be
     * consistent regardless of the standardize setting.
     */
    @Test
    public void testRoStandardizeInvariant() {
        Frame train = null;
        GLMModel glmNoStd = null;
        GLMModel glmStd = null;
        try {
            Scope.enter();
            train = prepareBinomial20ColsFrame();
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            // Model A: standardize=false — inline computation is correct
            // (_model.beta() and _state.expandBeta(_state.beta()) are the same when not standardized)
            GLMModel.GLMParameters paramsNoStd = new GLMModel.GLMParameters(family);
            paramsNoStd._train = train._key;
            paramsNoStd._response_column = responseColumn;
            paramsNoStd._offset_column = "C20";
            paramsNoStd._remove_offset_effects = true;
            paramsNoStd._standardize = false;
            paramsNoStd._score_each_iteration = true;

            glmNoStd = new GLM(paramsNoStd).trainModel().get();
            Scope.track_generic(glmNoStd);

            // Model B: standardize=true — inline computation uses denormalized beta (BUG)
            GLMModel.GLMParameters paramsStd = new GLMModel.GLMParameters(family);
            paramsStd._train = train._key;
            paramsStd._response_column = responseColumn;
            paramsStd._offset_column = "C20";
            paramsStd._remove_offset_effects = true;
            paramsStd._standardize = true;
            paramsStd._score_each_iteration = true;

            glmStd = new GLM(paramsStd).trainModel().get();
            Scope.track_generic(glmStd);

            // Both models' FINAL training metrics should be similar (computed via proper model.score())
            double metricsNoStd = ((ModelMetricsBinomialGLM) glmNoStd._output._training_metrics).residual_deviance();
            double metricsStd = ((ModelMetricsBinomialGLM) glmStd._output._training_metrics).residual_deviance();
            assertEquals("Final training metrics deviance should match regardless of standardization",
                    metricsNoStd, metricsStd, metricsNoStd * 0.05);

            // Check the scoring history NLL (from inline updateProgress computation at GLM.java:4118).
            // The inline path runs first in updateProgress, and ScoringHistory.addIterationScore()
            // deduplicates by iteration, so the inline values are what end up in the scoring history.
            TwoDimTable shNoStd = glmNoStd._output._scoring_history;
            TwoDimTable shStd = glmStd._output._scoring_history;
            assertNotNull("Scoring history should exist for non-standardized model", shNoStd);
            assertNotNull("Scoring history should exist for standardized model", shStd);

            int nllColNoStd = Arrays.asList(shNoStd.getColHeaders()).indexOf("negative_log_likelihood");
            int nllColStd = Arrays.asList(shStd.getColHeaders()).indexOf("negative_log_likelihood");
            assertTrue("Should have NLL column", nllColNoStd >= 0 && nllColStd >= 0);

            // Get the last NLL entry from each scoring history
            int lastRowNoStd = shNoStd.getRowDim() - 1;
            int lastRowStd = shStd.getRowDim() - 1;
            double nllNoStd = (double) shNoStd.get(lastRowNoStd, nllColNoStd);
            double nllStd = (double) shStd.get(lastRowStd, nllColStd);

            // Both should be finite and positive
            assertTrue("Non-standardized model NLL should be finite", Double.isFinite(nllNoStd));
            assertTrue("Standardized model NLL should be finite", Double.isFinite(nllStd));
            assertTrue("Non-standardized model NLL should be positive", nllNoStd > 0);
            assertTrue("Standardized model NLL should be positive", nllStd > 0);

            // The scoring history NLL values should be in the same ballpark.
            // With the bug (denormalized beta in standardized DataInfo), the standardized model's
            // inline NLL will be wildly wrong because r.innerProduct(beta) multiplies
            // standardized features by denormalized coefficients.
            double ratio = nllStd / nllNoStd;
            assertTrue("Scoring history NLL should match between standardize=true and false " +
                            "(ratio=" + ratio + "). If ratio is far from 1.0, the inline deviance " +
                            "computation uses denormalized beta with standardized DataInfo.",
                    ratio > 0.5 && ratio < 2.0);
        } finally {
            if (train != null) train.remove();
            if (glmNoStd != null) glmNoStd.remove();
            if (glmStd != null) glmStd.remove();
            Scope.exit();
        }
    }

    /**
     * L-BFGS with remove_offset_effects should produce deviance close to IRLSM
     * and the restricted model should differ from unrestricted.
     */
    @Test
    public void testRoLbfgsMatchesIrlsm() {
        Frame train = null;
        GLMModel glmLBFGS = null;
        GLMModel glmIRLSM = null;
        try {
            Scope.enter();
            train = prepareBinomial20ColsFrame();
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            // Train with L_BFGS solver
            GLMModel.GLMParameters paramsLBFGS = new GLMModel.GLMParameters(family);
            paramsLBFGS._train = train._key;
            paramsLBFGS._response_column = responseColumn;
            paramsLBFGS._offset_column = "C20";
            paramsLBFGS._remove_offset_effects = true;
            paramsLBFGS._solver = GLMModel.GLMParameters.Solver.L_BFGS;
            paramsLBFGS._score_each_iteration = true;

            glmLBFGS = new GLM(paramsLBFGS).trainModel().get();
            Scope.track_generic(glmLBFGS);

            assertNotNull("L_BFGS model should train successfully with remove_offset_effects", glmLBFGS);
            assertNotNull("L_BFGS model should have training metrics", glmLBFGS._output._training_metrics);
            assertNotNull("L_BFGS model should have unrestricted training metrics",
                    glmLBFGS._output._training_metrics_unrestricted_model);

            // Train the same model with IRLSM for comparison
            GLMModel.GLMParameters paramsIRLSM = new GLMModel.GLMParameters(family);
            paramsIRLSM._train = train._key;
            paramsIRLSM._response_column = responseColumn;
            paramsIRLSM._offset_column = "C20";
            paramsIRLSM._remove_offset_effects = true;
            paramsIRLSM._solver = GLMModel.GLMParameters.Solver.IRLSM;
            paramsIRLSM._score_each_iteration = true;

            glmIRLSM = new GLM(paramsIRLSM).trainModel().get();
            Scope.track_generic(glmIRLSM);

            // Both solvers should converge to similar solutions
            double devianceLBFGS = ((ModelMetricsBinomialGLM) glmLBFGS._output._training_metrics).residual_deviance();
            double devianceIRLSM = ((ModelMetricsBinomialGLM) glmIRLSM._output._training_metrics).residual_deviance();
            assertEquals("L_BFGS and IRLSM should converge to similar deviance",
                    devianceIRLSM, devianceLBFGS, devianceIRLSM * 0.05);

            // Verify unrestricted metrics also match
            double unrestDevianceLBFGS = ((ModelMetricsBinomialGLM) glmLBFGS._output._training_metrics_unrestricted_model).residual_deviance();
            double unrestDevianceIRLSM = ((ModelMetricsBinomialGLM) glmIRLSM._output._training_metrics_unrestricted_model).residual_deviance();
            assertEquals("L_BFGS and IRLSM unrestricted deviance should match",
                    unrestDevianceIRLSM, unrestDevianceLBFGS, unrestDevianceIRLSM * 0.05);

            // Verify the restricted deviance differs from the unrestricted (offset removal has effect)
            assertNotEquals("Restricted deviance should differ from unrestricted for L_BFGS",
                    devianceLBFGS, unrestDevianceLBFGS, 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glmLBFGS != null) glmLBFGS.remove();
            if (glmIRLSM != null) glmIRLSM.remove();
            Scope.exit();
        }
    }

    /**
     * L-BFGS with both CV and RO must produce all 4 metric sets with distinct deviances.
     */
    @Test
    public void testCvRoLbfgsProducesDistinctDerivedModels() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = prepareBinomial20ColsFrame();
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(family);
            params._train = train._key;
            params._response_column = responseColumn;
            params._offset_column = "C20";
            params._control_variables = new String[]{"C5"};
            params._remove_offset_effects = true;
            params._solver = GLMModel.GLMParameters.Solver.L_BFGS;
            params._score_each_iteration = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull("L_BFGS model with CV+RO should train successfully", glm);

            // All 4 metric sets should be populated
            assertNotNull("Main training metrics", glm._output._training_metrics);
            assertNotNull("Unrestricted training metrics", glm._output._training_metrics_unrestricted_model);
            assertNotNull("CV-only training metrics", glm._output._training_metrics_restricted_model_contr_vals);
            assertNotNull("RO-only training metrics", glm._output._training_metrics_restricted_model_ro);

            // All 4 scoring histories should be populated
            assertNotNull("Main scoring history", glm._output._scoring_history);
            assertNotNull("Unrestricted scoring history", glm._output._scoring_history_unrestricted_model);
            assertNotNull("CV-only scoring history", glm._output._scoring_history_restricted_model_contr_vals);
            assertNotNull("RO-only scoring history", glm._output._scoring_history_restricted_model_ro);

            // Restricted, unrestricted, CV-only, and RO-only deviances should all differ
            double devMain = ((ModelMetricsBinomialGLM) glm._output._training_metrics).residual_deviance();
            double devUnrestricted = ((ModelMetricsBinomialGLM) glm._output._training_metrics_unrestricted_model).residual_deviance();
            double devCV = ((ModelMetricsBinomialGLM) glm._output._training_metrics_restricted_model_contr_vals).residual_deviance();
            double devRO = ((ModelMetricsBinomialGLM) glm._output._training_metrics_restricted_model_ro).residual_deviance();

            assertNotEquals("Main vs unrestricted deviance should differ", devMain, devUnrestricted, 1e-10);
            assertNotEquals("CV-only vs RO-only deviance should differ", devCV, devRO, 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * haveMojo()/havePojo() should return true when remove_offset_effects=true.
     * MOJO/POJO scores the learned coefficients normally; offset removal is a training-time
     * concept that does not affect the exported scoring artifact.
     */
    @Test
    public void testRoMojoPojoGuard() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            train = makeBinomialOffsetFrame("p1_2_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertTrue("haveMojo() should return true when remove_offset_effects=true ", glm.haveMojo());
            assertTrue("havePojo() should return true when remove_offset_effects=true ", glm.havePojo());
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * make_derived_model with both remove_offset_effects and remove_control_variables_effects
     * set to true should be rejected.
     */
    @Test
    public void testDerivedModelRejectsBothFlags() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            train = makeBinomialOffsetFrame("p1_5_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Both flags true should throw
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.remove_offset_effects = true;
            args.remove_control_variables_effects = true;
            try {
                handler.make_derived_model(3, args);
                fail("Should have thrown IllegalArgumentException when both flags are true");
            } catch (IllegalArgumentException e) {
                assertTrue("Error message should mention the flags cannot be used together",
                        e.getMessage().contains("cannot be used together"));
            }

            // Model trained with only control_variables (no remove_offset_effects) should succeed.
            // Main training slots already hold the control-variables-restricted view in this case.
            GLMModel.GLMParameters paramsCtrlOnly = new GLMModel.GLMParameters();
            paramsCtrlOnly._train = train._key;
            paramsCtrlOnly._alpha = new double[]{0};
            paramsCtrlOnly._response_column = "y";
            paramsCtrlOnly._control_variables = new String[]{"x1"};
            paramsCtrlOnly._distribution = DistributionFamily.bernoulli;
            paramsCtrlOnly._link = GLMModel.GLMParameters.Link.logit;

            GLMModel glmCtrlOnly = new GLM(paramsCtrlOnly).trainModel().get();
            Scope.track_generic(glmCtrlOnly);

            MakeDerivedGLMModelV3 args2 = new MakeDerivedGLMModelV3();
            args2.model = new KeyV3.ModelKeyV3(glmCtrlOnly._key);
            args2.dest = "p1_5_ctrl_only_derived";
            args2.remove_control_variables_effects = true;
            handler.make_derived_model(3, args2);
            GLMModel derived = DKV.getGet(Key.make("p1_5_ctrl_only_derived"));
            Scope.track_generic(derived);
            assertNotNull("Derived model must be created for control-variables-only source", derived);
            assertNotNull("Derived model must have training metrics", derived._output._training_metrics);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * make_derived_model on a model trained without CV or RO should be rejected.
     */
    @Test
    public void testDerivedModelRejectsPlainModel() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_6_train"), new String[]{"x1", "x2", "y"}, new Vec[]{cat1, cat2, res});
            DKV.put(train);

            // Train a plain model without control_variables or remove_offset_effects
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Calling make_derived_model on a plain model should throw
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            try {
                handler.make_derived_model(3, args);
                fail("Should have thrown when source model has no control_variables or remove_offset_effects");
            } catch (IllegalArgumentException e) {
                assertTrue("Error should mention missing features",
                        e.getMessage().contains("not trained with control variables or remove offset effects"));
            }

            // make_unrestricted_model should also throw
            MakeUnrestrictedGLMModelV3 argsU = new MakeUnrestrictedGLMModelV3();
            argsU.model = new KeyV3.ModelKeyV3(glm._key);
            try {
                handler.make_unrestricted_model(3, argsU);
                fail("make_unrestricted_model should also reject plain models");
            } catch (IllegalArgumentException e) {
                assertTrue("Error should mention missing features",
                        e.getMessage().contains("not trained with control variables or remove offset effects"));
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * remove_offset_effects should work with Tweedie family.
     */
    @Test
    public void testRoTweedie() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec x1 = Vec.makeVec(new double[]{1,2,3,4,5,6,7,8,9,10,1.5,2.5,3.5,4.5,5.5,6.5,7.5,8.5,9.5,10.5,1.2,2.2,3.2,4.2,5.2,6.2}, Vec.newKey());
            Vec x2 = Vec.makeVec(new double[]{10,20,30,40,50,60,70,80,90,100,15,25,35,45,55,65,75,85,95,105,12,22,32,42,52,62}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec y = Vec.makeVec(new double[]{1.5,2.3,0.5,1.2,3.4,2.1,0.8,1.9,2.7,3.1,1.1,0.4,2.2,1.8,3.0,0.9,1.3,2.5,0.7,1.6,2.0,1.4,0.6,2.8,1.0,3.2}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_7_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{x1, x2, offset, y});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._remove_offset_effects = true;
            params._family = GLMModel.GLMParameters.Family.tweedie;
            params._tweedie_variance_power = 1.5;
            params._tweedie_link_power = 0;

            // This should succeed — Tweedie is not blocked by validation
            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull("Model should train successfully with Tweedie + remove_offset_effects", glm);
            assertNotNull("Training metrics should exist", glm._output._training_metrics);
            assertNotNull("Unrestricted training metrics should exist",
                    glm._output._training_metrics_unrestricted_model);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * remove_offset_effects=true without offset_column should fail validation.
     */
    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testRoRequiresOffsetColumn() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec x1 = Vec.makeVec(new double[]{1,2,3,4,5,6,7,8,9,10,1,2,3,4,5,6,7,8,9,10}, Vec.newKey());
            Vec y = Vec.makeVec(new double[]{1,0,1,0,1,0,1,0,1,0,1,1,0,0,1,1,0,0,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_9_train"), new String[]{"x1", "y"}, new Vec[]{x1, y});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._remove_offset_effects = true;
            // No offset_column specified
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * When remove_offset_effects=True and nfolds>0 and generate_scoring_history=True:
     * - The restricted scoring history (_scoring_history) must have deviance_xval computed WITHOUT the offset.
     * - The unrestricted scoring history (_scoring_history_unrestricted_model) must have deviance_xval computed WITH the offset.
     * The two deviance_xval values must differ (proving independent computation, not the same array).
     */
    @Test
    public void testRemoveOffsetCvScoringHistoryHasXvalDeviance() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("train_xval_sh");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._remove_offset_effects = true;
            params._nfolds = 3;
            params._generate_scoring_history = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._score_each_iteration = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // --- Restricted scoring history (offset removed) ---
            TwoDimTable sh = glm._output._scoring_history;
            assertNotNull("Restricted scoring history must be present", sh);
            String[] restrictedCols = sh.getColHeaders();
            assertTrue("deviance_xval column must be in restricted scoring history",
                    Arrays.asList(restrictedCols).contains("deviance_xval"));
            assertTrue("deviance_se column must be in restricted scoring history",
                    Arrays.asList(restrictedCols).contains("deviance_se"));
            int restrictedXvalCol = Arrays.asList(restrictedCols).indexOf("deviance_xval");
            int restrictedSeCol   = Arrays.asList(restrictedCols).indexOf("deviance_se");
            int lastRow = sh.getRowDim() - 1;
            double restrictedXvalDev = (double) sh.get(lastRow, restrictedXvalCol);
            double restrictedXvalSe  = (double) sh.get(lastRow, restrictedSeCol);
            assertTrue("deviance_xval in restricted history must be finite and positive",
                    restrictedXvalDev > 0 && !Double.isNaN(restrictedXvalDev));
            assertTrue("deviance_se in restricted history must be finite and non-negative",
                    restrictedXvalSe >= 0 && !Double.isNaN(restrictedXvalSe));

            // --- Unrestricted scoring history (with offset) ---
            TwoDimTable shUnrestricted = glm._output._scoring_history_unrestricted_model;
            assertNotNull("Unrestricted scoring history must be present", shUnrestricted);
            String[] unrestrictedCols = shUnrestricted.getColHeaders();
            assertTrue("deviance_xval must be in unrestricted scoring history",
                    Arrays.asList(unrestrictedCols).contains("deviance_xval"));
            assertTrue("deviance_se must be in unrestricted scoring history",
                    Arrays.asList(unrestrictedCols).contains("deviance_se"));
            int unrestrictedXvalCol = Arrays.asList(unrestrictedCols).indexOf("deviance_xval");
            int unrestrictedSeCol   = Arrays.asList(unrestrictedCols).indexOf("deviance_se");
            int lastRowUnrestricted = shUnrestricted.getRowDim() - 1;
            double unrestrictedXvalDev = (double) shUnrestricted.get(lastRowUnrestricted, unrestrictedXvalCol);
            double unrestrictedXvalSe  = (double) shUnrestricted.get(lastRowUnrestricted, unrestrictedSeCol);
            assertTrue("deviance_xval in unrestricted history must be finite and positive",
                    unrestrictedXvalDev > 0 && !Double.isNaN(unrestrictedXvalDev));
            assertTrue("deviance_se in unrestricted history must be finite and non-negative",
                    unrestrictedXvalSe >= 0 && !Double.isNaN(unrestrictedXvalSe));

            // Restricted (offset removed) and unrestricted (offset included) xval deviances must differ
            // because the test frame has non-zero offset values
            assertNotEquals("Restricted and unrestricted deviance_xval must differ because the offset is non-zero",
                    restrictedXvalDev, unrestrictedXvalDev, 1e-10);

            // The combined restricted scoring history must also carry "Unrestricted deviance_xval" and
            // "Unrestricted deviance_se" columns (added by combineScoringHistoryRestricted from the
            // unrestricted scoring history with the "Unrestricted " prefix)
            assertTrue("Unrestricted deviance_xval column must appear in combined restricted scoring history",
                    Arrays.asList(restrictedCols).contains("Unrestricted deviance_xval"));
            assertTrue("Unrestricted deviance_se column must appear in combined restricted scoring history",
                    Arrays.asList(restrictedCols).contains("Unrestricted deviance_se"));
            int unrestrictedXvalColInRestricted = Arrays.asList(restrictedCols).indexOf("Unrestricted deviance_xval");
            double unrestrictedXvalDevInRestricted = (double) sh.get(lastRow, unrestrictedXvalColInRestricted);
            assertTrue("Unrestricted deviance_xval in combined restricted history must be finite and positive",
                    unrestrictedXvalDevInRestricted > 0 && !Double.isNaN(unrestrictedXvalDevInRestricted));
            // The "Unrestricted deviance_xval" in the combined table equals the standalone unrestricted xval deviance
            assertEquals("Unrestricted deviance_xval in combined table must equal the standalone unrestricted history value",
                    unrestrictedXvalDev, unrestrictedXvalDevInRestricted, 1e-10);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * Unrestricted CV slots are populated when remove_offset_effects=true (impl + GLMModelV3
     * schema), distinct from their restricted counterparts, and null when remove_offset_effects=false.
     */
    @Test
    public void testRemoveOffsetCvUnrestrictedMetricsParity() {
        Frame train = null;
        Frame trainNoROE = null;
        GLMModel glm = null;
        GLMModel glmNoROE = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test04_unrestricted_cv_metrics");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._seed = 1234;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._keep_cross_validation_predictions = true;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Unrestricted impl slots populated.
            assertNotNull(glm._output._cross_validation_metrics_unrestricted_model);
            assertNotNull(glm._output._cross_validation_metrics_summary_unrestricted_model);
            assertNotNull(glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model);

            // Summary shape: structural check that tolerates future cv_* header renames.
            TwoDimTable unrestrictedSummary = glm._output._cross_validation_metrics_summary_unrestricted_model;
            assertEquals(5, unrestrictedSummary.getColDim());
            String[] colHeaders = unrestrictedSummary.getColHeaders();
            assertEquals("mean", colHeaders[0]);
            assertEquals("sd", colHeaders[1]);
            assertTrue(colHeaders[2].startsWith("cv_"));
            assertTrue(colHeaders[3].startsWith("cv_"));
            assertTrue(colHeaders[4].startsWith("cv_"));

            // Restricted and unrestricted aggregate residual_deviance must differ (non-zero offset).
            assertNotNull(glm._output._cross_validation_metrics);
            double restrictedResDev = ((ModelMetricsBinomialGLM) glm._output._cross_validation_metrics).residual_deviance();
            double unrestrictedResDev = ((ModelMetricsBinomialGLM) glm._output._cross_validation_metrics_unrestricted_model).residual_deviance();
            assertNotEquals(restrictedResDev, unrestrictedResDev, 1e-10);

            // residual_deviance mean differs between restricted and unrestricted summaries.
            TwoDimTable restrictedSummary = glm._output._cross_validation_metrics_summary;
            assertNotNull(restrictedSummary);
            int restrictedRow = findRowIndex(restrictedSummary, "residual_deviance");
            int unrestrictedRow = findRowIndex(unrestrictedSummary, "residual_deviance");
            assertTrue(restrictedRow >= 0);
            assertTrue(unrestrictedRow >= 0);
            double restrictedMean = ((Number) restrictedSummary.get(restrictedRow, 0)).doubleValue();
            double unrestrictedMean = ((Number) unrestrictedSummary.get(unrestrictedRow, 0)).doubleValue();
            assertNotEquals(restrictedMean, unrestrictedMean, 1e-10);

            // Combined unrestricted holdout-pred frame is retrievable.
            Key<Frame> unrestrictedHpKey = glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model;
            Frame unrestrictedHp = DKV.getGet(unrestrictedHpKey);
            assertNotNull(unrestrictedHp);
            Scope.track(unrestrictedHp);

            // Per-fold unrestricted predictions: non-null array of length nfolds, each key points to an existing frame.
            Key<Frame>[] perFoldUnrestricted = glm._output._cross_validation_predictions_unrestricted_model;
            assertNotNull("Per-fold unrestricted CV predictions must be non-null when keep_cross_validation_predictions=true",
                    perFoldUnrestricted);
            assertEquals("Per-fold unrestricted predictions array length must equal nfolds",
                    3, perFoldUnrestricted.length);
            for (int i = 0; i < perFoldUnrestricted.length; i++) {
                assertNotNull("Per-fold unrestricted key[" + i + "] must not be null", perFoldUnrestricted[i]);
                Frame foldFrame = DKV.getGet(perFoldUnrestricted[i]);
                assertNotNull("Per-fold unrestricted frame[" + i + "] must exist in DKV", foldFrame);
                Scope.track(foldFrame);
            }

            // Schema round-trip: Weaver auto-mapping bridges impl `_field` to schema `field`.
            GLMModelV3 schema = new GLMModelV3();
            schema.fillFromImpl(glm);
            assertNotNull(schema.output);
            assertNotNull(schema.output.cross_validation_metrics_unrestricted_model);
            assertNotNull(schema.output.cross_validation_metrics_summary_unrestricted_model);
            assertNotNull(schema.output.cross_validation_holdout_predictions_frame_id_unrestricted_model);
            assertTrue(schema.output.cross_validation_metrics_unrestricted_model instanceof ModelMetricsBaseV3);
            assertNotNull("Schema must expose per-fold unrestricted predictions",
                    schema.output.cross_validation_predictions_unrestricted_model);
            assertEquals("Schema per-fold unrestricted predictions length must equal nfolds",
                    3, schema.output.cross_validation_predictions_unrestricted_model.length);

            // Regression guard: remove_offset_effects=false → all unrestricted fields null.
            trainNoROE = makeBinomialOffsetFrame("test04_unrestricted_cv_metrics_no_roe");
            GLMModel.GLMParameters paramsNoROE = new GLMModel.GLMParameters();
            paramsNoROE._train = trainNoROE._key;
            paramsNoROE._response_column = "y";
            paramsNoROE._offset_column = "offset";
            paramsNoROE._alpha = new double[]{0};
            paramsNoROE._lambda = new double[]{0};
            paramsNoROE._intercept = false;
            paramsNoROE._nfolds = 3;
            paramsNoROE._seed = 1234;
            paramsNoROE._distribution = DistributionFamily.bernoulli;
            paramsNoROE._link = GLMModel.GLMParameters.Link.logit;
            paramsNoROE._keep_cross_validation_predictions = true;
            paramsNoROE._remove_offset_effects = false;

            glmNoROE = new GLM(paramsNoROE).trainModel().get();
            Scope.track_generic(glmNoROE);

            assertNull(glmNoROE._output._cross_validation_metrics_unrestricted_model);
            assertNull(glmNoROE._output._cross_validation_metrics_summary_unrestricted_model);
            assertNull(glmNoROE._output._cross_validation_holdout_predictions_frame_id_unrestricted_model);
            assertNull("Per-fold unrestricted predictions must be null when remove_offset_effects=false",
                    glmNoROE._output._cross_validation_predictions_unrestricted_model);

            // Strong invariant: with the same seed/folds and identical data, the with-offset (unrestricted)
            // CV view of the remove_offset_effects=true model must reproduce exactly what a plain
            // remove_offset_effects=false model computes for its main CV metrics.
            unrestrictedResDev = ((ModelMetricsBinomialGLM) glm._output._cross_validation_metrics_unrestricted_model).residual_deviance();
            double baselineResDev = ((ModelMetricsBinomialGLM) glmNoROE._output._cross_validation_metrics).residual_deviance();
            assertEquals("Unrestricted CV residual_deviance must match the same-seed remove_offset_effects=false baseline",
                    baselineResDev, unrestrictedResDev, 1e-8);
        } finally {
            if (train != null) train.remove();
            if (trainNoROE != null) trainNoROE.remove();
            Scope.exit();
        }
    }

    /**
     * Regression guard for the _description-set-after-DKV.put ordering bug: addModelMetrics()
     * DKV.puts the metric immediately, so setting _description afterward only updates the in-memory
     * object — a fresh DKV fetch of the same key would see a null description. The fix sets
     * _description before addModelMetrics() runs.
     */
    @Test
    public void testUnrestrictedCvMetricDescriptionPersistedToDkv() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_unrestricted_cv_metric_description");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            ModelMetrics unrestrictedCvMM = glm._output._cross_validation_metrics_unrestricted_model;
            assertNotNull(unrestrictedCvMM);
            assertNotNull("In-memory unrestricted CV metric must have a description",
                    unrestrictedCvMM._description);

            // Force a fresh deserialization from DKV bytes, bypassing the in-memory object that
            // would show the correct value regardless of when _description was set relative to the put.
            ModelMetrics fromDkv = DKV.getGet(unrestrictedCvMM._key);
            assertNotNull("Unrestricted CV metric must be retrievable from DKV", fromDkv);
            assertNotNull("Unrestricted CV metric's description must be persisted to DKV, not just set "
                    + "on the in-memory object after it was already put", fromDkv._description);
            assertEquals(unrestrictedCvMM._description, fromDkv._description);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * Regression guard for deleteCrossValidationPreds deleting a CV metric during a
     * predictions-only cleanup: the method is called standalone (e.g. by StackedEnsemble on a base
     * learner, or directly by callers wanting to free per-fold prediction memory) while the model
     * stays alive and usable. It must free per-fold/holdout prediction frames but must NOT delete
     * _cross_validation_metrics_unrestricted_model — that metric is already tracked in
     * _model_metrics and is cleaned up by remove_impl only when the model itself is deleted.
     */
    @Test
    public void testDeleteCrossValidationPredsKeepsUnrestrictedMetric() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_delete_cv_preds_keeps_metric");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._keep_cross_validation_predictions = true;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            Key<ModelMetrics> unrestrictedMetricKey = glm._output._cross_validation_metrics_unrestricted_model._key;
            Key<Frame> unrestrictedHoldoutKey = glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model;
            assertNotNull(DKV.getGet(unrestrictedMetricKey));
            assertNotNull(DKV.getGet(unrestrictedHoldoutKey));

            glm.deleteCrossValidationPreds();

            assertNotNull("deleteCrossValidationPreds must not remove the unrestricted CV metric "
                    + "while the model is still alive", DKV.getGet(unrestrictedMetricKey));
            assertNull("deleteCrossValidationPreds must still free the unrestricted holdout-pred frame",
                    DKV.getGet(unrestrictedHoldoutKey));
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * Regression guard for the DKV-deletion ordering bug: when keep_cross_validation_models=false
     * the base cv_mainModelScores deletes fold models from DKV before GLM's override runs.
     * The unrestricted summary table must still be populated (non-empty, numeric values differ
     * from the restricted summary) because GLM now builds it before calling super.
     */
    @Test
    public void testRemoveOffsetCvUnrestrictedSummaryWithoutKeptModels() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test04_unrestricted_cv_no_keep_models");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._keep_cross_validation_models = false;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Unrestricted aggregate metrics still populated despite CV models being deleted.
            assertNotNull(glm._output._cross_validation_metrics_unrestricted_model);

            // Summary table must be non-null and contain at least one metric row.
            TwoDimTable unrestrictedSummary = glm._output._cross_validation_metrics_summary_unrestricted_model;
            assertNotNull(unrestrictedSummary);
            assertTrue("Unrestricted CV summary must have at least one metric row",
                    unrestrictedSummary.getRowDim() > 0);

            // residual_deviance row is present and differs between restricted and unrestricted.
            TwoDimTable restrictedSummary = glm._output._cross_validation_metrics_summary;
            assertNotNull(restrictedSummary);
            int restrictedRow = findRowIndex(restrictedSummary, "residual_deviance");
            int unrestrictedRow = findRowIndex(unrestrictedSummary, "residual_deviance");
            assertTrue("residual_deviance row missing from restricted summary", restrictedRow >= 0);
            assertTrue("residual_deviance row missing from unrestricted summary", unrestrictedRow >= 0);
            double restrictedMean = ((Number) restrictedSummary.get(restrictedRow, 0)).doubleValue();
            double unrestrictedMean = ((Number) unrestrictedSummary.get(unrestrictedRow, 0)).doubleValue();
            assertNotEquals("Restricted and unrestricted summary means must differ when offset is non-zero",
                    restrictedMean, unrestrictedMean, 1e-10);

            // Per-fold unrestricted predictions must be null when keep_cross_validation_predictions is not set.
            assertNull("Per-fold unrestricted predictions must be null when keep_cross_validation_predictions=false",
                    glm._output._cross_validation_predictions_unrestricted_model);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * Poisson family, remove_offset_effects=true, nfolds>0, keep_cross_validation_predictions unset:
     * exercises the regression branch of cv_additionalScoringPerFold (cvModel.scoreMetrics(adaptFr),
     * i.e. Model.BigScore) rather than cv_scoreFold, since nclasses()==1 here. Prior to the
     * GLMMetricBuilder fix, BigScore fed the raw frame offset into null_deviance regardless of
     * _useRemoveOffsetEffects, so the restricted CV null_deviance silently matched the unrestricted
     * (with-offset) one instead of being computed with the offset zeroed out.
     */
    @Test
    public void testRemoveOffsetCvPoissonRegressionScoringPath() {
        Frame train = null;
        Frame trainNoROE = null;
        GLMModel glm = null;
        GLMModel glmNoROE = null;
        try {
            Scope.enter();
            train = makePoissonOffsetFrame("test_poisson_offset_cv");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._family = GLMModel.GLMParameters.Family.poisson;
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._nfolds = 3;
            params._seed = 1234;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull(glm._output._cross_validation_metrics);
            assertNotNull(glm._output._cross_validation_metrics_unrestricted_model);
            GLMMetrics restricted = (GLMMetrics) glm._output._cross_validation_metrics;
            GLMMetrics unrestricted = (GLMMetrics) glm._output._cross_validation_metrics_unrestricted_model;

            // residual_deviance must differ: offset-removed predictions vs with-offset predictions.
            assertNotEquals("Restricted and unrestricted CV residual_deviance must differ when offset is non-zero",
                    restricted.residual_deviance(), unrestricted.residual_deviance(), 1e-8);
            // null_deviance must also differ: this is the regression guard for the BigScore offset-zeroing fix.
            assertNotEquals("Restricted and unrestricted CV null_deviance must differ when offset is non-zero",
                    restricted.null_deviance(), unrestricted.null_deviance(), 1e-8);

            // Same-seed baseline without remove_offset_effects must reproduce the unrestricted view exactly.
            trainNoROE = makePoissonOffsetFrame("test_poisson_offset_cv_no_roe");
            GLMModel.GLMParameters paramsNoROE = new GLMModel.GLMParameters();
            paramsNoROE._train = trainNoROE._key;
            paramsNoROE._response_column = "y";
            paramsNoROE._offset_column = "offset";
            paramsNoROE._family = GLMModel.GLMParameters.Family.poisson;
            paramsNoROE._alpha = new double[]{0};
            paramsNoROE._lambda = new double[]{0};
            paramsNoROE._nfolds = 3;
            paramsNoROE._seed = 1234;
            paramsNoROE._remove_offset_effects = false;

            glmNoROE = new GLM(paramsNoROE).trainModel().get();
            Scope.track_generic(glmNoROE);
            GLMMetrics baseline = (GLMMetrics) glmNoROE._output._cross_validation_metrics;

            assertEquals("Unrestricted CV residual_deviance must match the same-seed remove_offset_effects=false baseline",
                    baseline.residual_deviance(), unrestricted.residual_deviance(), 1e-8);
            assertEquals("Unrestricted CV null_deviance must match the same-seed remove_offset_effects=false baseline",
                    baseline.null_deviance(), unrestricted.null_deviance(), 1e-8);
        } finally {
            if (train != null) train.remove();
            if (trainNoROE != null) trainNoROE.remove();
            Scope.exit();
        }
    }

    /**
     * Gaussian family, remove_offset_effects=true, nfolds>0, standardize=true, weights_column set:
     * regression guard for the GLMTask.GLMResDevTask sparseOffset fix, which specifically corrects a
     * standardization term that was previously (incorrectly) zeroed out alongside the offset column
     * under remove_offset_effects. Prior test coverage for remove_offset_effects+CV only exercised
     * binomial/Poisson with standardize left at its default and no weights, leaving this combination
     * (continuous family + standardization + weights, all under CV) unexercised.
     */
    @Test
    public void testRemoveOffsetCvGaussianStandardizedWeighted() {
        Frame train = null;
        Frame trainNoROE = null;
        GLMModel glm = null;
        GLMModel glmNoROE = null;
        try {
            Scope.enter();
            train = makeGaussianOffsetFrame("test_gaussian_offset_cv_std");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._weights_column = "weights";
            params._family = GLMModel.GLMParameters.Family.gaussian;
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._nfolds = 3;
            params._seed = 1234;
            params._standardize = true;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull(glm._output._cross_validation_metrics);
            assertNotNull(glm._output._cross_validation_metrics_unrestricted_model);
            GLMMetrics restricted = (GLMMetrics) glm._output._cross_validation_metrics;
            GLMMetrics unrestricted = (GLMMetrics) glm._output._cross_validation_metrics_unrestricted_model;

            // residual_deviance must differ: offset-removed predictions vs with-offset predictions.
            assertNotEquals("Restricted and unrestricted CV residual_deviance must differ when offset is non-zero",
                    restricted.residual_deviance(), unrestricted.residual_deviance(), 1e-8);

            // Same-seed baseline without remove_offset_effects must reproduce the unrestricted view exactly,
            // even with standardize=true and a weights_column set.
            trainNoROE = makeGaussianOffsetFrame("test_gaussian_offset_cv_std_no_roe");
            GLMModel.GLMParameters paramsNoROE = new GLMModel.GLMParameters();
            paramsNoROE._train = trainNoROE._key;
            paramsNoROE._response_column = "y";
            paramsNoROE._offset_column = "offset";
            paramsNoROE._weights_column = "weights";
            paramsNoROE._family = GLMModel.GLMParameters.Family.gaussian;
            paramsNoROE._alpha = new double[]{0};
            paramsNoROE._lambda = new double[]{0};
            paramsNoROE._nfolds = 3;
            paramsNoROE._seed = 1234;
            paramsNoROE._standardize = true;
            paramsNoROE._remove_offset_effects = false;

            glmNoROE = new GLM(paramsNoROE).trainModel().get();
            Scope.track_generic(glmNoROE);
            GLMMetrics baseline = (GLMMetrics) glmNoROE._output._cross_validation_metrics;

            assertEquals("Unrestricted CV residual_deviance must match the same-seed remove_offset_effects=false "
                    + "baseline even with standardize=true and weights_column set",
                    baseline.residual_deviance(), unrestricted.residual_deviance(), 1e-8);
        } finally {
            if (train != null) train.remove();
            if (trainNoROE != null) trainNoROE.remove();
            Scope.exit();
        }
    }

    /**
     * remove_offset_effects=true with an explicit fold_column (instead of nfolds) must populate the
     * unrestricted CV parity slots the same way the nfolds-driven path does.
     */
    @Test
    public void testRemoveOffsetCvUnrestrictedMetricsWithFoldColumn() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFoldColumnFrame("test_remove_offset_fold_column");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._fold_column = "fold";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull("Unrestricted CV metrics must populate via the fold_column path",
                    glm._output._cross_validation_metrics_unrestricted_model);
            assertNotNull("Unrestricted CV summary must populate via the fold_column path",
                    glm._output._cross_validation_metrics_summary_unrestricted_model);

            GLMMetrics restricted = (GLMMetrics) glm._output._cross_validation_metrics;
            GLMMetrics unrestricted = (GLMMetrics) glm._output._cross_validation_metrics_unrestricted_model;
            assertNotEquals("Restricted and unrestricted CV residual_deviance must differ when offset is non-zero",
                    restricted.residual_deviance(), unrestricted.residual_deviance(), 1e-10);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * make_unrestricted_model propagates the source's unrestricted CV parity slots into the
     * derived model's main CV slots (the derived IS the unrestricted view).
     */
    @Test
    public void testMakeUnrestrictedModelPropagatesCvMetrics() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_propagate_cv_unrestricted");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._keep_cross_validation_predictions = true;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Source has populated CV parity slots for propagation to copy.
            assertNotNull(glm._output._cross_validation_metrics);
            assertNotNull(glm._output._cross_validation_metrics_unrestricted_model);
            assertNotNull(glm._output._cross_validation_metrics_summary_unrestricted_model);
            assertNotNull(glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model);

            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeUnrestrictedGLMModelV3 args = new MakeUnrestrictedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "test_propagate_cv_unrestricted_derived";
            handler.make_unrestricted_model(3, args);
            derived = DKV.getGet(Key.make("test_propagate_cv_unrestricted_derived"));
            assertNotNull(derived);
            Scope.track_generic(derived);

            // Derived CV ModelMetrics shares the same key as source's unrestricted parity slot.
            assertNotNull(derived._output._cross_validation_metrics);
            assertEquals(glm._output._cross_validation_metrics_unrestricted_model._key,
                    derived._output._cross_validation_metrics._key);

            // Holdout-pred frame is deep-copied into the derived model, not transferred: the source
            // keeps its own reference, and the derived model gets an independent copy under a new key.
            assertNotNull("Source must keep its own holdout-pred frame reference",
                    glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model);
            assertNotNull(derived._output._cross_validation_holdout_predictions_frame_id);
            assertNotEquals("Derived model's holdout-pred frame must be a distinct copy, not the source's key",
                    glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model,
                    derived._output._cross_validation_holdout_predictions_frame_id);
            Frame sourceHoldout = DKV.getGet(glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model);
            Frame derivedHoldout = DKV.getGet(derived._output._cross_validation_holdout_predictions_frame_id);
            assertNotNull(sourceHoldout);
            assertNotNull(derivedHoldout);
            Scope.track(sourceHoldout);
            Scope.track(derivedHoldout);
            assertEquals("Deep-copied holdout frame must have the same shape as the source's",
                    sourceHoldout.numRows(), derivedHoldout.numRows());

            // Summary has the same shape.
            assertNotNull(derived._output._cross_validation_metrics_summary);
            assertEquals(glm._output._cross_validation_metrics_summary_unrestricted_model.getColDim(),
                    derived._output._cross_validation_metrics_summary.getColDim());
            assertEquals(glm._output._cross_validation_metrics_summary_unrestricted_model.getRowDim(),
                    derived._output._cross_validation_metrics_summary.getRowDim());

            // Derived (unrestricted) residual_deviance differs from source's restricted view,
            // and equals source's unrestricted parity slot — confirms the right field flowed through.
            double srcRestrictedDev = ((ModelMetricsBinomialGLM) glm._output._cross_validation_metrics).residual_deviance();
            double derivedDev = ((ModelMetricsBinomialGLM) derived._output._cross_validation_metrics).residual_deviance();
            assertNotEquals(srcRestrictedDev, derivedDev, 1e-10);
            double srcUnrestrictedDev = ((ModelMetricsBinomialGLM) glm._output._cross_validation_metrics_unrestricted_model).residual_deviance();
            assertEquals(srcUnrestrictedDev, derivedDev, 1e-10);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * Derived model created with remove_offset_effects=true flag must survive parent deletion.
     */
    @Test
    public void testDerivedRemoveOffsetEffectsSurvivesParentDeletion() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_ro_parent_deletion");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();

            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "test_ro_parent_deletion_derived";
            args.remove_offset_effects = true;
            new MakeGLMModelHandler().make_derived_model(3, args);
            derived = DKV.getGet(Key.make("test_ro_parent_deletion_derived"));
            assertNotNull(derived);

            double expectedTrainDev = ((ModelMetricsBinomialGLM) derived._output._training_metrics).residual_deviance();

            Key<GLMModel> derivedKey = derived._key;
            glm.remove();
            glm = null;

            GLMModel derivedAfter = DKV.getGet(derivedKey);
            assertNotNull("Derived model must still exist after parent deletion", derivedAfter);
            assertNotNull("Training metrics must survive parent deletion", derivedAfter._output._training_metrics);
            assertEquals("Training deviance unchanged after parent deletion",
                    expectedTrainDev,
                    ((ModelMetricsBinomialGLM) derivedAfter._output._training_metrics).residual_deviance(),
                    1e-10);

            Scope.track_generic(derivedAfter);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * Derived model created with remove_control_variables_effects=true flag must survive parent deletion.
     */
    @Test
    public void testDerivedRemoveControlVariablesSurvivesParentDeletion() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_cv_parent_deletion");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();

            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "test_cv_parent_deletion_derived";
            args.remove_control_variables_effects = true;
            new MakeGLMModelHandler().make_derived_model(3, args);
            derived = DKV.getGet(Key.make("test_cv_parent_deletion_derived"));
            assertNotNull(derived);

            double expectedTrainDev = ((ModelMetricsBinomialGLM) derived._output._training_metrics).residual_deviance();

            Key<GLMModel> derivedKey = derived._key;
            glm.remove();
            glm = null;

            GLMModel derivedAfter = DKV.getGet(derivedKey);
            assertNotNull("Derived model must still exist after parent deletion", derivedAfter);
            assertNotNull("Training metrics must survive parent deletion", derivedAfter._output._training_metrics);
            assertEquals("Training deviance unchanged after parent deletion",
                    expectedTrainDev,
                    ((ModelMetricsBinomialGLM) derivedAfter._output._training_metrics).residual_deviance(),
                    1e-10);

            Scope.track_generic(derivedAfter);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * Derived model created via make_unrestricted_model must survive parent deletion with all
     * metrics intact.  Verifies that metrics are serialized inline in the derived model's own
     * DKV entry and are not destroyed when the parent model (and its tracked metric keys) is
     * removed from DKV.
     */
    @Test
    public void testDerivedModelSurvivesParentDeletion() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_parent_deletion");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._keep_cross_validation_predictions = true;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();

            MakeUnrestrictedGLMModelV3 args = new MakeUnrestrictedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "test_parent_deletion_derived";
            new MakeGLMModelHandler().make_unrestricted_model(3, args);
            derived = DKV.getGet(Key.make("test_parent_deletion_derived"));
            assertNotNull(derived);

            // Record expected values from the derived model before parent deletion.
            double expectedTrainDev = ((ModelMetricsBinomialGLM) derived._output._training_metrics).residual_deviance();
            double expectedCvDev    = ((ModelMetricsBinomialGLM) derived._output._cross_validation_metrics).residual_deviance();
            int expectedSummaryRows = derived._output._cross_validation_metrics_summary.getRowDim();

            Key<GLMModel> derivedKey = derived._key;

            // Delete the parent model — this removes the parent's DKV entry and all metric keys
            // tracked in its _model_metrics list (including the unrestricted CV metrics).
            glm.remove();
            glm = null;

            // Re-fetch derived model from DKV to get a fresh deserialization (not the in-memory ref).
            GLMModel derivedAfter = DKV.getGet(derivedKey);
            assertNotNull("Derived model must still exist in DKV after parent deletion", derivedAfter);

            assertNotNull("Training metrics must survive parent deletion", derivedAfter._output._training_metrics);
            assertEquals("Training deviance unchanged after parent deletion",
                    expectedTrainDev,
                    ((ModelMetricsBinomialGLM) derivedAfter._output._training_metrics).residual_deviance(),
                    1e-10);

            assertNotNull("CV metrics must survive parent deletion", derivedAfter._output._cross_validation_metrics);
            assertEquals("CV deviance unchanged after parent deletion",
                    expectedCvDev,
                    ((ModelMetricsBinomialGLM) derivedAfter._output._cross_validation_metrics).residual_deviance(),
                    1e-10);

            assertNotNull("CV metrics summary must survive parent deletion", derivedAfter._output._cross_validation_metrics_summary);
            assertEquals("CV summary row count unchanged after parent deletion",
                    expectedSummaryRows, derivedAfter._output._cross_validation_metrics_summary.getRowDim());

            // Holdout frame was deep-copied into the derived model at creation time (independent of the
            // source's own copy); must still be in DKV after parent deletion.
            assertNotNull("CV holdout predictions frame must survive parent deletion",
                    derivedAfter._output._cross_validation_holdout_predictions_frame_id);
            assertNotNull("CV holdout predictions frame must be retrievable from DKV after parent deletion",
                    DKV.getGet(derivedAfter._output._cross_validation_holdout_predictions_frame_id));

            Scope.track_generic(derivedAfter);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * Once a derived unrestricted model is created and then manually removed from DKV, calling
     * make_unrestricted_model again on the same source must succeed and produce a fresh, valid
     * derived model. The holdout-predictions frame is deep-copied at derive time (never transferred
     * away from the source), so the source's own reference stays intact and re-deriving is repeatable.
     */
    @Test
    public void testMakeUnrestrictedModelCanBeRederivedAfterDerivedDeleted() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel secondDerived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_rederive_guard");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._keep_cross_validation_predictions = true;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            Key<Frame> sourceHoldoutKey = glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model;
            assertNotNull(sourceHoldoutKey);

            MakeUnrestrictedGLMModelV3 args = new MakeUnrestrictedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "test_rederive_guard_derived";
            new MakeGLMModelHandler().make_unrestricted_model(3, args);
            GLMModel derived = DKV.getGet(Key.make("test_rederive_guard_derived"));
            assertNotNull(derived);

            // Source keeps its own holdout-pred frame reference — it was deep-copied, not transferred.
            assertEquals("Source's holdout-pred frame key must be unchanged after deriving",
                    sourceHoldoutKey, glm._output._cross_validation_holdout_predictions_frame_id_unrestricted_model);
            assertNotNull("Source's holdout-pred frame must still be retrievable from DKV",
                    DKV.getGet(sourceHoldoutKey));

            // Manually remove the derived model from DKV (simulates user calling h2o.rm); this also
            // removes the derived model's own (deep-copied) holdout-pred frame, but not the source's.
            Keyed.remove(derived._key);
            assertNull(DKV.getGet(derived._key));
            assertNotNull("Source's holdout-pred frame must survive the derived model's removal",
                    DKV.getGet(sourceHoldoutKey));

            // Re-deriving with the same source and the same dest key must now succeed (the dest key
            // is free again) and produce a fresh, independently-owned derived model.
            new MakeGLMModelHandler().make_unrestricted_model(3, args);
            secondDerived = DKV.getGet(Key.make("test_rederive_guard_derived"));
            Scope.track_generic(secondDerived);
            assertNotNull("Re-deriving after the first derived model was deleted must succeed", secondDerived);
            assertNotNull(secondDerived._output._cross_validation_holdout_predictions_frame_id);
            assertNotEquals("Re-derived model must get its own fresh holdout-pred frame copy, not the source's key",
                    sourceHoldoutKey, secondDerived._output._cross_validation_holdout_predictions_frame_id);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * make_derived_model at an already-occupied dest key: re-deriving the same view from the same
     * source is idempotent (returns the existing model instead of erroring or creating a duplicate),
     * but a colliding key that does not carry matching provenance must be rejected instead of being
     * silently returned or blindly cast.
     */
    @Test
    public void testMakeDerivedModelProvenanceGuard() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        Frame collidingFrame = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_derive_provenance");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            MakeUnrestrictedGLMModelV3 args = new MakeUnrestrictedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "test_derive_provenance_derived";
            new MakeGLMModelHandler().make_unrestricted_model(3, args);
            derived = DKV.getGet(Key.make("test_derive_provenance_derived"));
            assertNotNull(derived);
            Scope.track_generic(derived);

            // Re-deriving the same view from the same source at the same dest is idempotent: the
            // existing model is returned as-is, not recreated or rejected.
            GLMModelV3 repeat = new MakeGLMModelHandler().make_unrestricted_model(3, args);
            assertEquals("Idempotent re-derive must return the same model key",
                    derived._key.toString(), repeat.model_id.name);

            // A key that already holds something other than a matching-provenance GLM model must be
            // rejected rather than silently returned or blindly cast.
            collidingFrame = new Frame(Key.<Frame>make("test_derive_provenance_colliding"), new String[]{"c"},
                    new Vec[]{Vec.makeVec(new double[]{1, 2, 3}, Vec.newKey())});
            DKV.put(collidingFrame);
            MakeUnrestrictedGLMModelV3 collidingArgs = new MakeUnrestrictedGLMModelV3();
            collidingArgs.model = new KeyV3.ModelKeyV3(glm._key);
            collidingArgs.dest = "test_derive_provenance_colliding";
            try {
                new MakeGLMModelHandler().make_unrestricted_model(3, collidingArgs);
                fail("Expected IllegalArgumentException when dest key holds a non-GLMModel object.");
            } catch (IllegalArgumentException e) {
                assertTrue("Exception must call out the wrong-type collision, not a ClassCastException",
                        e.getMessage().contains("does not refer to a GLM model"));
            }
        } finally {
            if (train != null) train.remove();
            if (collidingFrame != null) collidingFrame.remove();
            Scope.exit();
        }
    }

    @Test
    public void testMakeDerivedRemoveOffsetWithCvNoControlVariables() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("p0_1_derived_cv_no_ctrl");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            assertNotNull(glm._output._training_metrics);
            assertNotNull(glm._output._cross_validation_metrics);
            assertNotNull(glm._output._cross_validation_metrics_summary);
            double srcCvDev = ((ModelMetricsBinomialGLM) glm._output._cross_validation_metrics).residual_deviance();

            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "p0_1_derived_cv_no_ctrl_out";
            args.remove_offset_effects = true;
            new MakeGLMModelHandler().make_derived_model(3, args);
            GLMModel derived = DKV.getGet(Key.make("p0_1_derived_cv_no_ctrl_out"));
            assertNotNull(derived);
            Scope.track_generic(derived);

            assertNotNull("Derived training_metrics must not be null", derived._output._training_metrics);
            assertNotNull("Derived scoring_history must not be null", derived._output._scoring_history);
            assertNotNull("Derived cross_validation_metrics must be propagated", derived._output._cross_validation_metrics);
            assertNotNull("Derived cross_validation_metrics_summary must be propagated", derived._output._cross_validation_metrics_summary);

            assertEquals("Derived training MSE must match source's restricted view",
                    glm._output._training_metrics._MSE, derived._output._training_metrics._MSE, 1e-12);
            assertEquals("Derived CV deviance must match source's restricted CV deviance",
                    srcCvDev,
                    ((ModelMetricsBinomialGLM) derived._output._cross_validation_metrics).residual_deviance(), 1e-12);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * make_derived_glm_model(remove_offset_effects=true) must propagate the (restricted, offset-removed)
     * CV holdout-predictions frame into a derived-owned key, the same way make_unrestricted_glm_model
     * already does for the with-offset view. Regression guard: this branch previously left the derived
     * model's holdout-predictions frame id null even when the source had one.
     */
    @Test
    public void testMakeDerivedRemoveOffsetPropagatesHoldoutPreds() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("p0_1_derived_cv_holdout_preds");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._remove_offset_effects = true;
            params._keep_cross_validation_predictions = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            Key<Frame> sourceHoldoutKey = glm._output._cross_validation_holdout_predictions_frame_id;
            assertNotNull("Source must have a restricted CV holdout-predictions frame", sourceHoldoutKey);

            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "p0_1_derived_cv_holdout_preds_out";
            args.remove_offset_effects = true;
            new MakeGLMModelHandler().make_derived_model(3, args);
            derived = DKV.getGet(Key.make("p0_1_derived_cv_holdout_preds_out"));
            assertNotNull(derived);
            Scope.track_generic(derived);

            assertNotNull("Derived model must have a CV holdout-predictions frame",
                    derived._output._cross_validation_holdout_predictions_frame_id);
            assertNotEquals("Derived model's holdout-pred frame must be a distinct copy, not the source's key",
                    sourceHoldoutKey, derived._output._cross_validation_holdout_predictions_frame_id);

            Frame sourceHoldout = DKV.getGet(sourceHoldoutKey);
            Frame derivedHoldout = DKV.getGet(derived._output._cross_validation_holdout_predictions_frame_id);
            assertNotNull(sourceHoldout);
            assertNotNull(derivedHoldout);
            Scope.track(sourceHoldout);
            Scope.track(derivedHoldout);
            assertEquals("Deep-copied holdout frame must have the same shape as the source's",
                    sourceHoldout.numRows(), derivedHoldout.numRows());
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    @Test
    public void testMakeDerivedRemoveOffsetNoControlVariablesNoCv() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("p0_1_derived_no_ctrl_no_cv");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._remove_offset_effects = true;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assertNotNull(glm._output._training_metrics);

            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "p0_1_derived_no_ctrl_no_cv_out";
            args.remove_offset_effects = true;
            new MakeGLMModelHandler().make_derived_model(3, args);
            GLMModel derived = DKV.getGet(Key.make("p0_1_derived_no_ctrl_no_cv_out"));
            assertNotNull(derived);
            Scope.track_generic(derived);

            assertNotNull("Derived training_metrics must not be null", derived._output._training_metrics);
            assertNotNull("Derived scoring_history must not be null", derived._output._scoring_history);
            assertEquals("Derived training MSE must match source's restricted view",
                    glm._output._training_metrics._MSE, derived._output._training_metrics._MSE, 1e-12);
        } finally {
            if (train != null) train.remove();
            Scope.exit();
        }
    }

    /**
     * Regression guard for removing model.dinfo().setPredictorTransform(NONE) from make_model.
     * The old code mutated the source model's DataInfo by zeroing _normMul/_normSub; the fix
     * must leave those arrays intact.
     */
    @Test
    public void testMakeModelDoesNotMutateSourceDataInfo() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_no_mutate_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            DataInfo dinfoBefore = glm.dinfo();
            assertNotNull("DataInfo must exist on trained model", dinfoBefore);
            assertNotNull("_normMul must be non-null with standardize=true on numeric data", dinfoBefore._normMul);
            double[] normMulBefore = dinfoBefore._normMul.clone();

            // Call make_model with unchanged coefficients
            String[] coefNames = glm._output.coefficientNames();
            Key<GLMModel> destKey = Key.make("mm_no_mutate_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = glm.beta().clone();
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull("make_model must put a model into DKV", derived);
            Scope.track_generic(derived);

            // Source model DataInfo must be unmodified — the old code called
            // model.dinfo().setPredictorTransform(NONE) which nulled out _normMul
            DataInfo dinfoAfter = glm.dinfo();
            assertNotNull("make_model must not null out source DataInfo _normMul", dinfoAfter._normMul);
            assertArrayEquals("make_model must not mutate source DataInfo _normMul",
                    normMulBefore, dinfoAfter._normMul, 0);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * Verifies that make_model produces correct predictions without calling setPredictorTransform.
     * A derived model with zeroed predictor coefficients (intercept only) must predict a constant
     * probability for all rows; the source model with fitted predictors must predict varying values.
     */
    @Test
    public void testMakeModelProducesCorrectPredictions() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        Frame predSource = null;
        Frame predDerived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mm_preds_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._ignored_columns = new String[]{"offset"};
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Zero out all predictor coefficients, keep only the intercept (always last).
            // The derived model must then predict sigmoid(intercept) for every row.
            String[] coefNames = glm._output.coefficientNames();
            double[] beta = glm.beta();
            double[] interceptOnlyBeta = new double[coefNames.length];
            interceptOnlyBeta[coefNames.length - 1] = beta[coefNames.length - 1];

            Key<GLMModel> destKey = Key.make("mm_preds_derived");
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeGLMModelV3 args = new MakeGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = new KeyV3.ModelKeyV3(destKey);
            args.names = coefNames;
            args.beta = interceptOnlyBeta;
            handler.make_model(3, args);
            derived = DKV.getGet(destKey);
            assertNotNull("make_model must produce a model", derived);
            Scope.track_generic(derived);

            predSource = glm.score(train);
            Scope.track(predSource);
            predDerived = derived.score(train);
            Scope.track(predDerived);

            // Binomial prediction frame: [predict, p(y=0), p(y=1)]; use vec(2) for p(y=1)
            Vec srcProb = predSource.vec(2);
            Vec derivedProb = predDerived.vec(2);

            double constantProb = derivedProb.at(0);
            assertTrue("Intercept-only derived model must predict a positive probability", constantProb > 0);
            for (long i = 1; i < predDerived.numRows(); i++) {
                assertEquals("Derived model with zeroed predictors must predict constant probability",
                        constantProb, derivedProb.at(i), 1e-10);
            }

            boolean sourceVaries = false;
            double firstSrcProb = srcProb.at(0);
            for (long i = 1; i < predSource.numRows(); i++) {
                if (Math.abs(srcProb.at(i) - firstSrcProb) > 1e-10) { sourceVaries = true; break; }
            }
            assertTrue("Source model with fitted numeric predictors must predict varying probabilities", sourceVaries);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            if (predSource != null) predSource.remove();
            if (predDerived != null) predDerived.remove();
            Scope.exit();
        }
    }

    /**
     * Regression guard for a raw-beta-denormalization bug in make_derived_model/
     * make_unrestricted_model: dropping setPredictorTransform(NONE) entirely (to avoid mutating the
     * source's shared DataInfo via the old dinfo.setPredictorTransform(NONE) call on the *uncloned*
     * source dinfo) left the derived model's DataInfo reporting the source's STANDARDIZE transform
     * even though the beta stored on it is raw/denormalized. Two consequences must both be fixed:
     * 1. isStandardized() must report false (the stored beta genuinely isn't standardized).
     * 2. beta(lambda) — which denormalizes via DataInfo.denormalizeBeta() whenever
     *    isStandardized() is true — must NOT rescale the already-raw beta using the source's
     *    _normMul/_normSub (real, non-trivial values here since x1 has a 100-1050 numeric scale);
     *    it must return the same values as the plain beta() accessor.
     * (coefficients(true)/coef_norm() itself is unaffected either way here: GLMOutput.getNormBeta()
     * only recovers genuine standardized coefficients when the DataInfo's post-hoc standardization
     * stats are available, which requires a live _adaptedFrame — and a trained model's own retained
     * DataInfo always has _adaptedFrame nulled out (GLMModel's GLMOutput(GLM) constructor), whether
     * or not this fix is applied.)
     */
    @Test
    public void testMakeUnrestrictedModelDoesNotCorruptRawBetaViaDenormalize() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();
            train = makeNumericBinomialOffsetFrame("mu_denorm_train");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._standardize = true;
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assertNotNull("Sanity check: source dinfo must carry real, non-trivial normalization stats",
                    glm.dinfo()._normMul);

            MakeUnrestrictedGLMModelV3 args = new MakeUnrestrictedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "mu_denorm_derived";
            new MakeGLMModelHandler().make_unrestricted_model(3, args);
            derived = DKV.getGet(Key.make("mu_denorm_derived"));
            assertNotNull(derived);
            Scope.track_generic(derived);

            assertFalse("Derived model's DataInfo must not report STANDARDIZE over a raw beta",
                    derived._output.isStandardized());
            assertArrayEquals("beta(lambda) must equal the raw beta() accessor, not a spuriously "
                    + "denormalized value computed from the inherited STANDARDIZE transform",
                    derived.beta(), derived.beta(0.0), 1e-8);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
            Scope.exit();
        }
    }

    /**
     * Training with control_variables + nfolds (cross-validation) must be rejected.
     * CV is not supported when control_variables are set.
     */
    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testControlVariablesWithCvThrows() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("p0_1_derived_both_cv");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._nfolds = 3;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;
            params._control_variables = new String[]{"x1"};

            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * Verifies that remove_impl cleans up _training_metrics_unrestricted_model and
     * _validation_metrics_unrestricted_model from DKV when the model is deleted.
     * These metrics are Keyed but not tracked in _model_metrics, so without the explicit
     * remove_impl override they would leak in DKV.
     */
    @Test
    public void testRemoveImplCleansUpUnrestrictedMetrics() {
        Frame train = null;
        Frame valid = null;
        GLMModel glm = null;
        try {
            Scope.enter();
            train = makeBinomialOffsetFrame("test_remove_impl_train");
            valid = makeBinomialOffsetFrame("test_remove_impl_valid");

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._valid = valid._key;
            params._response_column = "y";
            params._offset_column = "offset";
            params._alpha = new double[]{0};
            params._lambda = new double[]{0};
            params._intercept = false;
            params._remove_offset_effects = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();

            GLMModel.GLMOutput out = (GLMModel.GLMOutput) glm._output;
            assertNotNull("Training unrestricted metrics must be populated.", out._training_metrics_unrestricted_model);
            assertNotNull("Validation unrestricted metrics must be populated.", out._validation_metrics_unrestricted_model);

            Key trainUnrestrKey = out._training_metrics_unrestricted_model._key;
            Key validUnrestrKey = out._validation_metrics_unrestricted_model._key;

            assertNotNull("Training unrestricted metrics must be in DKV before removal.", DKV.get(trainUnrestrKey));
            assertNotNull("Validation unrestricted metrics must be in DKV before removal.", DKV.get(validUnrestrKey));

            glm.remove();
            glm = null;

            assertNull("Training unrestricted metrics must be removed from DKV after model.remove().", DKV.get(trainUnrestrKey));
            assertNull("Validation unrestricted metrics must be removed from DKV after model.remove().", DKV.get(validUnrestrKey));
        } finally {
            if (train != null) train.remove();
            if (valid != null) valid.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

}
