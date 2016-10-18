#!/usr/bin/python
import sys
import os
import json
import subprocess
import time
import numpy as np

"""
This script is written to extract the the time (ms) taken to perform the various operations in GLRM
model iteration.  You should not use this for anything else.  Provide the absolute path to the data
file if it is not in the same directory as this script.
"""

# --------------------------------------------------------------------
# Main program
# --------------------------------------------------------------------

g_test_root_dir = os.path.dirname(os.path.realpath(__file__))   # directory where we are running out code from

g_initialXY = "Time taken (ms) to initializeXY with"   # text of interest
g_reguarlize_Y = "Time taken (ms) to calculate regularize_y"
g_regularize_X_objective = "Time taken (ms) to calculate regularize_x and calculate"
g_updateX = "Time taken (ms) to updateX"
g_updateY = "Time taken (ms) to updateY"
g_objective = "Time taken (ms) to calculate new objective function value"
g_stepsize = "Time taken (ms) to set the step size"
g_history = "Time taken (ms) to history of run"


def extractRunInto(javaLogText):
    """
    This function will extract the various operation time for GLRM model building iterations.

    :param javaLogText:
    :return:
    """
    global g_initialXY
    global g_reguarlize_Y
    global g_regularize_X_objective
    global g_updateX
    global g_updateY
    global g_objective
    global g_stepsize
    global g_history


    if os.path.isfile(javaLogText):

        run_result = dict()
        run_result["total time (ms)"] = []
        run_result["initialXY (ms)"] = []
        run_result["regularize Y (ms)"] = []
        run_result["regularize X and objective (ms)"] = []
        run_result["update X (ms)"] = []
        run_result["update Y (ms)"] = []
        run_result["objective (ms)"] = []
        run_result["step size (ms)"] = []
        run_result["update history (ms)"] = []

        total_run_time = -1
        val = 0.0
        with open(javaLogText, 'r') as thefile:   # go into tempfile and grab test run info
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
        print("Run result summary: \n {0}".format(run_result))

    else:
        print("Cannot find your java log file.  Nothing is done.\n")


def main(argv):
    """
    Main program.  Take user input, parse it and call other functions to execute the commands
    and extract run summary and store run result in json file

    @return: none
    """
    global g_test_root_dir
    global g_temp_filename

    if len(argv) < 2:
        print("invoke this script as python extractGLRMRuntimeJavaLog.py javatextlog.\n")
        sys.exit(1)
    else:   # we may be in business
        javaLogText = argv[1]         # filename while java log is stored

        print("your java text is {0}".format(javaLogText))
        extractRunInto(javaLogText)


if __name__ == "__main__":
    main(sys.argv)
