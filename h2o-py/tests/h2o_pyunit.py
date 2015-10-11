import urllib2
import sys, os
sys.path.insert(1, "..")
import h2o
from tests import utils

_H2O_IP_                      = "127.0.0.1"
_H2O_PORT_                    = 54321
_ON_HADOOP_                   = False
_HADOOP_NAMENODE_             = None
_IPYNB_                       = None

"""
Here is some testing infrastructure for running the pyunit tests in conjunction with run.py.

run.py issues an ip and port as a string:  "<ip>:<port>".
The expected value of sys_args[1] is "<ip>:<port>"

All tests MUST have the following structure:

import sys
sys.path.insert(1, "..")  # may vary depending on this test's position relative to h2o-py
import h2o, tests

def my_test():
  ...test filling...

if __name__ == "__main__":
  tests.run_test(sys.argv, my_test)

So each test must have an ip and port
"""

def run_test(sys_args, test_to_run):
    parse_args(sys_args)
    h2o.init(ip=get_test_ip(), port=get_test_port(), strict_version_check=False)
    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("STARTING TEST: "+str(h2o.ou()))
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")
    # num_keys = h2o.store_size()
    try:
        nb = get_ipynb()
        if nb: utils.ipy_notebook_exec(nb, save_and_norun=False)
        else: test_to_run()
    finally:
        h2o.remove_all()
        # if h2o.keys_leaked(num_keys): print "Leaked Keys!"

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
    if (test_is_on_hadoop()):
        # Jenkins jobs create symbolic links to smalldata and bigdata on the machine that starts the test. However,
        # in an h2o multinode hadoop cluster scenario, the clustered machines don't know about the symbolic link.
        # Consequently, `locate` needs to return the actual path to the data on the clustered machines. ALL jenkins
        # machines store smalldata and bigdata in /home/0xdiag/. If ON.HADOOP is set by the run.py, the path arg MUST
        # be an immediate subdirectory of /home/0xdiag/. Moreover, the only guaranteed subdirectories of /home/0xdiag/ are
        # smalldata and bigdata.
        p = os.path.realpath(os.path.join("/home/0xdiag/",path))
        if not os.path.exists(p): raise ValueError("File not found: " + path)
        return p
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
    global _ON_HADOOP_
    global _HADOOP_NAMENODE_
    global _IPYNB_
    i = 1
    while (i < len(args)):
        s = args[i]
        if ( s == "--usecloud" or s == "--uc" ):
            i = i + 1
            if (i > len(args)): usage()
            argsplit   = args[i].split(":")
            _H2O_IP_   = argsplit[0]
            _H2O_PORT_ = int(argsplit[1])
        elif (s == "--hadoopNamenode"):
            i = i + 1
            if (i > len(args)): usage()
            _HADOOP_NAMENODE_ = args[i]
        elif (s == "--onHadoop"):
            _ON_HADOOP_ = True
        elif (s == "--ipynb"):
            i = i + 1
            if (i > len(args)): usage()
            _IPYNB_ = args[i]
        else:
            unknownArg(s)
        i = i + 1

def usage():
    print("")
    print("Usage for:  python pyunit.py [...options...]")
    print("")
    print("    --usecloud        connect to h2o on specified ip and port, where ip and port are specified as follows:")
    print("                      IP:PORT")
    print("")
    print("    --onHadoop        Indication that tests will be run on h2o multinode hadoop clusters.")
    print("                      `locate` and `sandbox` pyunit test utilities use this indication in order to")
    print("                      behave properly. --hadoopNamenode must be specified if --onHadoop option is used.")
    print("    --hadoopNamenode  Specifies that the pyunit tests have access to this hadoop namenode.")
    print("                      `hadoop_namenode` pyunit test utility returns this value.")
    print("")
    print("    --ipynb           name of the ipython notebook.")
    print("")
    sys.exit(1) #exit with nonzero exit code

def unknownArg(arg):
    print("")
    print("ERROR: Unknown argument: " + arg)
    print("")
    usage()

# HDFS helpers
def hadoop_namenode():
    global _HADOOP_NAMENODE_
    return _HADOOP_NAMENODE_

def hadoop_namenode_is_accessible():
    url = "http://{0}:50070".format(hadoop_namenode())
    try:
        urllib2.urlopen(urllib2.Request(url))
        internal = True
    except:
        internal = False
    return internal

def test_is_on_hadoop():
    global _ON_HADOOP_
    return _ON_HADOOP_

def get_ipynb():
    global _IPYNB_
    return _IPYNB_

def get_test_ip():
    global _H2O_IP_
    return _H2O_IP_

def get_test_port():
    global _H2O_PORT_
    return _H2O_PORT_
