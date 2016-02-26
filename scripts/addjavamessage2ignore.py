#!/usr/bin/python

import sys
import os
import pickle

import copy
import subprocess


"""
This script is written for a user
1. to add new java messages that we can ignore during a log scraping session;
2. to remove old java messages that are okay to ignore in the past but cannot be ignored anymore.

To see how to call this script correctly, see usage().

To exclude java messages, the user can edit a text file that contains the following:
keyName = general
IgnoredMessage = nfolds: nfolds cannot be larger than the number of rows (406).
KeyName = pyunit_cv_cars_gbm.py
IgnoredMessage = Caught exception: Illegal argument(s) for GBM model: GBM_model_python_1452503348770_2586.  Details: ERRR on field: _nfolds: nfolds must be either 0 or >1.
...
KeyName = pyunit_cv_cars_gbm.py
IgnoredMessage = Stacktrace: [water.exceptions.H2OModelBuilderIllegalArgumentException.makeFromBuilder(H2OModelBuilderIllegalArgumentException.java:19), \
water.api.ModelBuilderHandler.handle(ModelBuilderHandler.java:45), water.api.RequestServer.handle(RequestServer.java:617), \
water.api.RequestServer.serve(RequestServer.java:558), water.JettyHTTPD$H2oDefaultServlet.doGeneric(JettyHTTPD.java:616), \
water.JettyHTTPD$H2oDefaultServlet.doPost(JettyHTTPD.java:564), javax.servlet.http.HttpServlet.service(HttpServlet.java:755), \
javax.servlet.http.HttpServlet.service(HttpServlet.java:848), org.eclipse.jetty.servlet.ServletHolder.handle(ServletHolder.java:684)]; \
Values: {"messages":[{"_log_level":1,"_field_name":"_nfolds","_message":"nfolds must be either 0 or >1."},\
{"_log_level":5,"_field_name":"_tweedie_power","_message":"Only for Tweedie Distribution."},{"_log_level":5,"_field_name":"_max_after_balance_size",\
"_message":"Balance classes is false, hide max_after_balance_size"},{"_log_level":5,"_field_name":"_max_after_balance_size","_message":"Only used with balanced classes"},\
{"_log_level":5,"_field_name":"_class_sampling_factors","_message":"Class sampling factors is only applicable if balancing classes."}], "algo":"GBM", \
"parameters":{"_train":{"name":"py_3","type":"Key"},"_valid":null,"_nfolds":-1,"_keep_cross_validation_predictions":false,"_fold_assignment":"AUTO",\
"_distribution":"multinomial","_tweedie_power":1.5,"_ignored_columns":["economy_20mpg","fold_assignments","name","economy"],"_ignore_const_cols":true,\
"_weights_column":null,"_offset_column":null,"_fold_column":null,"_score_each_iteration":false,"_stopping_rounds":0,"_stopping_metric":"AUTO",\
"_stopping_tolerance":0.001,"_response_column":"cylinders","_balance_classes":false,"_max_after_balance_size":5.0,"_class_sampling_factors":null,\
"_max_confusion_matrix_size":20,"_checkpoint":null,"_ntrees":5,"_max_depth":5,"_min_rows":10.0,"_nbins":20,"_nbins_cats":1024,"_r2_stopping":0.999999,\
"_seed":-1,"_nbins_top_level":1024,"_build_tree_one_node":false,"_initial_score_interval":4000,"_score_interval":4000,"_sample_rate":1.0,\
"_col_sample_rate_per_tree":1.0,"_learn_rate":0.1,"_col_sample_rate":1.0}, "error_count":1}

Given the above text file, this script will build a dict structure (g_ok_java_message_dict) that contains the
following key/value pairs:
g_ok_java_message_dict["general"] = ["nfolds: nfolds cannot be larger than the number of rows (406)."]
g_ok_java_message_dict["pyunit_cv_cars_gbm.py"] = ["Caught exception: Illegal argument(s) for GBM model: GBM_model_python_1452503348770_2586.  \
    Details: ERRR on field: _nfolds: nfolds must be either 0 or >1.","Stacktrace: [water.exceptions.H2OModelBuilderIllegalArgumentException.makeFromBuilder(H2OModelBuilderIllegalArgumentException.java:19), \
water.api.ModelBuilderHandler.handle(ModelBuilderHandler.java:45), water.api.RequestServer.handle(RequestServer.java:617), \
water.api.RequestServer.serve(RequestServer.java:558), water.JettyHTTPD$H2oDefaultServlet.doGeneric(JettyHTTPD.java:616), \
water.JettyHTTPD$H2oDefaultServlet.doPost(JettyHTTPD.java:564), javax.servlet.http.HttpServlet.service(HttpServlet.java:755), \
javax.servlet.http.HttpServlet.service(HttpServlet.java:848), org.eclipse.jetty.servlet.ServletHolder.handle(ServletHolder.java:684)]; \
Values: {"messages":[{"_log_level":1,"_field_name":"_nfolds","_message":"nfolds must be either 0 or >1."},\
{"_log_level":5,"_field_name":"_tweedie_power","_message":"Only for Tweedie Distribution."},{"_log_level":5,"_field_name":"_max_after_balance_size",\
"_message":"Balance classes is false, hide max_after_balance_size"},{"_log_level":5,"_field_name":"_max_after_balance_size","_message":"Only used with balanced classes"},\
{"_log_level":5,"_field_name":"_class_sampling_factors","_message":"Class sampling factors is only applicable if balancing classes."}], "algo":"GBM", \
"parameters":{"_train":{"name":"py_3","type":"Key"},"_valid":null,"_nfolds":-1,"_keep_cross_validation_predictions":false,"_fold_assignment":"AUTO",\
"_distribution":"multinomial","_tweedie_power":1.5,"_ignored_columns":["economy_20mpg","fold_assignments","name","economy"],"_ignore_const_cols":true,\
"_weights_column":null,"_offset_column":null,"_fold_column":null,"_score_each_iteration":false,"_stopping_rounds":0,"_stopping_metric":"AUTO",\
"_stopping_tolerance":0.001,"_response_column":"cylinders","_balance_classes":false,"_max_after_balance_size":5.0,"_class_sampling_factors":null,\
"_max_confusion_matrix_size":20,"_checkpoint":null,"_ntrees":5,"_max_depth":5,"_min_rows":10.0,"_nbins":20,"_nbins_cats":1024,"_r2_stopping":0.999999,\
"_seed":-1,"_nbins_top_level":1024,"_build_tree_one_node":false,"_initial_score_interval":4000,"_score_interval":4000,"_sample_rate":1.0,\
"_col_sample_rate_per_tree":1.0,"_learn_rate":0.1,"_col_sample_rate":1.0}, "error_count":1"]

The key value "general" implies that the java message stored in g_ok_java_message_dict["general"] will be ignored
for all unit tests.  The java messages stored by the specific unit test name is only ignored for that particular tests.

For each key value in the g_ok_java_message_dict, the values are stored as a list.

"""

