import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OAdaBoostEstimator


def adaboost():
    print("AdaBoost Weak Learner Params Smoke Test - test only that parameters are correctly passed to backend")

    train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    train["CAPSULE"] = train["CAPSULE"].asfactor()

    common_adaboost_def = {"nlearners": 10, "seed": 0xBEEF, "learn_rate": 0.6}
    common_adaboost_train = {"training_frame": train, "y": "CAPSULE"}

    adaboost_model = H2OAdaBoostEstimator(
        weak_learner="DRF",
        weak_learner_params={
            'ntrees': 10,
            'histogram_type': "UniformAdaptive"
        },
        **common_adaboost_def
    )
    assert isinstance(adaboost_model.weak_learner_params, dict)
    adaboost_model.train(**common_adaboost_train)
    assert adaboost_model._model_json is not None

    adaboost_model = H2OAdaBoostEstimator(
        weak_learner="GBM",
        weak_learner_params={
          'ntrees': 10,
          'histogram_type': "UniformAdaptive",
          "learn_rate": 0.1
        },
        **common_adaboost_def
    )
    assert isinstance(adaboost_model.weak_learner_params, dict)
    adaboost_model.train(**common_adaboost_train)
    assert adaboost_model._model_json is not None

    adaboost_model = H2OAdaBoostEstimator(
        weak_learner="GLM",
        weak_learner_params={
            'max_iterations': 10
        },
        **common_adaboost_def
    )
    assert isinstance(adaboost_model.weak_learner_params, dict)
    adaboost_model.train(**common_adaboost_train)
    assert adaboost_model._model_json is not None

    adaboost_model = H2OAdaBoostEstimator(
        weak_learner="DEEP_LEARNING",
        weak_learner_params={
            'nepochs': 10,
            'hidden': [2, 2, 4]
        },
        **common_adaboost_def
    )
    assert isinstance(adaboost_model.weak_learner_params, dict)
    adaboost_model.train(**common_adaboost_train)
    assert adaboost_model._model_json is not None


if __name__ == "__main__":
    pyunit_utils.standalone_test(adaboost)
else:
    adaboost()
