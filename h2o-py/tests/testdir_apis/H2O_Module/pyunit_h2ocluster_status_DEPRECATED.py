import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2ocluster_status():
    """
    Python API test: h2o.cluster_status()
    Deprecated, use h2o.cluster().show_status(True)
    """
    ret = h2o.cluster_status()    # no return type
    assert ret is None

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ocluster_status)
else:
    h2ocluster_status()
