package hex.glm;

import hex.MultinomialAucType;
import hex.genmodel.utils.ArrayUtils;
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
    
    public double toProb(double pred){
        return 1.0 / (Math.exp(-pred) + 1.0);
    }

    @Test
    public void testBasicDataMultinomial(){
        /* Test agains t multinomial GLM from glmnet library in R 
         
        cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
        cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
        res <- factor(c(1,2,0,0,2,1,0,1,0,1,2,1,1,2,1,0,2,0,2,0,2,0,1,2,1,1))
        data <- data.frame(cat1, cat2, res)

        library(glmnet)
        X <- model.matrix(~.,data[c("cat1", "cat2")])[,-1]

        fit <- glmnet(X, data$res, family = "multinomial", type.multinomial = "grouped", lambda = 0, alpha=0.5)
        coef(fit, s=0)
 
        preds <- predict(fit, X)

        $`0`
        3 x 1 sparse Matrix of class "dgCMatrix"
                             1
        (Intercept) -0.1505452
        cat11       -0.0935533
        cat21        0.2917359

        $`1`
        3 x 1 sparse Matrix of class "dgCMatrix"
                              1
        (Intercept)  0.03661836
        cat11        0.26101689
        cat21       -0.06250493

        $`2`
        3 x 1 sparse Matrix of class "dgCMatrix"
                             1
        (Intercept)  0.1139269
        cat11       -0.1674636
        cat21       -0.2292310
         */

        Frame train = null;
        GLMModel glm = null;
        GLMModel glmControl = null;
        Frame preds = null;
        Frame predsControl = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec num1 = Vec.makeVec(new double[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},Vec.newKey());
            Vec num2 = Vec.makeVec(new double[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,2,0,0,2,1,0,1,0,1,2,1,1,2,1,0,2,0,2,0,2,0,1,2,1,1}, new String[]{"0","1", "2"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"x1", "x2", "y"},new Vec[]{num1, num2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._standardize = true;
            params._lambda = new double[]{0};
            params._objective_epsilon = 1e-6;
            params._beta_epsilon = 1e-5;
            params._intercept = true;
            params._response_column = "y";
            params._lambda_search = false;
            params._distribution = DistributionFamily.multinomial;
            params._auc_type = MultinomialAucType.MACRO_OVR;
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);

            double[][] coefficientsH2o = glm._output.getNormBetaMultinomial();
            double[][] coefficientsH2oGlobalBeta = glm._output._global_beta_multinomial;

            params._control_variables = new String[]{"x1"};
            glmControl = new GLM(params).trainModel().get();
            predsControl = glmControl.score(train);
            

            double[] coefR0 = new double[]{-0.0935533, 0.2917359, -0.1505452};
            double[] coefR1 = new double[]{0.26101689, -0.06250493, 0.03661836};
            double[] coefR2 = new double[]{-0.1674636, -0.2292310, 0.1139269};

            double[][] coefficientsR = new double[][]{coefR0, coefR1, coefR2};

            double[] coefH2O0 = coefficientsH2o[0];
            double[] coefH2O1 = coefficientsH2o[1];
            double[] coefH2O2 = coefficientsH2o[2];

            System.out.println("Coefficients norm beta");
            System.out.println(Arrays.toString(coefH2O0) + Arrays.toString(coefH2O1) + Arrays.toString(coefH2O2));

            double[] coefH2O0Global = coefficientsH2oGlobalBeta[0];
            double[] coefH2O1Global = coefficientsH2oGlobalBeta[1];
            double[] coefH2O2Global = coefficientsH2oGlobalBeta[2];

            System.out.println("Coefficients global beta");
            System.out.println(Arrays.toString(coefH2O0Global) + Arrays.toString(coefH2O1Global) + Arrays.toString(coefH2O2Global));

            double[][] normCoefficientsH2O = normalizeValues(coefficientsH2oGlobalBeta);
            double[] coefH2O0Norm = normCoefficientsH2O[0];
            double[] coefH2O1Norm = normCoefficientsH2O[1];
            double[] coefH2O2Norm = normCoefficientsH2O[2];

            System.out.println("Coefficients h2o global beta normalized");
            System.out.println(Arrays.toString(coefH2O0Norm) + Arrays.toString(coefH2O1Norm) + Arrays.toString(coefH2O2Norm));
            
            System.out.println("Coefficients R");
            System.out.println(Arrays.toString(coefR0) + Arrays.toString(coefR1) + Arrays.toString(coefR2));

            System.out.println("Coefficients control h2o global beta");
            double[][] coefficientsControlH2o = glmControl._output._global_beta_multinomial;
            double[] coefH2O0Control = coefficientsControlH2o[0];
            double[] coefH2O1Control = coefficientsControlH2o[1];
            double[] coefH2O2Control = coefficientsControlH2o[2];
            System.out.println(Arrays.toString(coefH2O0Control) + Arrays.toString(coefH2O1Control) + Arrays.toString(coefH2O2Control));

            System.out.println("Coefficients control h2o normalized global beta");
            double[][] coefficientsControlH2oNorm = normalizeValues(glmControl._output._global_beta_multinomial);
            double[] coefH2O0ControlNorm = coefficientsControlH2o[0];
            double[] coefH2O1ControlNorm = coefficientsControlH2o[1];
            double[] coefH2O2ControlNorm = coefficientsControlH2o[2];
            System.out.println(Arrays.toString(coefH2O0ControlNorm) + Arrays.toString(coefH2O1ControlNorm) + Arrays.toString(coefH2O2ControlNorm));


            Vec predsRVec = Vec.makeVec(new double[]{2, 2, 2, 3, 3, 2, 2, 1, 1, 2, 1, 2, 3, 2, 2, 2, 3, 3, 1, 1, 2, 2, 2, 2, 1, 3},Vec.newKey());
            Vec preds0 = Vec.makeVec(new double[]{0.04763741, -0.24409854, 0.04763741, -0.15054523, -0.15054523, -0.24409854, -0.24409854, 0.14119072, 0.14119072, -0.24409854, 0.14119072, -0.24409854, -0.15054523, 0.04763741, -0.24409854, 0.04763741, -0.15054523, -0.15054523, 0.14119072, 0.14119072, -0.24409854, -0.24409854, 0.04763741, -0.24409854, 0.14119072, -0.15054523},Vec.newKey());
            Vec preds1 = Vec.makeVec(new double[]{0.23513032, 0.29763525, 0.23513032, 0.03661836, 0.03661836, 0.29763525, 0.29763525, -0.02588657, -0.02588657, 0.29763525, -0.02588657, 0.29763525, 0.03661836, 0.23513032, 0.29763525, 0.23513032, 0.03661836, 0.03661836, -0.02588657, -0.02588657, 0.29763525, 0.29763525, 0.23513032, 0.29763525, -0.02588657, 0.03661836},Vec.newKey());
            Vec preds2 = Vec.makeVec(new double[]{-0.28276773, -0.05353671, -0.28276773, 0.11392687, 0.11392687, -0.05353671, -0.05353671, -0.11530414, -0.11530414, -0.05353671, -0.11530414, -0.05353671, 0.11392687, -0.28276773, -0.05353671, -0.28276773, 0.11392687, 0.11392687, -0.11530414, -0.11530414, -0.05353671, -0.05353671, -0.28276773, -0.05353671, -0.11530414, 0.11392687},Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict", "0", "1", "2"},new Vec[]{predsRVec, preds0, preds1, preds2});
            
            System.out.println("Score manual R");
            Frame manualPredsR = scoreManualWithMultinomialCoefficients(coefficientsR, train, "manualPredsR", true);
            System.out.println("Score manual H2o global beta with sumExp");
            Frame manualPredsH2o = scoreManualWithMultinomialCoefficients(coefficientsH2oGlobalBeta, train, "manualPredsH2o", false);
            System.out.println("Score manual normalized H2o global beta without sumExp");
            Frame manualPredsH2oNormalized = scoreManualWithMultinomialCoefficients(normCoefficientsH2O, train, "manualPredsH2oNormalized", true);
            System.out.println("Score manual h2o control global beta with sumExp");
            Frame manualPredsControl = scoreManualWithMultinomialCoefficients(coefficientsControlH2o, train, "manualPredsControl", new int[]{0}, false);
            System.out.println("Score manual h2o control normalized without sumExp");
            Frame manualPredsControlNorm = scoreManualWithMultinomialCoefficients(coefficientsControlH2oNorm, train, "manualPredsControlNorm", new int[]{0}, true);
            System.out.println("Score manual R control");
            Frame manualPredsRControl = scoreManualWithMultinomialCoefficients(coefficientsR, train, "manualPredsR", new int[]{0}, true);

            double tol = 1e-2;
            long numRows = preds.numRows();
            //numRows = 5;
            for (long i = 0; i < numRows; i++) {
                long h2oPredict = preds.vec(0).at8(i);
                double h2o0 = preds.vec(1).at(i);
                double h2o1 = preds.vec(2).at(i);
                double h2o2 = preds.vec(3).at(i);

                double manualH2oPredict = manualPredsH2o.vec(0).at8(i);
                double manualH2o0 = manualPredsH2o.vec(1).at(i);
                double manualH2o1 = manualPredsH2o.vec(2).at(i);
                double manualH2o2 = manualPredsH2o.vec(3).at(i);

                double manualH2oPredictNorm = manualPredsH2oNormalized.vec(0).at8(i);
                double manualH2oNorm0 = manualPredsH2oNormalized.vec(1).at(i);
                double manualH2oNorm1 = manualPredsH2oNormalized.vec(2).at(i);
                double manualH2oNorm2 = manualPredsH2oNormalized.vec(3).at(i);

                long rPredict = predsR.vec(0).at8(i) - 1;
                double r0 = predsR.vec(1).at(i);
                double r1 = predsR.vec(2).at(i);
                double r2 = predsR.vec(3).at(i);

                double manualRPredict = manualPredsR.vec(0).at8(i);
                double manualR0 = manualPredsR.vec(1).at(i);
                double manualR1 = manualPredsR.vec(2).at(i);
                double manualR2 = manualPredsR.vec(3).at(i);


                long h2oControlPredict = predsControl.vec(0).at8(i);
                double h2oControl0 = predsControl.vec(1).at(i);
                double h2oControl1 = predsControl.vec(2).at(i);
                double h2oControl2 = predsControl.vec(3).at(i);

                double manualH2oControlPredict = manualPredsControl.vec(0).at(i);
                double manualH2oControl0 = manualPredsControl.vec(1).at(i);
                double manualH2oControl1 = manualPredsControl.vec(2).at(i);
                double manualH2oControl2 = manualPredsControl.vec(3).at(i);

                double manualH2oControlPredictNorm = manualPredsControlNorm.vec(0).at(i);
                double manualH2oControl0Norm = manualPredsControlNorm.vec(1).at(i);
                double manualH2oControl1Norm = manualPredsControlNorm.vec(2).at(i);
                double manualH2oControl2Norm = manualPredsControlNorm.vec(3).at(i);

                double manualRControlPredict = manualPredsRControl.vec(0).at(i);
                double manualRControl0 = manualPredsRControl.vec(1).at(i);
                double manualRControl1 = manualPredsRControl.vec(2).at(i);
                double manualRControl2 = manualPredsRControl.vec(3).at(i);


                System.out.println(i+"\nh2o predict: \n"+h2oPredict+" 0: "+h2o0+" 1: "+h2o1+" 2: "+h2o2+
                        "\nh2o manual predict: \n"+manualH2oPredict+" 0: "+manualH2o0+" 1: "+manualH2o1+" 2: "+manualH2o2+
                        "\nh2o manual normalized predict: \n"+manualH2oPredictNorm+" 0: "+manualH2oNorm0+" 1: "+manualH2oNorm1+" 2: "+manualH2oNorm2+
                        "\nR predict: \n"+rPredict+" 0: "+r0+" 1: "+r1+" 2: "+r2+
                        "\nR manual predict: \n"+manualRPredict+" 0: "+manualR0+" 1: "+manualR1+" 2: "+manualR2+
                        "\nh2o control predict: \n"+h2oControlPredict+" 0: "+h2oControl0+" 1: "+h2oControl1+" 2: "+h2oControl2+
                        "\nh2o manual control predict: \n"+manualH2oControlPredict+" 0: "+manualH2oControl0+" 1: "+manualH2oControl1+" 2: "+manualH2oControl2+
                        "\nh2o manual control normalized predict: \n"+manualH2oControlPredictNorm+" 0: "+manualH2oControl0Norm+" 1: "+manualH2oControl1Norm+" 2: "+manualH2oControl2Norm+
                        "\nR manual control predict: \n"+manualRControlPredict+" 0: "+manualRControl0+" 1: "+manualRControl1+" 2: "+manualRControl2+"\n");

                // glm score calculation check
                Assert.assertEquals(h2o0, manualH2o0, tol); // check manual scoring is identical with h2o scoring
                // check manual scoring with normalized coefficients is identical with R scoring
                Assert.assertEquals(manualH2oPredictNorm, manualRPredict, tol); 
                Assert.assertEquals(manualH2oNorm0, manualR0, tol); 
                Assert.assertEquals(manualH2oNorm1, manualR1, tol);
                Assert.assertEquals(manualH2oNorm2, manualR2, tol);
                // check R scoring is identical with manual R scoring
                Assert.assertEquals(r0, manualR0, tol);

                // control values calculation check
                Assert.assertEquals(h2oControl0, manualH2oControl0, tol);
                Assert.assertEquals(manualH2oControl0Norm, manualRControl0, tol);
                Assert.assertEquals(manualH2oControl1Norm, manualRControl1, tol);
                Assert.assertEquals(manualH2oControl2Norm, manualRControl2, tol);
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

    private double[][] normalizeValues(double[][] vals){
        double[][] normVals = new double[vals.length][vals[0].length];
        for (int i = 0; i < vals.length; i++) {
            double mean = 0;
            for (int j = 0; j < vals[i].length; j++) {
                mean += vals[j][i];
            }
            mean = mean/vals.length;
            for (int j = 0; j < vals[i].length; j++) {
                normVals[j][i] = vals[j][i] - mean;
            }
        } 
        return normVals;
    }

    private Frame scoreManualWithMultinomialCoefficients(double[][] coefficients, Frame data, String frameName, boolean isR){
        return scoreManualWithMultinomialCoefficients(coefficients, data, frameName, null, isR);
    }

    private Frame scoreManualWithMultinomialCoefficients(double[][] coefficients, Frame data, String frameName, int[] controlVariablesIdxs, boolean isR){
        Vec predictions = Vec.makeZero(data.numRows(), Vec.T_NUM);
        Vec[] classPredictions = new Vec[coefficients.length];
        for (int c = 0; c < coefficients.length; c++){
            classPredictions[c] = Vec.makeZero(data.numRows(), Vec.T_NUM);
        }
        for (long i = 0; i < data.numRows(); i++) {
            double sumExp = 0;
            double maxRow = 0;
            double[] preds = new double[coefficients.length];
            double[] eta = new double[coefficients.length];
            for (int c = 0; c < coefficients.length; ++c) {
                for (int j = 0; j < data.numCols()-1; j++) {
                    if (controlVariablesIdxs == null || Arrays.binarySearch(controlVariablesIdxs, j) < 0) {
                        eta[c] += data.vec(j).at(i) * coefficients[c][j];
                    }
                }
                eta[c] += coefficients[c][data.numCols()-1]; // intercept
                if (eta[c] > maxRow) {
                    maxRow = eta[c];
                }
            }
            
            if (!isR) {
                for (int c = 0; c < coefficients.length; ++c) {
                    sumExp += eta[c] = Math.exp(eta[c] - maxRow); // intercept
                }
                sumExp = 1.0 / sumExp;
            } else{
                sumExp = 1;
            }
            for (int c = 0; c < coefficients.length; ++c) {
                preds[c] = eta[c] * sumExp;
            }
            predictions.set(i, ArrayUtils.maxIndex(eta));
            for (int c = 0; c < coefficients.length; c++){
                classPredictions[c].set(i, preds[c]);
            }
        }
        return new Frame(Key.<Frame>make(frameName),new String[]{"predict", "0", "1", "2"},new Vec[]{predictions, classPredictions[0], classPredictions[1], classPredictions[2]});
    }


}
