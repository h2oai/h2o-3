from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch

# In this test, we check to make sure that a basic grid search on a GAM functions correctly.
# The test searches over 2 parameters, alpha and lambda.
# The test then compares the results of the grid search models with the models we created
# by manually searching over the hyperspace.
# If the coefficients do not match or an incorrect number of models is generated, the test throws an assertion error.
class test_gam_gridsearch:

    h2o_data = []
    myX = []
    myY = []
    hyper_parameters = {'alpha': [0.1, 0.9], 'lambda':[0, 0.01]}
    manual_gam_models = []
    h2o_model = []
    num_grid_models = 0
    num_expected_models = len(hyper_parameters['alpha']) * len(hyper_parameters['lambda'])

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the data sets and set the training set indices and response column index
        """
        self.h2o_data = h2o.import_file(
            path = pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
        self.h2o_data["C1"] = self.h2o_data["C1"].asfactor()
        self.h2o_data["C2"] = self.h2o_data["C2"].asfactor()
        self.myX = ["C1", "C2"]
        self.myY = "C21"
        for lambda_param in self.hyper_parameters['lambda']:
            for alpha_param in self.hyper_parameters['alpha']:
                self.manual_gam_models.append(H2OGeneralizedAdditiveEstimator(family = "gaussian", gam_columns=["C11", "C12", "C13"],
                                                                              keep_gam_cols = True, scale = [1,1,1], num_knots = [5,5,5],
                                                                              alpha = alpha_param, lambda_ = lambda_param,
                                                                              bs=[2,0,2]))

    def train_models(self):
        self.h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(family="gaussian", gam_columns=["C11", "C12", "C13"],
                                                                  keep_gam_cols=True, scale = [1,1,1], num_knots=[5,5,5],
                                                                       bs=[2,0,2]), self.hyper_parameters)
        self.h2o_model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)
        for model in self.manual_gam_models:
            model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)

    def match_models(self):
        for model in self.manual_gam_models:
            alpha = model.actual_params['alpha']
            lambda_ = model.actual_params['lambda']
            for grid_search_model in self.h2o_model.models:
                if grid_search_model.actual_params['alpha'] == alpha \
                    and grid_search_model.actual_params['lambda'] == lambda_:
                    self.num_grid_models += 1
                    assert grid_search_model.coef() == model.coef(), "coefficients should be equal"
                    break
        
        assert self.num_grid_models == self.num_expected_models, "Grid search model parameters incorrect or incorrect number of models generated"
            
def test_gridsearch():
    test_gam_grid = test_gam_gridsearch()
    test_gam_grid.train_models()
    test_gam_grid.match_models()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gridsearch)
else:
    test_gridsearch()
