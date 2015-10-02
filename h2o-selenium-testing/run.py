'''
For running tests, run this from command line.
'''
import sys
import os
import inspect
import argparse
import shutil
import time
import xml.etree.ElementTree as ET
import datetime
import logging
import subprocess
import sys
import codecs


from utils import se_functions
from utils.common import load_csv, append_xml, DatasetCharacteristics
from utils import config
from utils import Logger


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

    if os.path.exists(config.results):
        shutil.rmtree(config.results)

    mkdir(config.results)
    mkdir(config.results_logs)
    mkdir(config.results_screenshots)


def load_testsuite(suite_name):
    ''' load testsuite from csv file '''
    return load_csv(config.load_csv % suite_name)


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

    #are_headers_written = False

    result_filename = r'results/testng-results.xml'
    total_tc_Pass = 0
    total_tc_Fail = 0

    testng_results = ET.Element("testng_results")
    test = ET.SubElement(testng_results, "test")
    class1 = ET.SubElement(test, "class", name = 'h2o.testng.TestNG')

    for tc_id, test_cfgs in testsuite.iteritems():
        time_start_tc = datetime.datetime.now()

        # redirect output console to write log into TestNG format file
        sys.stdout = Logger.Logger(config.temporary_log_file_name)

        driver = se_functions.open_browser(additional_configs['args'])
        additional_configs['driver'] = driver

        testscript = test_cfgs.pop('testscript')
        classname = test_cfgs.pop('classname')

        testclass = load_testclass(testscript, classname)
        test_result = run_testcase(testclass, tc_id, test_cfgs, additional_configs)

        test_method_attributes =  dict()

        test_method_attributes['started-at'] = str(time_start_tc.strftime("%Y-%m-%dT%H:%M:%SZ"))
        test_method_attributes['name'] = tc_id
        test_method_attributes['status'] = test_result['result']
        time_finish_tc = datetime.datetime.now()
        test_method_attributes['finished-at'] = str(time_finish_tc.strftime("%Y-%m-%dT%H:%M:%SZ"))
        duration = (time_finish_tc - time_start_tc).seconds * 1000
        test_method_attributes['duration-ms'] = str(duration)

        test_method = ET.SubElement(class1, "test-method", test_method_attributes)

        params = ET.SubElement(test_method, 'params')

        param0 = ET.SubElement(params, 'param', index = '0')
        ET.SubElement(param0, 'value').text = "<![CDATA[Testcase: %s]]>" % tc_id
        param2 = ET.SubElement(params, 'param', index = '1')
        ET.SubElement(param2, 'value').text =  "<![CDATA[Train Dataset: %s]]>" % test_result['train_dataset_id']
        param3 = ET.SubElement(params, 'param', index = '2')
        ET.SubElement(param3, 'value').text =  "<![CDATA[Validate Dataset: %s]]>" % test_result['validate_dataset_id']
        param4 = ET.SubElement(params, 'param', index = '3')
        ET.SubElement(param4, 'value').text =  "<![CDATA[MSE: %s]]>" % test_result['mse']

        reporter_output = ET.SubElement(test_method, "reporter_output")

        #get log from log file
        ET.SubElement(reporter_output, 'line').text =  "<![CDATA[%s]]>" % (sys.stdout.read())



        if "PASS" == test_result['result']:
            total_tc_Pass += 1
        else:
            total_tc_Fail += 1

        total = total_tc_Pass + total_tc_Fail


        driver.quit()

        testng_results.set('Passed',str(total_tc_Pass))
        testng_results.set('Failed',str(total_tc_Fail))
        testng_results.set('total', str(total))


        append_xml(result_filename, testng_results)


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
        default = r'D:\application\phantomjs-1.9.7-windows\phantomjs.exe')

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
    print 'parse arguments: ', args

    # Create web driver instance, load dataset characteristics and pass them in as
    additional_configs = dict(
        args = args,
        dataset_chars = DatasetCharacteristics(config.dataset_chars),
    )

    # Load the testsuite and run all its testcases
    # Test results will be stored after each test is done
    testsuite = load_testsuite(args.testsuite)
    run_testsuite(testsuite, additional_configs)


if __name__ == '__main__':
    main()
