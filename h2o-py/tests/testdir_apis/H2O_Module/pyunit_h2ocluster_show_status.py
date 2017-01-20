from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2ocluster_status():
    """
    Python API test: h2o.cluster().show_status(True) and h2o.cluster().show_status()

    """
    try:
        h2o.cluster().show_status(True)    # no return type
        h2o.cluster().show_status()
    except Exception as e:
        assert False, "h2o.cluster().show_status() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ocluster_status)
else:
    h2ocluster_status()
