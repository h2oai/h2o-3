package hex.rulefit;

import hex.ConfusionMatrix;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.BeforeClass;
import org.junit.Test;

import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.test.util.ConfusionMatrixUtils;

public class RuleFitTest extends TestUtil {

    @BeforeClass public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testBestPracticeExample() {
        // https://github.com/h2oai/h2o-tutorials/blob/8df6b492afa172095e2595922f0b67f8d715d1e0/best-practices/explainable-models/rulefit_analysis.ipynb
        RuleFitModel rfModel = null;
        GLMModel glmModel = null;
        Frame fr = null, fr2 = null, fr3 = null;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
            Scope.track(fr);

            String responseColumnName = "survived";
            asFactor(fr, responseColumnName);
            asFactor(fr, "pclass");
            fr.remove("name").remove();
            fr.remove("ticket").remove();
            fr.remove("cabin").remove();
            fr.remove("embarked").remove();
            fr.remove("boat").remove();
            fr.remove("body").remove();
            fr.remove("home.dest").remove();
            
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._train = fr._key;
            params._response_column = responseColumnName;

            rfModel = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            fr2 = rfModel.score(fr);

            Vec predictions = fr2.vec("predict");
            Vec data = fr.vec("survived");
            
            ConfusionMatrix ruleFitConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);
            
            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel._parms._glm_params;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();

            fr3 = glmModel.score(fr);
            predictions = fr3.vec("predict");

            ConfusionMatrix glmConfusionMatrixGlm = ConfusionMatrixUtils.buildCM(data, predictions);

            System.out.println("RuleFit ACC: \n" + ruleFitConfusionMatrix.accuracy());
            System.out.println("RuleFit specificity: \n" + ruleFitConfusionMatrix.specificity());
            System.out.println("RuleFit sensitivity: \n" + ruleFitConfusionMatrix.recall());
            
            System.out.println("GLM ACC: \n" + glmConfusionMatrixGlm.accuracy());
            System.out.println("GLM specificity: \n" + glmConfusionMatrixGlm.specificity());
            System.out.println("GLM sensitivity: \n" + glmConfusionMatrixGlm.recall());
            
            
        } finally {
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
            if (fr3 != null) fr3.remove();
            if (rfModel != null) {
                if (rfModel.glmModel != null) rfModel.glmModel.remove();
                if (rfModel.treeModels != null) {
                    for (int i = 0; i < rfModel.treeModels.length; i++) {
                        rfModel.treeModels[i].remove();
                    }
                }
                rfModel.remove();
            }
            if (glmModel != null) {
                glmModel.remove();
            }
            Scope.exit();
        }
    }
    
    // todo: add examples from http://statweb.stanford.edu/~jhf/ftp/RuleFit.pdf as a test cases / experiment with setup from there / compare results
    
}
