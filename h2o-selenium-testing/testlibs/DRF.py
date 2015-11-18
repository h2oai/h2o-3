import copy

from testlibs import Common
from utils.Selenium import set_values
from utils.xpaths import DRF_XPATHS


# DRF config
row = 5
column = 2

ORDERS = [
    'train_dataset_id', 'validate_dataset_id',
    'nfolds', 'response_column',
    'ignore_const_cols', 'ntrees', 'max_depth',
    'min_rows', 'nbins', 'nbins_cats',
    # todo: Test case does not have these fields
    # seed, mtries, sample_rate

    #advance
    'score_each_iteration',
    # todo: Test case does not have this field
    # 'fold_assignment',
    'fold_column', 'offset_column',
    'weights_column', 'balance_classes',
    'max_confusion_matrix_size', 'max_hit_ratio_k',
    'r2_stopping', 'build_tree_one_node',
    'binomial_double_trees', 'checkpoint',

    #expert
    # todo: Test case does not have this field
    #'keep_cross_validation_predictions',
    'class_sampling_factors','nbins_top_level',
    ]


def create_model_drf(driver, configs={}, is_regression_tc=False):
    """
    Create drf_model
    """
    print 'Start create drf model.'

    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',

    )
    cfgs.update(configs)

    Common.navigate_to(driver, 'distributed_rf')

    orders = copy.deepcopy(ORDERS)

    if is_regression_tc:
        del orders[orders.index('balance_classes')]

    set_values(driver, orders, cfgs, XPATHS=DRF_XPATHS)

    Common.build_model(driver)

    print 'Model is created successfully.'

