'''
TODO:
1. xpath grouping by algo
2. xpath current cell support: //div[contains(@data-bind, 'click:select')][last()]
3. xpath grouping by UI Control type: radio buttons?
4. what is relative paths? should it be removed?
5. need to use testNG testcases for algo tests too...

Althru there are things to improve, I'm pretty much happy with this file... Good job, buddy! ;)
'''


def xp(xpath, xpath_type = 'button', is_current_cell = False):
    ''' helper function for creating xpath dict '''
    if is_current_cell:
        xpath = "//div[contains(@data-bind, 'click:select')][last()]" + xpath

    return dict(
        xpath = xpath,
        type = xpath_type,
    )


XPATHS = dict(
    assist_me = xp("//button[contains(@title,'Assist Me')]"),
    import_files = xp("//a[contains(.,'importFiles')]", is_current_cell = True),

    # Import Files dialog
    file_path = xp("//input[contains(@placeholder,'Enter')]", 'input', True),
    file_search_btn = xp("//button[@data-bind='click:tryImportFiles']", 'button', True),
    add_all_files_btn = xp("//a[contains(.,'Add all')]"),
    import_sel_files_btn = xp("//button[contains(@data-bind,'click:importSelectedFiles')]", 'button', True),

    # Parse Files dialog
    parse_file_test_btn = xp("//button[contains(@data-bind,'click:parse, enable:canParse')]", 'button', True),

    # choose auto
    auto = xp("//input[contains(@value,'auto')]", 'checkbox', True),

    # Choose single
    single_quotes = xp("//input[contains(@data-bind,'checked:useSingleQuotes')]", 'checkbox', True),

    # click parse button
    parse_btn = xp("//button[text()='Parse']", 'button', True),
    destination_key = xp("//tr[th[contains(., 'ID')]]//input", 'input', True),

    # Parse in progress
    progress_text = xp("//tr[contains(./th, 'Progress')]//td[@data-bind='text:progress']", 'text', True),

    # View parse result
    view_btn = xp("//button[contains(@data-bind,'click:view')]", 'button', True),
    cancel_btn = xp("//button[contains(@data-bind,'click:cancel')]", 'button', True),

    #Toggle stack trace
    toggle_stack_trace = xp("//a[contains(@data-bind, 'click:toggleStack')]",'text', True),

    # save and dowload flow
    flow_btn = xp("//a[.='Flow']"),
    save_flow_btn = xp("//span[text()='Save Flow']"),
    replace_btn = xp("//button[contains(.,'Replace')]"),
    cancel_save_btn = xp("//button[contains(.,'Cancel')]"),
    download_this_flow_btn = xp("//span[text()='Download this Flow...']"),
    new_flow_btn = xp("//span[text()='New Flow']"),
    create_new_notebook_btn = xp("//button[contains(.,'Create New Notebook')]"),
    cancel_new_flow_btn = xp("//button[contains(.,'Cancel')]"),

    # choose split file
    split_btn = xp("//button[contains(@data-bind,'click:splitFrame')]", 'button', True),
    splitted_train_column = xp("//input[contains(@data-bind,'value:lastSplitKey')]", 'input', True),
    splitted_test_column = xp("//input[contains(@data-bind,'value:key')]", 'input', True),
    create_split_btn = xp("//button[contains(@data-bind,'click:splitFrame')]", 'button', True),
    ratio = xp("//input [contains(@data-bind,'value:ratioText')]", 'input'),

    # Create model
    create_model = xp("//button[contains(@data-bind,'click:createModel')]"),
    model_select = xp("//select[contains(@data-bind,'options:algorithms')]", 'select'),
    build_model = xp("//button[text()='Build Model']", 'button', True),
    glm_btn = xp("//span[text()='Generalized Linear Model...']"),
    drf_btn = xp("//span[text()='Distributed RF...']"),
    km_btn = xp("//span[text()='K-means']"),
    gbm_btn = xp("//span[text()='Gradient Boosting Machine']"),
    model_btn = xp("//a[.='Model']"),
    dl_btn = xp("//span[text()='Deep Learning']"),

    #delete model
    delete_btn = xp("//button[contains(@data-bind,'click:deleteFrame')]"),
    cancel_frame = xp(" //button[contains(@data-bind, 'text:declineCaption')]"),
    delete_frame = xp("//button[contains(@data-bind, 'text:acceptCaption')]"),


    # select file
    training_frame = xp("//tr[th[contains(., 'training_frame')]]//select", 'select'),
    validation_frame = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select'),

    # predit flow
    predict_btn = xp("//button[contains(@data-bind,'click:predict')]", 'button', True),
    frame_select = xp("//tr[th[contains(., 'Frame:')]]//select", 'select', True),

    model = xp("//tr[th[contains(., 'model')]]//td", 'text', True),
    model_checksum = xp("//tr[th[contains(., 'model_checksum')]]//td", 'text', True),
    frame = xp("//tr[th[contains(., 'frame')]]//td", 'text', True),
    frame_checksum = xp("//tr[th[contains(., 'frame_checksum')]]//td", 'text', True),
    description = xp("//tr[th[contains(., 'description')]]//td", 'text', True),
    model_category = xp("//tr[th[contains(., 'model_category')]]//td", 'text', True),
    scoring_time = xp("//tr[th[contains(., 'scoring_time')]]//td", 'text', True),
    predictions = xp("//tr[th[contains(., 'predictions')]]//td", 'text', True),
    mse = xp("//tr[th[contains(., 'MSE')]]//td", 'text', True),
    r2 = xp("//tr[th[contains(., 'r2')]]//td", 'text', True),
    logloss = xp("//tr[th[contains(., 'logloss')]]//td", 'text', True),
    auc = xp("//tr[th[contains(., 'AUC')]]//td", 'text', True),
    Gini = xp("//tr[th[contains(., 'Gini')]]//td", 'text', True),

    # max_f1 = xp("//tr//td[contains(., 'max f1')]", 'text', True),
    # max_f2 = xp("//tr//td[contains(., 'max f2')]", 'text', True),
    # max_f0point5 = xp("//tr//td[contains(., 'max f0point5')]", 'text', True),
    # max_accuracy= xp("//tr//td[contains(., 'max accuracy')]", 'text', True),
    # max_precision = xp("//tr//td[contains(., 'max precision')]", 'text', True),
    # max_absolute_MCC = xp("//tr//td[contains(., 'max_absolute_MCC')]", 'text', True),
    # max_min_per_class_accuracy = xp("//tr//td[contains(., 'max min_per_class_accuracy')]", 'text', True),

    max_metrics = xp("//div[contains(@class,'flow-widget')]//div[5]//table//tbody//tr[%d]//td[%d]",'text', True),

    # Change the name of flow
    united_flow_btn = xp("//span[text()='Untitled Flow']"),
    name_flow = xp("//input[contains(@data-bind,'value:name')]", 'input'),

    #common
    ignored_columns_list = xp("//tr[th[contains(., 'ignored_columns')]]//tr"),
    ignored_columns = xp("//tr[th[contains(., 'ignored_columns')]]//tr[%s]"),
    ignore_const_cols = xp("//tr[th[contains(., 'ignore_const_cols')]]//input", 'checkbox'),
    response_column = xp("//tr[th[contains(., 'response_column')]]//select", 'select'),
    response_column_last = xp("//tr[th[contains(., 'response_column')]]//select[last()]", 'select'),

    #OUTPUT - MODEL SUMMARY
    output_model_summary = xp("//h4[contains(., 'output - Model Summary')]"),
    number_of_trees = xp("//tr[th[contains(., 'number_of_trees')]]//td", 'text', True),
    model_size_in_bytes = xp ("//tr[th[contains(., 'model_size_in_bytes')]]//td", 'text', True),
    min_depth = xp("//tr[th[contains(., 'min_depth')]]//td", 'text', True),
    mean_depth = xp("//tr[th[contains(., 'mean_depth')]]//td", 'text', True),
    min_leaves = xp("//tr[th[contains(., 'min_leaves')]]//td", 'text', True),
    max_leaves = xp("//tr[th[contains(., 'max_leaves')]]//td", 'text', True),
    mean_leaves = xp("//tr[th[contains(., 'mean_leaves')]]//td", 'text', True),
)


