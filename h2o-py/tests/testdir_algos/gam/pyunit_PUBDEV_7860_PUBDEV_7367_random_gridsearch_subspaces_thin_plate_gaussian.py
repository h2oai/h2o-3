from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch

# I copied Karthik's test to perform gridsearch on thin plate regression smoothers only with gridsearch.  In this test,
# I only allowed thin plate smoothers.
class test_random_gam_gridsearch_specific:
    h2o_data = []
    myX = []
    myY = []
    h2o_model = []
    search_criteria = {'strategy': 'RandomDiscrete', "max_models": 16, "seed": 1}
    hyper_parameters = {'lambda': [100, 200],
                        'subspaces': [{'scale': [[1, 1, 1], [2, 2, 2]],
                                         'num_knots': [[5, 5, 5], [6, 6, 6]],
                                         'bs':[[1,1,1]],
                                         'gam_columns': [[["C11", "C12"], ["C13", "C14"], ["C15", "C16"]]]},
                                        {'scale': [[1, 1], [2, 2]],
                                         'bs':[[1, 1]],
                                         'num_knots': [[6, 6], [5, 5]],
                                         'gam_columns': [[["C11"], ["C14"]]]}]}
    manual_gam_models = []
    num_grid_models = 0
    num_expected_models = 16
    manual_model_count = 0

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the data sets and set the training set indices and response column index
        """
        self.h2o_data = h2o.import_file(
            path = pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
        names = self.h2o_data.names
        counter = 0
        for name in names:
            self.h2o_data[name] = self.h2o_data[name].asfactor()
            counter = counter+1
            if counter > 9:
                break
        self.myY = "C21"
        self.myX = names.remove(self.myY)

        for lambda_ in self.hyper_parameters["lambda"]:
            for subspace in self.hyper_parameters["subspaces"]:
                for scale in subspace['scale']:
                    for gam_columns in subspace['gam_columns']:
                        for num_knots in subspace['num_knots']:
                            for bsVal in subspace['bs']:
                                self.manual_model_count += 1
                                self.manual_gam_models.append(H2OGeneralizedAdditiveEstimator(family="gaussian", 
                                                                                              gam_columns=gam_columns,
                                                                                              scale=scale,
                                                                                              num_knots=num_knots,
                                                                                              bs=bsVal,
                                                                                              lambda_=lambda_
                                                                                          ))

    def train_models(self):
        self.h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(family="gaussian",
                                                                       keep_gam_cols=True), hyper_params=self.hyper_parameters, search_criteria=self.search_criteria)
        self.h2o_model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)
        for model in self.manual_gam_models:
            model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)

    def match_models(self):
        for model in self.manual_gam_models:
            scale = model.actual_params['scale']
            gam_columns = model.actual_params['gam_columns']
            num_knots = model.actual_params['num_knots']
            lambda_ = model.actual_params['lambda']
            bsVal = model.actual_params['bs']
            for grid_search_model in self.h2o_model.models:
                if grid_search_model.actual_params['gam_columns'] == gam_columns \
                    and grid_search_model.actual_params['scale'] == scale \
                    and grid_search_model.actual_params['num_knots'] == num_knots \
                    and grid_search_model.actual_params['bs'] == bsVal \
                    and grid_search_model.actual_params['lambda'] == lambda_:
                    self.num_grid_models += 1
                    print("grid model number "+str(self.num_grid_models))
                    print("gridSearch model coefficients")
                    print(grid_search_model.coef())
                    print("manual model coefficients")
                    print(model.coef())
                    pyunit_utils.assertEqualCoeffDicts(grid_search_model.coef(), model.coef(), tol=1e-6)
                    break

        assert self.num_grid_models == self.num_expected_models, "Grid search model parameters incorrect or incorrect number of models generated"

def test_gridsearch():
    test_gam_grid = test_random_gam_gridsearch_specific()
    test_gam_grid.train_models()
    test_gam_grid.match_models()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gridsearch)
else:
    test_gridsearch()
