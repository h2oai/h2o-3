from testlibs import common
from utils.se_functions import set_values
from testlibs.xpaths import DRF_XPATHS


ORDERS = [
    'train_dataset_id', 'validate_dataset_id', 'nfolds', 'response_column',
    'ignored_columns', 'ignore_const_cols', 'ntrees', 'max_depth', 'min_rows', 'nbins',
     'nbins_cats', 'seed_drf', 'mtries', 'sample_rate',

    #advance
    'score_each_iteration_drf', 'fold_assignment', 'fold_column', 'offset_column_drf',
    'weights_column_drf', 'balance_classes', 'max_confusion_matrix_size', 'max_hit_ratio_k',
    'r2_stopping', 'build_tree_one_node', 'binomial_double_trees', 'checkpoint',

    #expert
    'keep_cross_validation_predictions', 'class_sampling_factors','nbins_top_level',

    #extra
    'train_dataset_id_split', 'validate_dataset_id_split',

    #build model button
    ]


def create_model_drf(driver, configs = {}):
    #Param is required
    cfgs = dict(
        train_dataset_id = '',
        validate_dataset_id = '',
        response_column = '',

    )
    cfgs.update(configs)

    print '---Select distributed_rf model:'
    common.navigate_to(driver, 'distributed_rf')

    print '---Set value for param:'
    set_values(driver, DRF_XPATHS, ORDERS, configs)

    print '---Click build model:'
    common.click_build_model(driver)
    common.wait_progress_n_click(driver)
    print 'Model is created done...'


def unit_test():
    pass


if __name__ == '__main__':
    unit_test()
