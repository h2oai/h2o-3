from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random
import copy
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.grid.grid_search import H2OGridSearch

    
def grid_specificity_metrics():

    gbm_grid1 = train_grid()

    gbm_gridper_specificity = gbm_grid1.get_grid(sort_by='specificity', decreasing=True)
    print(gbm_gridper_specificity)
    print("Model 0:")
    best_gbm_f2 = gbm_gridper_specificity.models[0]
    best_gbm_f2.specificity(valid=True)


def grid_recall_metrics():

    gbm_grid1 = train_grid()

    gbm_gridper_recall = gbm_grid1.get_grid(sort_by='recall', decreasing=True)
    print(gbm_gridper_recall)
    print("Model 0:")
    best_gbm_recall = gbm_gridper_recall.models[0]
    best_gbm_recall.recall(valid=True)
    

def train_grid():
    # Import a sample binary outcome dataset into H2O
    data = h2o.import_file(pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"))
    
    # Identify predictors and response
    x = data.columns
    y = "response"
    x.remove(y)
    # For binary classification, response should be a factor
    data[y] = data[y].asfactor()
    test[y] = test[y].asfactor()
    # Split data into train & validation
    ss = data.split_frame(seed=1)
    train = ss[0]
    valid = ss[1]
    # GBM hyperparameters
    gbm_params1 = {'learn_rate': [0.01],
                   'max_depth': [3],
                   'sample_rate': [0.8],
                   'col_sample_rate': [0.2, 0.5, 1.0]}
    # Train and validate a cartesian grid of GBMs
    gbm_grid1 = H2OGridSearch(model=H2OGradientBoostingEstimator,
                              grid_id='gbm_grid1',
                              hyper_params=gbm_params1)
    gbm_grid1.train(x=x, y=y,
                    training_frame=train,
                    validation_frame=valid,
                    ntrees=100,
                    seed=1)
    return gbm_grid1


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_specificity_metrics)
    pyunit_utils.standalone_test(grid_recall_metrics)
else:
    grid_specificity_metrics()
    grid_recall_metrics()
