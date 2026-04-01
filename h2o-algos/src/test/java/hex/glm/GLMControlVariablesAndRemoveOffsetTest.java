package hex.glm;

import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsBinomialGLM;
import hex.api.MakeGLMModelHandler;
import hex.genmodel.utils.DistributionFamily;
import hex.schemas.MakeDerivedGLMModelV3;
import hex.schemas.MakeUnrestrictedGLMModelV3;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.api.schemas3.KeyV3;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.DistributedException;
import water.util.TwoDimTable;

import java.util.Arrays;

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

            assertFalse(Arrays.equals(vi.getRowHeaders(), vi_unrestricted.getRowHeaders()));
            assertTrue(Arrays.equals(vi_unrestricted.getRowHeaders(), vi_unrestristed_2.getRowHeaders()));

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

            assertArrayEquals(vi.getRowHeaders(), vi_unrestricted.getRowHeaders());
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

            assertFalse(Arrays.equals(vi.getRowHeaders(), vi_unrestricted.getRowHeaders()));
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

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testRemoveOffsetWithLambdaSearch() {
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
            params._lambda_search = true;
            glm = new GLM(params).trainModel().get();
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testRemoveOffsetWithCrossValiadation() {
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
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
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

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p0_1_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            // Use numeric columns where standardize=true has a real effect on the DataInfo transform
            Vec x1 = Vec.makeVec(new double[]{100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
                    150, 250, 350, 450, 550, 650, 750, 850, 950, 1050,
                    120, 220, 320, 420, 520, 620}, Vec.newKey());
            Vec x2 = Vec.makeVec(new double[]{0.01, 0.02, 0.03, 0.04, 0.05, 0.06, 0.07, 0.08, 0.09, 0.10,
                    0.015, 0.025, 0.035, 0.045, 0.055, 0.065, 0.075, 0.085, 0.095, 0.105,
                    0.012, 0.022, 0.032, 0.042, 0.052, 0.062}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p0_2_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{x1, x2, offset, res});
            DKV.put(train);

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

    /**
     * Control variables must be excluded from restricted varimp even when their
     * column indices are not in ascending order.
     */
    @Test
    public void testCvVarimpExcludesControlVariables() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            // Create columns: a, b, c, d, y
            // Control variables specified as ["d", "b"] → indices [3, 1] — descending order
            Vec a = Vec.makeVec(new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0,
                    1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5, 10.5}, Vec.newKey());
            Vec b = Vec.makeVec(new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
                    0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95, 1.05}, Vec.newKey());
            Vec c = Vec.makeVec(new double[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100,
                    15, 25, 35, 45, 55, 65, 75, 85, 95, 105}, Vec.newKey());
            Vec d = Vec.makeVec(new double[]{0.5, 1.5, 2.5, 3.5, 4.5, 5.5, 6.5, 7.5, 8.5, 9.5,
                    0.75, 1.75, 2.75, 3.75, 4.75, 5.75, 6.75, 7.75, 8.75, 9.75}, Vec.newKey());
            Vec y = Vec.makeVec(new double[]{1, 0, 1, 0, 1, 0, 1, 0, 1, 0,
                    1, 1, 0, 0, 1, 1, 0, 0, 1, 1}, new String[]{"0", "1"}, Vec.newKey());

            train = new Frame(Key.<Frame>make("p0_3_train"), new String[]{"a", "b", "c", "d", "y"}, new Vec[]{a, b, c, d, y});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            params._alpha = new double[]{0};
            // Specify control variables in reverse column order: d (idx 3) before b (idx 1)
            params._control_variables = new String[]{"d", "b"};
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // The restricted variable importance should NOT contain the control variables "b" and "d"
            TwoDimTable vi = glm._output._variable_importances;
            assertNotNull("Variable importance should be present", vi);

            String[] viNames = vi.getRowHeaders();
            for (String varName : viNames) {
                assertNotEquals("Control variable 'b' should not appear in restricted variable importance",
                        "b", varName);
                assertNotEquals("Control variable 'd' should not appear in restricted variable importance",
                        "d", varName);
            }

            // The unrestricted variable importance SHOULD contain them
            TwoDimTable viUnrestricted = glm._output._variable_importances_unrestricted_model;
            assertNotNull("Unrestricted variable importance should be present", viUnrestricted);
            boolean foundB = false, foundD = false;
            for (String varName : viUnrestricted.getRowHeaders()) {
                if ("b".equals(varName)) foundB = true;
                if ("d".equals(varName)) foundD = true;
            }
            assertTrue("Unrestricted varimp should contain 'b'", foundB);
            assertTrue("Unrestricted varimp should contain 'd'", foundD);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * With both CV and RO enabled and generate_scoring_history=true, the scoring history
     * deviance_train must match training metrics mean residual deviance.
     */
    @Test
    public void testCvRoScoringHistoryDevianceMatchesMetrics() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p0_4_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._score_each_iteration = true;
            params._generate_scoring_history = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // The main restricted model's scoring history is in _scoring_history
            // (which represents the model with BOTH CV and RO restrictions applied)
            TwoDimTable sh = glm._output._scoring_history;
            assertNotNull("Main scoring history should exist", sh);

            // Get the training metrics for the main restricted model
            ModelMetrics mm = glm._output._training_metrics;
            assertNotNull("Training metrics should exist", mm);
            // Scoring history stores mean deviance (total / nobs), so normalize metrics the same way
            double metricsDeviance = ((ModelMetricsBinomialGLM) mm).residual_deviance() / mm._nobs;

            // Get the last row of scoring history deviance
            int devianceCol = Arrays.asList(sh.getColHeaders()).indexOf("deviance_train");
            assertTrue("Scoring history should have training_deviance column", devianceCol >= 0);
            int lastRow = sh.getRowDim() - 1;
            double shDeviance = (double) sh.get(lastRow, devianceCol);

            // The scoring history deviance at the last iteration should match the actual metrics deviance.
            // If the bug is present (offset not removed in deviance computation), these will differ.
            assertEquals("Scoring history deviance should match training metrics mean residual deviance " +
                            "(both should account for CV+RO restrictions)",
                    metricsDeviance, shDeviance, metricsDeviance * 0.01);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * With only RO enabled and generate_scoring_history=true, the scoring history
     * deviance_train must match training metrics mean residual deviance.
     */
    @Test
    public void testRoScoringHistoryDevianceMatchesMetrics() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p0_5_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._remove_offset_effects = true;
            params._score_each_iteration = true;
            params._generate_scoring_history = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // The restricted model's scoring history (with offset removed)
            TwoDimTable sh = glm._output._scoring_history;
            assertNotNull("Main scoring history should exist", sh);

            // Get the training metrics for the restricted model (offset removed)
            ModelMetrics mm = glm._output._training_metrics;
            assertNotNull("Training metrics should exist", mm);
            // Scoring history stores mean deviance (total / nobs), so normalize metrics the same way
            double metricsDeviance = ((ModelMetricsBinomialGLM) mm).residual_deviance() / mm._nobs;

            // Get the last row of scoring history deviance
            int devianceCol = Arrays.asList(sh.getColHeaders()).indexOf("deviance_train");
            assertTrue("Scoring history should have training_deviance column", devianceCol >= 0);
            int lastRow = sh.getRowDim() - 1;
            double shDeviance = (double) sh.get(lastRow, devianceCol);

            // The scoring history deviance should match the actual metrics deviance.
            // If the bug is present (using unrestricted _state.likelihood() instead of
            // GLMResDevTask with removeOffsetEffects=true), these will differ.
            assertEquals("Scoring history deviance should match training metrics mean residual deviance " +
                            "(both should account for offset removal)",
                    metricsDeviance, shDeviance, metricsDeviance * 0.01);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
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
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            // set cat columns (same pattern as existing tests)
            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            train.replace(numCols - 1, train.vec(numCols - 1).toCategoricalVec()).remove();
            DKV.put(train);
            Scope.track_generic(train);

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
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            train.replace(numCols - 1, train.vec(numCols - 1).toCategoricalVec()).remove();
            DKV.put(train);
            Scope.track_generic(train);

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
            train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            GLMModel.GLMParameters.Family family = GLMModel.GLMParameters.Family.binomial;
            String responseColumn = "C21";

            int numCols = train.numCols();
            int enumCols = (numCols - 1) / 2;
            for (int cindex = 0; cindex < enumCols; cindex++) {
                train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
            }
            train.replace(numCols - 1, train.vec(numCols - 1).toCategoricalVec()).remove();
            DKV.put(train);
            Scope.track_generic(train);

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
     * haveMojo()/havePojo() should return false when remove_offset_effects=true,
     * since MOJO/POJO scoring doesn't implement offset removal.
     */
    @Test
    public void testRoMojoPojoGuard() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_2_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            // With remove_offset_effects=true, the exported MOJO/POJO would score WITH offset
            // but the H2O model scores WITHOUT offset. haveMojo()/havePojo() should return false.
            // Currently haveMojo() only checks control_variables but NOT remove_offset_effects.
            assertFalse("haveMojo() should return false when remove_offset_effects=true " +
                    "(MOJO scoring doesn't implement offset removal)", glm.haveMojo());
            assertFalse("havePojo() should return false when remove_offset_effects=true " +
                    "(POJO scoring doesn't implement offset removal)", glm.havePojo());
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * Smoke test: remove_offset_effects=true without control_variables should complete training.
     */
    @Test
    public void testRoOnlyTrainingCompletes() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("ro_hang_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            assertNotNull("Model should complete training", glm);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * After training with both CV and RO, the scoring flags on the model should be set
     * so that default predict() uses the restricted scoring behavior.
     */
    @Test
    public void testCvRoScoringFlagsAfterTraining() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_3_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._alpha = new double[]{0};
            params._response_column = "y";
            params._offset_column = "offset";
            params._control_variables = new String[]{"x1"};
            params._remove_offset_effects = true;
            params._score_each_iteration = true;
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // After training with both features, flags are set permanently for the model's default scoring
            assertTrue("_useControlVariables should be true after training with control_variables",
                    glm._useControlVariables);
            assertTrue("_useRemoveOffsetEffects should be true after training with remove_offset_effects",
                    glm._useRemoveOffsetEffects);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * Derived model's metrics are shared references from the source model.
     * Documents the current behavior (not cloned).
     */
    @Test
    public void testDerivedModelMetricsAreSharedReferences() {
        Frame train = null;
        GLMModel glm = null;
        GLMModel derived = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_4_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            // Create derived model
            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.dest = "p1_4_derived";
            args.remove_offset_effects = false;
            args.remove_control_variables_effects = false;
            handler.make_derived_model(3, args);
            derived = DKV.getGet(Key.make("p1_4_derived"));
            Scope.track_generic(derived);

            // Verify derived model has metrics
            assertNotNull("Derived model training metrics should exist",
                    derived._output._training_metrics);

            // The metrics objects should be the same reference (shared, not cloned)
            // This test documents the current behavior — metrics are shared, not independent
            assertSame("Derived model metrics are shared references (not cloned) from source — " +
                            "removing the derived model could corrupt the source",
                    glm._output._training_metrics_unrestricted_model,
                    derived._output._training_metrics);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (derived != null) derived.remove();
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

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_5_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            // Individual flag on model trained without both features should also throw
            GLMModel.GLMParameters paramsCV = new GLMModel.GLMParameters();
            paramsCV._train = train._key;
            paramsCV._alpha = new double[]{0};
            paramsCV._response_column = "y";
            paramsCV._control_variables = new String[]{"x1"};
            paramsCV._distribution = DistributionFamily.bernoulli;
            paramsCV._link = GLMModel.GLMParameters.Link.logit;

            GLMModel glmCVOnly = new GLM(paramsCV).trainModel().get();
            Scope.track_generic(glmCVOnly);

            MakeDerivedGLMModelV3 args2 = new MakeDerivedGLMModelV3();
            args2.model = new KeyV3.ModelKeyV3(glmCVOnly._key);
            args2.remove_control_variables_effects = true;
            try {
                handler.make_derived_model(3, args2);
                fail("Should have thrown when using individual flag on model without both features");
            } catch (IllegalArgumentException e) {
                assertTrue("Error should mention both features must be set",
                        e.getMessage().contains("control_variables and remove_offset_effects are both set"));
            }
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
     * control_variables specified by column name must be excluded from restricted varimp.
     */
    @Test
    public void testCvVarimpExcludesNamedControl() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec a = Vec.makeVec(new double[]{1,2,3,4,5,6,7,8,9,10,1,2,3,4,5,6,7,8,9,10}, Vec.newKey());
            Vec b = Vec.makeVec(new double[]{10,20,30,40,50,60,70,80,90,100,15,25,35,45,55,65,75,85,95,105}, Vec.newKey());
            Vec y = Vec.makeVec(new double[]{1,0,1,0,1,0,1,0,1,0,1,1,0,0,1,1,0,0,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p1_8_train"), new String[]{"predictor_a", "control_b", "y"}, new Vec[]{a, b, y});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._response_column = "y";
            // Use column NAME, not index — this is what actually works
            params._control_variables = new String[]{"control_b"};
            params._distribution = DistributionFamily.bernoulli;
            params._link = GLMModel.GLMParameters.Link.logit;

            glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);

            // Verify control variable was applied by checking variable importance
            TwoDimTable vi = glm._output._variable_importances;
            assertNotNull("Variable importance should exist", vi);
            // Control variable should NOT appear in restricted varimp
            for (String name : vi.getRowHeaders()) {
                assertNotEquals("Control variable 'control_b' should not be in restricted varimp",
                        "control_b", name);
            }
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
     * Error message when both make_derived_model flags are set should mention they cannot be used together.
     */
    @Test
    public void testDerivedModelBothFlagsErrorMessage() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p2_6_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            MakeGLMModelHandler handler = new MakeGLMModelHandler();
            MakeDerivedGLMModelV3 args = new MakeDerivedGLMModelV3();
            args.model = new KeyV3.ModelKeyV3(glm._key);
            args.remove_offset_effects = true;
            args.remove_control_variables_effects = true;

            try {
                handler.make_derived_model(3, args);
                fail("Expected exception");
            } catch (IllegalArgumentException e) {
                // The current message says "It produces the same model as the main model."
                // This is misleading — setting both flags would give you the RESTRICTED model
                // (with both restrictions), which IS the main model's default scoring behavior.
                // The message should say something like "use both flags=false for the unrestricted model"
                String msg = e.getMessage();
                assertTrue("Error message should not confuse 'main model' with 'unrestricted model'. " +
                                "Current message: " + msg,
                        msg.contains("cannot be used together"));
            }
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

    /**
     * When both CV and RO are enabled, all 4 metric/scoring-history sets must be populated.
     */
    @Test
    public void testCvRoAllOutputFieldsExist() {
        Frame train = null;
        GLMModel glm = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0}, new String[]{"0","1"}, Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0}, new String[]{"0","1"}, Vec.newKey());
            Vec offset = Vec.makeVec(new double[]{0.1,0.2,0.2,0.2,0.1,0,0,0.2,0.3,0.5,0.3,0.4,0.8,0.4,0.4,0.5,0,0,0.5,0.1,0,0,0.1,0,0.1,0}, Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1}, new String[]{"0","1"}, Vec.newKey());
            train = new Frame(Key.<Frame>make("p2_7_train"), new String[]{"x1", "x2", "offset", "y"}, new Vec[]{cat1, cat2, offset, res});
            DKV.put(train);

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

            // When both features are enabled, all metric/scoring history sets should exist:
            // Main restricted (both effects removed)
            assertNotNull("Main training metrics should exist", glm._output._training_metrics);
            assertNotNull("Main scoring history should exist", glm._output._scoring_history);

            // Unrestricted (no effects removed)
            assertNotNull("Unrestricted training metrics should exist",
                    glm._output._training_metrics_unrestricted_model);
            assertNotNull("Unrestricted scoring history should exist",
                    glm._output._scoring_history_unrestricted_model);

            // CV-only restricted
            assertNotNull("CV-only training metrics should exist",
                    glm._output._training_metrics_restricted_model_contr_vals);
            assertNotNull("CV-only scoring history should exist",
                    glm._output._scoring_history_restricted_model_contr_vals);

            // RO-only restricted
            assertNotNull("RO-only training metrics should exist",
                    glm._output._training_metrics_restricted_model_ro);
            assertNotNull("RO-only scoring history should exist",
                    glm._output._scoring_history_restricted_model_ro);
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            Scope.exit();
        }
    }

}
