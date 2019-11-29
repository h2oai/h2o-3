package ai.h2o.automl.preprocessing;

import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.TestUtil;

import static ai.h2o.automl.targetencoder.strategy.ModelValidationMode.CV;


@RunWith(Enclosed.class)
public class TEPreprocessingStepTest {


  @RunWith(Parameterized.class)
  public static class GridSearchModelParametersSelectionStrategyParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "Validation mode = {0}")
    public static Object[] validationMode() {
      return new ModelValidationMode[]{
              CV, ModelValidationMode.VALIDATION_FRAME
      };
    }

    @Parameterized.Parameter
    public ModelValidationMode validationMode;

    @Test
    public void evaluated_during_search_models_are_being_removed_properly() {
      //TODO test that we do cleanup properly depending on whether we found better HPs
    }

  }
}