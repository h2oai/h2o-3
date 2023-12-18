import sys
sys.path.insert(1,"../../")
import h2o
import math
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def partial_plot_test():
    kwargs = dict()
    kwargs['server'] = True

    # Import data set
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))

    # Build a GBM model predicting for response CAPSULE
    x = ['AGE', 'RACE']
    y = 'CAPSULE'
    data[y] = data[y].asfactor()
    data['RACE'] = data['RACE'].asfactor()

    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05)
    gbm_model.train(x=x, y=y, training_frame=data)

    # Plot Partial Dependence for one feature then for both
    pdp1 = gbm_model.partial_plot(data=data, cols=['AGE'], server=True, plot=True)
    # Manual test
    h2o_mean_response_pdp1 = pdp1[0]["mean_response"]
    h2o_stddev_response_pdp1 = pdp1[0]["stddev_response"]
    h2o_std_error_mean_response_pdp1 = pdp1[0]["std_error_mean_response"]
    pdp_manual = partial_dependence(gbm_model, data, "AGE", pdp1, 0)

    assert h2o_mean_response_pdp1 == pdp_manual[0]
    assert h2o_stddev_response_pdp1 == pdp_manual[1]
    assert h2o_std_error_mean_response_pdp1 == pdp_manual[2]

    pdp2=gbm_model.partial_plot(data=data, cols=['AGE', 'RACE'], server=True, plot=False)
    # Manual test
    h2o_mean_response_pdp2 = pdp2[0]["mean_response"]
    h2o_stddev_response_pdp2 = pdp2[0]["stddev_response"]
    h2o_std_error_mean_response_pdp2 = pdp2[0]["std_error_mean_response"]
    pdp_manual = partial_dependence(gbm_model, data, "AGE", pdp2, 0)

    assert h2o_mean_response_pdp2 == pdp_manual[0]
    assert h2o_stddev_response_pdp2 == pdp_manual[1]
    assert h2o_std_error_mean_response_pdp2 == pdp_manual[2]

    # Manual test
    h2o_mean_response_pdp2_race = pdp2[1]["mean_response"]
    h2o_stddev_response_pdp2_race = pdp2[1]["stddev_response"]
    h2o_std_error_mean_response_pdp2_race = pdp2[1]["std_error_mean_response"]
    pdp_manual = partial_dependence(gbm_model, data, "RACE", pdp2, 1)

    assert h2o_mean_response_pdp2_race == pdp_manual[0]
    assert h2o_stddev_response_pdp2_race == pdp_manual[1]
    assert h2o_std_error_mean_response_pdp2_race == pdp_manual[2]

    # Plot Partial Dependence for one row 
    pdp_row = gbm_model.partial_plot(data=data, cols=['AGE'], server=True, plot=True, row_index=1)
    # Manual test
    h2o_mean_response_pdp_row = pdp_row[0]["mean_response"]
    h2o_stddev_response_pdp_row = pdp_row[0]["stddev_response"]
    h2o_std_error_mean_response_pdp_row = pdp_row[0]["std_error_mean_response"]
    pdp_row_manual = partial_dependence(gbm_model, data[1, :], "AGE", pdp_row, 0)

    assert h2o_mean_response_pdp_row == pdp_row_manual[0]
    assert h2o_stddev_response_pdp_row == pdp_row_manual[1]
    assert h2o_std_error_mean_response_pdp_row == pdp_row_manual[2]

    
def partial_dependence(object, pred_data, xname, h2o_pp, pdp_name_idx):
    x_pt = h2o_pp[pdp_name_idx][xname.lower()]  # Needs to be lower case here as the PDP response sets everything to lower
    y_pt = list(range(len(x_pt)))
    y_sd = list(range(len(x_pt)))
    y_sem = list(range(len(x_pt)))

    for i in range(len(x_pt)):
        x_data = pred_data
        x_data[xname] = x_pt[i]
        pred = object.predict(x_data)["p1"]
        y_pt[i] = pred.mean()[0,0]
        y_sd[i] = pred.sd()[0]
        y_sem[i] = y_sd[i]/math.sqrt(x_data.nrows)

    return y_pt, y_sd, y_sem

if __name__ == "__main__":
  pyunit_utils.standalone_test(partial_plot_test)
else:
  partial_plot_test()
