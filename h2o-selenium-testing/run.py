'''
For running tests, run this from command line.
'''

import sys
import os
import inspect
import argparse
import shutil
import time

from utils.common import load_csv, append_csv, DatasetCharacteristics
from utils.se_functions import get_web_driver


def load_testclass(modulename, classname):
    '''
    Dynamically load a test class by its name from 'tests' library under current working directory
    Assumption: module and class has the same name... ie. tests.drf.drf
    '''
    # In order to load the class automatically, current directory/tests should be added to sys.path
    # and this should be done only once
    module_dir = os.path.join(os.getcwd(), 'tests')

    if not module_dir in sys.path:
        sys.path.append(os.path.join(os.getcwd(), 'tests'))

    module = __import__(modulename, fromlist=[''])

    for clazzname, clazz in inspect.getmembers(module, inspect.isclass):
        if classname == clazzname:
            return clazz

    raise Exception('Unable to find class %s in tests.%s library.' % (classname, classname))


def mkdir(directory):
    ''' make a directory if it is not exist '''
    if not os.path.exists(directory):
        os.makedirs(directory)


def setup_ouput_directories():
    '''
    Remove all old results and create new /results directory
    '''
    if os.path.exists('results'):
        shutil.rmtree('results')

    mkdir(r'results')
    mkdir(r'results/logs')
    mkdir(r'results/screenshots')


def load_testsuite(suite_name):
    ''' load testsuite from csv file '''
    return load_csv('test_data/%s.csv' % suite_name)


def run_testcase(testclass, tc_id, configs, additional_configs):
    '''
    create an instance of testclass and call setup, test and clean_up sequentially
    '''
    test_obj = testclass(tc_id, configs, additional_configs)

    test_obj.setup()
    result = test_obj.test()
    test_obj.clean_up()
    return result



def run_testsuite(testsuite, additional_configs):
    '''
    For each test case in the suite, run it.

    Testsuite sample:
      {'deep_learning_01': {'activation_select': 'Tanh',
                      'alpha': '0.3',
                      'epochs': '0.1',
                      'lamda': '0.002',
                      'max_iterations': '100',
                      'testscript': 'ts1',
                      'classname': 'ts1',
                      'train_dataset': 'iris_train1',
                      'validate_dataset': 'iris_validation1'},
      }
    '''
    result_filename = r'results/test_results.csv'
    are_headers_written = False

    total_tc_Pass = 0
    total_tc_Fail = 0
    total = 0
    percentPass = 0
    percentFail = 0

    for tc_id, test_cfgs in testsuite.iteritems():
        testscript = test_cfgs.pop('testscript')
        classname = test_cfgs.pop('classname')

        testclass = load_testclass(testscript, classname)
        test_result = run_testcase(testclass, tc_id, test_cfgs, additional_configs)

        if not are_headers_written:
            append_csv(result_filename, ','.join(['testcase_id', 'result', 'message', 'mse', 'auc',
                                                  'train_dataset_id', 'validate_dataset_id',
                                                  'distribution', 'sparse']))
            are_headers_written = True

        append_csv(result_filename, ','.join([tc_id, test_result['result'], test_result['message'], test_result['mse'], test_result['auc'],
                                              test_result['train_dataset_id'], test_result['validate_dataset_id'],
                                              test_result['distribution'], test_result['sparse']]))


        if "PASS" == test_result['result']:
            total_tc_Pass += 1
        else:
            total_tc_Fail += 1

        total = total_tc_Pass + total_tc_Fail
        percentPass = total_tc_Pass * 100/ total
        percentFail = total_tc_Fail * 100/ total
        time.sleep(3)

    append_csv(result_filename, ','.join(['Total Tc Pass', 'Total TC Fail', 'Total', 'Percent Pass', 'Percent Fail']))
    append_csv(result_filename, ','.join([str(total_tc_Pass), str(total_tc_Fail), str(total), str(percentPass), str(percentFail)]))


def parse_arguments():
    ''' define all arguments '''
    parser = argparse.ArgumentParser(description='Run Selenium tests')
    parser.add_argument(
        '-b',
        dest = 'browser',
        help = 'Supported browsers: firefox, chrome, phantomjs',
        required = False,
        default = 'phantomjs',
        choices = ['firefox', 'chrome', 'phantomjs'])

    parser.add_argument(
        '-l',
        dest = 'location',
        help = 'Browser installed location on hard drive',
        required = False,
        default = 'C:\phantomjs-1.9.8-windows\phantomjs-1.9.8-windows/phantomjs.exe')

    parser.add_argument(
        '-t',
        dest = 'testsuite',
        help = 'Testsuite to be ran',
        required = True)

    return  parser.parse_args()


def unit_test():
    ''' unit tests '''
    from pprint import pprint as pp

    print load_testclass('deep_learning_basic', 'DeepLearningBasic')

    args = parse_arguments()
    pp(load_testsuite(args.testsuite)) # python run.py -t deep_learning

def main():
    '''
    test runner - parse input arguments, run test suite and save test results to /results
    '''
    # Setup /results directory
    setup_ouput_directories()

    args = parse_arguments()

    # Create web driver instance, load dataset characteristics and pass them in as
    additional_configs = dict(
        driver = get_web_driver(args.browser, args.location),
        dataset_chars = DatasetCharacteristics('dataset_characteristics.csv'),
    )

    # Load the testsuite and run all its testcases
    # Test results will be stored after each test is done
    testsuite = load_testsuite(args.testsuite)
    run_testsuite(testsuite, additional_configs)


if __name__ == '__main__':
    main()
