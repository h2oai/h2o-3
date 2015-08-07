import sys, os
sys.path.insert(1, "../../")
import h2o
import random

def download_all_logs(ip,port):
    # Connect to h2o
    

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
    h2o.run_test(sys.argv, download_all_logs)
