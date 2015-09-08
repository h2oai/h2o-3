import urllib2
import sys
sys.path.insert(1, "..")
import h2o
from tests import utils

"""
Here is some testing infrastructure for running the pyunit tests in conjunction with run.py.

run.py issues an ip and port as a string:  "<ip>:<port>".
The expected value of sys_args[1] is "<ip>:<port>"

All tests MUST have the following structure:

import sys
sys.path.insert(1, "..")  # may vary depending on this test's position relative to h2o-py
import h2o, tests

def my_test(ip=None, port=None):
  ...test filling...

if __name__ == "__main__":
  tests.run_test(sys.argv, my_test)

So each test must have an ip and port
"""

def run_test(sys_args, test_to_run):
    # import pkg_resources
    # ver = pkg_resources.get_distribution("h2o").version
    # print "H2O PYTHON PACKAGE VERSION: " + str(ver)
    ip, port = sys_args[2].split(":")
    h2o.init(ip,port,strict_version_check=False)
    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("STARTING TEST: "+str(h2o.ou()))
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")
<<<<<<< HEAD
    # num_keys = h2o.store_size()
    try:
        if len(sys_args) > 3 and sys_args[3] == "--ipynb": utils.ipy_notebook_exec(sys_args[4],save_and_norun=False)
        else: test_to_run(ip, port)
    finally:
        h2o.remove_all()
        # if h2o.keys_leaked(num_keys): print "Leaked Keys!"
=======
    num_keys = h2o.store_size()
    try:
        if len(sys_args) > 3 and sys_args[3] == "--ipynb": utils.ipy_notebook_exec(sys_args[4],save_and_norun=False)
        else: test_to_run()
    finally:
        h2o.remove_all()
        if h2o.keys_leaked(num_keys): print "Leaked Keys!"
>>>>>>> master

# HDFS helpers
def get_h2o_internal_hdfs_name_node():
    return "172.16.2.176"

def is_running_internal_to_h2o():
    url = "http://{0}:50070".format(get_h2o_internal_hdfs_name_node())
    try:
        urllib2.urlopen(urllib2.Request(url))
        internal = True
    except:
        internal = False
    return internal
