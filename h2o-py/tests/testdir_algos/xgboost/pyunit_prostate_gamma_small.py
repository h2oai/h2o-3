from h2o.estimators.xgboost import *
from tests import pyunit_utils


def xgboost_prostate_gamma_small():
    assert H2OXGBoostEstimator.available()

    prostate_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

    x = ["ID","AGE","RACE","GLEASON","DCAPS","PSA","VOL","CAPSULE"]
    y = 'DPROS'

    prostate_frame.split_frame(ratios=[0.75], destination_frames=['prostate_training', 'prostate_validation'], seed=1)

    training_frame = h2o.get_frame('prostate_training')
    test_frame = h2o.get_frame('prostate_validation')

    # Model with maximum of 2 trees
    model_2_trees = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.3,
                                        booster='gbtree', seed=1, ntrees=2, distribution='gamma')
    model_2_trees.train(x=x, y=y, training_frame=training_frame)
    prediction_2_trees = model_2_trees.predict(test_frame)

    assert prediction_2_trees.nrows == test_frame.nrows

    # Model with 10 trees
    model_10_trees = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.3,
                                         booster='gbtree', seed=1, ntrees=10, distribution='gamma')
    model_10_trees.train(x=x, y=y, training_frame=training_frame)
    prediction_10_trees = model_10_trees.predict(test_frame)

    assert prediction_10_trees.nrows == test_frame.nrows


    ## Performance of a model with 10 dec. trees should be better than model with 2 trees allowed only
    assert model_2_trees.mse() > model_10_trees.mse()

if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_prostate_gamma_small)
else:
    xgboost_prostate_gamma_small()
