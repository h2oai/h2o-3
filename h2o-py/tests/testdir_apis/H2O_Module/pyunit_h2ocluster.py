import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.backend.cluster import H2OCluster
from h2o.utils.typechecks import assert_is_type

def h2ocluster():
    """
    Python API test: h2o.cluster()
    """
    ret = h2o.cluster()
    assert_is_type(ret, H2OCluster)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ocluster)
else:
    h2ocluster()
