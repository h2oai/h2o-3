from h2o.estimators.xgboost import *
from tests import pyunit_utils
from h2o.exceptions import H2OResponseError
    
    
def test_param_and_alias_are_same(data, x_names, y):
    assert H2OXGBoostEstimator.available() is True
    
    num_round = 5
    params = {
        'tree_method': 'hist',
        'ntrees': num_round,
        'backend': 'cpu',
        'save_matrix_directory': "/home/mori/Documents/h2o/code/test/xgboost_data/",
        'seed': 42,
        'colsample_bylevel': 0.9,
        'col_sample_rate': 0.9
    }

    # train h2o XGBoost models
    h2o_model = H2OXGBoostEstimator(**params)
    h2o_model.train(x=x_names, y=y, training_frame=data)

    assert True, "Training should not fail."


def test_param_and_alias_are_not_same(data, x_names, y):
    assert H2OXGBoostEstimator.available() is True

    num_round = 5
    params = {
        'tree_method': 'hist',
        'ntrees': num_round,
        'backend': 'cpu',
        'save_matrix_directory': "/home/mori/Documents/h2o/code/test/xgboost_data/",
        'seed': 42,
        'colsample_bylevel': 0.9,
        'col_sample_rate': 0.3
    }

    # train h2o XGBoost models
    h2o_model = H2OXGBoostEstimator(**params)
    try:
        h2o_model.train(x=x_names, y=y, training_frame=data)
        assert False, "Training should fail."
    except H2OResponseError as e:
        assert "ERRR on field: _col_sample_rate" in str(e), \
            "col_sample_rate and its alias colsample_bylevel are both set"    
    

def test_alias():
    data = h2o.import_file(path="../../../../smalldata/gbm_test/ecology_model.csv")
    y = "Angaus"
    data[y] = data[y].asfactor()
    x_names = data.col_names.remove(y)
    test_param_and_alias_are_same(data, x_names, y)
    test_param_and_alias_are_not_same(data, x_names, y)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_alias)
else:
    test_alias()
