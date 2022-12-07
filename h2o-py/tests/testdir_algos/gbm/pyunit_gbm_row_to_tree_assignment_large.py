from __future__ import print_function
import os
import sys
import time

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OGradientBoostingEstimator


def test_row_to_tree_assignment_output_on_large_dataset():
    fr = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/creditcardfraud/creditcardfraud.csv"))
    target = "Class"
    fr[target] = fr[target].asfactor()
    
    start = time.time()
    gbm = H2OGradientBoostingEstimator(ntrees=1000,
                                       sample_rate=0.6)
    gbm.train(y=target, training_frame=fr)
    end = time.time()
    time_original = end - start

    start = time.time()
    gbm = H2OGradientBoostingEstimator(ntrees=1000,
                                       sample_rate=0.6,
                                       enable_row_to_tree_assignment=True)
    gbm.train(y=target, training_frame=fr)
    end = time.time()
    time_with_assignment = end - start
    
    print("Original training took", time_original, "with assignments took", time_with_assignment)
    
    assert_equals(time_original, time_with_assignment, "Time of training with row_to_tree_assignments is too different", delta=0.2*time_original)  # should not be different more than 20% of original training time.
    
    row_to_tree_assignment_key = gbm._model_json["output"]["row_to_tree_assignment"]["name"]

    names_expected = ["row_id"]
    names_expected.extend([("tree_" + str(item)) for item in range(1, 1001)])

    row_to_tree_assignment = h2o.get_frame(row_to_tree_assignment_key)
    print(row_to_tree_assignment)
    assert_equals(names_expected, row_to_tree_assignment.names)
    assert_equals(fr.nrows, row_to_tree_assignment.nrows)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_row_to_tree_assignment_output_on_large_dataset)
else:
    test_row_to_tree_assignment_output_on_large_dataset()
