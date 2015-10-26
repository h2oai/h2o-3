import copy
import time

from selenium import webdriver
from selenium.webdriver.support.ui import Select
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By

from utils.xpaths import XPATHS
from utils.xpaths import NAVIGATE_XPATHS
from utils import Config
from utils import DatasetCharacteristics as DS
from utils import se_functions


def navigate_to(driver, submenu):
    #Navigate to sub_menu in main menu
    orders = dict(
        model = ['deep_learning', 'distributed_rf',
                     'gradient_boosting_machine', 'generalized_linear_model',
                     'k_means', 'naive_bayes', 'list_all_models'],
        cell = ['cut_cell', 'copy_cell', 'paste_cell_above',
                    'paste_cell_below', 'delete_cell', 'undo_delete_cell',
                    'move_cell_up', 'move_cell_down', 'insert_cell_above'
                    'insert_cell_below', 'toggle_cell_input', 'toggle_cell_output',
                    'clear_cell_output'],
        flow = ['new_flow', 'cancel', 'open_flow', 'save_flow',
                    'make_a_copy', 'run_all_cells', 'run_all_cells_below',
                    'toggle_all_cell_inputs', 'toggle_all_cell_outputs',
                    'clear_all_cell_outputs', 'download_this_flow'],
        help = ['assist_me', 'contents', 'keyboard_shortcuts',
                    'documentation', 'faq', 'h2o_ai', 'h2o_on_github',
                    'report_an_issue', 'forum_ask_a_question', 'about',
                    'ok', 'upload'],
        admin = ['jobs', 'cluster_status', 'water_meter_cpu_meter',
                     'view_log', 'download_logs', 'create_synthetic_frame',
                     'stack_trace', 'network_test', 'profiler', 'timeline',
                     'shut_down'],
        score = ['predict', 'list_all_predictions'],
        data = ['import_files', 'upload_file', 'split_frame', 'list_all_frames']
    )

    for k, v in orders.iteritems():
        if submenu in v:
            se_functions.set_values(driver, NAVIGATE_XPATHS, [k, submenu], {})
            return
    raise Exception("Error has occurred")


# The private functions
def __import_file(driver, configs):
    #Import dataset

    print 'Start import dataset'
    orders = ['file_path', 'file_search_btn', 'add_all_files_btn', 'import_sel_files_btn']
    cfgs = dict(
        file_path = '',
    )
    cfgs.update(configs)

    navigate_to(driver, 'import_files')
    time.sleep(2)
    se_functions.set_values(driver, XPATHS, orders, cfgs)
    print 'Import dataset is done'


# def set_column_type(driver, column_name, column_type):
#
#     # to upper the first character in column_type. EX: Enum
#     column_type = column_type[0].upper() + column_type[1:].lower()
#
#     xpath = XPATHS['type_columns']['xpath'] % column_name
#     print xpath
#
#     # go to next page if the element do not exists
#     while not se_functions.exists_n_enable_element_by_xpath(driver, xpath):
#         if se_functions.exists_n_enable_element_by_xpath(driver, XPATHS['next_page']['xpath']):
#             try:
#                 se_functions.set_value(driver, XPATHS['next_page'], None)
#             except Exception as ex:
#                 print ex.__doc__
#                 print ex.message
#                 print str(ex)
#                 se_functions.set_value(driver, XPATHS['next_page'], None)
#         else:
#             # todo: should it be raised here?
#             print 'Can not found the %s column' % column_name
#             break
#
#     # set value
#     for i in range(1, 10):
#         print 'Try %s times' % i
#         try:
#             elements = driver.find_elements_by_xpath(xpath)
#
#             for element in elements:
#                 # find a element have title absolute equal column_name
#                 if element.get_attribute('value') == column_name:
#                     print element.tag_name
#                     etr = element.find_element_by_xpath(".//ancestor::tr")
#                     print etr.tag_name
#                     es = etr.find_element_by_tag_name("select")
#                     print es.tag_name
#
#                     print 'Set %s type into %s column' % (column_type, column_name)
#                     Select(es).select_by_visible_text(column_type)
#                     print 'Set successfully'
#
#                     return True
#
#         except Exception as ex:
#             print 'Cannot set value'
#             print ex.__doc__
#             print ex.message
#             print str(ex)
#
#     # todo: should it be raised here?
#     print 'Can not set %s type into %s column' % (column_type, column_name)


def set_column_types(driver, ds_chars, dataset_id):
    column_types = ds_chars.get_data_of_column(dataset_id, DS.column_types).split(DS.regex_split_content)
    print 'List column types:', column_types
    column_names = ds_chars.get_data_of_column(dataset_id, DS.column_names).split(DS.regex_split_content)
    print 'List column names:', column_names

    nums_column = 0
    for column_name in column_names:
        column_type = column_types[column_names.index(column_name)]

        # option 1
        # set_column_type(driver, column_name, column_type)

        # option 2
        print 'Set %s type into %s column' % (column_type, column_name)

        # to upper the first character in column_type. EX: Enum
        column_type = column_type[0].upper() + column_type[1:].lower()

        xpath = XPATHS['parse_columns']['xpath'] % column_name
        print xpath

        timeout = 10
        try:
            # se_functions.set_value(driver, XPATHS_TEMP, column_type)
            wait = WebDriverWait(driver, timeout)
            wait.until(EC.element_to_be_clickable((By.XPATH, xpath)))
            EC.visibility_of_element_located((By.XPATH, xpath))
            e = wait.until(EC.visibility_of_element_located((By.XPATH, xpath)))
            print 'The element is available'

            Select(e).select_by_visible_text(column_type)
            print 'Set successfully %s type into %s column' % (column_type, column_name)

        except Exception as ex:
            print 'Cannot set %s type into %s column' % (column_type, column_name)
            print ex.__doc__
            print str(ex)

        nums_column += 1
        if 15 == nums_column:
            # go to next page
            print 'go to next page'
            se_functions.set_value(driver, XPATHS['next_page'], None)

            # reset nums_column
            nums_column = 0