DEEP_LEARNING_XPATHS = dict(
    train_dataset_id = xp("//tr[th[contains(., 'training_frame')]]//select", 'select', True),
    validate_dataset_id = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select', True),
    nfolds = xp("//tr[th[contains(., 'nfolds')]]//input", 'input', True),
    response_column = xp("//tr[th[contains(., 'response_column')]]//select", 'select', True),
    ignored_columns = xp("//tr[th[contains(., 'ignored_columns')]]//input", 'input', True),
    ignore_const_cols = xp("//tr[th[contains(., 'ignore_const_cols')]]//input", 'checkbox', True),
    activation_select = xp("//tr[th[contains(., 'activation')]]//select", 'select', True),
    hidden = xp("//tr[th[contains(., 'hidden')]]//input", 'input', True),
    epochs = xp("//tr[th[contains(., 'epochs')]]//input", 'input', True),
    variable_importances = xp("//tr[th[contains(., 'variable_importances')]]//input", 'checkbox', True),

    ##advance
    fold_assignment = xp("//tr[th[contains(., 'fold_assignment')]]//select", 'select', True),
    fold_column = xp("//tr[th[contains(., 'fold_column')]]//select", 'select', True),
    weights_column = xp("//tr[th[contains(., 'weights_column')]]//select", 'select', True),
    offset_column = xp("//tr[th[contains(., 'offset_column')]]//select", 'select', True),
    balance_classes = xp("//tr[th[contains(., 'balance_classes')]]//input", 'checkbox', True),
    max_confusion_matrix_size= xp("//tr[th[contains(., 'max_confusion_matrix_size')]]//input", 'input', True),
    max_hit_ratio_k =  xp("//tr[th[contains(., 'max_hit_ratio_k')]]//input", 'input', True),
    checkpoint = xp("//tr[th[contains(., 'checkpoint')]]//input",  'input', True),
    use_all_factor_levels = xp("//tr[th[contains(., 'use_all_factor_levels')]]//input", 'checkbox', True),
    train_samples_per_iteration = xp("//tr[th[contains(., 'train_samples_per_iteration')]]//input", 'input', True),
    adaptive_rate = xp("//tr[th[contains(., 'adaptive_rate')]]//input", 'checkbox', True),
    input_dropout_ratio = xp("//tr[th[contains(., 'input_dropout_ratio')]]//input", 'input', True),
    l1 = xp("//tr[th[contains(., 'l1')]]//input", 'input', True),
    l2 = xp("//tr[th[contains(., 'l1')]]//input", 'input, True'),
    loss = xp("//tr[th[contains(., 'loss')]]//select", 'select'),
    distribution = xp("//tr[th[contains(., 'distribution')]]//select", 'select', True),
    tweedie_power = xp("//tr[th[contains(., 'tweedie_power')]]//input", 'input', True),
    score_interval = xp("//tr[th[contains(., 'score_interval')]]//input", 'input', True),
    score_training_samples = xp("//tr[th[contains(., 'score_training_samples')]]//input", 'input', True),
    #score_validation_samples = xp("//tr[th[contains(., 'score_validation_samples')]]//input", 'input', True),
    score_duty_cycle = xp("//tr[th[contains(., 'score_duty_cycle')]]//input", 'input', True),
    autoencoder = xp("//tr[th[contains(., 'autoencoder')]]//input", 'checkbox', True),

    #expert
    keep_cross_validation_predictions = xp("//tr[th[contains(., 'keep_cross_validation_predictions')]]//input", 'checkbox', True),
    class_sampling_factors = xp("//tr[th[contains(., 'class_sampling_factors')]]//input", 'input', True),
    max_after_balance_size = xp("//tr[th[contains(., 'max_after_balance_size')]]//input", 'input', True),
    overwrite_with_best_model = xp("//tr[th[contains(., 'overwrite_with_best_model')]]//input", 'checkbox', True),
    target_ratio_comm_to_comp = xp("//tr[th[contains(., 'target_ratio_comm_to_comp')]]//input", 'input', True),
    seed = xp("//tr[th[contains(., 'seed')]]//input",   'input', True),
    rho = xp("//tr[th[contains(., 'rho')]]//input", 'input', True),
    epsilon = xp("//tr[th[contains(., 'epsilon')]]//input", 'input', True),
    max_w2 = xp("//tr[th[contains(., 'max_w2')]]//input", 'input', True),
    initial_weight_distribution = xp("//tr[th[contains(., 'initial_weight_distribution')]]//select", 'select', True),
    #classification_stop = xp("//tr[th[contains(., 'max_w2')]]//input", 'input', True),
    regression_stop = xp("//tr[th[contains(., 'regression_stop')]]//input", 'input', True),
    #score_validation_sampling = xp("//tr[th[contains(., 'score_validation_sampling')]]//select", 'select', True),
    diagnostics = xp("//tr[th[contains(., 'diagnostics')]]//input", 'checkbox', True),
    fast_mode = xp("//tr[th[contains(., 'fast_mode')]]//input", 'checkbox', True),
    force_load_balance = xp("//tr[th[contains(., 'force_load_balance')]]//input", 'checkbox', True),
    single_node_mode = xp("//tr[th[contains(., 'single_node_mode')]]//input", 'checkbox', True),
    shuffle_training_data = xp("//tr[th[contains(., 'shuffle_training_data')]]//input", 'checkbox', True),
    missing_values_handling = xp("//tr[th[contains(., 'missing_values_handling')]]//select", 'select', True),
    quiet_mode = xp("//tr[th[contains(., 'quiet_mode')]]//input", 'select', True),
    sparse = xp("//tr[th[contains(., 'sparse')]]//input", 'select', True),
    col_major = xp("//tr[th[contains(., 'col_major')]]//input", 'select'),
    average_activation = xp("//tr[th[contains(., 'average_activation')]]//input", 'input', True),
    sparsity_beta = xp("//tr[th[contains(., 'sparsity_beta')]]//input", 'input', True),
    max_categorical_features = xp("//tr[th[contains(., 'max_categorical_features')]]//input", 'input', True),
    reproducible = xp("//tr[th[contains(., 'reproducible')]]//input", 'select'),
    export_weights_and_biases = xp("//tr[th[contains(., 'export_weights_and_biases')]]//input", 'select'),
    build_model = xp("//button[text()='Build Model']", 'button', True),
)


