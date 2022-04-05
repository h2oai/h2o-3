from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# test to find out why maxr is slow
def test_glm_backward_compare():
    tst_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/backwardBinomial200C50KRows.csv"))
    predictors = tst_data.columns[0:-1]
    response_col = 'response'
    weight = 'wt'
    tst_data['wt']=1
    tst_data[tst_data['response']==1,'wt'] = 100
    tst_data['response']=tst_data['response'].asfactor()
    min_predictor_num = 200
    backward_model = H2OModelSelectionEstimator(family = 'binomial', weights_column = weight, mode='backward',
                                                min_predictor_number=min_predictor_num)
    backward_model.train(predictors, response_col, training_frame=tst_data)
    backward_model_coeff = backward_model.coef()[0]
    glm_model = H2OGeneralizedLinearEstimator(family  = 'binomial',
                                          lambda_ = 0,
                                          compute_p_values = True,
                                          weights_column = weight)
    glm_model.train(predictors, response_col, training_frame=tst_data)
    glm_coeff = glm_model.coef()
    pyunit_utils.assertEqualCoeffDicts(glm_coeff, backward_model_coeff, tol = 1e-6)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_backward_compare)
else:
    test_glm_backward_compare()
