from __future__ import print_function
from builtins import range
import math
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def random_grid_model_seeds_PUBDEV_4090():
    '''
    This test is written to verify that I have implemented the model seed determination properly when
    random grid search is enabled.  Basically, there are three cases:
    1. Both the model and search_criteria did not set the seed value.  The seed values are either not set or
      set to default values.  In this case, random grid search and model will be using random seeds for itself
      independent of each other;
    2. Both the model and search_criteria set their seeds to be non-default values.  Random grid search will use
      the seed set in search_critera and model will be built using the seed set in its model parameter.
    3. The search_criteria seed is set to a non-default value while the model parameter seed is default value,
      Random grid search will use the search_criteria seed while the models will be built using the following
      sequence of seeds:
      - model 0: search_criteria seed;
      - model 1: search_criteria seed+1;
      -....
      - model n: search_criteria seed+n;
      ...
    4. Model parameter seed is set but search seed is set to default.  In this case, gridsearch will use random
      seed while models are built using the one seed.

    Current code already support cases 1/2/4.  The code changes were made to enable case 3 and this is the
    case that will be tested here.
    '''
    air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"),
                              destination_frame="air.hex")
    myX = ["Year","Month","CRSDepTime","UniqueCarrier","Origin","Dest"]
    grid_max_models = 8
    # create hyperameter and search criteria lists (ranges are inclusive..exclusive))
    hyper_params_tune = {'max_depth' : list(range(1,10+1,1)),
                         'sample_rate': [x/100. for x in range(20,101)],
                         'col_sample_rate' : [x/100. for x in range(20,101)],
                         'col_sample_rate_per_tree': [x/100. for x in range(20,101)],
                         'col_sample_rate_change_per_level': [x/100. for x in range(90,111)],
                         'min_rows': [2**x for x in range(0,int(math.log(air_hex.nrow,2)-1)+1)],
                         'nbins': [2**x for x in range(4,11)],
                         'nbins_cats': [2**x for x in range(4,13)],
                         'min_split_improvement': [0,1e-8,1e-6,1e-4],
                         'histogram_type': ["UniformAdaptive","QuantilesGlobal","RoundRobin"]}


    search_criteria_tune1 = {'strategy': "RandomDiscrete",
                             'max_models': grid_max_models ,   # limit the runtime
                             'seed' : 1234,
                             }
    search_criteria_tune2 = {'strategy': "RandomDiscrete",
                             'max_models': grid_max_models ,   # limit the runtime
                             'seed' : 1234,
                             }

    # case 3, search criteria seed is set but model parameter seed is not:
    air_grid1 = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_params_tune,
                              search_criteria=search_criteria_tune1)
    air_grid2 = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_params_tune,
                              search_criteria=search_criteria_tune2)
    air_grid1.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli")
    air_grid2.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli")

    # expect both models to render the same metrics as they use the same model seed, search criteria seed
    model_seeds1 = pyunit_utils.model_seed_sorted(air_grid1)
    model_seeds2 = pyunit_utils.model_seed_sorted(air_grid2)
    # check model seeds are set as gridseed+model number where model number = 0, 1, ..., ...
    model_len = min(len(air_grid1), len(air_grid2))

    model1Seeds = ','.join(str(x) for x in model_seeds1[0:model_len])
    model2Seeds = ','.join(str(x) for x in model_seeds2[0:model_len])
    assert model1Seeds==model2Seeds, "Model seeds are not equal: gridsearch 1 seeds %s; " \
                                                           " and gridsearch 2 seeds %s" % (model1Seeds, model2Seeds)

    # compare training_rmse from scoring history
    model1seed = air_grid1.models[0].full_parameters['seed']['actual_value']
    index2 = 0  # find the model in grid2 with the same seed
    for ind in range(0, len(air_grid2.models)):
        if air_grid2.models[ind].full_parameters['seed']['actual_value']==model1seed:
            index2=ind
            break

    metric_list1 = pyunit_utils.extract_scoring_history_field(air_grid1.models[0], "training_rmse", False)
    metric_list2 = pyunit_utils.extract_scoring_history_field(air_grid2.models[index2], "training_rmse", False)
    print(metric_list1)
    print(metric_list2)

    assert pyunit_utils.equal_two_arrays(metric_list1, metric_list2, 1e-5, 1e-6, False), \
                "Training_rmse are different between the two grid search models.  Tests are supposed to be repeatable in " \
                "this case.  Make sure model seeds are actually set correctly in the Java backend."


if __name__ == "__main__":
    pyunit_utils.standalone_test(random_grid_model_seeds_PUBDEV_4090)
else:
    random_grid_model_seeds_PUBDEV_4090()