GBM_XPATHS = dict(
    train_dataset_id = xp("//tr[th[contains(., 'training_frame')]]//select", 'select'),
    validate_dataset_id  = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select'),
    nfolds = xp("//tr[th[contains(., 'nfolds')]]//input", 'input', True),
    response_column = xp("//tr[th[contains(., 'response_column')]]//select", 'select'),
    ignored_columns = xp("//tr[th[contains(., 'ignored_columns')]]//input", 'input', True),
    ignore_const_cols = xp("//tr[th[contains(., 'ignore_const_cols')]]//input", 'checkbox'),
    ntrees = xp("//tr[th[contains(., 'ntrees')]]//input", 'input'),
    max_depth = xp("//tr[th[contains(., 'max_depth')]]//input", 'input'),
    min_rows = xp("//tr[th[contains(., 'min_rows')]]//input", 'input'),
    nbins = xp("//tr[th[contains(., 'nbins')]]//input", 'input'),
    nbins_cats = xp("//tr[th[contains(., 'nbins_cats')]]//input", 'input'),
    seed = xp("//tr[th[contains(., 'seed')]]//input", 'input'),
    learn_rate = xp("//tr[th[contains(., 'learn_rate')]]//input",  'input'),
    distribution = xp("//tr[th[contains(., 'distribution')]]//select",  'select'),
    score_each_iteration = xp("//tr[th[contains(., 'score_each_iteration')]]//input", 'checkbox'),
    fold_assignment = xp("//tr[th[contains(., 'fold_assignment')]]//select", 'select', True),
    fold_column = xp("//tr[th[contains(., 'fold_column')]]//select", 'select', True),
    offset_column  = xp("//tr[th[contains(., 'offset_column')]]//select", 'select'),
    weights_column = xp("//tr[th[contains(., 'weights_column')]]//select", 'select'),
    balance_classes = xp("//tr[th[contains(., 'balance_classes')]]//input", 'checkbox'),
    max_confusion_matrix_size = xp("//tr[th[contains(., 'max_confusion_matrix_size')]]//input", 'input'),
    max_hit_ratio_k =  xp("//tr[th[contains(., 'max_hit_ratio_k')]]//input", 'input'),
    r2_stopping = xp("//tr[th[contains(., 'r2_stopping')]]//input", 'input'),
    build_tree_one_node  = xp("//tr[th[contains(., 'build_tree_one_node')]]//input", 'checkbox'),
    tweedie_power = xp("//tr[th[contains(., 'tweedie_power')]]//input", 'input', True),
    checkpoint = xp("//tr[th[contains(., 'checkpoint')]]//input", 'input', True),
    keep_cross_validation_predictions = xp("//tr[th[contains(., 'keep_cross_validation_predictions')]]//input", 'checkbox', True),
    class_sampling_factors = xp("//tr[th[contains(., 'class_sampling_factors')]]//input", 'input'),
    max_after_balance_size = xp("//tr[th[contains(., 'max_after_balance_size')]]//input", 'input'),
    nbins_top_level = xp("//tr[th[contains(., 'nbins_top_level')]]//input", 'input', True),
    build_model = xp("//button[text()='Build Model']", 'button', True),
)

