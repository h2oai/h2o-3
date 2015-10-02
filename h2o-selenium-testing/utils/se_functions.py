'''
SE advanced functions
'''

import time
import logging

from datetime import datetime
from selenium import webdriver
from selenium.webdriver.support.ui import Select
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By

from utils import config


def open_browser(agrs):
    return get_web_driver(agrs.browser, agrs.location)


def get_web_driver(browser, location):
    if 'chrome' == browser:
        driver = webdriver.Chrome(location)
    elif 'phantomjs' == browser:
        driver = webdriver.PhantomJS (location, config.port)
    elif 'firefox' == browser:
        driver = webdriver.Firefox()
    else:
        print 'Do not implemented for browser :', browser
        raise Exception('Do not implemented for browser :' + browser)

    driver.get(config.H2O_WEBSITE)
    driver.implicitly_wait(60)
    driver.set_window_size (1124, 850)

    return driver

# def get_web_driver(name, location):
#     '''
#     TODO: implement for other browsers too
#     '''
#     #driver = webdriver.PhantomJS (location, port = 65000)
#     driver = webdriver.Chrome(location)
#     driver.get(H2O_WEBSITE)
#     driver.implicitly_wait(60)
#     driver.set_window_size (1124, 850)
#
#     return driver


def wait_n_click(driver, xpath, timeout = 100):
    '''
    wait for element to be clickable and click it
    default timeout is set to 60 secs
    '''
    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    driver.find_element_by_xpath(xpath).click()


def wait4text(driver, xpath, text, timeout = 1, tries = 20):
    '''
    wait for text to be found and return
    '''
    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    driver.find_element_by_xpath(xpath)
    while tries > 0:
        if text in driver.find_element_by_xpath(xpath).text:
            break
        tries -= 1
        time.sleep(timeout)


def send_keys(driver, xpath, text, timeout = 60):
    '''
    find the element, clear content and input text
    '''
    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    element = driver.find_element_by_xpath(xpath)
    element.clear()
    element.send_keys(text)


def select(driver, xpath, text, timeout = 60):
    ''' select an option in a drop down list '''
    wait = WebDriverWait(driver, timeout)
    wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))

    Select(driver.find_element_by_xpath(xpath)).select_by_visible_text(text)


def get_text(driver, xpath):
    '''
     get text of an input control
    '''
    try:
        condition = driver.find_element_by_xpath(xpath)
        return condition.text

    except Exception:
            return 'is not available'



def set_value(driver, xpath, value):

    print 'Set_values:',xpath['xpath']
    '''
    Depends on type of xpath, perform the correspondent action
    For example: send keys to a input, click a button, ...
    Raise exception if this type is not supported
    '''
    if  value == '':
        return

    if 'input' == xpath['type']:
        send_keys(driver, xpath['xpath'], value)
        return

    if 'select' == xpath['type']:
        select(driver, xpath['xpath'], value)
        return

    if 'button' == xpath['type']:
        wait_n_click(driver, xpath['xpath'], timeout = 100)
        return

    if 'checkbox' == xpath['type']:
        wait_n_click(driver, xpath['xpath'], timeout = 100)
        return

    if 'text' == xpath['type']:
        return get_text(driver, xpath['xpath'])

    raise Exception('Unknown given xpath type: %s, xpath: %s' % (xpath['type'], xpath['xpath']))


def set_values(driver, xpaths, orders, configs):

    '''
    Automatically setting all given configs based on the orders.
    With optional configs, only set those if they are passed in via configs.
    One special case is button, those will be clicked on the series

    For example: ... tbd...

    '''
    # Either set a value or click a button
    for i in orders:
        if i in configs.keys():
            set_value(driver, xpaths[i], configs[i])

        elif 'button' == xpaths[i]['type']:
            set_value(driver, xpaths[i], None)
            time.sleep(1)

        elif 'text' == xpaths[i]['type']:
            return set_value(driver, xpaths[i], None)


def get_auto_configs(orders, csv_configs):
    cfgs = dict()

    for i in orders:
        for j in csv_configs:
            if i == j :
                if csv_configs[j] != '':
                    cfgs[i] = csv_configs[j]
                else:
                    cfgs[i] = ''
    return cfgs


def verify_progress_and_click(driver, xpath_text, xpath_click, timeout = 7000):
    '''
    wait progress bar is 100% then clicking on view
    '''
    start_time = datetime.now()
    while True:

        progress = driver.find_element_by_xpath(xpath_text['xpath']).text
        if '100' in progress:
            time.sleep(5)
            set_value(driver, xpath_click, {})
            return

        current_time = datetime.now()
        if(current_time - start_time).total_seconds() > timeout:
            print 'Progress is timeout'
            return

        time.sleep(1)


def get_log(test_case_id):
    log_filename = config.log_filename % test_case_id
    logging.basicConfig(filename=log_filename, level=logging.ERROR)


def get_screenshot(driver, test_case_id):
    screenshot = config.screenshot % test_case_id
    driver.get_screenshot_as_file(screenshot)
