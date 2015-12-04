from tests import pyunit_utils
import sys
sys.path.insert(1, "../../")
import h2o
import random

def pyunit_remove_vecs():
    # TODO PUBDEV-1789
    pros = h2o.import_frame(pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    rows, cols = pros.dim()

    remove = random.randint(1,5)
    p1 = pros.remove_vecs(cols=random.sample(range(cols),remove))
    new_rows, new_cols = p1.dim()
    assert new_rows == rows and new_cols == cols-remove, "Expected {0} rows and {1} columns, but got {2} rows and {3} " \
                                                         "columns.".format(rows,cols,new_rows,new_cols)

    remove = random.randint(1,5)
    p1 = pros.remove_vecs(cols=random.sample(pros.names(),remove))
    new_rows, new_cols = p1.dim()
    assert new_rows == rows and new_cols == cols-remove, "Expected {0} rows and {1} columns, but got {2} rows and {3} " \
                                                         "columns.".format(rows,cols,new_rows,new_cols)

if __name__ == "__main__":
	pyunit_utils.standalone_test(pyunit_remove_vecs)
else:
	pyunit_remove_vecs()
