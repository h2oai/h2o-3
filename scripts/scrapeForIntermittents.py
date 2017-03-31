#!/usr/bin/python

import sys
import os
import json
import subprocess
import time
import datetime
from pytz import timezone
from dateutil import parser

"""
This script will be invoked if it is included in the post-build action of an jenkin job and the job has failed.

It will perform the following tasks:
1. attach the failure informaiton of all tests in the current build to a summary file including the following fields:
    - timestamp, jenkin_job_name, build_id, git_has, node_name, build_failure(test failed but to build failure),
    JUnit/PyUnit/RUnit/Hadoop, testName.
2. save the above summary file to s3 somewhere using the command: s3cmd put "$TEST_OUTPUT_FILE" s3://ai.h2o.tests/jenkins/
3. store the failed test info in a dictionary and save it to s3 as well;
4. for failed tests, save the txt failed test results to aid the debugging process.  Attach timestamp to file name
  in order to aid the process of cleaning out the file directory with obsolete files.
"""

# --------------------------------------------------------------------
# Main program
# --------------------------------------------------------------------

g_test_root_dir = os.path.dirname(os.path.realpath(__file__)) # directory where we are running out code from
g_script_name = ''  # store script name.
g_timestamp = ''
g_job_name = ''
g_build_id = ''
g_git_hash = ''
g_node_name = ''
g_unit_test_type = ''
g_jenkins_url = ''
g_temp_filename = os.path.join(g_test_root_dir,'tempText')  # temp file to store data curled from Jenkins
g_failed_testnames = []
g_failed_test_paths = []
g_failed_tests_dict = ''    # contains the filename that contains failed tests info in a dictionary
g_failed_tests_info_dict = dict()   # store failed tests info in a dictionary
g_resource_url = ''
g_timestring = ''
g_daily_failure_csv = ''

def init_failed_tests_dict():
    """
    initialize the fields of dictionary storing failed tests.
    :return:
    """
    global g_failed_test_info_dict
    g_failed_tests_info_dict["TestName"] = []
    g_failed_tests_info_dict["TestInfo"] = []


def init_update_each_failed_test_dict(one_test_info, failed_test_path, testName, newTest):
    """
    For each test, a dictionary structure will be built to record the various info about that test's failure
    information.  In particular, for each failed tests, there will be a dictionary associated with that test
    stored in the field "TestInfo" of g_faiiled_tests_info_dict.  The following fields are included:
        "JenkinsJobName": job name
        "BuildID"
        "Timestamp": in seconds
        "GitHash"
        "TestCategory": JUnit, PyUnit, RUnit or HadoopPyUnit, HadoopRUnit
        "NodeName": name of machine that the job was run on
        "FailureCount": integer counting number of times this particular test has failed.  An intermittent can be
          determined as any test with FailureCount >= 2.
        "FailureMessages": contains failure messages for the test
    :return: a new dict for that test
    """
    if newTest:
        one_test_info = dict()
        one_test_info["JenkinsJobName"] = []
        one_test_info["BuildID"] = []
        one_test_info["Timestamp"] = []
        one_test_info["GitHash"] = []
        one_test_info["TestCategory"] = []    # would be JUnit, PyUnit, RUnit or HadoopPyUnit, HadoopRUnit
        one_test_info["NodeName"] = []
        one_test_info["FailureMessages"] = [] # contains failure messages for the test
        one_test_info["FailureCount"] = 0
        one_test_info["TestName"] = testName

#    if g_timestamp not in one_test_info["Timestamp"]:
    one_test_info["JenkinsJobName"].append(g_job_name)
    one_test_info["BuildID"].append(g_build_id)
    one_test_info["Timestamp"].append(g_timestamp)
    one_test_info["GitHash"].append(g_git_hash)
    one_test_info["TestCategory"].append(g_unit_test_type) # would be JUnit, PyUnit, RUnit or HadoopPyUnit, HadoopRUnit
    one_test_info["NodeName"].append(g_node_name)
    one_test_info["FailureCount"] += 1

    error_url = '/'.join([g_resource_url, 'testReport', failed_test_path])
    get_console_out(error_url)      # store failure message in temp file

    if os.path.isfile(g_temp_filename):
        with open(g_temp_filename, 'r') as error_file:
            one_test_info["FailureMessages"].append(error_file.read())
    else:
        one_test_info["FailureMessages"].append("")   # append empty error message if file not found
    return one_test_info

def usage():
    """
    Print USAGE help.
    """
    print("")
    print("Usage:  ")
    print("python scrapeForIntermittents timestamp job_name build_id git_sha node_name unit_test_category jenkins_URL "
          "output_filename output_dict_name month_of_data_to_keep tests_info_per_build_failure")
    print(" The unit_test_category can be 'junit', 'pyunit' or 'runit'.")
    print(" The ouput_dict_name is the filename that we will save a dictionary structure of the failed unit tests.")
    print(" The month_of_data_to_keep is an integer indicating how many months that we want to kee the data "
          "starting from now.  Any data that is older than the value will be deleted.")
    print("tests_info_per_build_failure is the file name that will store failed test info per build failure.")

