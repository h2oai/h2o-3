
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator

from tests import pyunit_utils


def pubdev_5023_rm_metalearner():
    # Import a sample binary outcome dataset into H2O
    data = h2o.import_file(pyunit_utils.locate("smalldata/higgs/higgs_train_10k.csv"))

    # Identify predictors and response
    x = data.columns
    y = "response"
    x.remove(y)

    # For binary classification, response should be a factor
    data[y] = data[y].asfactor()
    gbm_h2o = H2OGradientBoostingEstimator(learn_rate=0.1, max_depth=4)
    gbm_h2o.train(x=x, y=y, training_frame=data)

    try:    # try to access metalearner method for GBM should encounter exception
        print(type(gbm_h2o.metalearner()))
        exit(1) # should have failed the test
    except Exception as ex:
        print(ex)


    # test to for method metalearner() can be found in pyunit_stackedensemble_regression.py
    # test to for method levelone_frame_id() can be found in pyunit_stackedensemble_levelone_frame.py
    # There is no need to add one here.

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5023_rm_metalearner)
else:
    pubdev_5023_rm_metalearner()