KMEAN_XPATHS = dict(
    train_dataset = xp("//tr[th[contains(., 'training_frame')]]//select", 'select'),
    validate_dataset = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select'),
    nfolds = xp("//tr[th[contains(., 'nfolds')]]//input", 'input', True),
    ignored_columns = xp("//tr[th[contains(., 'ignored_columns')]]//input", 'input', True),
    ignore_const_cols_kmeans = xp("//tr[th[contains(., 'ignore_const_cols')]]//input", 'checkbox'),
    k_input = xp("//tr[th[contains(., 'k*')]]//input", 'input'),
    user_points = xp("//tr[th[contains(., 'user_points')]]//select", 'select'),
    max_iterations_kmeans = xp("//tr[th[contains(., 'max_iterations')]]//input", 'input'),
    init_column = xp("//tr[th[contains(., 'init')]]//select", 'select'),
    fold_assignment = xp("//tr[th[contains(., 'fold_assignment')]]//select", 'select', True),
    fold_column = xp("//tr[th[contains(., 'fold_column')]]//select", 'select', True),
    score_each_iteration_kmeans = xp("//tr[th[contains(., 'score_each_iteration')]]//input", 'checkbox'),
    standardize_kmeans = xp("//tr[th[contains(., 'standardize')]]//input", 'checkbox'),
    keep_cross_validation_predictions = xp("//tr[th[contains(., 'keep_cross_validation_predictions')]]//input", 'checkbox', True),
    seed = xp("//tr[th[contains(., 'seed')]]//input", 'input'),
    build_model = xp("//button[text()='Build Model']", 'button', True),
)