'''
This function is written to extract the console output that has already been stored
in a text file in a remote place and saved it in a local directory that we have accessed
to.  We want to be able to read in the local text file and proces it.
'''
def get_console_out(url_string):
    """
    Grab the console output from Jenkins and save the content into a temp file
     (g_temp_filename).  From the saved text file, we can grab the names of
     failed tests.

    Parameters
    ----------
    url_string :  str
        contains information on the jenkins job whose console output we are interested in.  It is in the context
        of resource_url/job/job_name/build_id/testReport/

    :return: none
    """
    full_command = 'curl ' + '"'+ url_string +'"'+ ' --user '+'"admin:admin"'+' > ' + g_temp_filename
    subprocess.call(full_command,shell=True)


def extract_failed_tests_info():
    """
    This method will scrape the console output for pyunit,runit and hadoop runs and grab the list of failed tests
    and their corresponding paths so that the test execution summary can be located later.

    :return: none
    """
    global g_failed_testnames
    global g_failed_test_paths

    if os.path.isfile(g_temp_filename):
        console_file = open(g_temp_filename,'r')  # open temp file that stored jenkins job console output
        try:
            for each_line in console_file:  # go through each line of console output to extract build ID, data/time ...
                each_line.strip()
                print(each_line)
                if ("Test Result" in each_line) and ("failure" in each_line): # the next few lines will contain failed tests
                    temp = each_line.split("testReport")
                    if ("Test Result" in temp[1]) and ("failure" in temp[1]):        # grab number of failed tests
                        tempCount = int(temp[1].split("</a>")[1].split(" ")[0].split("(")[1])

                        if isinstance(tempCount, int) and tempCount > 0:  # temp[1], temp[2],... should contain failed tests
                            for findex in range(2,len(temp)):
                                tempMess = temp[findex].split(">")
                                g_failed_test_paths.append(tempMess[0].strip('"'))
                                ftestname = tempMess[1].strip("</a")
                                nameLen = len(ftestname)
                                true_testname = ftestname[8:nameLen] if 'r_suite.' in ftestname else ftestname
                                g_failed_testnames.append(true_testname)
                            break   # done.  Only one spot contains failed test info.
        finally:
            console_file.close()

def save_failed_tests_info():
    """
    Given the failed tests information in g_failed_testnames, add the new failed test to
    text file.  In addition, it will update the dictionary that stores all failed test info as well.

    :return: None
    """
    global g_failed_tests_info_dict
    if len(g_failed_testnames) > 0: # found failed test
        if os.path.isfile(g_failed_tests_dict) and os.path.getsize(g_failed_tests_dict) > 10:
            try:
                g_failed_tests_info_dict=json.load(open(g_failed_tests_dict, 'r'))
            except:
                init_failed_tests_dict()

            # with open(g_failed_tests_dict, 'rb') as dict_file:
            #     g_failed_tests_info_dict = pickle.load(dict_file)
        else:       # file not found, create new dict
            init_failed_tests_dict()

        with open(g_summary_text_filename, 'a') as failed_file:
            with open(g_daily_failure_csv, 'w') as daily_failure:
                for index in range(len(g_failed_testnames)):

                    testInfo = ','.join([g_timestring, g_job_name, str(g_build_id), g_git_hash, g_node_name,
                                         g_unit_test_type, g_failed_testnames[index]])
                    failed_file.write(testInfo+'\n')
                    daily_failure.write(testInfo+'\n')
                        # update failed tests dictionary
                    update_failed_test_info_dict(g_failed_testnames[index], g_failed_test_paths[index])
        json.dump(g_failed_tests_info_dict, open(g_failed_tests_dict, 'w'))



def update_failed_test_info_dict(failed_testname, failed_test_path):
    """
    Update the dictionary structure that stores failed unit test information.

    :param failed_testname: string containing name of failed test.
    :param failed_test_path: string containing the path to failed test url.
    :return: None
    """
    global g_failed_tests_info_dict

    if failed_testname in g_failed_tests_info_dict["TestName"]:     # existing test
        g_failed_tests_info_dict["TestInfo"][g_failed_tests_info_dict["TestName"].index(failed_testname)] = \
            init_update_each_failed_test_dict(
                g_failed_tests_info_dict["TestInfo"][g_failed_tests_info_dict["TestName"].index(failed_testname)],
                failed_test_path, failed_testname, False)
    else:   # next test
        g_failed_tests_info_dict["TestName"].append(failed_testname)
        g_failed_tests_info_dict["TestInfo"].append(init_update_each_failed_test_dict(dict(), failed_test_path,
                                                                                      failed_testname, True))

def trim_data_back_to(monthToKeep):
    """
    This method will remove data from the summary text file and the dictionary file for tests that occurs before
    the number of months specified by monthToKeep.

    :param monthToKeep:
    :return:
    """
    global g_failed_tests_info_dict
    current_time = time.time()      # unit in seconds

    oldest_time_allowed = current_time - monthToKeep*30*24*3600 # in seconds

    clean_up_failed_test_dict(oldest_time_allowed)
    clean_up_summary_text(oldest_time_allowed)


