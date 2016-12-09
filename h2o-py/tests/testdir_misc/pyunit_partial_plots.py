import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def partial_plot_test():
    kwargs = {}
    kwargs['server'] = True

    # Import data set
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))

    # Build a GBM model predicting for response CAPSULE
    x = ['AGE', 'RACE']
    y = 'CAPSULE'
    data[y] = data[y].asfactor()

    gbm_model = H2OGradientBoostingEstimator(ntrees=50,
                                           learn_rate=0.05)
    gbm_model.train(x=x, y=y, training_frame=data)

    # Plot Partial Dependence for one feature then for both
    pdp1=gbm_model.partial_plot(data=data,cols=['AGE'],server=True, plot=True)
    pdp2=gbm_model.partial_plot(data=data,cols=['AGE','RACE'], server=True, plot=False)


if __name__ == "__main__":
  pyunit_utils.standalone_test(partial_plot_test)
else:
  partial_plot_test()