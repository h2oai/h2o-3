from __future__ import print_function
from builtins import str
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2orapids():
    """
    Python API test: h2o.rapids(expr)
    """
    rapidTime = h2o.rapids("(getTimeZone)")["string"]
    print(str(rapidTime))

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2orapids)
else:
    h2orapids()
