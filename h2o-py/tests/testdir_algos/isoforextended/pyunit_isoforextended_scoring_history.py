from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest_scoring_history():
    print("Extended Isolation Forest Scoring History Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=10, seed=0xBEEF, sample_size=255, extension_level=1,
                                                    score_each_iteration=True)
    eif_model.train(training_frame=train)

    print(eif_model.scoring_history())
    assert_equals(11, len(eif_model.scoring_history()), "There should be one empty row and one row for each tree")

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=10, seed=0xBEEF, sample_size=255, extension_level=1,
                                                    score_tree_interval=3)
    eif_model.train(training_frame=train)
    print(eif_model.scoring_history())
    assert_equals(5, len(eif_model.scoring_history()), "There should be one empty row and one row for each interval")


if __name__ == "__main__":
    pyunit_utils.standalone_test(extended_isolation_forest_scoring_history)
else:
    extended_isolation_forest_scoring_history()
