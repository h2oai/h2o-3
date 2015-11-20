import copy

from testlibs import Common

from utils.Selenium import set_values
from utils.xpaths import GBM_XPATHS


# GBM config
row = 5
column = 2


ORDERS = [
    'train_dataset_id', 'validate_dataset_id', 'nfolds', 'response_column',
    'ignore_const_cols', 'ntrees', 'max_depth', 'min_rows', 'nbins',
    'nbins_cats',
    # todo: research it
    # 'seed',
    'learn_rate', 'distribution', 'sample_rate',
    'col_sample_rate',

    # ADVANCED
    'score_each_iteration',
    # todo: research it
    # 'fold_assignment',
    'fold_column', 'offset_column', 'weights_column', 'balance_classes',
    'max_confusion_matrix_size', 'max_hit_ratio_k',
    'r2_stopping', 'build_tree_one_node',
    # todo: research it
    # 'tweedie_power', 'checkpoint',


    # EXPERT
    # todo: research it
    # 'keep_cross_validation_predictions',
    'class_sampling_factors',
    # todo: research it
    # 'max_after_balance_size', 'nbins_top_level',
    ]


def create_model_gbm(driver, configs = {}, is_regression_tc=False):
    """
    Param is required
    """
    print 'Start create gbm model'

    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
    )

    cfgs.update(configs)

    print 'Select generalized_linear_model model.'
    Common.navigate_to(driver, 'gradient_boosting_machine')

    orders = copy.deepcopy(ORDERS)

    if is_regression_tc:
        del orders[orders.index('balance_classes')]

    print 'Set value for param.'
    set_values(driver, orders, cfgs, XPATHS=GBM_XPATHS)

    Common.build_model(driver)

    print 'Model is created successfully.'


