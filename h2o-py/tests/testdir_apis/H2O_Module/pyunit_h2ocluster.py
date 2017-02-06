from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2ocluster():
    """
    Python API test: h2o.cluster()
    """

    try:
        h2o.cluster()
    except Exception as e:
        assert False, "h2o.cluster() command not is working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ocluster)
else:
    h2ocluster()
