package hex.glm;

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
import water.util.ArrayUtils;
import water.util.DistributedException;
import water.util.TwoDimTable;

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
            
            preds = glm.score(test);
            Scope.track_generic(preds);

            // train model with control variables disabled
            params._control_variables = null;

            glm2 = new GLM(params).trainModel().get();
            Scope.track_generic(glm2);

            preds2 = glm2.score(test);
            Scope.track_generic(preds2);

            // check reuslt training metrics are the same
            double delta = 10e-10;
            assertEquals(glm.auc(), glm2.auc(), delta);
            assertEquals(glm.mse(), glm2.mse(), delta);
            assertEquals(glm.logloss(), glm2.logloss(), delta);
            
            double tMse = glm._output._training_metrics._MSE;
            double tMse2 = glm2._output._training_metrics._MSE;
            System.out.println(tMse+" "+tMse2);
            assertEquals(tMse, tMse2, delta);

            // check result training metrics are not the same with control val training metrics
            assertNotEquals(glm2._output._training_metrics.auc_obj()._auc, glm._output._control_val_training_metrics.auc_obj()._auc);
            assertNotEquals(glm2._output._training_metrics.mse(), glm._output._control_val_training_metrics.mse());
            assertNotEquals(glm2._output._training_metrics.rmse(), glm._output._control_val_training_metrics.rmse());
            
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

            TwoDimTable glmSH = glm._output._scoring_history;
            TwoDimTable glm2SH = glm2._output._scoring_history;
            TwoDimTable glmSHCV = glm._output._control_val_scoring_history;
            TwoDimTable glm2SHCV = glm2._output._control_val_scoring_history;
            
            // check scoring history is the same (instead of timestamp and duration column)
            assertTwoDimTableEquals(glmSH, glm2SH, new int[]{0,1});
            
            // check control val scoring history is not null when control vals is enabled
            assertNotNull(glmSHCV);

            // check control val scoring history is null when control vals is disabled
            assertNull(glm2SHCV);
            
            //check variable importance
            TwoDimTable vi = glm._output._variable_importances;
            String[] viRowHeader = vi.getRowHeaders();
            
            int numControlVariables = 0;
            String suffix = "_control_variable";
            for (String name : viRowHeader) {
                if(name.contains(suffix)){
                    numControlVariables++;
                    if (name.contains(".")) {
                        String catName = name.split("\\.")[0];
                        assert ArrayUtils.find(control_variables, catName) > -1 : 
                                "Variable "+catName+" should not be marked as _control_variable.";
                    } else {
                        String numericName = name.substring(0, name.length() - suffix.length());
                        assert ArrayUtils.find(control_variables, numericName) > -1 :
                                "Variable "+numericName+" should not be marked as _control_variable.";
                    }
                }
            }
            assert numControlVariables >= control_variables.length;
            
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
}