GLM_XPATHS = dict(
    train_dataset_id = xp("//tr[th[contains(., 'training_frame')]]//select", 'select', True),
    validate_dataset_id = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select', True),
    nfolds = xp("//tr[th[contains(., 'nfolds')]]//input", 'input', True),
    response_column = xp("//tr[th[contains(., 'response_column')]]//select", 'select', True),
    ignored_columns = xp("//tr[th[contains(., 'ignored_columns')]]//input", 'input', True),
    ignore_const_cols = xp("//tr[th[contains(., 'ignore_const_cols')]]//input", 'checkbox', True),
    family = xp("//tr[th[contains(., 'family')]]//select", 'select', True),
    solver = xp("//tr[th[contains(., 'solver')]]//select", 'select', True),
    alpha = xp("//tr[th[contains(., 'alpha')]]//input", 'input', True),
    lamda = xp("//tr[th[contains(., 'lambda')]]//input", 'input'),
    lambda_search = xp("//tr[th[contains(., 'lambda_search')]]//input", 'checkbox', True),
    #nlambdas = xp("//tr[th[contains(., 'nlambdas')]]//input", 'input', True),
    standardize = xp("//tr[th[contains(., 'standardize')]]//input", 'checkbox', True),
    non_negative = xp("//tr[th[contains(., 'non_negative')]]//input", 'checkbox'),
    beta_constraints = xp("//tr[th[contains(., 'beta_constraints')]]//select", 'select', True),
    fold_assignment = xp("//tr[th[contains(., 'fold_assignment')]]//select", 'select', True),
    fold_column = xp("//tr[th[contains(., 'fold_column')]]//select", 'select', True),
    offset_column = xp("//tr[th[contains(., 'offset_column')]]//select", 'select', True),
    weights_column = xp("//tr[th[contains(., 'weights_column')]]//select", 'select', True),
    score_each_iteration = xp("//tr[th[contains(., 'score_each_iteration')]]//input", 'checkbox', True),
    max_iterations = xp("//tr[th[contains(., 'max_iterations')]]//input", 'input', True),
    link = xp("//tr[th[contains(., 'link')]]//select", 'select', True),
    max_confusion_matrix_size= xp("//tr[th[contains(., 'max_confusion_matrix_size')]]//input", 'input', True),
    max_hit_ratio_k =  xp("//tr[th[contains(., 'max_hit_ratio_k')]]//input", 'input', True),
    keep_cross_validation_predictions = xp("//tr[th[contains(., 'keep_cross_validation_predictions')]]//input", 'checkbox', True),
    intercept = xp("//tr[th[contains(., 'intercept')]]//input", 'checkbox', True),
    objective_epsilon = xp("//tr[th[contains(., 'objective_epsilon')]]//input", 'input', True),
    beta_epsilon = xp("//tr[th[contains(., 'beta_epsilon')]]//input",'input', True),
    gradient_epsilon = xp("//tr[th[contains(., 'gradient_epsilon')]]//input", 'input', True),
    prior = xp("//tr[th[contains(., 'prior')]]//input", 'input', True),
    max_active_predictors = xp("//tr[th[contains(., 'max_active_predictors')]]//input", 'input', True),
    #lambda_min_ratio = xp("//tr[th[contains(., 'lambda_min_ratio')]]//input", 'input', True),
)

