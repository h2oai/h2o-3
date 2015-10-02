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


def create_model_glm(driver, configs = {}):
    #Create glm_model
    print 'Start create glm model...'
    #Param is required
    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',
    )
    cfgs.update(configs)

    print '---Select generalized_linear_model model:'
    common.navigate_to(driver, 'generalized_linear_model')

    print '---Set value for param:'
    set_values(driver, GLM_XPATHS, ORDERS, cfgs)

    print '---Click build model:'
    common.click_build_model(driver)
    common.wait_progress_n_click(driver)
    print 'Model is created done...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()
