import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators import H2OGradientBoostingEstimator


def test_row_to_tree_assignment_output_on_large_dataset():
    fr = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/creditcardfraud/creditcardfraud.csv"))
    target = "Class"
    fr[target] = fr[target].asfactor()

    gbm = H2OGradientBoostingEstimator(ntrees=1000,
                                       sample_rate=0.6)
    gbm.train(y=target, training_frame=fr)

    row_to_tree_assignment = gbm.row_to_tree_assignment(fr)

    names_expected = ["row_id"]
    names_expected.extend([("tree_" + str(item)) for item in range(1, 1001)])

    assert_equals(names_expected, row_to_tree_assignment.names)
    assert_equals(fr.nrows, row_to_tree_assignment.nrows)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_row_to_tree_assignment_output_on_large_dataset)
else:
    test_row_to_tree_assignment_output_on_large_dataset()