DRF_XPATHS = dict(
    train_dataset_id = xp("//tr[th[contains(., 'training_frame')]]//select", 'select', True),
    validate_dataset_id = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select', True),
    nfolds = xp("//tr[th[contains(., 'nfolds')]]//input", 'input', True),
    response_column = xp("//tr[th[contains(., 'response_column')]]//select", 'select', True),
    ignored_columns = xp("//tr[th[contains(., 'ignored_columns')]]//input", 'input', True),
    ignore_const_cols = xp("//tr[th[contains(., 'ignore_const_cols')]]//input", 'checkbox', True),
    ntrees = xp("//tr[th[contains(., 'ntrees')]]//input", 'input', True),
    max_depth = xp("//tr[th[contains(., 'max_depth')]]//input", 'input', True),
    min_rows = xp("//tr[th[contains(., 'min_rows')]]//input", 'input', True),
    nbins = xp("//tr[th[contains(., 'nbins')]]//input", 'input', True),
    nbins_cats = xp("//tr[th[contains(., 'nbins_cats')]]//input", 'input', True),
    seed_drf = xp("//tr[th[contains(., 'seed')]]//input",   'input', True),
    mtries = xp("//tr[th[contains(., 'mtries')]]//input", 'input', True),
    sample_rate = xp("//tr[th[contains(., 'sample_rate')]]//input", 'input', True),
    score_each_iteration_drf = xp("//tr[th[contains(., 'score_each_iteration')]]//input", 'checkbox', True),
    fold_assignment = xp("//tr[th[contains(., 'fold_assignment')]]//select", 'select', True),
    fold_column = xp("//tr[th[contains(., 'fold_column')]]//select", 'select', True),
    offset_column_drf = xp("//tr[th[contains(., 'offset_column')]]//select", 'select', True),
    weights_column_drf = xp("//tr[th[contains(., 'weights_column')]]//select", 'select', True),
    balance_classes = xp("//tr[th[contains(., 'balance_classes')]]//input", 'checkbox', True),
    max_confusion_matrix_size= xp("//tr[th[contains(., 'max_confusion_matrix_size')]]//input", 'input', True),
    max_hit_ratio_k =  xp("//tr[th[contains(., 'max_hit_ratio_k')]]//input", 'input', True),
    r2_stopping = xp("//tr[th[contains(., 'r2_stopping')]]//input", 'input', True),
    build_tree_one_node = xp("//tr[th[contains(., 'build_tree_one_node')]]//input", 'checkbox', True),
    binomial_double_trees = xp("//tr[th[contains(., 'binomial_double_trees')]]//input", 'checkbox', True),
    checkpoint = xp("//tr[th[contains(., 'checkpoint')]]//input", 'input', True),
    keep_cross_validation_predictions = xp("//tr[th[contains(., 'keep_cross_validation_predictions')]]//input", 'checkbox', True),
    class_sampling_factors = xp("//tr[th[contains(., 'class_sampling_factors')]]//input", 'input', True),
    nbins_top_level = xp("//tr[th[contains(., 'nbins_top_level')]]//input", 'input', True),
    train_dataset_id_split = xp("//tr[th[contains(., 'training_frame')]]//select", 'select', True),
    validate_dataset_id_split = xp("//tr[th[contains(., 'validation_frame')]]//select", 'select', True),
    build_model = xp("//button[text()='Build Model']", 'button', True),
)

