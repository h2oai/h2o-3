package hex.rulefit;

import hex.ConfusionMatrix;
import org.junit.BeforeClass;
import org.junit.Test;

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
        Frame fr = null, fr2 = null;
        try {
            fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

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
            
            ConfusionMatrix confusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);
            
            System.out.println("ACC: \n" + confusionMatrix.accuracy());
            System.out.println("specificity: \n" + confusionMatrix.specificity());
            System.out.println("sensitivity: \n" + confusionMatrix.recall());
            
        } finally {
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
            if (rfModel.glmModel != null) rfModel.glmModel.remove();
            for (int i = 0; i < rfModel.treeModels.length; i++) {
                rfModel.treeModels[i].remove();
            }
            if (rfModel != null) rfModel.remove();
        }
    }
    
    // todo: add examples from http://statweb.stanford.edu/~jhf/ftp/RuleFit.pdf as a test cases / experiment with setup from there / compare results
    
}
