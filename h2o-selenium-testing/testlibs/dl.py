from utils.se_functions import set_values
from utils.xpaths import DEEP_LEARNING_XPATHS
from testlibs import common


ORDERS = [
    'train_dataset_id', 'validate_dataset_id', 'nfolds',
    'response_column', 'ignored_columns', 'ignore_const_cols',
    'activation', 'hidden', 'epochs', 'variable_importances',

    # Advance
    'fold_assignment', 'fold_column', 'weights_column',
    'offset_column',    'balance_classes', 'max_confusion_matrix_size',
    'max_hit_ratio_k', 'checkpoint', 'use_all_factor_levels',
    'train_samples_per_iteration', 'adaptive_rate',
    'input_dropout_ratio', 'l1', 'l2', 'loss', 'distribution',
    'tweedie_power', 'score_interval', 'score_training_samples',
    'score_validation_samples', 'score_duty_cycle', 'autoencoder',

    # Expert
    'keep_cross_validation_predictions', 'class_sampling_factors',
    'max_after_balance_size','overwrite_with_best_model',
    'target_ratio_comm_to_comp', 'seed', 'rho', 'epsilon', 'max_w2',
    'initial_weight_distribution', 'classification_stop',
    'regression_stop', 'score_validation_sampling',
    'diagnostics', 'fast_mode', 'force_load_balance',
    'single_node_mode', 'shuffle_training_data',
    'missing_values_handling', 'quiet_mode', 'sparse',
    'col_major', 'average_activation', 'sparsity_beta',
    'max_categorical_features', 'reproducible',
    'export_weights_and_biases', 'elastic_averaging'

    ]


def create_model_dl(driver, configs = {}):
    #Required params
    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
    )

    cfgs.update(configs)

    print '---Select deep_learning model:'
    common.navigate_to(driver, 'deep_learning')

    print '---Set value for param:'
    set_values(driver, DEEP_LEARNING_XPATHS, ORDERS, configs)

    print '---Click build model:'
    common.click_build_model(driver)

    print 'Model is created done...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()