def __parse_file(driver, configs, additional_configs, dataset_id ):
    # Parse dataset
    print 'Start parse dataset'
    orders = ['destination_key', 'parse_btn' ]
    cfgs = dict(
        destination_key = '',
    )
    cfgs.update(configs)

    ds_chars = additional_configs['dataset_chars']

    se_functions.set_values(driver, XPATHS, ['parse_file_test_btn'], cfgs)

    print "Dataset_id: ",  dataset_id
    set_column_types(driver, ds_chars, dataset_id)

    se_functions.set_values(driver, XPATHS, orders, cfgs)

    print 'Parse dataset is done'


def __wait_progress_n_click(driver):
    #Wait progress bar is 100% then clicking on view
    se_functions.verify_progress_and_click(driver, XPATHS['progress_text'], XPATHS['view_btn'])


def __change_flow_name(driver, configs):
    #Change the name flow
    print 'Start change the name flow'
    orders = ['united_flow_btn', 'name_flow']
    cfgs = dict(
        name_flow = '',
    )
    cfgs.update(configs)

    se_functions.set_values(driver, XPATHS, orders, cfgs)
    print 'Name flow is changed sucessfully'


def __save_flow(driver, command = 'replace'):
    #Save flow
    print 'Start save flow'
    if 'replace' == command:
        se_functions.set_values(driver, XPATHS, ['flow_btn','save_flow_btn','replace_btn'], {})
    else:
        se_functions.set_values(driver, XPATHS, ['flow_btn', 'save_flow_btn', 'cancel_save_btn'], {})
    print 'Save flow is successful'


def __check_values(value, lower_bounds, upper_bounds, type):
    #Check values in or out of scope
    value = float(value)
    if lower_bounds <= value and value <= upper_bounds:
        print 'Values of %s'% type , 'is %s'% value, '. This value in scope'
        return True
    else:
        print 'Values of %s'% type , 'is %s'% value, '. This value out of scope'
        return False


# The public functions
def new_flow(driver, command = 'create new notebook'):
    #Create new flow
    print 'Start create new flow'
    if 'create new notebook' == command:
        se_functions.set_values(driver, XPATHS, ['flow_btn', 'new_flow_btn', 'create_new_notebook_btn'], {})
    else:
        se_functions.set_values(driver, XPATHS, ['flow_btn', 'new_flow_btn', 'cancel_new_flow_btn'], {})
    print 'New flow is created'


def import_parse_file(driver, configs, additional_configs, dataset_id):
    # Import and parse dataset
    __import_file(driver, configs)
    __parse_file(driver, configs, additional_configs, dataset_id)
    __wait_progress_n_click(driver)


def split_file(driver, configs):
    #Split file to train frame and validate frame
    print 'Start split file.'
    orders = ['split_btn', 'ratio', 'splitted_train_column', 'splitted_test_column', 'create_split_btn']
    cfgs = dict(
        ratio = '0.3',
        #Optional
        splitted_train_column = '',
        splitted_test_column = '',
    )
    time.sleep(2)
    cfgs.update(configs)

    se_functions.set_values(driver, XPATHS, orders, cfgs)
    print 'Split file is done'


def click_build_model(driver):
    #Click build model button
    time.sleep(2)
    se_functions.set_values(driver, XPATHS, ['build_model'], {})
    se_functions.get_error_message(driver, XPATHS, ['general_error', 'error_message_requirement', 'error_message_validate'])
    __wait_progress_n_click(driver)


def predict_file(driver, configs):
    #Predict model after built successfull
    print 'Start predict model'
    orders = ['predict_btn', 'frame_select', 'predict_btn']
    cfgs = dict(
        frame_select = '',
    )
    cfgs.update(configs)
    time.sleep(2)

    se_functions.set_values(driver, XPATHS, orders, cfgs)

    # Check the existence of value
    se_functions.wait_check_value(driver, XPATHS[Config.value_predict_list][Config.xpath])

    # Get MSE value
    get_values(driver, ['mse'])

    print 'Predict is done'


def get_values(driver, orders = []):
    #Get value after predict model successfull
    _orders= ['auc', 'mse', 'model', 'model_checksum', 'frame', 'frame_checksum', 'description']

    if not orders:   # If user don't have orders, set to _orders
        return se_functions.set_values(driver, XPATHS, _orders, {})

    for item in orders:
        if not item in _orders:
            raise Exception ('item in order is not available')
    return se_functions.set_values(driver, XPATHS, orders, {})


def check_values_AUC(driver, lower_bounds, upper_bounds):
    #Check value of AUC in or out scope
    value = get_values(driver, ['auc'])
    return __check_values(value, lower_bounds, upper_bounds)


def save_download_flow(driver):
    # Save and download flow
    __save_flow(driver)
    navigate_to(driver, 'download_this_flow')


def delete_frame(driver, command = 'delete'):
    #Delete frame
    print 'Start delete frame'
    if 'delete' == command:
        se_functions.set_values(driver, XPATHS, ['delete_btn', 'delete_frame'], {})
    else:
        se_functions.set_values(driver, XPATHS, ['delete_btn', 'cancel_frame'], {})
    print 'Delete frame is successful'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()




