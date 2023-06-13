import sys
import copy

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils


def colname_rename():
    print("Uploading iris data...")

    f1 = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    f2 = copy.copy(f1)

    t1 = f1.rename(columns={'C2': 'C1', 'C1': 'C2', 'C3': 'X3', 'F0': 'X0', 'C3': 'Y3'})
    e1 = ['C2', 'C1', 'Y3', 'C4', 'C5']
    assert t1.names == e1, "Expected the same column names but got {0} and {1}".format(t1.names, e1)

    t2 = f2.rename(columns={0: 'X1', 2: 'X3', -1: 'X2', 20: 'X20', 2: 'Y3'})
    e2 = ['X1', 'C2', 'Y3', 'C4', 'X2']
    assert t2.names == e2, "Expected the same column names but got {0} and {1}".format(t2.names, e2)


if __name__ == "__main__":
    pyunit_utils.standalone_test(colname_rename)
else:
    colname_rename()
