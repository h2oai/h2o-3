"""
For running tests, run this from command line.
"""
import argparse
import datetime
import inspect
import os
import time
import shutil
import sys


from utils import CSV
from utils import Common
from utils import Constant
from utils import Config
from utils import DatasetCharacteristics as DS
from utils import H2oServer as HS
from utils import Logger
from utils import Selenium
from utils import TestNGResult
from tests import ModelBasic
from testlibs import Common as CM


def load_testclass(modulename, classname):
    """
    Dynamically load a test class by its name from 'tests' library under current working directory
    Assumption: module and class has the same name... ie. tests.drf.drf
    """

    # In order to load the class automatically, current directory/tests should be added to sys.path
    # and this should be done only once
    module_dir = os.path.join(os.getcwd(), 'tests')

    if not module_dir in sys.path:
        sys.path.append(module_dir)

    module = __import__(modulename, fromlist=[''])

    for clazzname, clazz in inspect.getmembers(module, inspect.isclass):
        if classname == clazzname:
            return clazz

    raise Exception('Unable to find class %s in tests.%s library.' % (classname, classname))


def mkdir(directory):
    """ Make a directory if it is not exist """
    if not os.path.exists(directory):
        os.makedirs(directory)


def setup_ouput_directories():
    """
    Remove all old results and create new /results directory
    """

    # Remove old result if it exists
    if os.path.exists(Config.results):
        shutil.rmtree(Config.results)

    mkdir(Config.results)
    mkdir(Config.results_logs)
    mkdir(Config.results_screenshots)


def validate_testcase(test_case, ds_chars):
    print 'Start validate testcase'

    result = ''

    ds = ds_chars[test_case[Constant.train_dataset_id]]
    colunm_names = ds[DS.column_names].split(DS.regex_split_content)
    colunm_types = ds[DS.column_types].split(DS.regex_split_content)
    response_colunm_name = ds[DS.target]
    response_column_type = colunm_types[colunm_names.index(response_colunm_name)]

    if test_case['classification'] == 'x' and response_column_type.lower() != 'enum':
        result = 'This is classification testcase but response column type is not Enum'
    elif test_case['regression'] == 'x' and response_column_type.lower() == 'enum':
        result = 'This is regression testcase but response column type is Enum'
    elif not('glm' in test_case['suitename'])\
            and Common.is_regression_testcase(test_case, ds_chars) and test_case['balance_classes'] == 'x':
        result = 'This is regression testcase but balance classes value is checked'
    # todo: validate more here.

    print result
    return result


def run_testcase(testcase_id, testcase, dataset_characteristic, args):
    """
    create an instance of testclass and call test and clean_up sequentially
    """

    result = {
        Constant.train_dataset_id: testcase[Constant.train_dataset_id],
        Constant.validate_dataset_id: testcase[Constant.validate_dataset_id],
        'result': Constant.testcase_result_status_invalid,
        'message': 'N/A',
        'mse': 'N/A',
    }

    try:
        driver = Selenium.get_web_driver(args)

        test_obj = ModelBasic.ModelBasic(testcase_id, testcase, driver, dataset_characteristic)

        # run testcase
        result = test_obj.test()
        test_obj.clean_up()

    except Exception as ex:
        print ex.__doc__
        print str(ex)
        result['message'] = str(ex)

    finally:
        try:
            print 'Closing driver'
            driver.close()
            driver.quit()

            # To phantomjs clear catch & memory... after run each testcase
            time.sleep(3)
            print 'Closed driver'

        except Exception as ex:
            print ex.__doc__
            print str(ex)
            print 'Cannot close driver'

    return result


def run_testsuite(testsuite, dataset_characteristic, args):
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

    # start h2o server
    h2o_server = HS.H2oServer()

    total_tc_invalid = 0
    total_tc_fail = 0
    total_tc_pass = 0
    total_tc = 0

    testng_results = TestNGResult.TestNGResult()

    list_testcase_id = list(testsuite.keys())
    list_testcase_id.sort(cmp=Common.compare_string)
    for tc_id in list_testcase_id:
        testcase = testsuite.pop(tc_id)
        time_start_tc = datetime.datetime.now()

        # redirect output console to write log into TestNG format file
        # todo: please move sys.stdout into Logger class
        sys.stdout = Logger.Logger(Config.temporary_log_file_name)

        validate_message = validate_testcase(testcase, dataset_characteristic.get_dataset())

        if '' == validate_message:
            test_result = run_testcase(tc_id, testcase, dataset_characteristic, args)
        else:
            test_result = {
                Constant.train_dataset_id : testcase[Constant.train_dataset_id],
                Constant.validate_dataset_id : testcase[Constant.validate_dataset_id],
                'result' : Constant.testcase_result_status_invalid,
                'message' : validate_message,
                'mse' : 'N/A',
            }

        total_tc += 1
        if Constant.testcase_result_status_pass == test_result['result']:
            total_tc_pass += 1
        elif Constant.testcase_result_status_fail == test_result['result']:
            total_tc_fail += 1
        else:
            total_tc_invalid += 1

        time_finish_tc = datetime.datetime.now()
        log = sys.stdout.read()

        # write log
        testng_results.add_test_method_n_child(tc_id, time_start_tc, time_finish_tc, test_result, log)
        testng_results.set_summary_attribute(total_tc_invalid, total_tc_fail, total_tc_pass)
        testng_results.write()

        # restart h2o server
        if total_tc % Config.test_case_num == 0:
            CM.delete_all_DKV(args)
            # h2o_server.restart(args)

            if args.location == 'phantomjs':
                Common.kill_phantomjs(args.location)

    # todo: refactor it
    # stop h2o server
    # h2o_server.stop_by_terminal()
    h2o_server.stop_by_UI(args)


def parse_arguments():
    """ define all arguments """
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

    return parser.parse_args()


def main():
    """
    test runner - parse input arguments, run test suite and save test results to /results
    """
    args = parse_arguments()
    print 'parse arguments: ', args

    # Setup /results directory
    setup_ouput_directories()

    # Load the testsuite
    testsuite = CSV.load_testsuite(args.testsuite)
    # load the dataset characteristic
    dataset_chars = DS.DatasetCharacteristics()

    # Run all its testcases
    run_testsuite(testsuite, dataset_chars, args)


if __name__ == '__main__':
    main()
