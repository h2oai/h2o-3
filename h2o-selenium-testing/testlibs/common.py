import time

from testlibs.xpaths import XPATHS
from testlibs.xpaths import NAVIGATE_XPATHS
from utils import se_functions


def __change_flow_name(driver, configs):
    #Change the name of flow
    print 'Start change the name of flow'
    orders = ['united_flow_btn', 'name_flow']
    cfgs = dict(
        name_flow = '',
    )
    cfgs.update(configs)

    se_functions.set_values(driver, XPATHS, orders, cfgs)
    print 'Flow name is changed'


def __import_file(driver, configs):
    #Import file dataset
    print 'Start import file dataset'
    orders = ['file_path', 'file_search_btn', 'add_all_files_btn', 'import_sel_files_btn']
    cfgs = dict(
        file_path = '',
    )
    cfgs.update(configs)

    navigate_to(driver, 'import_files')
    time.sleep(2)
    se_functions.set_values(driver, XPATHS, orders, cfgs)
    print 'The end import dataset file'


def __parse_file(driver, configs):
    # Parse dataset file
    print 'Start parse file'
    orders = ['parse_file_test_btn', 'destination_key', 'parse_btn']
    cfgs = dict(
        destination_key = '',
    )
    cfgs.update(configs)

    se_functions.set_values(driver, XPATHS, orders, cfgs)
    print 'The end parse file'


def import_parse_file(driver, configs):
    __import_file(driver, configs)
    __parse_file(driver, configs)
    wait_progress_n_click(driver)


def wait_progress_n_click(driver):
    #Wait progress bar is 100% then clicking on view
    se_functions.verify_progress_and_click(driver, XPATHS['progress_text'], XPATHS['view_btn'])


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
    print 'The end split file'


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
    get_values(driver, ['mse'])
    print 'The end predict file'


def get_values(driver, orders = []):
    #Get value required after predict model successfull
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
    return check_values(value, lower_bounds, upper_bounds)


def check_values(value, lower_bounds, upper_bounds, type):
    #Check values in or out of scope
    value = float(value)
    if lower_bounds <= value and value <= upper_bounds:
        print 'Values of %s'% type , 'is %s'% value, '. This value in scope'
        return True
    else:
        print 'Values of %s'% type , 'is %s'% value, '. This value out of scope'
        return False


def click_build_model(driver):
    #Click build model button
    time.sleep(2)
    se_functions.set_values(driver, XPATHS, ['build_model'], {})


def delete_frame(driver, command = 'delete'):
    #Delete frame
    print 'Start delete frame'
    if 'delete' == command:
        se_functions.set_values(driver, XPATHS, ['delete_btn', 'delete_frame'], {})
    else:
        se_functions.set_values(driver, XPATHS, ['delete_btn', 'cancel_frame'], {})
    print 'Delete frame is successful'


def __save_flow(driver, command = 'replace'):
    #Save flow after built successfull
    print 'Start save flow'
    if 'replace' == command:
        se_functions.set_values(driver, XPATHS, ['flow_btn','save_flow_btn','replace_btn'], {})
    else:
        se_functions.set_values(driver, XPATHS, ['flow_btn', 'save_flow_btn', 'cancel_save_btn'], {})
    print 'Save flow is successful'


def save_download_flow(driver):
    #Download flow after save successfull
    __save_flow(driver)
    navigate_to(driver, 'download_this_flow')


def new_flow(driver, command = 'create new notebook'):
    #Create new flow
    print 'Start create new flow'
    if 'create new notebook' == command:
        se_functions.set_values(driver, XPATHS, ['flow_btn', 'new_flow_btn', 'create_new_notebook_btn'], {})
    else:
        se_functions.set_values(driver, XPATHS, ['flow_btn', 'new_flow_btn', 'cancel_new_flow_btn'], {})
    print 'New flow is created'


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


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()




