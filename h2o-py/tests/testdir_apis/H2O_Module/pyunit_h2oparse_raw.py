from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2oparse_raw():
    """
    Python API test: h2o.parse_raw(setup, id=None, first_line_is_header=0)

    copied from pyunit_hexdev_29_parse_false.py
    """
    fraw = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"), parse=False)
    assert isinstance(fraw, list)

    fhex = h2o.parse_raw(h2o.parse_setup(fraw), id='hexdev_29.hex', first_line_is_header=0)
    fhex.summary()
    assert_is_type(fhex, H2OFrame)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oparse_raw)
else:
    h2oparse_raw()
