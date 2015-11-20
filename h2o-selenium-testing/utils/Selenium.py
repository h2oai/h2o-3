"""
selenium advanced functions
"""

import time
from selenium import webdriver
from selenium.webdriver.support.ui import Select
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By

from utils import Config
from utils import Constant
from utils import Common
from utils import xpaths
from utils.xpaths import XPATHS as COMMON_XPATHS


def get_web_driver(agrs):
    browser = agrs.browser
    location = agrs.location

    print 'Open browser %s at %s' % (browser, location)

    if 'chrome' == browser:
        driver = webdriver.Chrome(location)
    elif 'phantomjs' == browser:
        driver = webdriver.PhantomJS (location, Config.port)
    elif 'firefox' == browser:
        driver = webdriver.Firefox()
    else:
        print 'Do not implemented for browser :', browser
        raise Exception('Do not implemented for browser :' + browser)

    driver.implicitly_wait(60)
    driver.set_page_load_timeout(60)
    driver.set_window_size(1124, 850)

    for i in range(3):
        try:
            driver.get(Config.h2o_website)
            print 'Load page successfully'
            return driver
        except Exception as e:
            print e.__doc__
            print str(e)

            time.sleep(5)
    print "Can't load page:", Config.h2o_website
    raise Exception(e)


def send_keys(driver, xpath, text, timeout=60):
    """
    find the element, clear content and input text
    """

    # If the value in testcase is empty, don't set it, use default value
    if text == '':
        return

    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    element = driver.find_element_by_xpath(xpath)
    element.clear()
    element.send_keys(text)

def select(driver, xpath, text, timeout=60):
    """ select an option in a drop down list """

    # If the value in testcase is empty, don't set it, use default value
    if text == '':
        return

    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    Select(driver.find_element_by_xpath(xpath)).select_by_visible_text(text)


def wait_n_click(driver, xpath, timeout=100):
    """
    Wait for element to be clickable and click it
    Default timeout is set to 100 secs
    """

    wait = WebDriverWait(driver, timeout)
    wait.until(EC.visibility_of_element_located((By.XPATH, xpath)))
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    driver.find_element_by_xpath(xpath).click()


def set_value_checkbox(driver, xpath, value, timeout=60):
    """
    Wait for element to be clickable and set value
    Default timeout is set to 60 secs
    Value: True or False
    """
    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    element = driver.find_element_by_xpath(xpath)

    if (not value and element.is_selected()) or (value and not element.is_selected()):
        element.click()


# todo: enhancement timeout to decrease performance
def check_element_enable(driver, xpath, timeout=30):
    """
    Wait until element to be clickable and can enable
    :param driver:
    :param xpath:
    :param timeout: default 30 (second)
    :return: True if element exists, otherwise return False
    """
    try:
        wait = WebDriverWait(driver, timeout)
        wait.until(EC.visibility_of_element_located((By.XPATH, xpath)))
        wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))
        driver.find_element_by_xpath(xpath).is_enabled()
    except Exception as ex:
        print 'Element is not exists'
        print ex.__doc__
        print str(ex)
        return False
    return True


def get_value(driver, xpath_dict={}, xpath_key=''):
    """
    Get text of a text element
    Raise exception if this type is not supported

    xpath_dict: dict(
        xpath = xpath,
        type = xpath_type,
    )
    """

    if len(xpath_dict) == 0:
        xpath_dict = COMMON_XPATHS[xpath_key]

    xpath = xpath_dict[xpaths.xpath]

    print 'get value: ', xpath

    if xpath_dict[xpaths.type] == Constant.text:
        if check_element_enable(driver, xpath):
            element = driver.find_element_by_xpath(xpath)
            return element.text
        else:
            print 'Element is not exists'
            raise Exception('Element is not exists')
    else:
        print 'Do not implement type: ', xpath_dict[xpaths.type]
        raise Exception('Do not implement type: ', xpath_dict[xpaths.type])


def set_value(driver, xpath_dict, value=None):
    """
    Depends on type of xpath, perform the correspondent action
    For example: send keys to a input, click a button, ...
    Raise exception if this type is not supported
    """

    xpath = xpath_dict[xpaths.xpath]
    type = xpath_dict[xpaths.type]

    print 'Set_value:', xpath

    if Constant.input == type:
        send_keys(driver, xpath, value)
    elif Constant.select == type:
        select(driver, xpath, value)
    elif Constant.button == type:
        wait_n_click(driver, xpath)
    elif Constant.checkbox == type:
        set_value_checkbox(driver, xpath, Common.parse_boolean(value))
    else:
        raise Exception('Unknown given xpath type: %s, xpath: %s' % (type, xpath))


def set_values(driver, orders, values={}, XPATHS=COMMON_XPATHS):
    """
    Automatically setting all given configs based on the orders.
    With optional configs, only set those if they are passed in via configs.
    One special case is button, those will be clicked on the series

    For example: ... tbd...
    """

    # Either set a value or click a button
    print 'Start set value list'
    for xpath_key in orders:
        value = None
        if xpath_key in values.keys():
            value = values[xpath_key]

        set_value(driver, XPATHS[xpath_key], value)
    print 'Set value is successfully.'


# todo: move it to Common.py
def get_auto_configs(orders, csv_configs):
    cfgs = dict()
    for model_parameter in orders:
        if model_parameter in csv_configs.keys():
            cfgs[model_parameter] = csv_configs[model_parameter]

    return cfgs


# todo: refactor it
def get_error_message(driver, xpaths, orders):
    error = ''
    for i in orders:
        if check_element_enable(driver, xpaths[i]['xpath'], timeout=10):
            error += get_text_all(driver, xpaths[i]['xpath'])

    if error != '':
        print error
        raise Exception(error)


# todo: refactor it
def get_text_all(driver, xpath):
    """
    get text of an input control
    """
    result = ''
    try:
        conditions = driver.find_elements_by_xpath(xpath)
        for c in conditions:
            print c.text
            result += c.text
        return result

    except Exception as e:
        print e.__doc__
        print str(e)
