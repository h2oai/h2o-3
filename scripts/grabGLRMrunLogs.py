#!/usr/bin/python

import sys
import os
import json
import pickle

import copy
import subprocess


"""
This script is written to grab the logs from our GLRM runs and saved them onto our local machines for
later analysis.  For me, this is how I will call this script and the input arguments I will use:

python grabGLRMrunLogs.py /Users/wendycwong/Documents/PUBDEV_3454_GLRM/experimentdata/glrm_memory_10_25_16
    http://mr-0xa1:8080/view/wendy/job/glrm_memory_performance/
    /Users/wendycwong/Documents/PUBDEV_3454_GLRM/experimentdata/glrm_memory_10_25_16
    java_0_0.out_airline.txt java_1_0.out_milsongs.txt pyunit_airlines_performance_profile.py.out.txt
    pyunit_milsongs_performance_profile.py.out.txt 8 26
"""

# --------------------------------------------------------------------
# Main program
# --------------------------------------------------------------------
    # directory where we are running out code from h2o-3/scripts
g_test_root_dir = os.path.dirname(os.path.realpath(__file__))

g_airline_py_tail = "/artifact/h2o-py/GLRM_performance_tests/results/pyunit_airlines_performance_profile.py.out.txt"
g_milsongs_py_tail = "/artifact/h2o-py/GLRM_performance_tests/results/pyunit_milsong_performance_profile.py.out.txt"
g_airline_java_tail = "/artifact/h2o-py/GLRM_performance_tests/results/java_0_0.out.txt"
g_milsongs_java_tail = "/artifact/h2o-py/GLRM_performance_tests/results/java_1_0.out.txt"


    # base directory where different logs will be stored under different build directory, e.g. glrm_memory_10_25_16
g_log_base_dir = ""
g_airline_java = ""     # name of java log file for airline runs you want to store as, e.g. java_0_0.out_airline.txt
g_milsongs_java = ""    # name of java log file for milsongs runs you want to store as, e.g. java_1_0.out_milsongs.txt
    # name of python run results for airline runs to store as, e.g. pyunit_airlines_performance_profile.py.out.txt
g_airline_python = ""
    # name of python run results for airline runs to store as, e.g. pyunit_milsong_performance_profile.py.out.txt
g_milsongs_python = ""
g_jenkins_url = ""  # url to your jenkins job, e.g.http://mr-0xa1:8080/view/wendy/job/glrm_original_performance/
g_start_build_number = 0    # starting build number to collect your data
g_end_build_number = 1      # ending build number to collect your data


def get_file_out(build_index, python_name, jenkin_name):
    """
    This function will grab one log file from Jenkins and save it to local user directory
    :param g_jenkins_url:
    :param build_index:
    :param airline_java:
    :param airline_java_tail:
    :return:
    """
    global g_log_base_dir
    global g_jenkins_url
    global g_log_base_dir

    directoryB = g_log_base_dir+'/Build'+str(build_index)

    if not(os.path.isdir(directoryB)): # make directory if it does not exist
        os.mkdir(directoryB)

    url_string_full = g_jenkins_url+'/'+str(build_index)+jenkin_name
    filename = os.path.join(directoryB, python_name)

    full_command = 'curl ' + url_string_full + ' > ' + filename
    subprocess.call(full_command,shell=True)


def main(argv):
    """
    Main program.

    @return: none
    """
    global g_log_base_dir
    global g_airline_java
    global g_milsongs_java
    global g_airline_python
    global g_milsongs_python
    global g_jenkins_url
    global g_airline_py_tail
    global g_milsongs_py_tail
    global g_airline_java_tail
    global g_milsongs_java_tail

    if len(argv) < 9:
        print "python grabGLRMrunLogs logsBaseDirectory airlineJavaFileNameWithPath milsongJavaFileNameWithPath " \
              "airlinePyunitWithPath airlinePyunitWithPath jenkinsJobURL startBuild# endBuild#.\n"
        sys.exit(1)
    else:   # we may be in business
#        g_script_name = os.path.basename(argv[0])   # get name of script being run.
            # base directory where all logs will be collected according to build #
        g_log_base_dir = argv[1]
        g_jenkins_url = argv[2]
        g_airline_java = argv[3]
        g_milsongs_java = argv[4]
        g_airline_python = argv[5]
        g_milsongs_python = argv[6]
        start_number = int(argv[7])
        end_number = int(argv[8])

        if (start_number > end_number):
            print "startBuild# must be <= end_number"
            sys.exit(1)
        else:
            for build_index in range(start_number, end_number+1):   # grab log info for all builds
                # copy the java jobs
                get_file_out(build_index, g_airline_java, g_airline_java_tail)
                get_file_out(build_index, g_milsongs_java, g_milsongs_java_tail)

                # copy the pyunit jobs
                get_file_out(build_index, g_airline_python, g_airline_py_tail)
                get_file_out(build_index, g_milsongs_python, g_milsongs_py_tail)


if __name__ == "__main__":
    main(sys.argv)
