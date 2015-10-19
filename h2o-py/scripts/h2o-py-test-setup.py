import sys, os
from time import gmtime, strftime

_H2O_IP_                      = "127.0.0.1"
_H2O_PORT_                    = 54321
_ON_HADOOP_                   = False
_HADOOP_NAMENODE_             = None
_IS_IPYNB_                    = False
_IS_PYDEMO_                   = False
_IS_PYUNIT_                   = False
_IS_PYBOOKLET_                = False
_RESULTS_DIR_                 = False
_TEST_NAME_                   = None

def parse_args(args):
    global _H2O_IP_
    global _H2O_PORT_
    global _ON_HADOOP_
    global _HADOOP_NAMENODE_
    global _IS_IPYNB_
    global _IS_PYDEMO_
    global _IS_PYUNIT_
    global _IS_PYBOOKLET_
    global _RESULTS_DIR_
    global _TEST_NAME_

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
            _IS_IPYNB_ = True
        elif (s == "--pyDemo"):
            _IS_PYDEMO_ = True
        elif (s == "--pyUnit"):
            _IS_PYUNIT_ = True
        elif (s == "--pyBooklet"):
            _IS_PYBOOKLET_ = True
        elif (s == "--resultsDir"):
            i = i + 1
            if (i > len(args)): usage()
            _RESULTS_DIR_ = args[i]
        elif (s == "--testName"):
            i = i + 1
            if (i > len(args)): usage()
            _TEST_NAME_ = args[i]
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
    print("    --ipynb           test is ipython notebook")
    print("")
    print("    --pyDemo          test is python demo")
    print("")
    print("    --pyUnit          test is python unit test")
    print("")
    print("    --pyBooklet       test is python booklet")
    print("")
    print("    --resultsDir      the results directory.")
    print("")
    print("    --testName        name of the pydemo, pyunit, or pybooklet.")
    print("")
    sys.exit(1) #exit with nonzero exit code

def unknownArg(arg):
    print("")
    print("ERROR: Unknown argument: " + arg)
    print("")
    usage()

def set_pyunit_pkg_attrs(pkg):
    setattr(pkg, '__on_hadoop__', _ON_HADOOP_)
    setattr(pkg, '__hadoop_namenode__', _HADOOP_NAMENODE_)

def set_pybooklet_pkg_attrs(pkg):
    setattr(pkg, '__test_name__', _TEST_NAME_)
    setattr(pkg, '__results_dir__', _RESULTS_DIR_)

def h2o_test_setup(sys_args):
    h2o_py_dir = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),".."))
    h2o_docs_dir = os.path.realpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),"..","..","h2o-docs"))

    parse_args(sys_args)

    sys.path.insert(1, h2o_py_dir)
    import h2o
    from tests import pyunit_utils, pydemo_utils, pybooklet_utils

    set_pyunit_pkg_attrs(pyunit_utils)
    set_pybooklet_pkg_attrs(pybooklet_utils)

    if _IS_PYUNIT_ or _IS_IPYNB_ or _IS_PYBOOKLET_:
        pass
    elif _IS_PYDEMO_:
        raise(NotImplementedError, "pydemos are not supported at this time")
    else:
        raise(EnvironmentError, "Unrecognized test type. Must be of type ipynb, pydemo, pyunit, or pybooklet, but got: "
                                "{0}".format(_TEST_NAME_))

    print "[{0}] {1}\n".format(strftime("%Y-%m-%d %H:%M:%S", gmtime()), "Connect to h2o on IP: {0} PORT: {1}"
                                                                        "".format(_H2O_IP_, _H2O_PORT_))
    h2o.init(ip=_H2O_IP_, port=_H2O_PORT_, strict_version_check=False)

    #rest_log = os.path.join(_RESULTS_DIR_, "rest.log")
    #h2o.start_logging(rest_log)
    #print "[{0}] {1}\n".format(strftime("%Y-%m-%d %H:%M:%S", gmtime()), "Started rest logging in: {0}".format(rest_log))

    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("STARTING TEST: " + _TEST_NAME_)
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")

    h2o.remove_all()

    if _IS_IPYNB_:       pydemo_utils.ipy_notebook_exec(_TEST_NAME_)
    elif _IS_PYUNIT_:    pyunit_utils.pyunit_exec(_TEST_NAME_, h2o_py_dir)
    elif _IS_PYBOOKLET_: pybooklet_utils.pybooklet_exec(_TEST_NAME_, h2o_py_dir)

if __name__ == "__main__":
    h2o_test_setup(sys.argv)