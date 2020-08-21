from __future__ import print_function
from builtins import range
import sys

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.grid.grid_search import H2OGridSearch
import random


def get_hyperparams_dict_return_correct_params():
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    
    num_folds = random.randint(2,5)
    fold_assignments = h2o.H2OFrame([[random.randint(0,num_folds-1)] for _ in range(prostate_train.nrow)])
    fold_assignments.set_names(["fold_assignments"])
    prostate_train = prostate_train.cbind(fold_assignments)
    
    x_features=range(1,prostate_train.ncol)
    y_target="CAPSULE"
    h2o_data_frame=prostate_train
    
    
    grid = H2OGridSearch(model=H2OGradientBoostingEstimator,
                         hyper_params={'fold_assignment':['Stratified'],'sample_rate_per_class':[[1.0, 0.6]]},
                         search_criteria={'strategy': 'RandomDiscrete', 'max_models': 1})
    grid.train(x=x_features, y=y_target, training_frame=h2o_data_frame, nfolds=num_folds)

    print(grid.get_grid())
    hyperparams_dict = grid.get_hyperparams_dict(0);
    assert hyperparams_dict['fold_assignment'] == 'Stratified'
    assert hyperparams_dict['sample_rate_per_class'] == [1.0, 0.6]


    
if __name__ == "__main__":
    pyunit_utils.standalone_test(get_hyperparams_dict_return_correct_params)
else:
    get_hyperparams_dict_return_correct_params()
