import sys, os

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator


def test_GAM_monotone_splines_sine():
    train_file = pyunit_utils.locate("smalldata/gam_test/monotonic_sine.csv")
    test_file = pyunit_utils.locate("smalldata/gam_test/notQuite_monotone_sine.csv")
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [3], [2], [1])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [4], [2], [3])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [5], [2], [4])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [5], [2], [5])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [4], [2], [6])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [3], [2], [7])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [4], [2], [8])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [3], [2], [9])
    build_assert_monotone_output(train_file, test_file, [], 'Y', ['X'], [5], [2], [10])
    
    
def build_assert_monotone_output(train_file, test_file, x, target, gam_columns, num_knot, bs_choice, spline_order):
    train_data = h2o.import_file(train_file)
    test_data = h2o.import_file(test_file)
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='gaussian',
                                                 gam_columns=gam_columns,
                                                 num_knots=num_knot,
                                                 spline_orders=spline_order,
                                                 splines_non_negative=[True],
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
