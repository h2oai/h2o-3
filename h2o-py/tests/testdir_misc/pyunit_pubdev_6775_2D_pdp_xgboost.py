import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator
import os

# Honza discovered that when no 1D-pdp is specified and with only 2D-pdp, the code crashed.  The reason is due
# to the fact that we tried to locate the correct nBin number from user-define-splits.  In this case, no user-defined
# splits are specified and hence the code crashed.  I have hence fixed this condition.  No assert statement is
# needed for this tests, I just need to make sure it ran to completion.
def partial_plot_test_with_no_user_splits_no_1DPDP():
    data = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate_cat_NA.csv'))
    x = data.names
    y = 'CAPSULE'
    x.remove(y)

    # Build a GBM model predicting for response CAPSULE
    gbm_model = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.05, seed=12345)
    gbm_model.train(x=x, y=y, training_frame=data)

    # pdp without weight or NA
    pdp2dOnly = gbm_model.partial_plot(frame=data, server=True, plot=False, 
        col_pairs_2dpdp=[['AGE', 'PSA'],['AGE', 'RACE']])
    pdp1D2D = gbm_model.partial_plot(frame=data, cols=['AGE', 'RACE', 'DCAPS'], server=True, plot=False,
                                              col_pairs_2dpdp=[['AGE', 'PSA'], ['AGE', 'RACE']])
    # compare results 2D pdp 
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdp2dOnly[0], pdp1D2D[3],
                                                  pdp2dOnly[0].col_header, tolerance=1e-10)
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(pdp2dOnly[1], pdp1D2D[4],
                                                  pdp2dOnly[1].col_header, tolerance=1e-10)
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(partial_plot_test_with_no_user_splits_no_1DPDP)
else:
    partial_plot_test_with_no_user_splits_no_1DPDP()
