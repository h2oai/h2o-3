package hex.rulefit;

import hex.ConfusionMatrix;
import hex.ScoringInfo;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.test.util.ConfusionMatrixUtils;
import static org.junit.Assert.*;

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
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES;

            rfModel = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            fr2 = rfModel.score(fr);

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            Vec predictions = fr2.vec("predict");
            Vec data = fr.vec("survived");
            
            ConfusionMatrix ruleFitConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);
            
            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();

            fr3 = glmModel.score(fr);
            predictions = fr3.vec("predict");

            ConfusionMatrix glmConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);
            
            System.out.println("RuleFit ACC: \n" + ruleFitConfusionMatrix.accuracy());
            System.out.println("RuleFit specificity: \n" + ruleFitConfusionMatrix.specificity());
            System.out.println("RuleFit sensitivity: \n" + ruleFitConfusionMatrix.recall());

            assertEquals(ruleFitConfusionMatrix.accuracy(),0.7868601986249045,1e-4);
            assertEquals(ruleFitConfusionMatrix.specificity(),0.8207663782447466,1e-4);
            assertEquals(ruleFitConfusionMatrix.recall(),0.732,1e-4);
            
            System.out.println("pure GLM ACC: \n" + glmConfusionMatrix.accuracy());
            System.out.println("pure GLM specificity: \n" + glmConfusionMatrix.specificity());
            System.out.println("pure GLM sensitivity: \n" + glmConfusionMatrix.recall());

            assertEquals(glmConfusionMatrix.accuracy(),0.7815126050420168,1e-4);
            assertEquals(glmConfusionMatrix.specificity(),0.8145859085290482,1e-4);
            assertEquals(glmConfusionMatrix.recall(),0.728,1e-4);

            ScoringInfo RuleFitScoringInfo = rfModel.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);
            
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
            if (glmModel != null) glmModel.remove();
            Scope.exit();
        }
    }

    @Test
    public void testBestPracticeExampleWithLinearVariables() {
        // the same as above but uses rules + linear terms
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
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;

            rfModel = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            fr2 = rfModel.score(fr);

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            Vec predictions = fr2.vec("predict");
            Vec data = fr.vec("survived");

            ConfusionMatrix ruleFitConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();

            fr3 = glmModel.score(fr);
            predictions = fr3.vec("predict");

            ConfusionMatrix glmConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);

            System.out.println("RuleFit ACC: \n" + ruleFitConfusionMatrix.accuracy());
            System.out.println("RuleFit specificity: \n" + ruleFitConfusionMatrix.specificity());
            System.out.println("RuleFit sensitivity: \n" + ruleFitConfusionMatrix.recall());

            assertEquals(ruleFitConfusionMatrix.accuracy(),0.7685255920550038,1e-4);
            assertEquals(ruleFitConfusionMatrix.specificity(),0.761433868974042,1e-4);
            assertEquals(ruleFitConfusionMatrix.recall(),0.78,1e-4);

            System.out.println("pure GLM ACC: \n" + glmConfusionMatrix.accuracy());
            System.out.println("pure GLM specificity: \n" + glmConfusionMatrix.specificity());
            System.out.println("pure GLM sensitivity: \n" + glmConfusionMatrix.recall());

            assertEquals(glmConfusionMatrix.accuracy(),0.7815126050420168,1e-4);
            assertEquals(glmConfusionMatrix.specificity(),0.8145859085290482,1e-4);
            assertEquals(glmConfusionMatrix.recall(),0.728,1e-4);

            ScoringInfo RuleFitScoringInfo = rfModel.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

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
            if (glmModel != null) glmModel.remove();
            Scope.exit();
        }
    }

    @Test
    public void testCarsRules() {
        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null, fr2 = null, fr3 = null;
        RuleFitModel model = null;
        GLMModel glmModel = null;
        Frame score = null;
        try {
            fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = parsed;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES;

            model = new RuleFit( params).trainModel().get();

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);
            
            fr2 = model.score(fr);

// will be commented until how to make getOptimalLambda() will be resolved            
//            double[] expectedCoeffs = new double[] {13.004402060936888, 9.113603209948424, 7.68771232020914, 6.824080671930569, -5.628368368360579,
//                    5.274377189184506, 5.046628698255106, 5.015034906113102, -4.4200157573980885, 3.1860670448265282, -2.979522789418401, 2.9039591116960635,
//                    -2.42070125353312, 2.3911818561425418, 2.066540600979733, -1.878938007410801, -1.5163118005971468, -1.3062651329031085, -1.2804225662948483,
//                    1.0996496571874164, 0.9528000637892864, -0.8982300672540523, -0.7915602005810082, -0.6984875168240434, -0.6867872999692551, -0.5111780981012599,
//                    -0.19300513004373684, -0.12219542917921311, 0.1177744584884146, -0.03326857456036694, 1.7909238277924778E-11};
//            
//            String[] expectedVars = new String[] {"tree_2.T26.RLL", "tree_2.T10.LRL", "tree_3.T27.RLRR", "tree_4.T27.RLRRR", "tree_1.T38.LR", "tree_3.T25.RLLR",
//            "tree_2.T21.LRL", "tree_3.T23.LRRL", "tree_0.T6.R", "tree_3.T39.LRRL", "tree_2.T25.LRR", "tree_2.T1.LRL", "tree_2.T43.LLR", "tree_5.T13.LLRRRR",
//            "tree_5.T30.LRLRLR", "tree_4.T11.RRRLR", "tree_1.T37.LR", "tree_3.T3.LRRL", "tree_2.T36.LLL", "tree_1.T11.LR", "tree_3.T1.LRLR", "tree_1.T36.LL",
//            "tree_1.T41.LL", "tree_3.T9.LLRR", "tree_2.T17.RLR", "tree_1.T31.LL", "tree_1.T17.RL", "tree_2.T49.LLR", "tree_4.T30.LRLRL", "tree_1.T42.LR", "tree_2.T25.RLL"};
//            
//            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {
//                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
//                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
//            }

            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();
            fr3 = glmModel.score(fr);

            Assert.assertTrue(model.testJavaScoring(fr,fr2,1e-4));

            ScoringInfo RuleFitScoringInfo = model.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];
            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);
            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

        } finally {
            if (fr != null) fr.delete();
            if (fr2 != null) fr2.delete();
            if (fr3 != null) fr3.delete();
            if (score != null) score.delete();
            if (model != null) {
                if (model.glmModel != null) model.glmModel.remove();
                if (model.treeModels != null) {
                    for (int i = 0; i < model.treeModels.length; i++) {
                        model.treeModels[i].remove();
                    }
                }
                model.remove();
            }
            if (glmModel != null) glmModel.delete();
            Scope.exit();
        }
    }


    @Test
    public void testCarsRulesAndLinear() {
        // only linear variables are important in this case
        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null, scoredByRF = null, scoredByGLM = null;
        RuleFitModel model = null;
        GLMModel glmModel = null;
        Frame score = null;
        try {
            fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = parsed;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;

            model = new RuleFit( params).trainModel().get();

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);
            
            
            double[] expectedCoeffs = new double[] {-3.76823, -0.12718, 0.11265, -0.08923, 0.01601};
            String[] expectedVars = new String[] {"linear.0-60 mph (s)", "linear.economy (mpg)", "linear.displacement (cc)", "linear.year", "linear.weight (lb)"};
            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {
                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }

            scoredByRF = model.score(fr);
            Vec RFpredictions = scoredByRF.vec("predict");

            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();
            scoredByGLM = glmModel.score(fr);
            Vec GLMpredictions = scoredByGLM.vec("predict");
            
            Assert.assertTrue(model.testJavaScoring(fr,scoredByRF,1e-4)); 

            // should be equal because only linear terms were important during RF training
            assertVecEquals(GLMpredictions, RFpredictions, 1e-4);

            ScoringInfo RuleFitScoringInfo = model.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];
            
            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

        } finally {
            if (fr != null) fr.delete();
            if (scoredByRF != null) scoredByRF.delete();
            if (scoredByGLM != null) scoredByGLM.delete();
            if (score != null) score.delete();
            if (model != null) {
                if (model.glmModel != null) model.glmModel.remove();
                if (model.treeModels != null) {
                    for (int i = 0; i < model.treeModels.length; i++) {
                        model.treeModels[i].remove();
                    }
                }
                model.remove();
            }
            if (glmModel != null) glmModel.delete();
            Scope.exit();
        }
    }

    @Test
    public void testCarsLongRules() {
        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null, fr2 = null, fr3 = null;
        RuleFitModel model = null;
        GLMModel glmModel = null;
        Frame score = null;
        try {
            fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = parsed;
            params._max_num_rules = 200;
            params._max_rule_length = 500;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;

            model = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);

            fr2 = model.score(fr);

            double[] expectedCoeffs = new double[] {-3.76824, -0.12718, 0.11265, -0.08923, 0.01601};
            String[] expectedVars = new String[] {"linear.0-60 mph (s)", "linear.economy (mpg)", "linear.displacement (cc)", "linear.year", "linear.weight (lb)"};
            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {
                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }

            Assert.assertTrue(model.testJavaScoring(fr, fr2,1e-4));
            
            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();
            fr3 = glmModel.score(fr);
            
            ScoringInfo RuleFitScoringInfo = model.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);
            
        } finally {
            if (fr != null) fr.delete();
            if (fr2 != null) fr2.delete();
            if (fr3 != null) fr3.delete();
            if (score != null) score.delete();
            if (model != null) {
                if (model.glmModel != null) model.glmModel.remove();
                if (model.treeModels != null) {
                    for (int i = 0; i < model.treeModels.length; i++) {
                        model.treeModels[i].remove();
                    }
                }
                model.remove();
            }
            if (glmModel != null) glmModel.delete();
            Scope.exit();
        }
    }
    
    @Test
    public void testBostonHousing() {
        // example from http://statweb.stanford.edu/~jhf/ftp/RuleFit.pdf but need to experiment with setup
        RuleFitModel rfModel = null;
        GLMModel glmModel = null;
        Frame fr = null, fr2 = null, fr3 = null;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/gbm_test/BostonHousing.csv");
            Scope.track(fr);

            String responseColumnName = fr.lastVecName();

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._response_column = responseColumnName;
            params._max_num_rules = 2000;

            rfModel = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            fr2 = rfModel.score(fr);

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();

            fr3 = glmModel.score(fr);

            ScoringInfo RuleFitScoringInfo = rfModel.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

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
            if (glmModel != null) glmModel.remove();
            Scope.exit();
        }
    }


    @Test
    public void testDiabetes() {
        RuleFitModel rfModel = null;
        GLMModel glmModel = null;
        Frame fr = null, fr2 = null, fr3 = null;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/diabetes/diabetes_text_train.csv");
            Scope.track(fr);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._response_column = "diabetesMed";
            params._max_num_rules = 2000;


            rfModel = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            fr2 = rfModel.score(fr);

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmModel = new GLM(glmParameters).trainModel().get();

            fr3 = glmModel.score(fr);

            ScoringInfo RuleFitScoringInfo = rfModel.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

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
            if (glmModel != null) glmModel.remove();
            Scope.exit();
        }
    }
    
    // h2o-3/smalldata/diabetes


   
    
}
