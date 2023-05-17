import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type


def h2olazy_import():
    """
    Python API test: h2o.lazy_import(path)
    """
    training_data = h2o.lazy_import(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    assert_is_type(training_data, list)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2olazy_import)
else:
    h2olazy_import()
