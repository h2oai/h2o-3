from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2olazy_import():
    """
    Python API test: h2o.lazy_import(path)
    """
    try:
        training_data = h2o.lazy_import(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    except Exception as e:
        assert False, "h2o.lazy_import() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2olazy_import)
else:
    h2olazy_import()
