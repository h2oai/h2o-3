package hex;

import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.BeforeClass;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

public class GLMLambdaSearchMetricsRace  extends TestUtil {
  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }
  /* @Test */ public void checkNullMetrics() {
    Frame tfr = null;
    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/iris/iris_wheader.csv");
      DKV.put(tfr);
      for (int i = 0; i < 10; ++i) {
        GLMModel.GLMParameters parmsGLM = new GLMModel.GLMParameters();
        parmsGLM._train = tfr._key;
        parmsGLM._response_column = "sepal_len";
        parmsGLM._lambda_search=true;
        GLM glmJob = new GLM(parmsGLM);
        GLMModel glm = glmJob.trainModel().get();
        assert glm._output._cross_validation_metrics !=null;
        glm.delete();
        glm.deleteCrossValidationModels();
      }
    } finally {
      if( tfr!=null) tfr.delete();
      Scope.exit();
    }
  }
}
