#!/usr/bin/python

import sys
import os
import json

"""
This script is written to analysis the Jenkins run logs that we have saved from jenkins to our local computer.
  For me, this is how I will call this script and the input arguments I will use:

python grabGLRMJenkinRunResults.py /Users/wendycwong/Documents/PUBDEV_3454_GLRM/experimentdata/glrm_memory_10_25_16

"""

# --------------------------------------------------------------------
# Main program
# --------------------------------------------------------------------
    # directory where we are running out code from h2o-3/scripts
g_test_root_dir = os.path.dirname(os.path.realpath(__file__))


    # base directory where different logs will be stored under different build directory, e.g. glrm_memory_10_25_16
g_log_base_dir = ""
g_airline_java = "java_0_0.out_airline.txt"     # name of java log file for airline runs you want to store as, e.g. java_0_0.out_airline.txt
g_milsongs_java = "java_1_0.out_milsongs.txt"    # name of java log file for milsongs runs you want to store as, e.g. java_1_0.out_milsongs.txt
    # name of python run results for airline runs to store as, e.g. pyunit_airlines_performance_profile.py.out.txt
g_airline_python = "pyunit_airlines_performance_profile.py.out.txt"
    # name of python run results for airline runs to store as, e.g. pyunit_milsong_performance_profile.py.out.txt
g_milsongs_python = "pyunit_milsong_performance_profile.py.out.txt"
g_direct_name_start = "Build"
g_initialXY = "Time taken (ms) to initializeXY with"   # text of interest
g_reguarlize_Y = "Time taken (ms) to calculate regularize_y"
g_regularize_X_objective = "Time taken (ms) to calculate regularize_x and calculate"
g_updateX = "Time taken (ms) to updateX"
g_updateY = "Time taken (ms) to updateY"
g_objective = "Time taken (ms) to calculate new objective function value"
g_stepsize = "Time taken (ms) to set the step size"
g_history = "Time taken (ms) to history of run"
g_py_runtime = "Run time in ms: "
g_py_iteration = "number of iterations:"


def generate_octave_java_ascii(java_dict, fname):
    global g_log_base_dir

    updateX = java_dict["update X (ms)"]
    updateY = java_dict["update Y (ms)"]
    obj = java_dict["objective (ms)"]


    with open(os.path.join(g_log_base_dir, fname),'w') as test_file:
        for ind in range(0, len(updateX)):
            temp_str = str(updateX[ind])+" "+str(updateY[ind])+" "+str(obj[ind])+"\n"
            test_file.write(temp_str)


def generate_octave_py_ascii(py_dict, fname):
    global g_log_base_dir

    run_time = py_dict["total time (ms)"]
    iter_number = py_dict["iteration number"]
    time_per_iter = py_dict["time (ms) per iteration"]

    with open(os.path.join(g_log_base_dir, fname),'wb') as test_file:
        for ind in range(0,len(run_time)):
            temp_str = str(run_time[ind])+" "+str(iter_number[ind])+" "+str(time_per_iter[ind])+"\n"
            test_file.write(temp_str)


def init_java_dict():
    dict_name = dict()
    dict_name["total time (ms)"] = []
    dict_name["initialXY (ms)"] = []
    dict_name["regularize Y (ms)"] = []
    dict_name["regularize X and objective (ms)"] = []
    dict_name["update X (ms)"] = []
    dict_name["update Y (ms)"] = []
    dict_name["objective (ms)"] = []
    dict_name["step size (ms)"] = []
    dict_name["update history (ms)"] = []

    return dict_name


def init_python_dict():
    dict_name = dict()
    dict_name["total time (ms)"] = []
    dict_name["iteration number"] = []
    dict_name["time (ms) per iteration"] = []

    return dict_name


