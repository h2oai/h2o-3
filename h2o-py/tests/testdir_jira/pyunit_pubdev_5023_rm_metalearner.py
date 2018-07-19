
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.grid.grid_search import H2OGridSearch

from tests import pyunit_utils


def pubdev_5023_rm_metalearner():
    # Import a sample binary outcome dataset into H2O
    data = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    test = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

    # Identify predictors and response
    x = data.columns
    y = "response"
    x.remove(y)

    # For binary classification, response should be a factor
    data[y] = data[y].asfactor()
    test[y] = test[y].asfactor()

    # Split data into train & validation
    ss = data.split_frame(seed = 1)
    train = ss[0]
    valid = ss[1]


    # GBM hyperparameters
    gbm_params1 = {'learn_rate': [0.01, 0.1],
                   'max_depth': [3],
                   'sample_rate': [1.0],
                   'col_sample_rate': [1.0]}

    # Train and validate a cartesian grid of GBMs
    gbm_grid1 = H2OGridSearch(model=H2OGradientBoostingEstimator,
                              grid_id='gbm_grid1',
                              hyper_params=gbm_params1)
    gbm_grid1.train(x=x, y=y,
                    training_frame=train,
                    validation_frame=valid,
                    ntrees=100,
                    seed=1)

    # Get the grid results, sorted by validation AUC
    gbm_gridperf1 = gbm_grid1.get_grid(sort_by='auc', decreasing=True)
    # Grab the top GBM model, chosen by validation AUC
    best_gbm1 = gbm_gridperf1.models[0]
    try:
        print(type(best_gbm1.metalearner()))
        exit(1) # should have failed the test
    except Exception as ex:
        print(ex)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5023_rm_metalearner)
else:
    pubdev_5023_rm_metalearner()
