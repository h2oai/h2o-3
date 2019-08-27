import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import os

'''
Copied example from Honza to fix bug in 2D pdp.  Basically, when no figure is plotted, this bug will thrown an error.
Hence, this code just needs to run to completion.  No assertion check is necessary for this test.
'''
def partial_plot_test_with_user_splits():
    train = h2o.import_file(pyunit_utils.locate('smalldata/flow_examples/abalone.csv.gz'))
    model = H2OGeneralizedLinearEstimator(training_frame=train)
    model.train(y="C9")
    fn = "pdp.png"
    model.partial_plot(train, col_pairs_2dpdp=[["C1", "C2"]], save_to_file=fn)

    if os.path.isfile(fn):
        os.remove(fn)
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(partial_plot_test_with_user_splits)
else:
    partial_plot_test_with_user_splits()
