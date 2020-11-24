from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.grid.grid_search import H2OGridSearch

# specify gam_columns as [x, [x,x]] or [[x],[x,x]] and they should both work and generate the same model
def test_randomdiscrete_gridsearch():
    h2o_data = h2o.import_file(path = pyunit_utils.locate("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv"))
    h2o_data['response'] = h2o_data['response'].asfactor()
    h2o_data['C3'] = h2o_data['C3'].asfactor()
    h2o_data['C7'] = h2o_data['C7'].asfactor()
    h2o_data['C8'] = h2o_data['C8'].asfactor()
    h2o_data['C10'] = h2o_data['C10'].asfactor()
    names = h2o_data.names
    myY = "response"
    myX = names.remove(myY)
    search_criteria = {'strategy': 'RandomDiscrete', "seed": 1}
    hyper_parameters = {'lambda': [1, 2],
                        'subspaces': [{'scale': [[0.001], [0.0002]], 'num_knots': [[5], [10]], 'bs':[[1], [0]], 
                                       'gam_columns': [[["c_0"]], [["c_1"]]]},
                                      {'scale': [[0.001, 0.001, 0.001], [0.0002, 0.0002, 0.0002]], 
                                       'bs':[[1, 1, 1], [0, 1, 1]], 
                                       'num_knots': [[5, 10, 12], [6, 11, 13]], 
                                       'gam_columns': [[["c_0"], ["c_1", "c_2"], ["c_3", "c_4", "c_5"]],
                                                   [["c_1"], ["c_2", "c_3"], ["c_4", "c_5", "c_6"]]]}]}
    hyper_parameters2 = {'lambda': [1, 2],
                        'subspaces': [{'scale': [[0.001], [0.0002]], 'num_knots': [[5], [10]], 'bs':[[1], [0]],
                                       'gam_columns': [[["c_0"]], [["c_1"]]]},
                                      {'scale': [[0.001, 0.001, 0.001], [0.0002, 0.0002, 0.0002]],
                                       'bs':[[1, 1, 1], [0, 1, 1]],
                                       'num_knots': [[5, 10, 12], [6, 11, 13]],
                                       'gam_columns': [["c_0", ["c_1", "c_2"], ["c_3", "c_4", "c_5"]],
                                                       ["c_1", ["c_2", "c_3"], ["c_4", "c_5", "c_6"]]]}]}
    h2o_model = H2OGridSearch(H2OGeneralizedAdditiveEstimator(family="binomial", keep_gam_cols=True), 
                              hyper_params=hyper_parameters, search_criteria=search_criteria)
    h2o_model.train(x = myX, y = myY, training_frame = h2o_data)
    h2o_model2 = H2OGridSearch(H2OGeneralizedAdditiveEstimator(family="binomial", keep_gam_cols=True),
                              hyper_params=hyper_parameters2, search_criteria=search_criteria)
    h2o_model2.train(x = myX, y = myY, training_frame = h2o_data)
    # compare two models by checking their coefficients.  They should be the same
    for index in range(0, len(h2o_model)):
        model1 = h2o_model[index]
        model2 = h2o_model2[index]
        pyunit_utils.assertEqualCoeffDicts(model1.coef(), model2.coef(), tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_randomdiscrete_gridsearch)
else:
    test_randomdiscrete_gridsearch()
