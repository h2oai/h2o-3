from __future__ import print_function
import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from h2o.assembly import *
from h2o.utils.typechecks import assert_is_type

def h2oassembly_divide():
    """
    Python API test: test all H2OAssembly static methods and they are:
    H2OAssembly.divide(frame1, frame2)
    H2OAssembly.plus(frame1, frame2)
    H2OAssembly.multiply(frame1, frame2)
    H2OAssembly.minus(frame1, frame2)
    H2OAssembly.less_than(frame1, frame2)
    H2OAssembly.less_than_equal(frame1, frame2)
    H2OAssembly.equal_equal(frame1, frame2)
    H2OAssembly.not_equal(frame1, frame2)
    H2OAssembly.greater_than(frame1, frame2)
    H2OAssembly.greater_than_equal(frame1, frame2)
    """
    python_list1 = [[4,4,4,4],[4,4,4,4]]
    python_list2 = [[2,2,2,2], [2,2,2,2]]
    frame1 = h2o.H2OFrame(python_obj=python_list1)
    frame2 = h2o.H2OFrame(python_obj=python_list2)

    verify_results(H2OAssembly.divide(frame1, frame2), 2, "H2OAssembly.divide()")   #  test H2OAssembly.divide()
    verify_results(H2OAssembly.plus(frame1, frame2), 6, "H2OAssembly.plus()")       #  test H2OAssembly.plus()
    verify_results(H2OAssembly.multiply(frame1, frame2), 8, "H2OAssembly.multiply()")   #  test H2OAssembly.multiply()
    verify_results(H2OAssembly.minus(frame1, frame2), 2, "H2OAssembly.minus()")     #  test H2OAssembly.minus()
        # test H2OAssembly.less_than()
    verify_results(H2OAssembly.less_than(frame2, frame1), 1.0, "H2OAssembly.less_than()")
    verify_results(H2OAssembly.less_than(frame2, frame2), 0.0, "H2OAssembly.less_than()")
        # test H2OAssembly.less_than_equal()
    verify_results(H2OAssembly.less_than_equal(frame2, frame1), 1.0, "H2OAssembly.less_than_equal()")
    verify_results(H2OAssembly.less_than_equal(frame2, frame2), 1.0, "H2OAssembly.less_than_equal()")
        # test H2OAssembly.equal_equal()
    verify_results(H2OAssembly.equal_equal(frame2, frame1), 0.0, "H2OAssembly.equal_equal()")
    verify_results(H2OAssembly.equal_equal(frame2, frame2), 1.0, "H2OAssembly.equal_equal()")
        # test H2OAssembly.not_equal()
    verify_results(H2OAssembly.not_equal(frame2, frame1), 1.0, "H2OAssembly.not_equal()")
    verify_results(H2OAssembly.not_equal(frame2, frame2), 0.0, "H2OAssembly.not_equal()")
        # test H2OAssembly.greater_than()
    verify_results(H2OAssembly.greater_than(frame1, frame2), 1.0, "H2OAssembly.greater_than()")
    verify_results(H2OAssembly.greater_than(frame2, frame2), 0.0, "H2OAssembly.greater_than()")
    # test H2OAssembly.greater_than_equal()
    verify_results(H2OAssembly.greater_than_equal(frame1, frame2), 1.0, "H2OAssembly.greater_than_equal()")
    verify_results(H2OAssembly.greater_than_equal(frame2, frame2), 1.0, "H2OAssembly.greater_than_equal()")


def verify_results(resultFrame, matchValue, commandName):
    assert_is_type(resultFrame, H2OFrame)
    assert (resultFrame==matchValue).all(), commandName+" command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassembly_divide)
else:
    h2oassembly_divide()
