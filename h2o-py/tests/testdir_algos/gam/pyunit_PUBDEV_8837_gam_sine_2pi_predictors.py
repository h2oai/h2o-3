from __future__ import division
from __future__ import print_function
import sys, os

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# If other predictors are involved, the prediction outputs are no longer guaranteed to be monotonic since the 
# predictor is not required to be monotonic.  To make sure the final output is monotonic, make sure you choose
# parameter non_negative=True
def test_GAM_monotone_splines_sine():
    file_name = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gam_test/sine_2PI.csv"
    test_data = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gam_test/sine_2PI.csv"
    train_data = h2o.import_file(pyunit_utils.locate(file_name))
    x = train_data.names[0:2]
    build_check_monotone_output(file_name, test_data, x, 'Y', ['X'], [10], [10], [2], False) # monotone output not guaranteed
    build_check_monotone_output(file_name, test_data, x, 'Y', ['X'], [10], [10], [2], True) # monotone output here
    
def build_check_monotone_output(file_name, test_name, x, target, gam_columns, spline_orders, num_knot, bs_choice, 
                                assertMonotone):
    train_data = h2o.import_file(file_name)
    test_data = h2o.import_file(test_name)
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='gaussian',
                                                 gam_columns=gam_columns,
                                                 spline_orders=spline_orders,
                                                 num_knots=num_knot,
                                                 non_negative=assertMonotone,
                                                 bs=bs_choice)
    h2o_model2.train(x=x, y=target, training_frame=train_data)
    pred_frame = h2o_model2.predict(test_data)  
    if assertMonotone:
        assertMonotoneF(pred_frame)
    else:
        checkMonotonePrediction(pred_frame)

def assertMonotoneF(pred_frame):
    prediction = pred_frame[0].as_data_frame(use_pandas=True)
    num_row = pred_frame.nrow
    preds = prediction['predict']
    for ind in range(1, num_row):
        assert preds[ind]>=preds[ind], "prediction at row {0} is {1}. Prediction at row {2} with value {3}. They are" \
                                       " not non-decreasing".format(ind, preds[ind], ind-1, preds[ind-1])
        
def checkMonotonePrediction(pred_frame):
    prediction = pred_frame[0].as_data_frame(use_pandas=True)
    num_row = pred_frame.nrow
    preds = prediction['predict']
    try:
        for ind in range(1, num_row):
            assert preds[ind]>=preds[ind], "prediction at row {0} is {1}. Prediction at row {2} with value {3}. They" \
                                           " are not non-decreasing".format(ind, preds[ind], ind-1, preds[ind-1])
    except Exception as ex:
        print("predictions are not expected to be monotone.")
        
   
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_GAM_monotone_splines_sine)
else:
    test_GAM_monotone_splines_sine()
