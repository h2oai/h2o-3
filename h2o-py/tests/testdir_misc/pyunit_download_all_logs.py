import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

import os

def download_all_logs():
    
    

    # default
    log_location = h2o.download_all_logs()
    assert os.path.exists(log_location), "Expected h2o logs to be saved in {0}, but they weren't".format(log_location)
    os.remove(log_location)

    # dirname and filename
    log_location = h2o.download_all_logs(".","h2o_logs.txt")
    assert os.path.exists(log_location), "Expected h2o logs to be saved in {0}, but they weren't".format(log_location)
    os.remove(log_location)

    # dirname
    log_location = h2o.download_all_logs(dirname=".")
    assert os.path.exists(log_location), "Expected h2o logs to be saved in {0}, but they weren't".format(log_location)
    os.remove(log_location)

    # filename
    log_location = h2o.download_all_logs(filename="h2o_logs.txt")
    assert os.path.exists(log_location), "Expected h2o logs to be saved in {0}, but they weren't".format(log_location)
    os.remove(log_location)


if __name__ == "__main__":
    pyunit_utils.standalone_test(download_all_logs)
else:
    download_all_logs()
