from __future__ import division
from __future__ import print_function
import sys, os

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator


def test_GAM_monotone_splines_sine():
    file_name = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gam_test/sine_2PI.csv"
    test_data = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gam_test/sine_2PI.csv"
    build_assert_monotone_output(file_name, test_data, [], 'Y', ['X'], [2], [5], [2]) # second order spline
    build_assert_monotone_output(file_name, test_data, [], 'Y', ['X'], [3], [6], [2]) # third order spline
    build_assert_monotone_output(file_name, test_data, [], 'Y', ['X'], [4], [7], [2]) # fourth order spline
    build_assert_monotone_output(file_name, test_data, [], 'Y', ['X'], [5], [8], [2]) # fourth order spline
    build_assert_monotone_output(file_name, test_data, [], 'Y', ['X'], [10], [10], [2]) # tenth order spline
    
def build_assert_monotone_output(file_name, test_name, x, target, gam_columns, spline_orders, num_knot, bs_choice):
    train_data = h2o.import_file(file_name)
    test_data = h2o.import_file(test_name)
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='gaussian',
                                                 gam_columns=gam_columns,
                                                 spline_orders=spline_orders,
                                                 num_knots=num_knot,
                                                 bs=bs_choice)
    h2o_model2.train(x=x, y=target, training_frame=train_data)
    pred_frame = h2o_model2.predict(test_data)  
    assertMonotonePrediction(pred_frame)

def assertMonotonePrediction(pred_frame):
    prediction = pred_frame[0].as_data_frame(use_pandas=True)
    num_row = pred_frame.nrow
    preds = prediction['predict']
    for ind in range(1, num_row):
        assert preds[ind]>=preds[ind], "prediction at row {0} is {1} and it should exceed prediction at row {2} with" \
                                       " value {3} but is not.".format(ind, preds[ind], ind-1, preds[ind-1])
   
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_GAM_monotone_splines_sine)
else:
    test_GAM_monotone_splines_sine()
