import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def cluster_status_test():
    h2o.cluster_status()


if __name__ == "__main__":
    pyunit_utils.standalone_test(cluster_status_test)
else:
    cluster_status_test()
