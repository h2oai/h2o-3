import subprocess

from utils import Constant
from utils import DatasetCharacteristics as DS


def compare_string(str1, str2):
    """
    :param str1: string
    :param str2: string
    :return: -1 if str1 < str2, 0 if str1 == str2, 1 if str1 > str2
    """

    # validate parameter
    if type(str1) is not str or type(str2) is not str:
        print 'Parameter required a string type'
        raise TypeError('Parameter required a string type')

    result = 0

    if len(str1) > len(str2):
        result = 1
    elif len(str1) < len(str2):
        result = -1
    elif str1 > str2:
        result = 1
    elif str1 < str2:
        result = -1

    return result


def kill_phantomjs(phantomjs_location):
    print 'kill phantomjs server'
    command = "kill $(ps aux | grep '" + phantomjs_location + " --webdriver=65000' | awk '{print $2}')"
    subprocess.Popen(command, shell=True)


def parse_boolean(value):
    value = value.lower()

    if 'x' == value or 'y' == value or 'yes' == value:
        return True
    return False


def get_value_from_2list(keys, values):
    if 0 == values.count('x'):
        value = keys[0]
    else:
        value = keys[values.index('x')]
    return value


def is_regression_testcase(test_case, ds_chars):
    """
    Check testcase is regression or not.
    Return True if testcase is regression, otherwise return False
    """
    print 'Check regression testcase'
    ds = ds_chars[test_case[Constant.train_dataset_id]]

    is_regression_tc = False
    if test_case['regression'] == 'x':
        is_regression_tc = True
    elif test_case['classification'] == 'x':
        is_regression_tc = False
    else:
        colunm_names = ds[DS.column_names].split(DS.regex_split_content)
        colunm_types = ds[DS.column_types].split(DS.regex_split_content)
        response_colunm_name = ds[DS.target]
        response_column_type = colunm_types[colunm_names.index(response_colunm_name)]
        if response_column_type.lower() != 'enum':
            is_regression_tc = True

    if is_regression_tc:
        print 'Testcase type is regression'
    else:
        print 'Testcase type is classification'
    return is_regression_tc