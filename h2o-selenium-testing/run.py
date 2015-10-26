'''
For running tests, run this from command line.
'''
import argparse
import datetime
import inspect
import os
import time
import shutil
import sys


from utils.common import load_csv
from utils import Constant
from utils import Config
from utils import ConfigAlgorithm
from utils import DatasetCharacteristics as DS
from utils import Logger
from utils import se_functions
from utils import TestNGResult


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

    if os.path.exists(Config.results):
        shutil.rmtree(Config.results)

    mkdir(Config.results)
    mkdir(Config.results_logs)
    mkdir(Config.results_screenshots)


def load_testsuite(suite_name):
    ''' load testsuite from csv file '''
    if 'drf' in suite_name:
        return load_csv(Config.load_csv % suite_name, ConfigAlgorithm.drf_row, ConfigAlgorithm.drf_column)
    elif 'gbm' in suite_name:
        return load_csv(Config.load_csv % suite_name, ConfigAlgorithm.gbm_row, ConfigAlgorithm.gbm_column)
    elif 'glm' in suite_name:
        return load_csv(Config.load_csv % suite_name, ConfigAlgorithm.glm_row, ConfigAlgorithm.glm_column)
    elif 'dl' in suite_name:
        return load_csv(Config.load_csv % suite_name, ConfigAlgorithm.dl_row, ConfigAlgorithm.dl_column)
    else:
        raise Exception ("Uncorrect Testsuite Name, must be cotains 'drf' or 'gbm' or 'glm' or 'dl'")


def check_model_types(test_case, ds_chars):
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
    # todo: validate more here.

    print result
    return result


def run_testcase(testcase_id, testcase, args_params):
    '''
    create an instance of testclass and call setup, test and clean_up sequentially
    '''

    # set up testcase
    driver = se_functions.open_browser(args_params['args'])
    # todo: refactor it
    args_params['driver'] = driver

    testscript = testcase.pop('testscript')
    classname = testcase.pop('classname')

    # use reflection to get CLASS for run testcase
    testclass = load_testclass(testscript, classname)

    test_obj = testclass(testcase_id, testcase, args_params)

    try:
        # run testcase
        test_obj.setup()
        result = test_obj.test()
        test_obj.clean_up()

    except Exception as ex:
        print ex.message
        result = {
            Constant.train_dataset_id : testcase[Constant.train_dataset_id],
            Constant.validate_dataset_id : testcase[Constant.validate_dataset_id],
            'result' : Constant.testcase_result_status_invalid,
            'message' : ex.message,
            'mse' : 'N/A',
        }
        # todo: do not handle it
        # raise ex

    finally:
        print 'Closing browser'
        driver.close()
        driver.quit()
        time.sleep(3)
        print 'Closed browser'

    return result


def run_testsuite(testsuite, dataset_characteristic, additional_configs):
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
    total_tc_pass = 0
    total_tc_fail = 0
    total_tc_invalid = 0

    testng_results = TestNGResult.TestNGResult()

    list_testcase_id = list(testsuite.keys())
    list_testcase_id.sort(key=len)
    # for tc_id, testcase in testsuite.iteritems():
    for tc_id in list_testcase_id:
        testcase = testsuite.pop(tc_id)
        time_start_tc = datetime.datetime.now()

        # redirect output console to write log into TestNG format file
        sys.stdout = Logger.Logger(Config.temporary_log_file_name)

        validate_message = check_model_types(testcase, dataset_characteristic.get_dataset())

        if '' == validate_message:
            test_result = run_testcase(tc_id, testcase, additional_configs)
        else:
            test_result = {
                Constant.train_dataset_id : testcase[Constant.train_dataset_id],
                Constant.validate_dataset_id : testcase[Constant.validate_dataset_id],
                'result' : Constant.testcase_result_status_invalid,
                'message' : validate_message,
                'mse' : 'N/A',
            }

        if Constant.testcase_result_status_pass == test_result['result']:
            total_tc_pass += 1
        elif Constant.testcase_result_status_fail == test_result['result']:
            total_tc_fail += 1
        else:
            total_tc_invalid += 1

        time_finish_tc = datetime.datetime.now()
        log = sys.stdout.read()

        testng_results. add_test_method_n_child(tc_id, time_start_tc, time_finish_tc, test_result, log)
        testng_results.set_summary_attribute(total_tc_invalid, total_tc_fail, total_tc_pass)
        testng_results.write()


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

    return parser.parse_args()


def main():
    '''
    test runner - parse input arguments, run test suite and save test results to /results
    '''
    args = parse_arguments()
    print 'parse arguments: ', args

    if not se_functions.check_website_connect():
        print 'FlowUI is not available'
        sys.exit(0)

    # Setup /results directory
    setup_ouput_directories()

    # Load the testsuite
    testsuite = load_testsuite(args.testsuite)
    # load the dataset characteristic
    dataset_chars = DS.DatasetCharacteristics()

    # Create web driver instance, load dataset characteristics and pass them in as
    additional_configs = dict(
        args = args,
        # to use in tests folder
        dataset_chars = dataset_chars
    )

    # Run all its testcases
    run_testsuite(testsuite, dataset_chars, additional_configs)


if __name__ == '__main__':
    main()
