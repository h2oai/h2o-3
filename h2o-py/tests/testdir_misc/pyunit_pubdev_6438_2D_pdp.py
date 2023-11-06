import sys
import os
sys.path.insert(1,"../../")
import h2o
import tempfile
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

'''
This test is used to make sure users can specify 1d pdp and/or 2d pdps.  The following will be done:
1. Build a 1D pdp;
2. Build a 2D pdp with the same 1D pdp as in 1.  The 1D pdp results should be the same.
3. Build a 2D pdp with the same setting as in 2 with no 1D pdp.  The 2D results should be the same.

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

    file, filename = tempfile.mkstemp(suffix=".png")
    user_splits = dict()
    user_splits['AGE'] = [43.0, 44.89473684210526, 46.78947368421053, 48.68421052631579, 50.578947368421055,
                          52.473684210526315, 54.368421052631575, 56.26315789473684, 58.1578947368421,
                          60.05263157894737, 61.94736842105263, 63.84210526315789, 65.73684210526315,
                          67.63157894736842, 69.52631578947368, 71.42105263157895, 73.3157894736842,
                          75.21052631578948, 77.10526315789474]
    user_splits['RACE'] = ["Black", "White"]
    pdpUserSplit2D = gbm_model.partial_plot(frame=data,server=True, plot=True, user_splits=user_splits, 
                                            col_pairs_2dpdp=[['AGE', 'PSA'], ['AGE', 'RACE']], save_to_file=filename)  
    pdpUserSplit1D2D = gbm_model.partial_plot(frame=data, cols=['AGE', 'RACE', 'DCAPS'], server=True, plot=True, 
                                              user_splits=user_splits, 
                                              col_pairs_2dpdp=[['AGE', 'PSA'], ['AGE', 'RACE']], save_to_file=filename)
    pdpUserSplit1D = gbm_model.partial_plot(frame=data,cols=['AGE', 'RACE', 'DCAPS'], server=True, plot=True, 
                                            user_splits=user_splits, save_to_file=filename)
    if os.path.isfile(filename):
        os.remove(filename)
        # compare results 1D pdp
    for i in range(3):
        pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpUserSplit1D[i], pdpUserSplit1D2D[i], 
                                                      pdpUserSplit1D[i].col_header, tolerance=1e-10)
    # compare results 2D pdp 
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpUserSplit2D[0], pdpUserSplit1D2D[3],
                                                      pdpUserSplit2D[0].col_header, tolerance=1e-10)
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdpUserSplit2D[1], pdpUserSplit1D2D[4],
                                                  pdpUserSplit2D[1].col_header, tolerance=1e-10)
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(partial_plot_test_with_user_splits)
else:
    partial_plot_test_with_user_splits()