def clean_up_failed_test_dict(oldest_time_allowed):
    # read in data from dictionary file
    global g_failed_tests_info_dict
    if os.path.isfile(g_failed_tests_dict) and os.path.getsize(g_failed_tests_dict)>10:
        try:
            g_failed_tests_info_dict=json.load(open(g_failed_tests_dict,'r'))
            test_index = 0
            while test_index < len(g_failed_tests_info_dict["TestName"]):
                test_dicts = g_failed_tests_info_dict["TestInfo"][test_index]   # a list of dictionary

                dict_index = 0
                while (len(test_dicts["Timestamp"]) > 0) and (dict_index < len(test_dicts["Timestamp"])):
                    if (test_dicts["Timestamp"][dict_index] < oldest_time_allowed):
                        del test_dicts["JenkinsJobName"][dict_index]
                        del test_dicts["BuildID"][dict_index]
                        del test_dicts["Timestamp"][dict_index]
                        del test_dicts["GitHash"][dict_index]
                        del test_dicts["TestCategory"][dict_index]
                        del test_dicts["NodeName"][dict_index]
                        test_dicts["FailureCount"] -= 1
                    else:
                        dict_index = dict_index+1

                if test_dicts["FailureCount"] <= 0: # remove test name with 0 counts of failure count
                    del g_failed_tests_info_dict["Testname"][test_index]
                    del g_failed_tests_info_dict["TestInfo"][test_index]
                else:
                    test_index = test_index+1

            json.dump(g_failed_tests_info_dict, open(g_failed_tests_dict, 'w'))
        except:
            pass


def clean_up_summary_text(oldest_time_allowed):
    # clean up the summary text data
    if os.path.isfile(g_summary_text_filename):
        with open(g_summary_text_filename, 'r') as text_file:
            with open(g_temp_filename, 'w') as temp_file:
                for each_line in text_file:
                    temp = each_line.split(',')
                    if len(temp) >= 7:
                        dateObj = parser.parse(temp[0]).timetuple()
                        timestamp = time.mktime(dateObj)

                        if (timestamp > oldest_time_allowed):
                            temp_file.write(each_line)

        with open(g_summary_text_filename, 'w') as text_file:   # write content back to original summary file
            with open(g_temp_filename, 'r') as temp_file:
                text_file.write(temp_file.read())


def main(argv):
    """
    Main program.  Expect script name plus 7 inputs in the following order:
    - This script name
    1. timestamp: time in s
    2. jenkins_job_name (JOB_NAME)
    3. build_id (BUILD_ID)
    4. git hash (GIT_COMMIT)
    5. node name (NODE_NAME)
    6. unit test category (JUnit, PyUnit, RUnit, Hadoop)
    7. Jenkins URL (JENKINS_URL)
    8. Text file name where failure summaries are stored
    9. Filename that stored all failed test info as a dictionary
    10. duration (month) to keep data: data older tghan this input will be removed

    @return: none
    """
    global g_script_name
    global g_test_root_dir
    global g_timestamp
    global g_job_name
    global g_build_id
    global g_git_hash
    global g_node_name
    global g_unit_test_type
    global g_jenkins_url
    global g_temp_filename
    global g_summary_text_filename  # store failed test info in csv format
    global g_failed_tests_dict      # store failed test info as a dictionary
    global g_resource_url
    global g_timestring
    global g_daily_failure_csv

    if len(argv) < 12:
        print "Wrong call.  Not enough arguments.\n"
        usage()
        sys.exit(1)
    else:   # we may be in business
        g_script_name = os.path.basename(argv[0])   # get name of script being run.
        g_timestamp = float(argv[1])
        g_job_name = argv[2]
        g_build_id = argv[3]
        g_git_hash = argv[4]
        g_node_name= argv[5]
        g_unit_test_type = argv[6]
        g_jenkins_url = argv[7]

        localtz = time.tzname[0]
        dt = parser.parse(time.ctime(g_timestamp)+ ' '+localtz)
        g_timestring = dt.strftime("%a %b %d %H:%M:%S %Y %Z")
        g_temp_filename = os.path.join(g_test_root_dir,'tempText')
        g_summary_text_filename = os.path.join(g_test_root_dir, argv[8])
        g_failed_tests_dict = os.path.join(g_test_root_dir, argv[9])
        monthToKeep = float(argv[10])
        g_daily_failure_csv = os.path.join(g_test_root_dir, argv[11])+'_'+argv[1]+'.csv'

        g_resource_url = '/'.join([g_jenkins_url, "job", g_job_name, g_build_id])
        get_console_out(g_resource_url+"/#showFailuresLink/")       # save remote console output in local directory
        extract_failed_tests_info()      # grab the console text and stored the failed tests/paths
        save_failed_tests_info()         # save new failed test info into a file
        if monthToKeep > 0:
            trim_data_back_to(monthToKeep)   # remove data that are too old to save space

if __name__ == "__main__":
    main(sys.argv)
