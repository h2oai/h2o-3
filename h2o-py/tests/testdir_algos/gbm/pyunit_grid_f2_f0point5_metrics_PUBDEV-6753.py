import sys

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.grid.grid_search import H2OGridSearch


def grid_f0point5_metrics():

    gbm_grid1 = train_grid()
    
    gbm_gridper_f0point5 = gbm_grid1.get_grid(sort_by='f0point5', decreasing=True)
    print(gbm_gridper_f0point5)
    sorted_metric_table_f0point5 = gbm_gridper_f0point5.sorted_metric_table()

    # Lets compare values grid was sorted with and metrics from each model in the grid 
    print("Model 0:")
    best_gbm_f0point5 = gbm_gridper_f0point5.models[0]
    model0_f0point5_valid = best_gbm_f0point5.F0point5(valid=True)
    print(model0_f0point5_valid)
    errorMsg = "Expected that metric value from sorted_metric_table is equal to corresponding metric for the model"
    assert float(model0_f0point5_valid[0][1]) == float(sorted_metric_table_f0point5['f0point5'][0]), errorMsg

    print("Model 1:")
    best_gbm_f0point5_1 = gbm_gridper_f0point5.models[1]
    model1_f0point5_valid = best_gbm_f0point5_1.F0point5(valid=True)
    print(model1_f0point5_valid)
    assert float(model1_f0point5_valid[0][1]) == float(sorted_metric_table_f0point5['f0point5'][1]), errorMsg


    print("Model 2:")
    best_gbm_f0point5_2 = gbm_gridper_f0point5.models[2]
    model2_f0point5_valid = best_gbm_f0point5_2.F0point5(valid=True)
    print(model2_f0point5_valid)
    assert float(model2_f0point5_valid[0][1]) == float(sorted_metric_table_f0point5['f0point5'][2]), errorMsg


def grid_f2_metrics():

    gbm_grid1 = train_grid()

    gbm_gridper_f2 = gbm_grid1.get_grid(sort_by='f2', decreasing=True)
    print(gbm_gridper_f2)
    sorted_metric_table_f2 = gbm_gridper_f2.sorted_metric_table()

    # Lets compare values grid was sorted with and metrics from each model in the grid 
    print("Model 0:")
    best_gbm_f2 = gbm_gridper_f2.models[0]
    model0_f2_valid = best_gbm_f2.F2(valid=True)
    print(model0_f2_valid)
    
    errorMsg = "Expected that metric value from sorted_metric_table is equal to corresponding metric for the model"
    assert float(model0_f2_valid[0][1]) == float(sorted_metric_table_f2['f2'][0]), errorMsg

    print("Model 1:")
    best_gbm_f2_1 = gbm_gridper_f2.models[1]
    model1_f2_valid = best_gbm_f2_1.F2(valid=True)
    print(model1_f2_valid)
    assert float(model1_f2_valid[0][1]) == float(sorted_metric_table_f2['f2'][1]), errorMsg


    print("Model 2:")
    best_gbm_f2_2 = gbm_gridper_f2.models[2]
    model2_f2_valid = best_gbm_f2_2.F2(valid=True)
    print(model2_f2_valid)
    assert float(model2_f2_valid[0][1]) == float(sorted_metric_table_f2['f2'][2]), errorMsg
    

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


pyunit_utils.run_tests([
    grid_f0point5_metrics,
    grid_f2_metrics
])
