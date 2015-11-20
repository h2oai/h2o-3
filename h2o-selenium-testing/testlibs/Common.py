import time

from datetime import datetime
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.support.ui import Select
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from utils import xpaths
from utils import Config
from utils import Selenium
from utils.xpaths import XPATHS
from utils.xpaths import NAVIGATE_XPATHS
from utils import DatasetCharacteristics as DS


def navigate_to(driver, submenu):
    """
    Navigate to sub_menu in main menu
    """
    orders = dict(
        model = ['deep_learning', 'distributed_rf','gradient_boosting_machine',
                 'generalized_linear_model', 'k_means', 'naive_bayes', 'list_all_models'],
        cell = ['cut_cell', 'copy_cell', 'paste_cell_above', 'paste_cell_below', 'delete_cell', 'undo_delete_cell',
                'move_cell_up', 'move_cell_down', 'insert_cell_above', 'insert_cell_below',
                'toggle_cell_input', 'toggle_cell_output', 'clear_cell_output'],
        flow = ['new_flow', 'cancel', 'open_flow', 'save_flow', 'make_a_copy', 'run_all_cells', 'run_all_cells_below',
                'toggle_all_cell_inputs', 'toggle_all_cell_outputs', 'clear_all_cell_outputs', 'download_this_flow'],
        help = ['assist_me', 'contents', 'keyboard_shortcuts', 'documentation', 'faq', 'h2o_ai',
                'h2o_on_github', 'report_an_issue', 'forum_ask_a_question', 'about', 'ok', 'upload'],
        admin = ['jobs', 'cluster_status', 'water_meter_cpu_meter', 'view_log', 'download_logs',
                 'create_synthetic_frame','stack_trace', 'network_test', 'profiler', 'timeline', 'shut_down'],
        score = ['predict', 'list_all_predictions'],
        data = ['import_files', 'upload_file', 'split_frame', 'list_all_frames']
    )

    for k, v in orders.iteritems():
        if submenu in v:
            Selenium.set_values(driver, [k, submenu], XPATHS=NAVIGATE_XPATHS)
            return
    raise Exception("Error has occurred")


def __import_file(driver, configs):
    """
    Import dataset
    """
    print 'Start import dataset'

    orders = ['file_path', 'file_search_btn', 'add_all_files_btn', 'import_sel_files_btn']
    cfgs = dict(
        file_path = '',
    )
    cfgs.update(configs)

    navigate_to(driver, 'import_files')

    Selenium.set_values(driver, orders, cfgs)

    print 'Import dataset is done'


def __parse_file(driver, configs, ds_chars, dataset_id ):
    """
    Parse dataset
    """
    print 'Start parse dataset'

    orders = ['destination_key', 'parse_btn' ]
    cfgs = dict(
        destination_key = '',
    )
    cfgs.update(configs)

    Selenium.set_values(driver, ['parse_file_test_btn'], cfgs)

    set_column_types(driver, ds_chars, dataset_id)

    Selenium.set_values(driver, orders, cfgs)

    Selenium.get_error_message(driver, XPATHS, ['failure'])

    print 'Parse dataset is done'


def __wait_progress_n_click(driver):
    """
    Wait progress bar is 100% then clicking on view
    """
    __verify_progress_and_click_predict(driver, XPATHS['progress_text'], XPATHS['view_btn'])


def __verify_progress_and_click_predict(driver, text_xpath_dict, click_xpath_dict, timeout=3600):
    """
    Wait progress bar is 100% then clicking on view
    """
    start_time = datetime.now()
    while True:

        progress = driver.find_element_by_xpath(text_xpath_dict[xpaths.xpath]).text
        if '100' in progress:
            Selenium.set_value(driver, click_xpath_dict)
            time.sleep(2)
            if Selenium.check_element_enable(driver, XPATHS['predict_btn']['xpath'], timeout=2):
                return

        current_time = datetime.now()
        if(current_time - start_time).total_seconds() > timeout:
            print 'Progress is timeout'
            raise Exception('Progress is timeout')

        time.sleep(1)


# Now: this function don't use
def new_flow(driver, command = 'create new notebook'):
    """
    Create new flow
    """
    print 'Start create new flow'

    if 'create new notebook' == command:
        Selenium.set_values(driver, ['flow_btn', 'new_flow_btn', 'create_new_notebook_btn'])
    else:
        Selenium.set_values(driver, ['flow_btn', 'new_flow_btn', 'cancel_new_flow_btn'])

    print 'New flow is created'


def import_parse_file(driver, configs, ds_chars, dataset_id):
    """
    Import and parse dataset
    """
    __import_file(driver, configs)
    __parse_file(driver, configs, ds_chars, dataset_id)
    __wait_progress_n_click(driver)


def split_file(driver, configs):
    """
    Split file to train frame and validate frame
    """
    print 'Start split file.'

    orders = ['split_btn', 'ratio', 'splitted_train_column', 'splitted_test_column', 'create_split_btn']
    cfgs = dict(
        ratio = '0.3',
        #Optional
        splitted_train_column = '',
        splitted_test_column = '',
    )
    cfgs.update(configs)

    Selenium.set_values(driver, orders, cfgs)

    print 'Split file is done'


def build_model(driver):
    """
    Click build model button
    """
    print 'Start build model.'

    Selenium.set_values(driver, ['build_model'])

    # Get error message when build model
    Selenium.get_error_message(driver, XPATHS,
                               ['general_error', 'error_message_requirement',
                                'error_message_validate', 'warning_message']
                               )
    __verify_progress_and_click_predict(driver, XPATHS['progress_text'], XPATHS['view_btn'])

    print 'Model is built successfully.'


def predict_model(driver, configs):
    """
    Predict model after built successfull
    """
    print 'Start predict model'

    orders = ['predict_btn', 'frame_select', 'predict_btn']
    cfgs = dict(
        frame_select = '',
    )
    cfgs.update(configs)

    Selenium.set_values(driver, orders, cfgs)

    print 'Predict is done'


def delete_all_DKV(args):
    """
    Delete all DKV
    """
    print 'Start delete all DKV'

    driver = Selenium.get_web_driver(args)
    Selenium.send_keys(driver, XPATHS['delete_all_DKV']['xpath'], 'deleteAll')
    elem = driver.find_element_by_xpath(XPATHS['delete_all_DKV']['xpath'])
    elem.send_keys(Keys.CONTROL + Keys.ENTER)

    print 'Delete all DKV successfully'

    driver.quit()


def set_column_types(driver, ds_chars, dataset_id):
    """
    Set types for all column
    """
    print 'Start set types for all column'

    column_types = ds_chars.get_data_of_column(dataset_id, DS.column_types).split(DS.regex_split_content)
    print 'List column types:', column_types
    column_names = ds_chars.get_data_of_column(dataset_id, DS.column_names).split(DS.regex_split_content)
    print 'List column names:', column_names

    nums_column = 0
    for column_name in column_names:
        column_type = column_types[column_names.index(column_name)]

        print 'Set %s type into %s column' % (column_type, column_name)

        # to upper the first character in column_type. EX: Enum
        column_type = column_type[0].upper() + column_type[1:].lower()

        xpath = XPATHS['parse_columns']['xpath'] % column_name

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
        if Config.nums_column == nums_column:
            # go to next page
            print 'Go to next page'
            Selenium.set_value(driver, XPATHS['next_page'], None)

            # reset nums_column
            nums_column = 0

    print 'Set column types is done.'




