import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def setup_dataset():
    h2o.remove_all()
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    return train


def check_model_property(model, prop_name, actual_value=None, default_value=None, present=True):
    if present:
        assert prop_name in model.params.keys(), \
            "missing {prop} in model {model}".format(prop=prop_name, model=model)
        assert actual_value is None or model.params[prop_name]['actual'] == actual_value, \
            "actual value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=model, val=model.params[prop_name]['actual'], exp=actual_value)
        assert default_value is None or model.params[prop_name]['default'] == default_value, \
            "default value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=model, val=model.params[prop_name]['default'], exp=default_value)
    else:
        assert prop_name not in model.params.keys(), "unexpected {prop} in model {model}".format(prop=prop_name, model=model)


def test_max_runtime_secs_in_algo_def():
    train = setup_dataset()

    # Run GBM
    gbm = H2OGradientBoostingEstimator(max_runtime_secs=3, ntrees=50, learn_rate=0.1, distribution="multinomial")
    gbm.train(x=list(range(1,4)), y="class", training_frame=train)

    check_model_property(gbm, 'max_runtime_secs', 3)


def test_max_runtime_secs_in_train():
    train = setup_dataset()

    # Run GBM
    gbm = H2OGradientBoostingEstimator(max_runtime_secs=10, ntrees=50, learn_rate=0.1, distribution="multinomial")
    gbm.train(x=list(range(1,4)), y="class", training_frame=train, max_runtime_secs=3)

    check_model_property(gbm, 'max_runtime_secs', 3)


tests = [
    test_max_runtime_secs_in_algo_def,
    test_max_runtime_secs_in_train
]

if __name__ == "__main__":
    for test in tests: pyunit_utils.standalone_test(test)
else:
    for test in tests: test()
