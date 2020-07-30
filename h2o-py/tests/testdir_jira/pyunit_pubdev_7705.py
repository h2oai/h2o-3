import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def partial_plot_row_index():
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate_cat_NA.csv'))
    x = data.names
    y = 'CAPSULE'
    x.remove(y)

    # Build a GBM model predicting for response CAPSULE
    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05, seed=12345)
    gbm_model.train(x=x, y=y, training_frame=data)

    # Generate Partial Dependence for row index -1 and row index 0, they should differ
    pdp = gbm_model.partial_plot(data=data, cols=['RACE'], plot=False, plot_stddev=False, row_index=-1)
    pdp0 = gbm_model.partial_plot(data=data, cols=['RACE'], plot=False, plot_stddev=False, row_index=0)
    assert not(pyunit_utils.equal_two_arrays(pdp[0][1], pdp0[0][1], throw_error=False))


if __name__ == "__main__":
  pyunit_utils.standalone_test(partial_plot_row_index)
else:
  partial_plot_row_index()
