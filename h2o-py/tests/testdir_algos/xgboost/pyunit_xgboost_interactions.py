import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.xgboost import *


def interaction_constraint_test():
    assert H2OXGBoostEstimator.available() is True

    # CPU Backend is forced for the results to be comparable
    h2o_params = {"tree_method": "exact", "seed": 123, "backend": "cpu", "ntrees": 5}

    train = pyunit_utils.genTrainFrame(100, 10, enumCols=0, randseed=17)
    print(train)
    x = train.names
    y = 'response'
    x.remove(y)

    h2o_params["interaction_constraints"] = [["C1", "C2"], ["C3", "C4", "C5"]]

    model = H2OXGBoostEstimator(**h2o_params)
    model.train(x=x, y=y, training_frame=train)

    native_params = model._model_json["output"]["native_parameters"].as_data_frame()
    print(native_params)

    constraints = (native_params[native_params['name'] == "interaction_constraints"])['value'].values[0]

    assert constraints == u'[[0,1],[2,3,4]]', "Constraints should be [[0,1],[2,3,4]] but it is:"+constraints


if __name__ == "__main__":
    pyunit_utils.standalone_test(interaction_constraint_test)
else:
    interaction_constraint_test()