def grab_java_results(dirName, java_file, run_result):
    global g_direct_name_start
    global g_log_base_dir
    global g_initialXY
    global g_reguarlize_Y
    global g_regularize_X_objective
    global g_updateX
    global g_updateY
    global g_objective
    global g_stepsize
    global g_history

    if not (g_direct_name_start in dirName):
        print("Cannot find your java log file.  Nothing is done.\n")
        return run_result

    logText = os.path.join(os.path.join(g_log_base_dir, dirName), java_file)
    total_run_time = -1
    val = 0.0

    with open(logText, 'r') as thefile:   # go into tempfile and grab test run info
        for each_line in thefile:
            temp_string = each_line.split()

            if len(temp_string) > 0:
                val = temp_string[-1].replace('\\','')

            if g_initialXY in each_line:    # start of a new file
                if total_run_time > 0:  # update total run time
                    run_result["total time (ms)"].append(total_run_time)
                    total_run_time = 0.0
                else:
                    total_run_time = 0.0

                run_result["initialXY (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_reguarlize_Y in each_line:
                run_result["regularize Y (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_regularize_X_objective in each_line:
                run_result["regularize X and objective (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_updateX in each_line:
                run_result["update X (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_updateY in each_line:
                run_result["update Y (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_objective in each_line:
                run_result["objective (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_stepsize in each_line:
                run_result["step size (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

            if g_history in each_line:
                run_result["update history (ms)"].append(float(val))
                total_run_time = total_run_time+float(val)

        run_result["total time (ms)"].append(total_run_time)    # save the last one

    return run_result


def grab_py_results(dirName, python_file, run_result):
    global g_direct_name_start
    global g_log_base_dir
    global g_py_runtime
    global g_py_iteration

    if not (g_direct_name_start in dirName):
        print("Cannot find your python log file.  Nothing is done.\n")
        return run_result

    logText = os.path.join(os.path.join(g_log_base_dir, dirName), python_file)
    with open(logText, 'r') as thefile:   # go into tempfile and grab test run info
        for each_line in thefile:
            temp_string = each_line.split(':')

            if len(temp_string) > 0:
                val = temp_string[-1].replace('\n','')

            if g_py_runtime in each_line:    # found run time sequence
                run_result["total time (ms)"].extend(eval(val))

            if g_py_iteration in each_line:
                run_result["iteration number"].extend(eval(val))
    return run_result

def transform_time_python(run_result):
    run_times = run_result["total time (ms)"]
    total_iterations = run_result["iteration number"]

    for ind in range(0, len(run_times)):
        run_result["time (ms) per iteration"].append(run_times[ind]*1.0/total_iterations[ind])

    return run_result


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

    if len(argv) < 2:
        print "python grabGLRMrunLogs logsBaseDirectory\n"
        sys.exit(1)
    else:   # we may be in business
        g_log_base_dir = argv[1]

        if (os.path.isdir(g_log_base_dir)):     # open directory and start to process logs in each one
            airline_java_dict = init_java_dict()
            milsongs_java_dict = init_java_dict()
            airline_py_dict = init_python_dict()
            milsongs_py_dict = init_python_dict()

            allBuilds = os.listdir(g_log_base_dir)
            for dirName in allBuilds:
                airline_java_dict = grab_java_results(dirName, g_airline_java, airline_java_dict)
                milsongs_java_dict = grab_java_results(dirName, g_milsongs_java, milsongs_java_dict)
                airline_py_dict = grab_py_results(dirName, g_airline_python, airline_py_dict)
                milsongs_py_dict = grab_py_results(dirName, g_milsongs_python, milsongs_py_dict)

        airline_py_dict = transform_time_python(airline_py_dict)    # calculate time taken per iteration
        milsongs_py_dict = transform_time_python(milsongs_py_dict)

        print("Airline Java log results: \n {0}".format(airline_java_dict))
        print("Airline Python log results: \n {0}".format(airline_py_dict))
        print("Milsongs Java log results: \n {0}".format(milsongs_java_dict))
        print("Milsongs Python log results: \n {0}".format(milsongs_py_dict))

        # dump dictionary into json files for later analysis
        with open(os.path.join(g_log_base_dir, "airline_java_dict"),'wb') as test_file:
            json.dump(airline_java_dict, test_file)

        with open(os.path.join(g_log_base_dir, "airline_py_dict"),'wb') as test_file:
            json.dump(airline_py_dict, test_file)

        with open(os.path.join(g_log_base_dir, "milsongs_java_dict"),'wb') as test_file:
            json.dump(milsongs_java_dict, test_file)

        with open(os.path.join(g_log_base_dir, "milsongs_py_dict"),'wb') as test_file:
            json.dump(milsongs_py_dict, test_file)

        # dump analysis results into json format that octave can understand and process
        generate_octave_java_ascii(airline_java_dict, "airline_java_octave")
        generate_octave_java_ascii(milsongs_java_dict, "milsongs_java_octave")
        generate_octave_py_ascii(airline_py_dict, "airline_py_octave")
        generate_octave_py_ascii(milsongs_py_dict, "milsongs_py_octave")


if __name__ == "__main__":
    main(sys.argv)
