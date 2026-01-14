package hex.glm;

import hex.genmodel.utils.DistributionFamily;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
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
public class GLMControlVariablesTest extends TestUtil {
    
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

            // train model witch control variables enabled
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
            assert differ > threshold;

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
            
            assertNotEquals(vi, vi_unrestricted);
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
    public void testBasicDataGaussian(){
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
    public void testBasicDataBinomial(){
        /** Test against GLM in R 
         * cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
         * cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
         * res <- factor(c(1,1,0,0,0,1,0,1,0,1,1,1,1,1,1,0,0,0,1,0,1,0,1,1,1,1))
         * data <- data.frame(cat1, cat2, res)
         * glm <- glm(res ~ cat1 + cat2, data=data, family = binomial)
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
            params._distribution = DistributionFamily.bernoulli;
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

            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR", null, true);
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficients, train, "manualPredsH2o", null, true);
            Frame manualPredsControl = scoreManualWithCoefficients(coefficientsControl, train, "manualPredsControl", new int[]{0}, true);
            Frame manualPredsRControl = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR", new int[]{0}, true);

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
        return scoreManualWithCoefficients(coefficients, data, frameName, null, false);
    }

    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, int[] controlVariablesIdx){
        return scoreManualWithCoefficients(coefficients, data, frameName, controlVariablesIdx, false);
    }
    
    private Frame scoreManualWithCoefficients(Double[] coefficients, Frame data, String frameName, int[] controlVariablesIdxs, boolean binomial){
        Vec predictions = Vec.makeZero(data.numRows(), Vec.T_NUM);
        for (long i = 0; i < data.numRows(); i++) {
            double prediction = 0;
            for (int j = 0; j < data.numCols()-1; j++) {
                if(controlVariablesIdxs == null || Arrays.binarySearch(controlVariablesIdxs, j) < 0) {
                    double coefficient = coefficients[j];
                    double datapoint = data.vec(j).at(i);
                    prediction += coefficient * datapoint;
                }
            }
            prediction += coefficients[coefficients.length-1];
            if(binomial){
                prediction = 1.0 / (Math.exp(-prediction) + 1.0);
            }
            predictions.set(i, prediction);
        }
        return new Frame(Key.<Frame>make(frameName),new String[]{"predict"},new Vec[]{predictions});
    }
    
}
