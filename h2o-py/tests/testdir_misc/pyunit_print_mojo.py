from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils
import json

RESULTS_DIR = pyunit_utils.locate("results")


def test_print_mojo():
    prostate_train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    
    ntrees = 20
    learning_rate = 0.1
    depth = 5
    min_rows = 10
    gbm_h2o = H2OGradientBoostingEstimator(
        ntrees=ntrees, learn_rate=learning_rate, max_depth=depth,
        min_rows=min_rows, distribution="bernoulli"
    )
    gbm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
    mojo_path = gbm_h2o.download_mojo(RESULTS_DIR)
    
    # print all
    mojo_str = h2o.print_mojo(mojo_path)
    mojo_dict = json.loads(mojo_str)
    assert "trees" in mojo_dict.keys()
    assert ntrees == len(mojo_dict["trees"])

    # print one tree to dot
    mojo_str = h2o.print_mojo(mojo_path, tree_index=2, format="dot")
    assert "Level 0" in mojo_str


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_print_mojo)
else:
  test_print_mojo()
