import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from collections import OrderedDict
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def grid_multinomial_auc():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    # Run GBM Grid Search
    ntrees_opts = [1, 5]
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = ntrees_opts
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    gbm = H2OGradientBoostingEstimator(auc_type="WEIGHTED_OVO")

    gs = H2OGridSearch(gbm, hyper_params=hyper_parameters)
    gs.train(x=list(range(4)), y=4, training_frame=train)
    assert gs is not None
    # Should not fail
    auc = gs.auc(train=True)
    auc_value = auc[list(auc.keys())[0]]
    print(auc_value)
    assert auc_value != "NaN", "AUC expected not to be NaN but is:"+auc_value
    
    
def grid_multinomial_auc_none_type():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    # Run GBM Grid Search
    ntrees_opts = [1, 5]
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = ntrees_opts
    print("GBM grid with the following hyper_parameters:", hyper_parameters)
    
    gbm = H2OGradientBoostingEstimator(auc_type="NONE")

    gs = H2OGridSearch(gbm, hyper_params=hyper_parameters)
    gs.train(x=list(range(4)), y=4, training_frame=train)
    assert gs is not None
    # Should not fail
    auc = gs.auc(train=True)
    auc_value = auc[list(auc.keys())[0]]
    print(auc_value)
    assert auc_value == "NaN", "AUC expected to be NaN but is:"+auc_value
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_multinomial_auc)
    pyunit_utils.standalone_test(grid_multinomial_auc_none_type)
else:
    grid_multinomial_auc()
    grid_multinomial_auc_none_type()
