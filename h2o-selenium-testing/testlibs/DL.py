import copy

from testlibs import Common

from utils.Selenium import set_values
from utils.xpaths import DEEP_LEARNING_XPATHS


# DL config
row = 4
column = 2


ORDERS = [
    'train_dataset_id', 'validate_dataset_id', 'nfolds',
    'response_column', 'ignored_columns',
    # 'ignore_const_cols',
    'activation', 'hidden', 'epochs', 'variable_importances',

    # Advance
    # todo: research it
    # 'fold_assignment',
    'fold_column', 'weights_column',
    'offset_column', 'balance_classes', 'max_confusion_matrix_size',
    'max_hit_ratio_k', 'check_point', 'use_all_factor_levels',
    'train_samples_per_iteration', 'adaptive_rate',
    'input_dropout_ratio', 'l1', 'l2', 'loss', 'distribution',
    # todo: research it
    # 'tweedie_power',
    'score_interval', 'score_training_samples',
    # todo: research it
    # 'score_validation_samples',
    'score_duty_cycle', 'autoencoder',

    # Expert
    # todo: research it
    # 'keep_cross_validation_predictions',
    'class_sampling_factors',
    # todo: research it
    # 'max_after_balance_size',
    # 'overwrite_with_best_model',
    'target_ratio_comm_to_comp', 'seed', 'rho', 'epsilon', 'max_w2',
    'initial_weight_distribution',
    # todo: research it
    # 'classification_stop',
    'regression_stop',
    # todo: research it
    # 'score_validation_sampling',
    'diagnostics', 'fast_mode', 'force_load_balance',
    'single_node_mode', 'shuffle_training_data',
    'missing_values_handling', 'quiet_mode', 'sparse',
    'col_major', 'average_activation', 'sparsity_beta',
    'max_categorical_features', 'reproducible',
    'export_weights_and_biases',
    # todo: research it
    # 'elastic_averaging'
    ]


def create_model_dl(driver, configs = {}, is_regression_tc=False):
    """
    Required params
    """
    print 'Start create gbm model'

    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
    )

    cfgs.update(configs)

    print '---Select deep_learning model:'
    Common.navigate_to(driver, 'deep_learning')

    orders = copy.deepcopy(ORDERS)

    if is_regression_tc:
        del orders[orders.index('balance_classes')]

    print '---Set value for param:'
    set_values(driver, orders, cfgs, XPATHS=DEEP_LEARNING_XPATHS)

    Common.build_model(driver)

    print 'Model is created successfully.'