NAVIGATE_XPATHS = dict(
    #FLOW LIST
    flow = xp("//a[.='Flow']"),
    new_flow = xp("//a[.='New Flow']"),
    cancel = xp("//button[.='Cancel']"),
    open_flow = xp("//a[.='Open Flow...']"),
    save_flow = xp("//a[.='Save Flow']"),
    make_a_copy = xp("//a[.='Make a Copy...']"),
    run_all_cells = xp("//a[.='Run All Cells']"),
    run_all_cells_below = xp("//a[.='Run All Cells Below']"),
    toggle_all_cell_inputs = xp("//a[.='Toggle All Cell Inputs']"),
    toggle_all_cell_outputs = xp("//a[.='Toggle All Cell Outputs']"),
    clear_all_cell_outputs = xp("//a[.='Clear All Cell Outputs']"),
    download_this_flow = xp("//a[.='Download this Flow...']"),


    #CELL LIST
    cell = xp("//a[.='Cell']"),
    cut_cell = xp("//a[.='Cut Cell']"),
    copy_cell = xp("//a[.='Copy Cell']"),
    paste_cell_above = xp("//a[.='Paste Cell Above']"),
    paste_cell_below = xp("//a[.='Paste Cell Below']"),
    delete_cell = xp("//a[.='Delete Cell']"),
    undo_delete_cell = xp("//a[.='Undo Delete Cell']"),
    move_cell_up = xp("//a[.='Move Cell Up']"),
    move_cell_down = xp("//a[.='Move Cell Down']"),
    insert_cell_above = xp("//a[.='Insert Cell Above']"),
    insert_cell_below = xp("//a[.='Insert Cell Below']"),
    toggle_cell_input = xp("//a[.='Toggle Cell Input']"),
    toggle_cell_output = xp("//a[.='Toggle Cell Output']"),
    clear_cell_output = xp("//a[.='Clear Cell Output']"),


    #DATA LIST
    data = xp("//a[.='Data']"),
    import_files = xp("//a[.='Import Files...']"),
    upload_file = xp("//a[.='Upload File...']"),
    split_frame = xp("//a[.='Split Frame...']"),
    list_all_frames = xp("//a[.='List All Frames']"),


    #MODEL LIST
    model = xp("//a[.='Model']"),
    deep_learning = xp("//span[text()='Deep Learning...']"),
    distributed_rf = xp("//span[text()='Distributed RF...']"),
    gradient_boosting_machine =xp("//span[text()='Gradient Boosting Machine...']"),
    generalized_linear_model = xp("//span[text()='Generalized Linear Model...']"),
    k_means = xp("//span[text()='K-means...']"),
    naive_bayes = xp("//a[.='Naive Bayes']"),
    list_all_models = xp("//a[.='List All Models']"),


    #SCORE LIST
    score = xp("//a[.='Score']"),
    predict = xp("//a[.='Predict...']"),
    list_all_predictions = xp("//a[.='List All Predictions']"),


    #ADMIN LIST
    admin = xp("//a[.='Admin']"),
    jobs = xp("//a[.='Jobs']"),
    cluster_status = xp("//a[.='Cluster Status']"),
    water_meter_cpu_meter = xp("//a[.='Water Meter (CPU meter)']"),
    view_log = xp("//a[.='View Log']"),
    download_logs = xp("//a[.='Download Logs']"),
    create_synthetic_frame = xp("//a[.='Create Synthetic Frame']"),
    stack_trace = xp("//a[.='Stack Trace']"),
    network_test = xp("//a[.='Network Test']"),
    profiler = xp("//a[.='Profiler']"),
    timeline = xp("//a[.='Timeline']"),
    shut_down = xp("//a[.='Shut Down']"),


    #HELP LIST
    help = xp("//a[.='Help']"),
    assist_me = xp("//a[.='Assist Me']"),
    contents = xp("//a[.='Contents']"),
    keyboard_shortcuts = xp("//a[.='Keyboard Shortcuts']"),
    documentation = xp("//a[.='Documentation']"),
    faq = xp("//a[.='FAQ']"),
    h2o_ai = xp("//a[.='H2O.ai']"),
    h2o_on_github = xp("//a[.='H2O on Github']"),
    report_an_issue = xp("//a[.='Report an issue']"),
    forum_ask_a_question = xp("//a[.='Forum / Ask a question']"),
    about = xp("//a[.='About']"),
    ok = xp("//button[.='OK'] "),
    upload = xp("//button[.='Upload']"),

)


def unit_test():
    ''' unit tests '''
    print XPATHS['united_flow']


if __name__ == '__main__':
    unit_test()
