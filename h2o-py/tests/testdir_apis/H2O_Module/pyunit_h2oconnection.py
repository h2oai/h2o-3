from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.backend.connection import H2OConnection

def h2oconnection():
    """
    Python API test: h2o.connection()
    """
    # call with no arguments
    temp = h2o.connection()
    assert_is_type(temp, H2OConnection)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oconnection)
else:
    h2oconnection()
