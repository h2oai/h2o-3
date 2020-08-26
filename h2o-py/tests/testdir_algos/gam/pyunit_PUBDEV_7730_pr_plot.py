from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import os

# In this test, we check and make sure that we can plot precision recall for train/validation and when
# cross-validation is enabled.
def test_gam_pr_plot():
    print("Checking pr plot for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    enum_cols = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C21"]
    for cname in enum_cols:
        h2o_data[cname] = h2o_data[cname].asfactor()
    
    myY = "C21"
    splits = h2o_data.split_frame(ratios=[0.8])
    x=h2o_data.names
    x.remove(myY)
    h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["C11", "C12", "C13"],  scale = [1,1,1])
    h2o_model.train(x=["C1","C2"], y=myY, training_frame=splits[0], validation_frame=splits[1])
    fn = "pr_plot.png"
    h2o_model.pr_plot(train=True, valid=True, save_to_file=fn)
    if os.path.isfile(fn):
        os.remove(fn)

    (precision, recall) = h2o_model.pr_plot(train=True, valid=True, plot=False)
    assert len(precision)==len(recall), "Expected precision and recall to have the same shape but they are not."

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_pr_plot)
else:
    test_gam_pr_plot()
