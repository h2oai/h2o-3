from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.xgboost import H2OXGBoostEstimator
from tests import pyunit_utils
import json

RESULTS_DIR = pyunit_utils.locate("results")

ALGOS = [
    H2OGradientBoostingEstimator, 
    H2OIsolationForestEstimator,
    H2ORandomForestEstimator,
    H2OXGBoostEstimator
]


def test_print_mojo():
    prostate_train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    
    ntrees = 20
    for algo in ALGOS:
        print("testing " + algo.__name__)
        model = algo(ntrees=ntrees)
        model.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
        mojo_path = model.download_mojo(RESULTS_DIR)
        
        # print all into JSON
        mojo_str = h2o.print_mojo(mojo_path)

        print("dumping " + algo.__name__ + " JSON trees")
        print("==BEGIN==")
        print(mojo_str)
        print("==/END==")

        mojo_dict = json.loads(mojo_str)
        assert "trees" in mojo_dict.keys()
        assert ntrees == len(mojo_dict["trees"])
    
        # print one tree to dot
        mojo_str = h2o.print_mojo(mojo_path, tree_index=2, format="dot")
        print("dumping " + algo.__name__ + " DOT tree")
        print("==BEGIN==")
        print(mojo_str)
        print("==/END==")

        assert "Level 0" in mojo_str


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_print_mojo)
else:
  test_print_mojo()
