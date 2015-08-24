import sys
sys.path.insert(1, "../../")
import h2o, tests
import random

def pyunit_remove_vecs(ip,port):
    # TODO PUBDEV-1789
    pros = h2o.import_file(h2o.locate("smalldata/prostate/prostate.csv"))
    rows, cols = pros.dim

    remove = random.randint(1,5)
    p1 = pros.remove_vecs(cols=random.sample(range(cols),remove))
    new_rows, new_cols = p1.dim
    assert new_rows == rows and new_cols == cols-remove, "Expected {0} rows and {1} columns, but got {2} rows and {3} " \
                                                         "columns.".format(rows,cols,new_rows,new_cols)

    remove = random.randint(1,5)
    p1 = pros.remove_vecs(cols=random.sample(pros.names,remove))
    new_rows, new_cols = p1.dim
    assert new_rows == rows and new_cols == cols-remove, "Expected {0} rows and {1} columns, but got {2} rows and {3} " \
                                                         "columns.".format(rows,cols,new_rows,new_cols)

if __name__ == "__main__":
    tests.run_test(sys.argv, pyunit_remove_vecs)
