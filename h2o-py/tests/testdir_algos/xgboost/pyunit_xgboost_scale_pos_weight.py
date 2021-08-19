from h2o.estimators.xgboost import *
from tests import pyunit_utils


def scale_pos_weight_test():
    assert H2OXGBoostEstimator.available() is True

    train = pyunit_utils.genTrainFrame(1000, 0, enumCols=10, enumFactors=2, miscfrac=0.1, randseed=17)

    xgboost = H2OXGBoostEstimator(ntrees=1, seed=1, scale_pos_weight=1.2)
    xgboost.train(y='response', training_frame=train)

    native_params = xgboost._model_json["output"]["native_parameters"].as_data_frame()
    assert min(native_params[native_params['name'] == 'scale_pos_weight']["value"]) == 1.2


if __name__ == "__main__":
    pyunit_utils.standalone_test(scale_pos_weight_test)
else:
    scale_pos_weight_test()
