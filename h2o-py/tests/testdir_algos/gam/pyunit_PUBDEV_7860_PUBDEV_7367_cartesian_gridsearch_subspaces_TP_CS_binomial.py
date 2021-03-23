from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch

# I copied Karthik's test to perform gridsearch on TP/CS smoothers with cartesian gridsearch  
class test_random_gam_gridsearch_specific:
    h2o_data = []
    myX = []
    myY = []
    h2o_model = []
    search_criteria = {'strategy': 'Cartesian'}
    hyper_parameters = {'lambda': [1, 2],
                        'subspaces': [{'scale': [[0.001], [0.0002]], 'num_knots': [[5], [10]], 'bs':[[1], [0]], 
                                       'gam_columns': [[["c_0"]], [["c_1"]]]}, 
                                      {'scale': [[0.001, 0.001, 0.001], [0.0002, 0.0002, 0.0002]], 
                                       'bs':[[1, 1, 1], [0, 1, 1]], 
                                       'num_knots': [[5, 10, 12], [6, 11, 13]], 
                                       'gam_columns': [[["c_0"], ["c_1", "c_2"], ["c_3", "c_4", "c_5"]], 
                                                       [["c_1"], ["c_2", "c_3"], ["c_4", "c_5", "c_6"]]]}]}
    manual_gam_models = []
    num_grid_models = 0
    num_expected_models = 64
    manual_model_count = 0

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the data sets and set the training set indices and response column index
        """
        self.h2o_data = \
            h2o.import_file(path = pyunit_utils.locate("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv"))
        self.h2o_data['response'] = self.h2o_data['response'].asfactor()
        self.h2o_data['C3'] = self.h2o_data['C3'].asfactor()
        self.h2o_data['C7'] = self.h2o_data['C7'].asfactor()
        self.h2o_data['C8'] = self.h2o_data['C8'].asfactor()
        self.h2o_data['C10'] = self.h2o_data['C10'].asfactor()
        names = self.h2o_data.names
        self.myY = "response"
        self.myX = names.remove(self.myY)

        for lambda_ in self.hyper_parameters["lambda"]:
            for subspace in self.hyper_parameters["subspaces"]:
                for scale in subspace['scale']:
                    for gam_columns in subspace['gam_columns']:
                        for num_knots in subspace['num_knots']:
                            for bsVal in subspace['bs']:
                                self.manual_model_count += 1
                                self.manual_gam_models.append(H2OGeneralizedAdditiveEstimator(family="binomial", 
                                                                                              gam_columns=gam_columns,
                                                                                              scale=scale,
                                                                                              num_knots=num_knots,
                                                                                              bs=bsVal,
                                                                                              lambda_=lambda_
                                                                                          ))

    def train_models(self):
        self.h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(family="binomial",
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
