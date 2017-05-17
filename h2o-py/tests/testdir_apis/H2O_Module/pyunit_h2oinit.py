from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.exceptions import H2OConnectionError

def h2oinit():
    """
    Python API test: h2o.init(url=None, ip=None, port=None, https=None, insecure=None, username=None, password=None,
     cookies=None, proxy=None, start_h2o=True, nthreads=-1, ice_root=None, enable_assertions=True,
      max_mem_size=None, min_mem_size=None, strict_version_check=None, **kwargs)
    """
    start_h2o = False
    strict_version_check = False

    print("Testing h2o.init() command...")
    try:
        h2o.init(start_h2o=start_h2o)
        print("h2o.init() command works!")
    except Exception as e:  # some errors are okay like version mismatch
        print("error message type is {0} and the error message is \n".format(e.__class__.__name__, e.args[0]))
        assert_is_type(e, H2OConnectionError)

    try:
        h2o.init(strict_version_check=strict_version_check, start_h2o=start_h2o)
    except Exception as e:
        print("error message type is {0} and the error message is \n".format(e.__class__.__name__, e.args[0]))
        assert_is_type(e, H2OConnectionError)

    # try to join a cluster and test out various command arguments
    ipS = "127.16.2.27"
    portS = "54321"
    nthread = 2
    max_mem_size=10
    min_mem_size=3

    try:
        h2o.init(ip=ipS, port=portS, nthreads=nthread, max_mem_size=max_mem_size, min_mem_size=min_mem_size,
                 start_h2o=start_h2o, strict_version_check=strict_version_check)
        print("Command h2o.init(ip=ipS, port=portS, nthreads=nthread, max_mem_size=max_mem_size, "
              "min_mem_size=min_mem_size,start_h2o=start_h2o, strict_version_check=strict_version_check) works!")
    except Exception as e:  # make sure correct error message is received
        print("error message type is {0} and the error message is \n".format(e.__class__.__name__, e.args[0]))
        assert_is_type(e, H2OConnectionError)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oinit)
else:
    h2oinit()
