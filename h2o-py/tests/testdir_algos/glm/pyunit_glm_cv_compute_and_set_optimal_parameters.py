import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_alpha_lambda_arrays_last_lambda_best():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    glm = H2OGeneralizedLinearEstimator(family='binomial', lambda_=[0.9, 0.2, 0.8, 0.5, 0.1, 0.099, 0.098],
                                        alpha=[0.1, 0.5, 0.2, 0.9, 0.8], nfolds=5, seed=1234)
    glm.train(training_frame=df, y="CAPSULE")
    assert glm._model_json["output"]["alpha_best"] == 0.1
    assert glm._model_json["output"]["lambda_best"] == 0.098
    assert glm._model_json["output"]["lambda_1se"] == 0.1


def glm_alpha_lambda_arrays_inner_lambda_best():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    df["CAPSULE"] = df["CAPSULE"].asfactor()
    glm = H2OGeneralizedLinearEstimator(family='binomial', lambda_=[0.9, 0.2, 0.8, 0.5, 0.098, 0.1, 0.099],
                                        alpha=[0.1, 0.5, 0.2, 0.9, 0.8], nfolds=5, seed=1234)
    glm.train(training_frame=df, y="CAPSULE")
    assert glm._model_json["output"]["alpha_best"] == 0.1
    assert glm._model_json["output"]["lambda_best"] == 0.098
    assert glm._model_json["output"]["lambda_1se"] == 0.1


pyunit_utils.run_tests([
    glm_alpha_lambda_arrays_last_lambda_best,
    glm_alpha_lambda_arrays_inner_lambda_best
])