# --------------------------------------------------------------------
# Main program
# --------------------------------------------------------------------

g_test_root_dir = os.path.dirname(os.path.realpath(__file__)) # directory where we are running out code from
g_load_java_message_filename = "bad_java_messages_to_exclude.pickle" # default pickle filename that store previous java messages that we wanted to exclude
g_save_java_message_filename = "bad_java_messages_to_exclude.pickle" # pickle filename that we are going to store our added java messages to
g_new_messages_to_exclude = ""  # user file that stores the new java messages to ignore
g_old_messages_to_remove = ""   # user file that stores java messages that are to be removed from the ignore list.
g_dict_changed = False          # True if dictionary has changed and False otherwise
g_java_messages_to_ignore_text_filename = "java_messages_to_ignore.txt"     # store all rules for humans to read
g_print_java_messages = False

# store java bad messages that we can ignore.  The keys are "general",testnames that we
# want to add exclude messages for.  The values will all be a list of java messages that we want to ignore.
g_ok_java_messages = {}

def load_dict():
    """
    Load java messages that can be ignored pickle file into a dict structure g_ok_java_messages.

    :return: none
    """
    global g_load_java_message_filename
    global g_ok_java_messages

    if os.path.isfile(g_load_java_message_filename):
            # only load dict from file if it exists.
        with open(g_load_java_message_filename,'rb') as ofile:
            g_ok_java_messages = pickle.load(ofile)
    else:   # no previous java messages to be excluded are found
        g_ok_java_messages["general"] = []

def add_new_message():
    """
    Add new java messages to ignore from user text file.  It first reads in the new java ignored messages
    from the user text file and generate a dict structure to out of the new java ignored messages.  This
    is achieved by function extract_message_to_dict.  Next, new java messages will be added to the original
    ignored java messages dict g_ok_java_messages.  Again, this is achieved by function update_message_dict.

    :return: none
    """
    global g_new_messages_to_exclude    # filename containing text file from user containing new java ignored messages
    global g_dict_changed               # True if new ignored java messages are added.

    new_message_dict = extract_message_to_dict(g_new_messages_to_exclude)

    if new_message_dict:
        g_dict_changed = True
        update_message_dict(new_message_dict,1) # update g_ok_java_messages with new message_dict, 1 to add, 2 to remove


