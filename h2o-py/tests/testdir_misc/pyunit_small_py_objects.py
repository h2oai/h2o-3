import h2o
from tests import pyunit_utils


def pyunit_small_py_objects():

    d = h2o.H2OFrame([3.2])
    d.show()
    assert d.nrow == d.ncol == 1

    d = h2o.H2OFrame([[3.2], [3.2]])
    assert d.nrow == 2
    assert d.ncol == 1
    d.show()

    d = h2o.H2OFrame([3.2, 3.2])
    d.show()
    assert d.nrow == 2
    assert d.ncol == 1

if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_small_py_objects)
else:
    pyunit_small_py_objects()
