import os
import sys
import tempfile

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils


def test_single_entry_arff_file():
    with open(pyunit_utils.locate("smalldata/junit/arff/iris.arff"), "r") as input_:
        arff = input_.readlines()
    data_start = arff.index("@DATA\n")
    subsample = arff[:data_start + 2]
    print(subsample[-3:])
    _fd, tmp = tempfile.mkstemp(".arff")
    try:
        with open(tmp, "w") as output:
            output.write(''.join(subsample))

        train = h2o.import_file(pyunit_utils.locate("smalldata/junit/arff/iris.arff"))
        test = h2o.import_file(tmp)

        print(f"{train.columns} == {test.columns}: {train.columns == test.columns}")
        assert train.columns == test.columns
    finally:
        os.unlink(tmp)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_single_entry_arff_file)
else:
    test_single_entry_arff_file()
