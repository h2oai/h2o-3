from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest():
    print("Extended Isolation Forest Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    if_model = H2OExtendedIsolationForestEstimator(ntrees=7, seed=12, sample_size=5)
    if_model.train(training_frame=train)

    print(if_model)

if __name__ == "__main__":
    pyunit_utils.standalone_test(extended_isolation_forest)
else:
    extended_isolation_forest()
