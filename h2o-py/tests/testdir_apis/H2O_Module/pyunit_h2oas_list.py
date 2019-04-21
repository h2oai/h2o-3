from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type

def h2oas_list():
    """
    Python API test: h2o.as_list(data, use_pandas=True, header=True)
    Copied from pyunit_frame_as_list.py
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    res1 = h2o.as_list(iris, use_pandas=False)
    assert_is_type(res1, list)
    res1 = list(zip(*res1))
    assert abs(float(res1[0][9]) - 4.4) < 1e-10 and abs(float(res1[1][9]) - 2.9) < 1e-10 and \
       abs(float(res1[2][9]) - 1.4) < 1e-10, "incorrect values"


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oas_list)
else:
    h2oas_list()
