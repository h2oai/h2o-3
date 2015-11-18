from testlibs import Common

from utils.Selenium import set_values
from utils.xpaths import GLM_XPATHS


# GLM config
row = 4
column = 2


ORDERS = [
    'train_dataset_id', 'validate_dataset_id',
    'ignore_const_cols', 'response_column',
    'offset_column', 'weights_column', 'family',
    'solver', 'alpha', 'lamda', 'lambda_search',
    'standardize', 'non_negative',
    # todo: research it
    # 'beta_constraints',

    # advanced
    'fold_column',
    # 'score_each_iteration',
    'offset_column',
    'weights_column',
    # todo: research it
    # 'max_iterations', 'link',
    # 'max_confusion_matrix_size', 'max_hit_ratio_k',

    # expert
    'intercept', 'prior', 'max_active_predictors'
    # todo: research it
    # 'objective_epsilon', 'beta_epsilon',
    # 'gradient_epsilon', 'prior', 'max_active_predictors'
    ]


def create_model_glm(driver, configs = {}):
    """
    Create glm_model
    """
    print 'Start create glm model.'

    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
    )
    cfgs.update(configs)

    Common.navigate_to(driver, 'generalized_linear_model')

    set_values(driver, ORDERS, cfgs, XPATHS=GLM_XPATHS)

    Common.build_model(driver)

    print 'Model is created successfully.'