def remove_old_message():
    """
    Remove java messages from ignored list if users desired it.  It first reads in the java ignored messages
    from user stored in g_old_messages_to_remove and build a dict structure (old_message_dict) out of it.  Next, it removes the
    java messages contained in old_message_dict from g_ok_java_messages.
    :return: none
    """
    global g_old_messages_to_remove
    global g_dict_changed

    # extract old java ignored messages to be removed in old_message_dict
    old_message_dict = extract_message_to_dict(g_old_messages_to_remove)

    if old_message_dict:
        g_dict_changed = True
        update_message_dict(old_message_dict,2) # remove the java messages stored in old_message_dict from g_ok_java_messages


def update_message_dict(message_dict,action):
    """
    Update the g_ok_java_messages dict structure by
    1. add the new java ignored messages stored in message_dict if action == 1
    2. remove the java ignored messages stired in message_dict if action == 2.

    Parameters
    ----------

    message_dict :  Python dict
      key: unit test name or "general"
      value: list of java messages that are to be ignored if they are found when running the test stored as the key.  If
        the key is "general", the list of java messages are to be ignored when running all tests.
    action : int
      if 1: add java ignored messages stored in message_dict to g_ok_java_messages dict;
      if 2: remove java ignored messages stored in message_dict from g_ok_java_messages dict.

    :return: none
    """
    global g_ok_java_messages

    allKeys = g_ok_java_messages.keys()

    for key in message_dict.keys():
        if key in allKeys:  # key already exists, just add to it
            for message in message_dict[key]:

                if action == 1:
                    if message not in g_ok_java_messages[key]:
                        g_ok_java_messages[key].append(message)

                if action == 2:
                    if message in g_ok_java_messages[key]:
                        g_ok_java_messages[key].remove(message)
        else:   # new key here.  Can only add and cannot remove
            if action == 1:
                g_ok_java_messages[key] = message_dict[key]


def extract_message_to_dict(filename):
    """
    Read in a text file that java messages to be ignored and generate a dictionary structure out of
    it with key and value pairs.  The keys are test names and the values are lists of java message
    strings associated with that test name where we are either going to add to the existing java messages
    to ignore or remove them from g_ok_java_messages.

    Parameters
    ----------

    filename :  Str
       filename that contains ignored java messages.  The text file shall contain something like this:
        keyName = general
        Message = nfolds: nfolds cannot be larger than the number of rows (406).
        KeyName = pyunit_cv_cars_gbm.py
        Message = Caught exception: Illegal argument(s) for GBM model: GBM_model_python_1452503348770_2586.  \
            Details: ERRR on field: _nfolds: nfolds must be either 0 or >1.
        ...

    :return:
    message_dict : dict
        contains java message to be ignored with key as unit test name or "general" and values as list of ignored java
        messages.
    """
    message_dict = {}

    if os.path.isfile(filename):
        # open file to read in new exclude messages if it exists
        with open(filename,'r') as wfile:

            key = ""
            val = ""
            startMess = False

            while 1:
                each_line = wfile.readline()

                if not each_line:   # reached EOF
                    if startMess:
                        add_to_dict(val.strip(),key,message_dict)
                    break

                # found a test name or general with values to follow
                if "keyname" in each_line.lower():  # name of test file or the word "general"
                    temp_strings = each_line.strip().split('=')

                    if (len(temp_strings) > 1): # make sure the line is formatted sort of correctly
                        if startMess:   # this is the start of a new key/value pair
                            add_to_dict(val.strip(),key,message_dict)
                            val = ""

                        key = temp_strings[1].strip()
                        startMess = False

                if (len(each_line) > 1) and startMess:
                    val += each_line

                if "ignoredmessage" in each_line.lower():
                    startMess = True    # start of a Java message.
                    temp_mess = each_line.split('=')

                    if (len(temp_mess) > 1):
                        val = temp_mess[1]



    return message_dict


def add_to_dict(val,key,message_dict):
    """
    Add new key, val (ignored java message) to dict message_dict.

    Parameters
    ----------

    val :  Str
       contains ignored java messages.
    key :  Str
        key for the ignored java messages.  It can be "general" or any R or Python unit
        test names
    message_dict :  dict
        stored ignored java message for key ("general" or any R or Python unit test names)

    :return: none
    """
    allKeys = message_dict.keys()
    if (len(val) > 0):    # got a valid message here
        if (key in allKeys) and (val not in message_dict[key]):
            message_dict[key].append(val)   # only include this message if it has not been added before
        else:
            message_dict[key] = [val]


def save_dict():
    """
    Save the ignored java message dict stored in g_ok_java_messages into a pickle file for future use.

    :return: none
    """
    global g_ok_java_messages
    global g_save_java_message_filename
    global g_dict_changed

    if g_dict_changed:
        with open(g_save_java_message_filename,'wb') as ofile:
            pickle.dump(g_ok_java_messages,ofile)

