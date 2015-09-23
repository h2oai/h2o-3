__author__ = 'trang.huynh'

from testlibs import common
from utils.se_functions import set_values
from testlibs.xpaths import GLM_XPATHS


ORDERS = [
    'train_dataset_id', 'validate_dataset_id',
    'ignore_const_cols', 'response_column',
    'offset_column', 'weights_column', 'family',
    'solver', 'alpha', 'lamda', 'lambda_search',
    'standardize', 'non_negative','beta_constraints',

    #advanced
    'fold_column', 'score_each_iteration', 'offset_column',
    'weights_column', 'max_iterations', 'link',
    'max_confusion_matrix_size', 'max_hit_ratio_k',

    #expert
    'intercept', 'objective_epsilon', 'beta_epsilon',
    'gradient_epsilon', 'prior', 'max_active_predictors'

    ]


def create_model_glm (driver, configs = {}):
    #Param is required
    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
    )

    cfgs.update(configs)

    common.navigate_to(driver, 'generalized_linear_model')

    set_values(driver, GLM_XPATHS, ORDERS, cfgs)
    common.click_build_model(driver)

    common.wait_progress_n_click(driver)

def unit_test():
    pass


if __name__ == '__main__':
    unit_test()
