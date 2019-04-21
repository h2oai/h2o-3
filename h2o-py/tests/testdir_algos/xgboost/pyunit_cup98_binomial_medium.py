from h2o.estimators.xgboost import *
from tests import pyunit_utils


def xgboost_cup98_medium():
    assert H2OXGBoostEstimator.available()

    # Import big dataset to ensure run across multiple nodes
    data = h2o.import_file(pyunit_utils.locate('bigdata/laptop/usecases/cup98LRN_z.csv'))
    y='TARGET_B'
    x = data.names
    x.remove(y)

    data[y] = data[y].asfactor()

    data.split_frame(ratios=[0.75], destination_frames=['training', 'validation'], seed=1)

    training_frame = h2o.get_frame('training')
    test_frame = h2o.get_frame('validation')


    # Model with maximum of 2 trees
    model_2_trees = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7,
                                        booster='gbtree', seed=1, ntrees=2, distribution='bernoulli')
    model_2_trees.train(x=x, y=y, training_frame=training_frame)
    predition_2_trees = model_2_trees.predict(test_frame)

    assert predition_2_trees.nrows == test_frame.nrows

    # Model with 10 trees
    model_10_trees = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7,
                                         booster='gbtree', seed=1, ntrees=10, distribution='bernoulli')
    model_10_trees.train(x=x, y=y, training_frame=training_frame)
    prediction_10_trees = model_10_trees.predict(test_frame)

    assert prediction_10_trees.nrows == test_frame.nrows

    ##Performance of a model with 10 dec. trees should be better than model with 2 trees allowed only
    assert model_2_trees.logloss(train=True) > model_10_trees.logloss(train=True)


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_cup98_medium)
else:
    xgboost_cup98_medium()
