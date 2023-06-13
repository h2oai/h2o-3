import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils


def test_binomial_response_warning():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    y = "survived"
    features = ["name", "sex"]

    expected_warning = 'We have detected that your response column has only 2 unique values (0/1). ' \
                       'If you wish to train a binary model instead of a regression model, ' \
                       'convert your target column to categorical before training.'

    with pyunit_utils.catch_warnings() as ws:
        model = H2OGradientBoostingEstimator(ntrees=1)
        model.train(x=features, y=y, training_frame=training_data)
        assert pyunit_utils.contains_warning(ws, expected_warning)

    training_data[training_data[y] == 0, y] = -1
    with pyunit_utils.catch_warnings() as ws:
        model = H2OGradientBoostingEstimator(ntrees=1)
        model.train(x=features, y=y, training_frame=training_data)
        assert pyunit_utils.contains_warning(ws, expected_warning)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_binomial_response_warning)
else:
    test_binomial_response_warning()
