package hex.glm;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
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
            // params._standardize = true;
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
}
