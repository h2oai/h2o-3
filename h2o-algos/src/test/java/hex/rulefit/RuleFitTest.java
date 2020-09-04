package hex.rulefit;

import hex.ConfusionMatrix;
import hex.ScoringInfo;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import water.DKV;
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
            params._distribution = DistributionFamily.bernoulli;

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

            final Vec weightsVector = Vec.makeOne(fr.numRows());
            final String weightsColumnName = "weights";
            fr.add(weightsColumnName, weightsVector);
            DKV.put(fr);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._train = fr._key;
            params._response_column = responseColumnName;
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._weights_column = "weights";

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
            
            double[] expectedCoeffs = new double[] {13.54857, 8.37943,  8.33535, 7.78235, 7.62020, -7.57865, -5.59529, 5.54992, -4.04620, -3.73222, -3.66495,
                    -3.42013, -3.15808, -2.35471, -2.18179, 1.37956, -1.21565, -1.14398, -0.72780, -0.65794,  -0.60032, -0.51938, -0.24730, -0.21409, 0.16232,
                    0.15663, 0.11327, 0.09523, -0.02568, -0.02156, 0.00606, 0.00080,  -0.00059, 0.00000, 0.00000, -0.00000, -0.00000};

            String[] expectedVars = new String[] {"tree_0.T1.R", "tree_1.T26.RL", "tree_1.T49.LR", "tree_1.T29.RL", "tree_1.T19.LR", "tree_1.T14.LR",
            "tree_1.T7.LR", "tree_1.T27.LR", "tree_1.T5.RR", "tree_2.T31.LLL", "tree_1.T1.LL", "tree_2.T37.LLL", "tree_1.T37.LL", "tree_1.T15.LL", "tree_1.T2.RL",
            "tree_0.T2.L", "tree_1.T3.RR", "tree_3.T14.LRRR", "tree_1.T27.RL", "tree_1.T21.RL", "tree_1.T34.RL", "tree_2.T39.LRL", "tree_1.T6.LL", "tree_0.T1.L",
            "tree_0.T12.R", "tree_0.T19.L", "tree_0.T27.L", "tree_0.T23.R", "tree_0.T2.R", "tree_1.T8.LL", "tree_1.T4.RL", "tree_0.T28.R", "tree_0.T12.L",
            "tree_1.T38.RL", "tree_0.T15.R", "tree_0.T5.R", "tree_1.T10.LL"};

            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {
                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }

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
            params._distribution = DistributionFamily.gaussian;

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
    public void testDiabetesWithWeights() {
        RuleFitModel rfModel = null;
        GLMModel glmModel = null;
        Frame fr = null, fr2 = null, fr3 = null;
        try {
            Scope.enter();
            fr = parse_test_file("./smalldata/diabetes/diabetes_text_train.csv");

            final Vec weightsVector = createRandomBinaryWeightsVec(fr.numRows(), 10);
            final String weightsColumnName = "weights";
            fr.add(weightsColumnName, weightsVector);
            DKV.put(fr);
            
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._response_column = "diabetesMed";
            params._weights_column = "weights";


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
