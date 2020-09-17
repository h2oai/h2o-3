package hex.rulefit;

import hex.ConfusionMatrix;
import hex.ScoringInfo;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.Test;

import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.util.ConfusionMatrixUtils;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class RuleFitTest extends TestUtil {

    @Test
    public void testBestPracticeExample() {
        // https://github.com/h2oai/h2o-tutorials/blob/8df6b492afa172095e2595922f0b67f8d715d1e0/best-practices/explainable-models/rulefit_analysis.ipynb
        try {
            Scope.enter();
            final Frame fr = Scope.track(parse_test_file("./smalldata/gbm_test/titanic.csv"));
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

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            Vec predictions = fr2.vec("predict");
            Vec data = fr.vec("survived");
            
            ConfusionMatrix ruleFitConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);
            
            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);

            final Frame fr3 = Scope.track(glmModel.score(fr));
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

            System.out.println("RuleFit AUC:" +  rfModel.auc());
            System.out.println("GLM AUC:" + glmModel.auc());

            System.out.println("RuleFit logloss:" +  rfModel.logloss());
            System.out.println("GLM logloss:" + glmModel.logloss());
            
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBestPracticeExampleWithLinearVariables() {
        // the same as above but uses rules + linear terms
        try {
            Scope.enter();
            final Frame fr = Scope.track(parse_test_file("./smalldata/gbm_test/titanic.csv"));
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

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            Vec predictions = fr2.vec("predict");
            Vec data = fr.vec("survived");

            ConfusionMatrix ruleFitConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);

            final Frame fr3 = Scope.track(glmModel.score(fr));
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

            System.out.println("RuleFit AUC:" +  rfModel.auc());
            System.out.println("GLM AUC:" + glmModel.auc());

            System.out.println("RuleFit logloss:" +  rfModel.logloss());
            System.out.println("GLM logloss:" + glmModel.logloss());

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCarsRules() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parse_test_file("./smalldata/junit/cars.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES;

            RuleFitModel model = new RuleFit( params).trainModel().get();
            Scope.track_generic(model);

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);
            
            final Frame fr2 = Scope.track(model.score(fr));
            
            double[] expectedCoeffs = new double[] {13.50732, 8.37949, 8.33540, 7.78257, -7.57849, 7.51095, 5.65917, -5.59525, -4.04595, -3.73260, -3.66489,
                    -3.42019, -3.15852, -2.35430, -2.21467, 1.44047, -1.21574, -1.14403, -0.69578, -0.65761, -0.60060, -0.51955, 0.31480, -0.24659, -0.19722,
                    0.19242, -0.03043, -0.02091, 0.01179, 0.00583, 0.00102, 7.412075457645576E-4, -5.431219267171002E-4, 2.9864012497505935E-12, -2.5400188774894335E-13, 6.067795838432106E-14};

            String[] expectedVars = new String[] {"tree_0.T1.R", "tree_1.T26.RL", "tree_1.T49.LR", "tree_1.T29.RL", "tree_1.T14.LR", "tree_1.T19.LR", 
            "tree_1.T27.LR", "tree_1.T7.LR", "tree_1.T5.RR", "tree_2.T31.LLL", "tree_1.T1.LL", "tree_2.T37.LLL", "tree_1.T37.LL", "tree_1.T15.LL", "tree_1.T2.RL",
            "tree_0.T2.L", "tree_1.T3.RR", "tree_3.T14.LRRR", "tree_1.T27.RL", "tree_1.T21.RL", "tree_1.T34.RL", "tree_2.T39.LRL", "tree_0.T12.R",
            "tree_1.T6.LL", "tree_0.T1.L", "tree_0.T19.L", "tree_0.T2.R", "tree_1.T8.LL", "tree_0.T27.L", "tree_1.T4.RL", "tree_0.T23.R", "tree_0.T28.R",
            "tree_0.T12.L", "tree_1.T8.RL", "tree_0.T5.R", "tree_0.T15.R"};

            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {

                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }

            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);
            Scope.track(glmModel.score(fr));

            Assert.assertTrue(model.testJavaScoring(fr,fr2,1e-4));

            ScoringInfo RuleFitScoringInfo = model.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];
            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);
            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);
            System.out.println("RuleFit RMSLE:" +  model.rmsle());
            System.out.println("GLM RMSLE:" + glmModel.rmsle());

        } finally {
            Scope.exit();
        }
    }


    @Test
    public void testCarsRulesAndLinear() {
        // only linear variables are important in this case
        try {
            Scope.enter();
            final Frame fr = Scope.track(parse_test_file( "./smalldata/junit/cars.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._distribution = DistributionFamily.gaussian;

            final RuleFitModel model = new RuleFit(params).trainModel().get();
            Scope.track_generic(model);

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);
            
            
            double[] expectedCoeffs = new double[] {-3.76823, -0.12718, 0.11265, -0.08923, 0.01601};
            String[] expectedVars = new String[] {"linear.0-60 mph (s)", "linear.economy (mpg)", "linear.displacement (cc)", "linear.year", "linear.weight (lb)"};
            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {
                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }

            final Frame scoredByRF = Scope.track(model.score(fr));
            Vec RFpredictions = scoredByRF.vec("predict");

            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);
            final Frame scoredByGLM = Scope.track(glmModel.score(fr));
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
            Scope.exit();
        }
    }

    @Test
    public void testCarsLongRules() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parse_test_file("./smalldata/junit/cars.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._max_num_rules = 200;
            params._max_rule_length = 10;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;

            final RuleFitModel model = new RuleFit(params).trainModel().get();
            Scope.track_generic(model);

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);

            final Frame fr2 = Scope.track(model.score(fr));

            double[] expectedCoeffs = new double[] {-3.76824, -0.12718, 0.11265, -0.08923, 0.01601};
            String[] expectedVars = new String[] {"linear.0-60 mph (s)", "linear.economy (mpg)", "linear.displacement (cc)", "linear.year", "linear.weight (lb)"};
            for (int i = 0; i < model._output._rule_importance.getRowDim(); i++) {
                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),1e-4);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }

            Assert.assertTrue(model.testJavaScoring(fr, fr2,1e-4));
            
            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);
            Scope.track(glmModel.score(fr));
            
            ScoringInfo RuleFitScoringInfo = model.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);
            
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testBostonHousing() {
        // example from http://statweb.stanford.edu/~jhf/ftp/RuleFit.pdf but need to experiment with setup
        try {
            Scope.enter();
            final Frame fr = parse_test_file("./smalldata/gbm_test/BostonHousing.csv");
            Scope.track(fr);

            String responseColumnName = fr.lastVecName();

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._response_column = responseColumnName;

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);
            
            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);

            Scope.track(glmModel.score(fr));

            ScoringInfo RuleFitScoringInfo = rfModel.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

        } finally {
            Scope.exit();
        }
    }


    @Test
    public void testDiabetesWithWeights() {
        try {
            Scope.enter();
            final Frame fr = parse_test_file("./smalldata/diabetes/diabetes_text_train.csv");
            Scope.track(fr);
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


            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);

            Scope.track(glmModel.score(fr));

            ScoringInfo RuleFitScoringInfo = rfModel.glmModel.getScoringInfo()[0];
            ScoringInfo GLMScoringInfo = glmModel.getScoringInfo()[0];

            System.out.println("RuleFit MSE: " + RuleFitScoringInfo.scored_train._mse);
            System.out.println("GLM MSE: " + GLMScoringInfo.scored_train._mse);

            System.out.println("RuleFit r2: " + RuleFitScoringInfo.scored_train._r2);
            System.out.println("GLM r2: " + GLMScoringInfo.scored_train._r2);

        } finally {
            Scope.exit();
        }
    }
    
    // h2o-3/smalldata/diabetes


   
    
}
