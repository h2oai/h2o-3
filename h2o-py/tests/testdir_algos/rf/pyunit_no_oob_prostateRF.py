from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator



def build_decision_tree():
  prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
  prostate_train = prostate_train.drop("ID")

  decision_tree = H2ORandomForestEstimator(ntrees=1, max_depth=3, sample_rate=1.0, mtries=len(prostate_train.columns) - 1)
  decision_tree.train(y="CAPSULE", training_frame=prostate_train)
  decision_tree.show()


if __name__ == "__main__":
  pyunit_utils.standalone_test(build_decision_tree)
else:
  build_decision_tree()
