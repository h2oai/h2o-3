package hex.glm;

import hex.Model;
import hex.ModelMetricsBinomialGLM;
import org.junit.Assert;
import org.junit.BeforeClass;
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

import java.io.IOException;
import java.util.HashMap;

import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.glm.GLMModel.GLMParameters.Link.log;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMMojoWithOffsetTest extends TestUtil {
    
    // for multinomials, there is no effect with adding offsets.
    @Test
    public void testOffsetMultinomial() {
        Scope.enter();
        try {
            Frame train = parseTestFile("smalldata/iris/iris_train.csv");
            Frame trainOffset = TestUtil.generateRealOnly(1, (int) train.numRows(), 0);
            trainOffset.setNames(new String[]{"offset"});
            DKV.put(trainOffset);
            train.replace(4, train.vec(4).toCategoricalVec()).remove();
            train.add(trainOffset);
            DKV.put(train);
            Scope.track(trainOffset);
            Scope.track(train);
            
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "species";
            params._train = train._key;
            params._family = multinomial;
            params._link = GLMModel.GLMParameters.Link.multinomial;
            params._offset_column = "offset";
            GLMModel model = new GLM(params).trainModel().get();
            Scope.track_generic(model);
            
            Frame scoreFrame = model.score(train);
            Scope.track(scoreFrame);
            Model.JavaScoringOptions options = new Model.JavaScoringOptions();
            options._abs_epsilon = 1e-6;
            options._fraction = 1.0;    // check all rows
            options._disable_pojo = true;   // only test mojo, no offset support for pojo
            Assert.assertTrue(model.testJavaScoring(train, scoreFrame, 1e-6, options));
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testOffsetGaussian() {
        Scope.enter();
        try {
            Frame prostateTrain = parseTestFile("smalldata/prostate/prostate_complete.csv.zip");
            Frame trainOffset = TestUtil.generateRealOnly(1, (int) prostateTrain.numRows(), 0);
            trainOffset.setNames(new String[]{"offset"});
            DKV.put(trainOffset);
            Scope.track(trainOffset);
            prostateTrain.add(trainOffset);
            DKV.put(prostateTrain);
            Scope.track(prostateTrain);
            
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._response_column = "GLEASON";
            params._train = prostateTrain._key;
            params._family = gaussian;
            params._link = GLMModel.GLMParameters.Link.identity;
            params._offset_column = "offset";
            GLMModel model = new GLM(params).trainModel().get();
            Scope.track_generic(model);
            Frame scoreFrame = model.score(prostateTrain);
            Scope.track(scoreFrame);
            
            Model.JavaScoringOptions options = new Model.JavaScoringOptions();
            options._abs_epsilon = 1e-6;
            options._fraction = 1.0;    // check all rows
            options._disable_pojo = true;   // only test mojo, no offset support for pojo
            Assert.assertTrue(model.testJavaScoring(prostateTrain, scoreFrame, 1e-6, options));
            
            // model without offset should be different
            params._offset_column = null;
            params._ignored_columns = new String[]{"offset"};
            GLMModel model2 = new GLM(params).trainModel().get();
            Scope.track_generic(model2);
            Frame scoreFrame2 = model2.score(prostateTrain);
            Scope.track(scoreFrame2);
            Assert.assertTrue(Math.abs(scoreFrame.vec(0).at(0)-scoreFrame2.vec(0).at(0))>1e-3);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testOffsetBinomial() {
        Scope.enter();
        try {
            Frame prostateTrain = parseTestFile("smalldata/glm_test/prostate_cat_train.csv");
            Frame trainOffset = TestUtil.generateRealOnly(1, (int) prostateTrain.numRows(), 0);
            trainOffset.setNames(new String[]{"offset"});
            DKV.put(trainOffset);
            Scope.track(trainOffset);
            prostateTrain.add(trainOffset);
            DKV.put(prostateTrain);
            Scope.track(prostateTrain);

            Frame prostateTest =  parseTestFile("smalldata/glm_test/prostate_cat_test.csv");
            Frame testOffset = TestUtil.generateRealOnly(1, (int) prostateTest.numRows(), 0);
            testOffset.setNames(new String[]{"offset"});
            DKV.put(testOffset);
            Scope.track(testOffset);
            prostateTest.add(testOffset);
            DKV.put(prostateTest);
            Scope.track(prostateTest);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "CAPSULE";
            params._ignored_columns = new String[]{"ID", "RACE", "DPROS", "DCAPS"};
            params._train = prostateTrain._key;
            params._offset_column = "offset";
            GLMModel model = new GLM(params).trainModel().get();
            Scope.track_generic(model);
            Frame scoreTest = model.score(prostateTest);
            Scope.track(scoreTest);
            Model.JavaScoringOptions options = new Model.JavaScoringOptions();
            options._abs_epsilon = 1e-6;
            options._fraction = 1.0;    // check all rows
            options._disable_pojo = true;   // only test mojo, no offset support for pojo
            Assert.assertTrue(model.testJavaScoring(prostateTest, scoreTest, 1e-6, options));
            
            params._offset_column = null;
            params._ignored_columns = new String[]{"ID", "RACE", "DPROS", "DCAPS", "offset"};
            GLMModel model2 = new GLM(params).trainModel().get();
            Scope.track_generic(model2);
            Frame scoreTest2 = model2.score(prostateTest);
            Scope.track(scoreTest2);
            Assert.assertTrue(Math.abs(scoreTest.vec(1).at(0)-scoreTest2.vec(1).at(0)) > 1e-3);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testOffsetGamma() {
        Scope.enter();
        try {
            Frame prostateTrain = parseTestFile("smalldata/prostate/prostate.csv");
            Frame trainOffset = TestUtil.generateRealOnly(1, (int) prostateTrain.numRows(), 0);
            trainOffset.setNames(new String[]{"offset"});
            DKV.put(trainOffset);
            Scope.track(trainOffset);
            prostateTrain.add(trainOffset);
            DKV.put(prostateTrain);
            Scope.track(prostateTrain);

            Frame prostateTest =  parseTestFile("smalldata/prostate/prostate.csv");
            Frame testOffset = TestUtil.generateRealOnly(1, (int) prostateTest.numRows(), 0);
            testOffset.setNames(new String[]{"offset"});
            DKV.put(testOffset);
            Scope.track(testOffset);
            prostateTest.add(testOffset);
            DKV.put(prostateTest);
            Scope.track(prostateTest);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "DPROS";
            params._family = gamma;
            params._link = log;
            params._ignored_columns = new String[]{"ID", "DPROS"};
            params._train = prostateTrain._key;
            params._offset_column = "offset";
            GLMModel model = new GLM(params).trainModel().get();
            Scope.track_generic(model);
            Frame scoreTest = model.score(prostateTest);
            Scope.track(scoreTest);
            Model.JavaScoringOptions options = new Model.JavaScoringOptions();
            options._abs_epsilon = 1e-6;
            options._fraction = 1.0;    // check all rows
            options._disable_pojo = true;   // only test mojo, no offset support for pojo
            Assert.assertTrue(model.testJavaScoring(prostateTest, scoreTest, 1e-6, options));

            params._offset_column = null;
            params._ignored_columns = new String[]{"ID", "DPROS", "offset"};
            GLMModel model2 = new GLM(params).trainModel().get();
            Scope.track_generic(model2);
            Frame scoreTest2 = model2.score(prostateTest);
            Scope.track(scoreTest2);
            Assert.assertTrue(Math.abs(scoreTest.vec(0).at(0)-scoreTest2.vec(0).at(0)) > 1e-3);
        } finally {
            Scope.exit();
        }
    }
}
