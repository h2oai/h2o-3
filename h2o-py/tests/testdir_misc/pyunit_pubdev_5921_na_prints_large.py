import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import math


def partial_plot_test():
    # Import data set that contains NAs

    data = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrainWgt.csv"), na_strings=["NA"])
    test = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrainWgt.csv"), na_strings=["NA"])
    x = data.names
    y = "IsDepDelayed"
    data[y] = data[y]
    x.remove(y)
    x.remove("Weight")
    x.remove("IsDepDelayed_REC")
    WC = "Weight"

    # Build a GBM model predicting for response CAPSULE
    gbm_model = H2OGradientBoostingEstimator(ntrees=80, learn_rate=0.1, seed=12345)
    gbm_model.train(x=x, y=y, training_frame=data)

    # pdp with weight and no NA
    pdpw = gbm_model.partial_plot(data=test, cols=["Input_miss", "Distance"], server=True, plot=False,
                                  weight_column=WC)

    # pdp with weight and NA
    pdpwNA = gbm_model.partial_plot(data=test, cols=["Input_miss", "Distance"], server=True, plot=False,
                                    weight_column=WC, include_na = True)
    input_miss_list = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpwNA[0], "input_miss")
    assert math.isnan(input_miss_list[-1]), "Expected last element to be nan but is not."
    distance_list = pyunit_utils.extract_col_value_H2OTwoDimTable(pdpwNA[1], "distance")
    assert math.isnan(distance_list[-1]), "Expected last element to be nan but is not."
    # compare pdpw with pdpwNA, they should equal upto NA since the pdpw does not have NAs.
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpw[0], pdpwNA[0], pdpw[0].col_header, tolerance=1e-10)
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpw[1], pdpwNA[1], pdpw[1].col_header, tolerance=1e-10)

    # compare pdpwNA with theoretical results
    pyunit_utils.compare_weightedStats(gbm_model, test, input_miss_list, "Input_miss",
                                       test[WC].as_data_frame(use_pandas=False, header=False), pdpwNA[0], tol=1e-10)
    pyunit_utils.compare_weightedStats(gbm_model, test, distance_list, "Distance",
                                       test[WC].as_data_frame(use_pandas=False, header=False), pdpwNA[1], tol=1e-10)

if __name__ == "__main__":
  pyunit_utils.standalone_test(partial_plot_test)
else:
  partial_plot_test()