from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch


# In this test, we check to make sure that a grid search on a GAM with hyperparameters and subspaces fails.
# The grid search should fail because gam_columns is specified in both the hyper parameters and the constrained hyper parameters.
class test_gam_gridsearch_specific:
    h2o_data = []
    myX = []
    myY = []
    search_criteria = {'strategy': 'Cartesian'}
    hyper_parameters = {'gam_columns': [["C11", "C12", "C13"]],
                        'subspaces': [{'scale': [[1, 1, 1], [2, 2, 2]], 'gam_columns': [["C11", "C12", "C13"]]}]}
    h2o_model = []

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the data sets and set the training set indices and response column index
        """
        self.h2o_data = h2o.import_file(
            path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
        self.h2o_data["C1"] = self.h2o_data["C1"].asfactor()
        self.h2o_data["C2"] = self.h2o_data["C2"].asfactor()
        self.myX = ["C1", "C2"]
        self.myY = "C21"

    def train_models(self):
        try:
            self.h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(
                family="gaussian", keep_gam_cols=True), self.hyper_parameters, search_criteria=self.search_criteria)
            self.h2o_model.train(x=self.myX, y=self.myY, training_frame=self.h2o_data)
        except:
            print("Error was raised because gam_columns was specified in hyper parameters and constrained hyper parameters")
        else:
            raise Exception("No errors raised despite gam_columns being in hyper parameters and constrained hyper parameters")

def test_gridsearch_specific():
    test_gam_grid = test_gam_gridsearch_specific()
    test_gam_grid.train_models()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gridsearch_specific)
else:
    test_gridsearch_specific()