def print_dict():
    """
    Write the java ignored messages in g_ok_java_messages into a text file for humans to read.

    :return: none
    """
    global g_ok_java_messages
    global g_java_messages_to_ignore_text_filename

    allKeys = sorted(g_ok_java_messages.keys())

    with open(g_java_messages_to_ignore_text_filename,'w') as ofile:
        for key in allKeys:

            for mess in g_ok_java_messages[key]:
                ofile.write('KeyName: '+key+'\n')
                ofile.write('IgnoredMessage: '+mess+'\n')

            print('KeyName: ',key)
            print('IgnoredMessage: ',g_ok_java_messages[key])
            print('\n')



def parse_args(argv):
    """
    Parse user inputs and set the corresponing global variables to perform the
    necessary tasks.

    Parameters
    ----------

    argv : string array
        contains flags and input options from users

    :return:
    """
    global g_new_messages_to_exclude
    global g_old_messages_to_remove
    global g_load_java_message_filename
    global g_save_java_message_filename
    global g_print_java_messages


    if len(argv) < 2:   # print out help menu if user did not enter any arguments.
        usage()

    i = 1
    while (i < len(argv)):
        s = argv[i]

        if (s == "--inputfileadd"):         # input text file where new java messages are stored
            i += 1
            if (i > len(argv)):
                usage()
            g_new_messages_to_exclude = argv[i]
        elif (s == "--inputfilerm"):        # input text file containing java messages to be removed from the ignored list
            i += 1
            if (i > len(argv)):
                usage()
            g_old_messages_to_remove = argv[i]
        elif (s == "--loadjavamessage"):    # load previously saved java message pickle file from file other than
            i += 1                          # the default one before performing update
            if i > len(argv):
                usage()
            g_load_java_message_filename = argv[i]
        elif (s == "--savejavamessage"):    # save updated java message in this file instead of default file
            i += 1
            if (i > len(argv)):
                usage()
            g_save_java_message_filename = argv[i]
        elif (s == '--printjavamessage'):   # will print java message out to console and save in a text file
            i += 1
            g_print_java_messages = True
            g_load_java_message_filename = argv[i]
        elif (s == '--help'):               # print help menu and exit
            usage()
        else:
            unknown_arg(s)

        i += 1


def usage():
    """
    Illustrate what the various input flags are and the options should be.

    :return: none
    """
    global g_script_name    # name of the script being run.

    print("")
    print("Usage:  " + g_script_name + " [...options...]")
    print("")
    print("     --help print out this help menu and show all the valid flags and inputs.")
    print("")
    print("    --inputfileadd filename where the new java messages to ignore are stored in.")
    print("")
    print("    --inputfilerm filename where the java messages are removed from the ignored list.")
    print("")
    print("    --loadjavamessage filename pickle file that stores the dict structure containing java messages to include.")
    print("")
    print("    --savejavamessage filename pickle file that saves the final dict structure after update.")
    print("")
    print("    --printjavamessage filename print java ignored java messages stored in pickle file filenam onto console and save into a text file.")
    print("")
    sys.exit(1)


def unknown_arg(s):
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()
        
def main(argv):
    """
    Main program.

    @return: none
    """
    global g_script_name
    global g_test_root_dir
    global g_new_messages_to_exclude
    global g_old_messages_to_remove
    global g_load_java_message_filename
    global g_save_java_message_filename
    global g_print_java_messages
    global g_java_messages_to_ignore_text_filename


    g_script_name = os.path.basename(argv[0])   # get name of script being run.


    # Override any defaults with the user's choices.
    parse_args(argv)

    g_load_java_message_filename = os.path.join(g_test_root_dir,g_load_java_message_filename)
    load_dict() # load previously stored java messages to g_ok_java_messages

    if len(g_new_messages_to_exclude) > 0:
        g_new_messages_to_exclude = os.path.join(g_test_root_dir,g_new_messages_to_exclude)
        add_new_message()   # add new java messages to exclude to dictionary

    if len(g_old_messages_to_remove) > 0:
        g_old_messages_to_remove = os.path.join(g_test_root_dir,g_old_messages_to_remove)
        remove_old_message()    # remove java messages from ignored list if users desired it

    g_save_java_message_filename = os.path.join(g_test_root_dir,g_save_java_message_filename)
    save_dict()                 # save the updated dict g_ok_java_messages to pickle file

    if g_print_java_messages:   # print java ignored messages to console and text file
        g_java_messages_to_ignore_text_filename = os.path.join(g_test_root_dir,g_java_messages_to_ignore_text_filename)
        print_dict()



if __name__ == "__main__":
    main(sys.argv)
