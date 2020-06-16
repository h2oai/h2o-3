from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch

class test_gam_gridsearch_specific:

    h2o_data = []
    myX = []
    myY = []
    hyper_parameters = {'alpha': [0.1, 0.9], 'lambda':[0, 0.01],
                        'scale':[[1, 1, 1], [2, 2, 2]], 'num_knots': [[5, 6, 7], [5, 5, 5]]}

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

    def train_models(self):
        h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(
            family = "gaussian", gam_columns = ["C11", "C12", "C13"],
            keep_gam_cols = True), self.hyper_parameters)
        h2o_model.train(x = self.myX, y = self.myY, training_frame = self.h2o_data)
        print("done")

def test_gridsearch_specific():
    test_gam_grid = test_gam_gridsearch_specific()
    test_gam_grid.train_models()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gridsearch_specific)
else:
    test_gridsearch_specific()
