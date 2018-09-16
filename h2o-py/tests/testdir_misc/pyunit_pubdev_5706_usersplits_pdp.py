import sys
sys.path.insert(1,"../../")
import h2o
import math
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

'''
This test is used to make sure users can specify their own split points.  The following will be done:
1. build a pdp without any user split point with 3 columns, one numeric, two enum columns
2. build a pdp with user defined-split points for one numeric, and one enum column.  We will use the actual
features used by 1 but shorten it.

We compare results from 1 and 2 and they should agree up to the length of the shorter one.
'''
def partial_plot_test_with_user_splits():
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate_cat_NA.csv'))
    x = data.names
    y = 'CAPSULE'
    x.remove(y)

    # Build a GBM model predicting for response CAPSULE
    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05, seed=12345)
    gbm_model.train(x=x, y=y, training_frame=data)

    user_splits = dict()
    user_splits['AGE'] = [43.0, 44.89473684210526, 46.78947368421053, 48.68421052631579, 50.578947368421055,
                          52.473684210526315, 54.368421052631575, 56.26315789473684, 58.1578947368421,
                          60.05263157894737, 61.94736842105263, 63.84210526315789, 65.73684210526315,
                          67.63157894736842, 69.52631578947368, 71.42105263157895, 73.3157894736842,
                          75.21052631578948, 77.10526315789474]
    user_splits['RACE'] = ["Black"]
    # pdp without weight or NA
    pdpOrig = gbm_model.partial_plot(data=data,cols=['AGE', 'RACE', 'DCAPS'],server=True, plot=True)
    pdpUserSplit = gbm_model.partial_plot(data=data,cols=['AGE', 'RACE', 'DCAPS'],server=True, plot=True,
                                          user_splits=user_splits)

    # compare results
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpUserSplit[0], pdpOrig[0], pdpUserSplit[0].col_header, tolerance=1e-10)
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpUserSplit[1], pdpOrig[1], pdpUserSplit[1].col_header, tolerance=1e-10)
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpOrig[2], pdpUserSplit[2], pdpUserSplit[2].col_header, tolerance=1e-10)


if __name__ == "__main__":
  pyunit_utils.standalone_test(partial_plot_test_with_user_splits)
else:
  partial_plot_test_with_user_splits()