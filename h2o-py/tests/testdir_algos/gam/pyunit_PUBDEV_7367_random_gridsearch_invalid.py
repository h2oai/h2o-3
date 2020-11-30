from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch

# In this test, we check to make sure that a random discrete grid search on a GAM functions correctly.
# The test searches over 3 parameters, lambda, scale, and gam_columns, with one of the lambda values being invalid.
# The test then checks to make sure the random grid search does not use any invalid values.
class test_random_gam_gridsearch_invalid:

    h2o_data = []
    myX = []
    myY = []
    h2o_model = []
    search_criteria = {'strategy': 'RandomDiscrete', "max_models": 12, "seed": 1}
    hyper_parameters = {'scale': [[1, 1], [2, 2]], 'gam_columns': [["C11", "C12"], ["C12", "C13"]], 'lambda': [-1, 0, 0.01]}
    manual_gam_models = []
    num_grid_models = 0
    num_expected_models = 8

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
        for scale in self.hyper_parameters['scale']:
            for gam_columns in self.hyper_parameters['gam_columns']:
                for lambda_ in self.hyper_parameters['lambda']:
                    if lambda_ == -1:
                        continue
                    self.manual_gam_models.append(H2OGeneralizedAdditiveEstimator(family="gaussian", gam_columns=gam_columns,
                                                                                  keep_gam_cols=True, scale=scale, lambda_=lambda_,
                                                                                  ))

    def train_models(self):
        self.h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(family="gaussian",
                                                                       keep_gam_cols=True), hyper_params=self.hyper_parameters, search_criteria=self.search_criteria)
        self.h2o_model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)
        for model in self.manual_gam_models:
            model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)
        print("done")

    def match_models(self):
        for model in self.manual_gam_models:
            assert model.actual_params['lambda'] != -1, "Incorrect lambda value in grid search"
            gam_columns = model.actual_params['gam_columns']
            scale = model.actual_params['scale']
            lambda_ = model.actual_params['lambda']
            for grid_search_model in self.h2o_model.models:
                if grid_search_model.actual_params['gam_columns'] == gam_columns \
                    and grid_search_model.actual_params['scale'] == scale \
                    and grid_search_model.actual_params['lambda'] == lambda_:
                    self.num_grid_models += 1
                    assert grid_search_model.coef() == model.coef(), "coefficients should be equal"
                    break

        assert self.num_grid_models == self.num_expected_models, "Grid search model parameters incorrect or incorrect number of models generated"

def test_gridsearch():
    test_gam_grid = test_random_gam_gridsearch_invalid()
    test_gam_grid.train_models()
    test_gam_grid.match_models()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gridsearch)
else:
    test_gridsearch()
