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

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMControlVariablesTest extends TestUtil {

    @Test
    public void testTrainScoreModel() {
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
            
            
            glm.adaptTestForTrain(test,true,false);
            test.remove(test.numCols()-1); // remove response
            test.add(preds.names(),preds.vecs());

            DKV.put(test);
            Scope.track_generic(test);
            
            new GLMTest.TestScore0(glm,false,false).doAll(test);
            
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
