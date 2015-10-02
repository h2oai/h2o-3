import urllib2
import sys, os
sys.path.insert(1, "..")
import h2o
from tests import utils

_H2O_IP_            = "127.0.0.1"
_H2O_PORT_          = 54321
_ON_JENKINS_HADOOP_ = False
_IPYNB_             = None

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
    global _IPYNB_
    parse_args(sys_args)
    h2o.init(ip=_H2O_IP_, port=_H2O_PORT_, strict_version_check=False)
    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("STARTING TEST: "+str(h2o.ou()))
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")
    # num_keys = h2o.store_size()
    try:
        if _IPYNB_: utils.ipy_notebook_exec(_IPYNB_, save_and_norun=False)
        else: test_to_run()
    finally:
        h2o.remove_all()
        # if h2o.keys_leaked(num_keys): print "Leaked Keys!"

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

def locate(path):
    """
    Search for a relative path and turn it into an absolute path.
    This is handy when hunting for data files to be passed into h2o and used by import file.
    Note: This function is for unit testing purposes only.

    Parameters
    ----------
    path : str
      Path to search for

    :return: Absolute path if it is found.  None otherwise.
    """
    global _ON_JENKINS_HADOOP_
    if (_ON_JENKINS_HADOOP_):
        # HACK: jenkins jobs create symbolic links to smalldata and bigdata on the machine that starts the test. However,
        # in a h2o-hadoop cluster scenario, the other machines don't have this link. We need to reference the actual path,
        # which is /home/0xdiag/ on ALL jenkins machines. If _ON_JENKINS_HADOOP_ is set by the run.py, path MUST be
        # relative to /home/0xdiag/
        return os.path.join("/home/0xdiag/",path)
    else:
        tmp_dir = os.path.realpath(os.getcwd())
        possible_result = os.path.join(tmp_dir, path)
        while (True):
            if (os.path.exists(possible_result)):
                return possible_result

            next_tmp_dir = os.path.dirname(tmp_dir)
            if (next_tmp_dir == tmp_dir):
                raise ValueError("File not found: " + path)

            tmp_dir = next_tmp_dir
            possible_result = os.path.join(tmp_dir, path)

def parse_args(args):
    global _H2O_IP_
    global _H2O_PORT_
    global _ON_JENKINS_HADOOP_
    global _IPYNB_
    i = 1
    while (i < len(args)):
        s = args[i]
        if ( s == "--usecloud" or s == "--uc" ):
            i = i + 1
            if (i > len(args)):
                usage()
            argsplit   = args[i].split(":")
            _H2O_IP_   = argsplit[0]
            _H2O_PORT_ = int(argsplit[1])
        elif (s == "--onJenkHadoop"):
            _ON_JENKINS_HADOOP_ = True
        elif (s == "--ipynb"):
            i = i + 1
            if (i > len(args)):
                usage()
            _IPYNB_ = args[i]
        else:
            unknownArg(s)
        i = i + 1

def usage():
    print("")
    print("Usage for:  python pyunit.py [...options...]")
    print("")
    print("    --usecloud       connect to h2o on specified ip and port, where ip and port are specified as follows:")
    print("                     IP:PORT")
    print("")
    print("    --onJenkHadoop   signal to runt that it will be run on h2o-hadoop cluster.")
    print("")
    print("    --ipynb          name of the ipython notebook.")
    print("")
    sys.exit(1) #exit with nonzero exit code

def unknownArg(arg):
    print("")
    print("ERROR: Unknown argument: " + arg)
    print("")
    usage()
