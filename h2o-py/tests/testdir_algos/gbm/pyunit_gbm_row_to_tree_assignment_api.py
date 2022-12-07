from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OGradientBoostingEstimator


def test_row_to_tree_assignment_output():
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()
    
    gbm = H2OGradientBoostingEstimator(ntrees=3,
                                       sample_rate=0.6, 
                                       enable_row_to_tree_assignment=True,
                                       seed=95)
    gbm.train(y=target, training_frame=fr)
    
    row_to_tree_assignment_frame_id = gbm._model_json["output"]["row_to_tree_assignment"]["name"]
    
    print(h2o.ls())
    
    print()
    print("Frame id", row_to_tree_assignment_frame_id)
    print()
    
    # h2o.remove_all(retained=[fr.frame_id, row_to_tree_assignment_frame_id])
    
    print(h2o.ls())

    try:
        row_to_tree_assignment = h2o.get_frame(row_to_tree_assignment_frame_id)
        print(row_to_tree_assignment)
    except Exception as e:
        print(e)

    assert_equals(["row_id", "tree_1", "tree_2", "tree_3"], row_to_tree_assignment.names)
    assert_equals(fr.nrows, row_to_tree_assignment.nrows)
    assert False


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_row_to_tree_assignment_output)
else:
    test_row_to_tree_assignment_output()
