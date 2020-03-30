from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2ocluster_get_status():
    """
    Python API test: h2o.cluster().get_status(), h2o.cluster().get_status_details()
    """
    table = h2o.cluster().get_status()
    details = h2o.cluster().get_status_details()
    table.show()
    details.show()

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ocluster_get_status)
else:
    h2ocluster_get_status()
