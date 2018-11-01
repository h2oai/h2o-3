from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.exceptions import H2OConnectionError, H2OServerError, H2OValueError
import tempfile
import shutil
import os

def h2oinit():
    """
    Python API test: h2o.init(url=None, ip=None, port=None, name = None, https=None, insecure=None,
    username=None, password=None, ookies=None, proxy=None, start_h2o=True, nthreads=-1, ice_root=None,
    enable_assertions=True, max_mem_size=None, min_mem_size=None, strict_version_check=None, **kwargs)
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

def h2oinitname():
    """
    Python API test for h2o.init
    :return:
    """
    try:
        h2o.init(strict_version_check=False, name="test")  # Should initialize
        h2o.init(strict_version_check=False, name="test")  # Should just connect
        assert h2o.cluster().cloud_name == "test"
    except H2OConnectionError as e:  # some errors are okay like version mismatch
        print("error message type is {0} and the error message is {1}\n".format(e.__class__.__name__, e.args[0]))

    try:
        h2o.init(strict_version_check=False, port=54321, name="test2", as_port=True)
        assert False, "Should fail to connect and the port should be used by previous invocation."
    except H2OServerError as e:
        print("error message type is {0} and the error message is {1}\n".format(e.__class__.__name__, e.args[0]))

    try:
        h2o.init(strict_version_check=False, port=54321, name="test2")  # Should bump the port to next one
        assert h2o.cluster().cloud_name == "test2"
    except H2OConnectionError as e:
        print("error message type is {0} and the error message is {1}\n".format(e.__class__.__name__, e.args[0]))

    try:
        h2o.init(strict_version_check=False, port=54327, name="test3", as_port=True)
        assert h2o.cluster().cloud_name == "test3"
    except H2OConnectionError as e:
        print("error message type is {0} and the error message is {1}\n".format(e.__class__.__name__, e.args[0]))
        assert_is_type(e, H2OConnectionError)
        h2o.cluster().shutdown()


def h2oinit_default_log_dir():
    tmpdir = tempfile.mkdtemp()
    try:
        h2o.init(strict_version_check=False, name="default_log", ice_root=tmpdir)
    except H2OConnectionError as e:  # some errors are okay like version mismatch
        print("error message type is {0} and the error message is {1}\n".format(e.__class__.__name__, e.args[0]))
    finally:
        assert os.path.exists(os.path.join(tmpdir, "h2ologs")) == True
        shutil.rmtree(tmpdir)
        h2o.cluster().shutdown()


def h2oinit_custom_log_dir():
    tmpdir = tempfile.mkdtemp()
    tmpdir_logs = tempfile.mkdtemp()
    try:
        h2o.init(strict_version_check=False, name="custom_log", ice_root=tmpdir, log_dir=tmpdir_logs)
    except H2OConnectionError as e:  # some errors are okay like version mismatch
        print("error message type is {0} and the error message is {1}\n".format(e.__class__.__name__, e.args[0]))
    finally:
        assert os.path.exists(os.path.join(tmpdir, "h2ologs")) == False
        assert any(".log" in log for log in os.listdir(tmpdir_logs))
        shutil.rmtree(tmpdir)
        shutil.rmtree(tmpdir_logs)
        h2o.cluster().shutdown()


def h2oinit_fail_invalid_log_level():
    try:
        h2o.init(strict_version_check=False, log_level="BAD_LOG_LEVEL")
        assert False, "Should fail to start an h2o instance with an invalid log level."
    except H2OConnectionError as e:  # some errors are okay like version mismatch
        assert False, "Should fail to start an h2o instance with an invalid log level but H2OConnectionError was thrown."
    except H2OValueError:
        print("H2OValueError properly thrown")
        return
    finally:
        h2o.cluster().shutdown()


# None of the tests below need a pre initialized instance
h2oinit_default_log_dir()
h2oinit_custom_log_dir()
h2oinit_fail_invalid_log_level()
h2oinitname()

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oinit)
else:
    h2oinit()
