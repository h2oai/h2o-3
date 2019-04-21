from h2o.estimators.xgboost import *
from tests import pyunit_utils

def get_native_parameters_test():
    assert H2OXGBoostEstimator.available() is True

    # CPU Backend is forced for the results to be comparable
    h2oParamsS = {"tree_method": "exact", "seed": 123, "backend": "cpu", "ntrees": 5}

    trainFile = pyunit_utils.genTrainFrame(100, 10, enumCols=0, randseed=17)
    print(trainFile)
    myX = trainFile.names
    y='response'
    myX.remove(y)

    h2oParamsS["monotone_constraints"] = {
        "C1": -1,
        "C3": 1,
        "C7": 1
    }

    h2oModelS = H2OXGBoostEstimator(**h2oParamsS)
    h2oModelS.train(x=myX, y=y, training_frame=trainFile)

    native_params = h2oModelS._model_json["output"]["native_parameters"].as_data_frame()
    print(native_params)

    constraints = (native_params[native_params['name'] == "monotone_constraints"])['value'].values[0]

    assert constraints == u'(-1,0,1,0,0,0,1,0,0,0)'


if __name__ == "__main__":
    pyunit_utils.standalone_test(get_native_parameters_test)
else:
    get_native_parameters_test()
