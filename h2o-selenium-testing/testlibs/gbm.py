from testlibs import common
from utils import se_functions
from utils.se_functions import set_values
from utils.se_functions import wait_n_click
from utils.xpaths import GBM_XPATHS
from utils.xpaths import XPATHS

ORDERS = [
    'train_dataset_id', 'validate_dataset_id', 'nfolds', 'response_column',
    'ignore_const_cols', 'ntrees', 'max_depth', 'min_rows', 'nbins',
    'nbins_cats', 'seed', 'learn_rate', 'distribution', 'sample_rate',
    'col_sample_rate',

    # ADVANCED
    'score_each_iteration', 'fold_assignment', 'fold_column',
    'offset_column', 'weights_column', 'balance_classes',
    'max_confusion_matrix_size', 'max_hit_ratio_k',
    'r2_stopping', 'build_tree_one_node', 'tweedie_power',
    'checkpoint',


    # EXPERT
    'keep_cross_validation_predictions', 'class_sampling_factors',
    'max_after_balance_size', 'nbins_top_level',
    'train_dataset_id_split', 'validate_dataset_id_split'
    ]


def create_model_gbm(driver, configs = {}):
    #Param is required
    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
        ignored_columns = ''
    )

    cfgs.update(configs)

    print '---Select generalized_linear_model model:'
    common.navigate_to(driver, 'gradient_boosting_machine')

    print '---Set value for param:'
    set_values(driver, GBM_XPATHS, ORDERS, cfgs)
    print 'Check on ignored_columns from file config'
    if '' != cfgs.get('ignored_columns'):
        list_ignored_columns = cfgs.get('ignored_columns').split(',')
        for ignored_columns in list_ignored_columns:
            while True:
                if se_functions.exists_element(driver, GBM_XPATHS['ignored_columns']['xpath'] % ignored_columns.strip()):
                    wait_n_click(driver, GBM_XPATHS['ignored_columns']['xpath'] % ignored_columns.strip(), timeout = 10)
                    print "Click on Ignored columns successfully"
                    break
                else:
                    if True == se_functions.check_element_enable(driver, XPATHS['next_button_ignored']):
                        print "Click on the next page"
                        se_functions.set_value(driver, XPATHS['next_button_ignored'], None)
                    else:
                        print "Incorrect ignored columns"
                        raise Exception ("Incorrect ignored columns")

    print '---Click build model:'
    common.click_build_model(driver)
    print 'Model is created done...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()



