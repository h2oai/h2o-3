package hex.rulefit;

import hex.ConfusionMatrix;
import hex.ScoringInfo;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.util.ConfusionMatrixUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class RuleFitTest extends TestUtil {

    @Test
    public void testBestPracticeExampleWithoutScope() {
        // https://github.com/h2oai/h2o-tutorials/blob/8df6b492afa172095e2595922f0b67f8d715d1e0/best-practices/explainable-models/rulefit_analysis.ipynb
        RuleFitModel rfModel = null;
        GLMModel glmModel = null;
        Frame fr = null, fr2 = null, fr3 = null;
        try {
            fr = parseTestFile("./smalldata/gbm_test/titanic.csv");

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
            DKV.put(fr);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._train = fr._key;
            params._response_column = responseColumnName;
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._distribution = DistributionFamily.bernoulli;
            params._min_rule_length = 1;
            params._max_rule_length = 10;

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
            glmParameters._response_column = rfModel._parms._response_column;
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
                rfModel.remove();
            }
            if (glmModel != null) glmModel.remove();
        }
    }

    @Test
    public void testBestPracticeExampleWithLinearVariablesWithoutScope() {
        // the same as above but uses rules + linear terms
        RuleFitModel rfModel = null;
        GLMModel glmModel = null;
        Frame fr = null, fr2 = null, fr3 = null, valid = null;
        try {
            fr = parseTestFile("./smalldata/gbm_test/titanic.csv");
            valid = parseTestFile("./smalldata/gbm_test/titanic.csv");

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

            final Vec weightsVector = fr.anyVec().makeOnes(1)[0];
            final String weightsColumnName = "weights";
            fr.add(weightsColumnName, weightsVector);
            
            DKV.put(fr);
            DKV.put(valid);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._train = fr._key;
            params._valid = valid._key;
            params._response_column = responseColumnName;
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._weights_column = "weights";
            params._min_rule_length = 1;
            params._max_rule_length = 7;

            rfModel = new RuleFit(params).trainModel().get();

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            fr2 = rfModel.score(fr);

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            Vec predictions = fr2.vec("predict");
            Vec data = fr.vec("survived");

            ConfusionMatrix ruleFitConfusionMatrix = ConfusionMatrixUtils.buildCM(data, predictions);

            assertEquals(rfModel._output._validation_metrics.rmse(), rfModel._output._training_metrics.rmse(), 1e-4);
            assertEquals(rfModel._output._validation_metrics.mse(), rfModel._output._training_metrics.mse(), 1e-4);
            assertEquals(rfModel._output._validation_metrics.auc_obj()._auc, rfModel._output._training_metrics.auc_obj()._auc, 1e-4);
            assertEquals(rfModel._output._validation_metrics.hr(), rfModel._output._training_metrics.hr());
            
            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmParameters._valid = valid._key;
            glmParameters._weights_column = rfModel._parms._weights_column;
            glmParameters._response_column = rfModel._parms._response_column;
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
            if (valid != null) valid.remove();
            if (rfModel != null) {
                rfModel.remove();
            }
            if (glmModel != null) glmModel.remove();
        }
    }
    
    @Test
    public void testBestPracticeExample() {
        // https://github.com/h2oai/h2o-tutorials/blob/8df6b492afa172095e2595922f0b67f8d715d1e0/best-practices/explainable-models/rulefit_analysis.ipynb
        try {
            Scope.enter();
            final Frame fr = Scope.track(parseTestFile("./smalldata/gbm_test/titanic.csv"));

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
            DKV.put(fr);
            
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._train = fr._key;
            params._response_column = responseColumnName;
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._distribution = DistributionFamily.bernoulli;
            params._min_rule_length = 1;
            params._max_rule_length = 10;

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
            glmParameters._response_column = rfModel._parms._response_column;
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
            final Frame fr = Scope.track(parseTestFile("./smalldata/gbm_test/titanic.csv"));
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
            params._min_rule_length = 1;
            params._max_rule_length = 10;

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
            glmParameters._weights_column = rfModel._parms._weights_column;
            glmParameters._response_column = rfModel._parms._response_column;
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
            final Frame fr = Scope.track(parseTestFile("./smalldata/junit/cars.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._min_rule_length = 1;
            params._remove_duplicates = false;
            params._max_categorical_levels = 1000;

            RuleFitModel model = new RuleFit( params).trainModel().get();
            Scope.track_generic(model);

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model.getRuleImportanceTable());
            
            final Frame fr2 = Scope.track(model.score(fr));
            
            double[] expectedCoeffs = new double[] {13.50732, 8.37949, 8.33540, 7.78257, -7.57849, 7.51095, 5.65917, -5.59525, -4.04595, -3.73260, -3.66489,
                    -3.42019, -3.15852, -2.35430, -2.21467, 1.44047, -1.21574, -1.14403, -0.69578, -0.65761, -0.60060, -0.51955, 0.31480, -0.24659, -0.19722,
                    0.19242, -0.03043, -0.02091, 0.01179, 0.00583, 0.00102, 7.412075457645576E-4, -5.431219267171002E-4};

            String[] expectedVars = new String[] {"M0T0N4", "M1T25N9", "M1T48N8", "M1T28N9", "M1T13N8", "M1T18N8", 
            "M1T26N8", "M1T6N8", "M1T4N10", "M2T30N15", "M1T0N7", "M2T36N15", "M1T36N7", "M1T14N7", "M1T1N9",
            "M0T1N3", "M1T2N10", "M3T13N36", "M1T26N9", "M1T20N9", "M1T33N9", "M2T38N17", "M0T11N4",
            "M1T5N7", "M0T0N3", "M0T18N3", "M0T1N4", "M1T7N7", "M0T26N3", "M1T3N9", "M0T22N4", "M0T27N4",
            "M0T11N3"};
            //Set<String> zeroVars = new HashSet<>(Arrays.asList("M1T37N9", "M0T14N4", "M1T38N9", "M1T7N9", "M0T4N3", "M1T43N9"));
            final double coefDelta = 1e-4;
            for (int i = 0; i < expectedVars.length; i++) {
                assertEquals(expectedCoeffs[i], (double) model._output._rule_importance.get(i,1),coefDelta);
                assertEquals(expectedVars[i], model._output._rule_importance.get(i,0));
            }
            // zero coeff names are not deterministic:
            // zero-coef vars can be in any order (it doesn't make sense to compare order if it is bellow the delta precision)  
//            for (int i = expectedVars.length; i < model._output._rule_importance.getRowDim(); i++) {
//                assertEquals(0, (double) model._output._rule_importance.get(i,1),coefDelta);
//                assertTrue(zeroVars.contains((String) model._output._rule_importance.get(i,0)));
//            }

            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            glmParameters._response_column = model._parms._response_column;
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
    public void testCarsRulesValidation() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parseTestFile("smalldata/testng/cars_train.csv"));
            final Frame valid = Scope.track(parseTestFile("smalldata/testng/cars_test.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._valid = valid._key;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._min_rule_length = 1;

            RuleFitModel model = new RuleFit( params).trainModel().get();
            Scope.track_generic(model);

            System.out.println("Intercept: \n" + model._output._intercept[0]);
            System.out.println(model._output._rule_importance);

            final Frame fr2 = Scope.track(model.score(fr));

            GLMModel.GLMParameters glmParameters = model.glmModel._parms;
            glmParameters._train = fr._key;
            glmParameters._valid = valid._key;
            glmParameters._response_column = model._parms._response_column;
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
            final Frame fr = Scope.track(parseTestFile( "./smalldata/junit/cars.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._distribution = DistributionFamily.gaussian;
            params._min_rule_length = 1;

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
            glmParameters._response_column = model._parms._response_column;
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
            final Frame fr = Scope.track(parseTestFile("./smalldata/junit/cars.csv"));
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = fr._key;
            params._max_num_rules = 200;
            params._max_rule_length = 10;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._min_rule_length = 1;

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
            glmParameters._response_column = model._parms._response_column;
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
            final Frame fr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
            Scope.track(fr);

            String responseColumnName = fr.lastVecName();

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES;
            params._response_column = responseColumnName;
            params._min_rule_length = 1;
            params._max_rule_length = 10;

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);
            
            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmParameters._response_column = rfModel._parms._response_column;
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
            final Frame fr = parseTestFile("./smalldata/diabetes/diabetes_text_train.csv");
            Scope.track(fr);
           // final Vec weightsVector = createRandomBinaryWeightsVec(fr.numRows(), 10); // failing with these weights is ok because https://github.com/h2oai/h2o-3/issues/7404 is not a bug
            final Vec weightsVector = Vec.makeOne(fr.numRows());
            weightsVector.set(1, 0.5);
            final String weightsColumnName = "weights";
            fr.add(weightsColumnName, weightsVector);
            DKV.put(fr);
            
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._response_column = "diabetesMed";
            params._weights_column = "weights";
            params._max_num_rules = 200;
            params._max_rule_length = 5;
            params._min_rule_length = 1;
            params._rule_generation_ntrees = 3;


            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4, 1e-4, 1));

            // GLM to compare:

            GLMModel.GLMParameters glmParameters = rfModel.glmModel._parms;
            glmParameters._train = fr._key;
            glmParameters._weights_column = rfModel._parms._weights_column;
            glmParameters._response_column = rfModel._parms._response_column;
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);

            final Frame fr3 = Scope.track(glmModel.score(fr));
            
            Assert.assertTrue(glmModel.testJavaScoring(fr,fr3,1e-4, 1e-4, 1));

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

    @Test @Ignore // this failing is ok because https://github.com/h2oai/h2o-3/issues/7404 is not a bug
    public void testDiabetesWithWeightsShowWhatGlmIsDoingSeparately() { 
        try {
            Scope.enter();
            final Frame fr = parseTestFile("./smalldata/diabetes/diabetes_text_train.csv");
            Scope.track(fr);
            final Vec weightsVector = createRandomBinaryWeightsVec(fr.numRows(), 10);
            // works with non-zero weights, but if I create zero ( weightsVector.set(1, 0.5); -> weightsVector.set(1, 0.0); ) it will fail again
            // final Vec weightsVector = Vec.makeOne(fr.numRows());
            weightsVector.set(1, 0.5);
            
            final String weightsColumnName = "weights";
            fr.add(weightsColumnName, weightsVector);
            DKV.put(fr);
            
            GLMModel.GLMParameters glmParameters = new GLMModel.GLMParameters();
            glmParameters._seed = 12345;
            glmParameters._train = fr._key;
            glmParameters._response_column = "diabetesMed";
            glmParameters._weights_column = "weights";
            
            
            final GLMModel glmModel = new GLM(glmParameters).trainModel().get();
            Scope.track_generic(glmModel);

            final Frame fr3 = Scope.track(glmModel.score(fr));

            // this fails
            Assert.assertTrue(glmModel.testJavaScoring(fr,fr3,1e-4, 1e-4, 1));
            
            
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testMulticlass() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parseTestFile("smalldata/iris/iris_train.csv"));
           
            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._response_column = "species";

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr, fr2, 1e-4));
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testProstateMulticlass() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parseTestFile("smalldata/prostate/prostate_cat.csv"));

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 12345;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._response_column = "DPROS";

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr, fr2, 1e-4));
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testBadColsBug() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parse_test_file("smalldata/rulefit/repro_bad_cols_bug.csv"));

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 42;
            params._train = fr._key;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._response_column = "target";
            params._max_num_rules = 1000;

            asFactor(fr, "target");

            final RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);

            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

            final Frame fr2 = Scope.track(rfModel.score(fr));

            Assert.assertTrue(rfModel.testJavaScoring(fr,fr2,1e-4));
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testIrisMulticlassWithoutScope() {
        RuleFitModel rfModel = null;
        Frame fr = null, fr2 = null;
        try {
            fr = parseTestFile("./smalldata/iris/iris_wheader.csv");
            DKV.put(fr);
            
            final RuleFitModel.RuleFitParameters ruleFitParameters = new RuleFitModel.RuleFitParameters();
            ruleFitParameters._train = fr._key;
            ruleFitParameters._response_column = "class";
            ruleFitParameters._seed = 0XFEED;
            ruleFitParameters._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            ruleFitParameters._max_rule_length = 5;
            ruleFitParameters._min_rule_length = 1;
            ruleFitParameters._max_num_rules = 50;
            
            rfModel = new RuleFit(ruleFitParameters).trainModel().get();
            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);
            
            fr2 = rfModel.score(fr);

            Assert.assertTrue(rfModel.testJavaScoring(fr, fr2,1e-4));
            
        } finally {
            if (rfModel != null) {
                rfModel.remove();
            }
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
        }
    }

    @Test
    public void testTitanicMulticlass() {
        try {
            Scope.enter();
            final Frame fr = Scope.track(parseTestFile("./smalldata/gbm_test/titanic.csv"));

            String responseColumnName = "parch";
            asFactor(fr, responseColumnName);
            asFactor(fr, "pclass");
            fr.remove("name").remove();
            fr.remove("ticket").remove();
            fr.remove("cabin").remove();
            fr.remove("embarked").remove();
            fr.remove("boat").remove();
            fr.remove("body").remove();
            fr.remove("home.dest").remove();
            DKV.put(fr);

            RuleFitModel.RuleFitParameters params = new RuleFitModel.RuleFitParameters();
            params._seed = 1234;
            params._train = fr._key;
            params._response_column = responseColumnName;
            params._max_num_rules = 100;
            params._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;
            params._min_rule_length = 1;
            params._max_rule_length = 10;


            RuleFitModel rfModel = new RuleFit(params).trainModel().get();
            Scope.track_generic(rfModel);
            System.out.println("Intercept: \n" + rfModel._output._intercept[0]);
            System.out.println(rfModel._output._rule_importance);

           // TODO: this reproduces problem with mapping (will be fixed in PUBDEV-8333)
            //     Frame scored = rfModel.score(fr);
            //   Assert.assertTrue(rfModel.testJavaScoring(fr, scored, 1e-4));


        } finally {
            Scope.exit();
        }
    }

    @Test public void testEcologyMulticlass() {
        try {
            Scope.enter();
            Frame  train = parseTestFile("smalldata/gbm_test/ecology_model.csv")
                    .toCategoricalCol("Method");
     
            train.remove("Site").remove();
            Scope.track(train);
            DKV.put(train);
            RuleFitModel.RuleFitParameters parms = new RuleFitModel.RuleFitParameters();
            parms._seed = 1234;
            parms._train = train._key;
            parms._response_column = "Method";
            parms._max_num_rules = 100;
            parms._model_type = RuleFitModel.ModelType.RULES_AND_LINEAR;

            RuleFitModel rfit = new RuleFit(parms).trainModel().get();
            Scope.track_generic(rfit);
            
            System.out.println("Intercept: \n" + rfit._output._intercept[0]);
            System.out.println(rfit._output._rule_importance);
            
            Frame scored = Scope.track(rfit.score(train));
            
            Assert.assertTrue(rfit.testJavaScoring(train, scored, 1e-4));
        } finally {
            Scope.exit();
        }

    }


    @Test public void testEcologyBinomial() {
        try {
            Scope.enter();
            Frame  train = parseTestFile("smalldata/gbm_test/ecology_model.csv")
                    .toCategoricalCol("Angaus");
            train.remove("Site").remove();
            Scope.track(train);
            DKV.put(train);
            RuleFitModel.RuleFitParameters parms = new RuleFitModel.RuleFitParameters();
            parms._train = train._key;
            parms._response_column = "Angaus";
            parms._max_num_rules = 100;
            parms._model_type = RuleFitModel.ModelType.RULES;
            parms._min_rule_length = 1;
            parms._max_rule_length = 10;
            parms._seed = -2348835740834922574L;

            RuleFitModel rfit = new RuleFit(parms).trainModel().get();
            Scope.track_generic(rfit);

            System.out.println("Intercept: \n" + rfit._output._intercept[0]);
            System.out.println(rfit._output._rule_importance);

            Frame scored = Scope.track(rfit.score(train));

            Assert.assertTrue(rfit.testJavaScoring(train, scored, 1e-4));
            
            
            // test transform by rules functionality:
            Frame transformedOutput = rfit.predictRules(train, new String[] {"M1T38N9, M1T44N9", "M2T34N20"});
            Scope.track(transformedOutput);
            Rule rule1 = rfit.ruleEnsemble.getRuleByVarName("M1T38N9");
            Rule rule2 = rfit.ruleEnsemble.getRuleByVarName("M2T34N20");

            System.out.println("Rule 1: \n" + rule1.languageRule);
            System.out.println("Rule 2: \n" + rule2.languageRule);
            
            assertEquals((int)transformedOutput.vec("M1T38N9").at(0), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(1), 1);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(2), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(3), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(4), 1);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(5), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(6), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(7), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(8), 0);
            assertEquals((int)transformedOutput.vec("M1T38N9").at(9), 1);
            
            assertEquals((int)transformedOutput.vec("M2T34N20").at(0), 1);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(1), 0);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(2), 0);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(3), 0);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(4), 1);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(5), 1);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(6), 0);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(7), 0);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(8), 0);
            assertEquals((int)transformedOutput.vec("M2T34N20").at(9), 0);
            
        } finally {
            Scope.exit();
        }

    }

}
