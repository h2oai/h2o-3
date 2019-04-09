package ai.h2o.automl;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;

import static org.junit.Assert.assertTrue;

public class AutoMLKeepCVPreds extends water.TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  long autoMLSeed = 2345;

  int numberOfModelsToCompareWith = 1;
  
  Algo[] excludeAlgos = {Algo.DeepLearning, Algo.DRF, Algo.GLM /*Algo.XGBoost */, Algo.GBM, Algo.StackedEnsemble};
//  Algo[] excludeAlgos = {/*Algo.DeepLearning,*/ Algo.DRF, Algo.GLM , Algo.XGBoost , Algo.GBM, Algo.StackedEnsemble};

  @Test
  public void keep_cv_predictions_parameter_is_being_ignored_test() {
    AutoML aml = null;
    Frame fr = null;

    String responseColumnName = "survived";
    AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

    fr = parse_test_file(Key.make("titanic_test_parsed"), "smalldata/gbm_test/titanic.csv");

    autoMLBuildSpec.input_spec.training_frame = fr._key;
    autoMLBuildSpec.build_control.nfolds = 5;
    autoMLBuildSpec.input_spec.response_column = responseColumnName;
    autoMLBuildSpec.build_models.exclude_algos = excludeAlgos;

    autoMLBuildSpec.build_control.project_name = "1234";
    autoMLBuildSpec.build_control.stopping_criteria.set_max_models(numberOfModelsToCompareWith);
    autoMLBuildSpec.build_control.stopping_criteria.set_seed(autoMLSeed);
    autoMLBuildSpec.build_control.keep_cross_validation_models = false;
    autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

    long start1 = System.currentTimeMillis();
    aml = AutoML.startAutoML(autoMLBuildSpec);
    aml.get();

    Key[] cross_validation_predictions = aml.leader()._output._cross_validation_predictions;
    assertTrue(cross_validation_predictions == null);
        
  }

  
}
