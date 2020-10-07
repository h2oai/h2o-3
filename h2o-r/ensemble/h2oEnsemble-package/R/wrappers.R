# Set of default wrappers to create a uniform interface for h2o supervised ML functions (H2O 3.0 and above)
# These wrapper functions should always be compatible with the master branch of: https://github.com/h2oai/h2o-3
# See the ensemble README for a full wrapper compatibility chart

# Example of a wrapper function:
h2o.example.wrapper <- function(x, y, training_frame, model_id = NULL, family = c("gaussian", "binomial"), ...) {
  # This function is just an example.  
  # You can wrap any H2O learner inside a wrapper function, example: h2o.glm
  h2o.glm(x = x, y = y, training_frame = training_frame, family = family)
}



# H2O Algorithm function wrappers for:
# h2o.glm
# h2o.randomForest
# h2o.gbm
# h2o.deeplearning
# h2o.naiveBayes (classification only)


h2o.glm.wrapper <- function(x, y, training_frame, 
                            model_id = NULL,
                            validation_frame = NULL,
                            nfolds = 0,
                            seed = -1,
                            keep_cross_validation_predictions = TRUE,
                            keep_cross_validation_fold_assignment = FALSE,
                            fold_assignment = c("AUTO", "Random", "Modulo", "Stratified"),
                            fold_column = NULL,
                            ignore_const_cols = TRUE,
                            score_each_iteration = FALSE,
                            offset_column = NULL,
                            weights_column = NULL,
                            family = "AUTO",
                            #family = c("AUTO", "binomial", "gaussian", "quasibinomial", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber"),
                            tweedie_variance_power = 0,
                            tweedie_link_power = 1,
                            solver = c("AUTO", "IRLSM", "L_BFGS", "COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT"),
                            alpha = NULL,
                            lambda = NULL,
                            lambda_search = FALSE,
                            early_stopping = TRUE,
                            nlambdas = -1,
                            standardize = TRUE,
                            missing_values_handling = c("MeanImputation", "Skip"),
                            compute_p_values = FALSE,
                            remove_collinear_columns = FALSE,
                            intercept = TRUE,
                            non_negative = FALSE,
                            max_iterations = -1,
                            objective_epsilon = -1,
                            beta_epsilon = 0.0001,
                            gradient_epsilon = -1,
                            link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"),
                            prior = -1,
                            lambda_min_ratio = -1,
                            beta_constraints = NULL,
                            max_active_predictors = -1,
                            interactions = NULL,
                            balance_classes = FALSE,
                            class_sampling_factors = NULL,
                            max_after_balance_size = 5.0,
                            max_runtime_secs = 0, ...) {
  
  # If family is not specified, set it using the datatype of the response column
  #family <- match.arg(family)
  if (family == "AUTO") {
    if (is.factor(training_frame[,y])) {
      family <- "binomial"
    } else {
      family <- "gaussian"
    }
  } else if (family %in% c("laplace", "quantile", "huber")) { # not supported by GLM
      family <- "gaussian"
  }
  
  # Also, offset_column, weights_column, intercept not implemented at the moment due to similar bug as beta_constraints
  # intercept argument not currently supported due to GLM bug with explicitly setting interactions = NULL (the default) 
  h2o.glm(x = x, 
          y = y, 
          training_frame = training_frame, 
          model_id = model_id, 
          validation_frame = validation_frame,
          nfolds = nfolds,
          seed = seed,
          keep_cross_validation_predictions = TRUE,  #must have for stacking
          keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment, 
          fold_assignment = match.arg(fold_assignment),
          fold_column = fold_column,
          ignore_const_cols = ignore_const_cols,
          score_each_iteration = score_each_iteration,
          offset_column = offset_column,
          weights_column = weights_column,
          family = family, 
          tweedie_variance_power = tweedie_variance_power,
          tweedie_link_power = tweedie_link_power,        
          solver = match.arg(solver), 
          alpha = alpha, 
          lambda = lambda, 
          lambda_search = lambda_search,
          early_stopping = early_stopping,
          nlambdas = nlambdas,           
          standardize = standardize,
          missing_values_handling = match.arg(missing_values_handling),
          compute_p_values = compute_p_values,
          remove_collinear_columns = remove_collinear_columns,
          intercept = intercept,
          non_negative = non_negative, 
          max_iterations = max_iterations,
          objective_epsilon = objective_epsilon,
          beta_epsilon = beta_epsilon, 
          gradient_epsilon = gradient_epsilon, 
          link = match.arg(link),
          prior = prior,
          lambda_min_ratio = lambda_min_ratio,
          beta_constraints = beta_constraints,
          max_active_predictors = max_active_predictors,
          #interactions = interactions,  #causes a bug when set to NULL (the default), the h2o.glm function needs to be fixed: https://0xdata.atlassian.net/browse/PUBDEV-4698
          balance_classes = balance_classes,
          class_sampling_factors = class_sampling_factors,
          max_after_balance_size = max_after_balance_size,
          max_runtime_secs = max_runtime_secs)
}



h2o.gbm.wrapper <- function(x, y, training_frame, model_id = NULL,
                            family = "AUTO",
                            validation_frame = NULL,
                            nfolds = 0,
                            keep_cross_validation_predictions = FALSE,
                            keep_cross_validation_fold_assignment = FALSE,
                            score_each_iteration = FALSE,
                            score_tree_interval = 0,
                            fold_assignment = c("AUTO", "Random", "Modulo", "Stratified"),
                            fold_column = NULL,
                            ignore_const_cols = TRUE,
                            offset_column = NULL,
                            weights_column = NULL,
                            balance_classes = FALSE,
                            class_sampling_factors = NULL,
                            max_after_balance_size = 5.0,
                            ntrees = 50,
                            max_depth = 5,
                            min_rows = 10,
                            nbins = 20,
                            nbins_top_level = 1024,
                            nbins_cats = 1024,
                            #r2_stopping = 1.797693135e+308,  #deprecated
                            stopping_rounds = 0,
                            stopping_metric = c("AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "lift_top_group", "misclassification", "mean_per_class_error"),
                            stopping_tolerance = 0.001,
                            max_runtime_secs = 0,
                            seed = -1,
                            build_tree_one_node = FALSE,
                            learn_rate = 0.1,
                            learn_rate_annealing = 1,
                            #distribution = c("AUTO", "bernoulli", "multinomial", "gaussian", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber"),
                            quantile_alpha = 0.5,
                            tweedie_power = 1.5,
                            huber_alpha = 0.9,
                            checkpoint = NULL,
                            sample_rate = 1,
                            sample_rate_per_class = NULL,
                            col_sample_rate = 1,
                            col_sample_rate_change_per_level = 1,
                            col_sample_rate_per_tree = 1,
                            min_split_improvement = 1e-05,
                            histogram_type = c("AUTO", "UniformAdaptive", "Random", "QuantilesGlobal", "RoundRobin"),
                            max_abs_leafnode_pred = 1.797693135e+308,
                            pred_noise_bandwidth = 0,
                            categorical_encoding = c("AUTO", "Enum", "OneHotInternal", "OneHotExplicit", "Binary", "Eigen", "LabelEncoder", "SortByResponse", "EnumLimited"),
                            calibrate_model = FALSE,
                            calibration_frame = NULL,
                            #verbose = FALSE,  #remove so that this is compatible with earlier versions of H2O
                            ...) {
  
  # If family is not specified, set it using the datatype of the response column
  # GBM uses `distribution` instead of `family` so we set `distribution` here instead
  if (family == "AUTO") {
    if (is.factor(training_frame[,y])) {
      distribution <- "bernoulli"
    } else {
      distribution <- "gaussian"
    }
  } else if (family %in% c("laplace", "quantile", "huber")) { # extra distributions for GBM
    distribution <- family
  } else if (family == "binomial") {
    distribution <- "bernoulli"
  } else {
    # not supported by GBM, so we set to "gaussian"
    distribution <- "gaussian"
  }
  
  h2o.gbm(x = x, 
          y = y, 
          training_frame = training_frame, 
          model_id = model_id,
          validation_frame = validation_frame,
          nfolds = nfolds,
          keep_cross_validation_predictions = TRUE,  #must have for stacking
          keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment,
          score_each_iteration = score_each_iteration,
          score_tree_interval = score_tree_interval,
          fold_assignment = match.arg(fold_assignment),
          fold_column = fold_column,
          ignore_const_cols = ignore_const_cols,
          offset_column = offset_column,
          weights_column = weights_column,
          balance_classes = balance_classes,
          class_sampling_factors = class_sampling_factors,
          max_after_balance_size = max_after_balance_size,
          ntrees = ntrees,
          max_depth = max_depth,
          min_rows = min_rows,
          nbins = nbins,
          nbins_top_level = nbins_top_level,
          nbins_cats = nbins_cats,
          #r2_stopping = r2_stopping,
          stopping_rounds = stopping_rounds,
          stopping_metric = match.arg(stopping_metric),
          stopping_tolerance = stopping_tolerance,
          max_runtime_secs = max_runtime_secs,
          seed = seed,
          build_tree_one_node = build_tree_one_node,
          learn_rate = learn_rate,
          learn_rate_annealing = learn_rate_annealing,
          distribution = distribution, # set above
          quantile_alpha = quantile_alpha,
          tweedie_power = tweedie_power,
          huber_alpha = huber_alpha,
          checkpoint = checkpoint,
          sample_rate = sample_rate,
          sample_rate_per_class = sample_rate_per_class,
          col_sample_rate = col_sample_rate,
          col_sample_rate_change_per_level = col_sample_rate_change_per_level,
          col_sample_rate_per_tree = col_sample_rate_per_tree,
          min_split_improvement = min_split_improvement,
          histogram_type = match.arg(histogram_type),
          max_abs_leafnode_pred = max_abs_leafnode_pred,
          pred_noise_bandwidth = pred_noise_bandwidth,
          categorical_encoding = match.arg(categorical_encoding),
          calibrate_model = calibrate_model,
          calibration_frame = calibration_frame)
}


h2o.randomForest.wrapper <- function(x, y, training_frame, model_id = NULL,
                                     validation_frame = NULL,
                                     nfolds = 0,
                                     keep_cross_validation_predictions = TRUE,
                                     keep_cross_validation_fold_assignment = FALSE,
                                     score_each_iteration = FALSE,
                                     score_tree_interval = 0,
                                     fold_assignment = c("AUTO", "Random", "Modulo", "Stratified"),
                                     fold_column = NULL,
                                     ignore_const_cols = TRUE,
                                     offset_column = NULL,
                                     weights_column = NULL,
                                     balance_classes = FALSE,
                                     class_sampling_factors = NULL,
                                     max_after_balance_size = 5.0,
                                     ntrees = 50,
                                     max_depth = 20,
                                     min_rows = 1,
                                     nbins = 20,
                                     nbins_top_level = 1024,
                                     nbins_cats = 1024,
                                     #r2_stopping = 1.797693135e+308,  #deprecated
                                     stopping_rounds = 0,
                                     stopping_metric = c("AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "lift_top_group", "misclassification", "mean_per_class_error"),
                                     stopping_tolerance = 0.001,
                                     max_runtime_secs = 0,
                                     seed = -1,
                                     build_tree_one_node = FALSE,
                                     mtries = -1,
                                     sample_rate = 0.6320000291,
                                     sample_rate_per_class = NULL,
                                     binomial_double_trees = FALSE,
                                     checkpoint = NULL,
                                     col_sample_rate_change_per_level = 1,
                                     col_sample_rate_per_tree = 1,
                                     min_split_improvement = 1e-05,
                                     histogram_type = c("AUTO", "UniformAdaptive", "Random", "QuantilesGlobal", "RoundRobin"),
                                     categorical_encoding = c("AUTO", "Enum", "OneHotInternal", "OneHotExplicit", "Binary", "Eigen", "LabelEncoder", "SortByResponse", "EnumLimited"),
                                     calibrate_model = FALSE,
                                     calibration_frame = NULL,
                                     #verbose = FALSE,  #remove so that this is compatible with earlier versions of H2O
                                     ...) {
  
  # Currently ignoring the `family` arg (so it's removed), will get class from outcome in H2OFrame
  # TO DO: Add a check to make sure that outcome/family type is consistent with specified family
  h2o.randomForest(x = x, 
                   y = y, 
                   training_frame = training_frame, 
                   model_id = model_id, 
                   validation_frame = validation_frame,
                   nfolds = nfolds,
                   keep_cross_validation_predictions = TRUE,  #must have for stacking
                   keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment,
                   score_each_iteration = score_each_iteration,
                   score_tree_interval = score_tree_interval,
                   fold_assignment = match.arg(fold_assignment),
                   fold_column = fold_column,
                   ignore_const_cols = ignore_const_cols,
                   offset_column = offset_column,
                   weights_column = weights_column,
                   balance_classes = balance_classes,
                   class_sampling_factors = class_sampling_factors,
                   max_after_balance_size = max_after_balance_size,
                   ntrees = ntrees,
                   max_depth = max_depth,
                   min_rows = min_rows,
                   nbins = nbins,
                   nbins_top_level = nbins_top_level,
                   nbins_cats = nbins_cats,
                   #r2_stopping = r2_stopping,
                   stopping_rounds = stopping_rounds,
                   stopping_metric = match.arg(stopping_metric),
                   stopping_tolerance = stopping_tolerance,
                   max_runtime_secs = max_runtime_secs,
                   seed = seed,
                   build_tree_one_node = build_tree_one_node,
                   mtries = mtries,
                   sample_rate = sample_rate,
                   sample_rate_per_class = sample_rate_per_class,
                   binomial_double_trees = binomial_double_trees,
                   checkpoint = checkpoint,
                   col_sample_rate_change_per_level = col_sample_rate_change_per_level,
                   col_sample_rate_per_tree = col_sample_rate_per_tree,
                   min_split_improvement = min_split_improvement,
                   histogram_type = match.arg(histogram_type),
                   categorical_encoding = match.arg(categorical_encoding),
                   calibrate_model = calibrate_model,
                   calibration_frame = calibration_frame)
}


h2o.deeplearning.wrapper <- function(x, y, training_frame, model_id = NULL,
                                     family = "AUTO", 
                                     validation_frame = NULL,
                                     nfolds = 0,
                                     keep_cross_validation_predictions = FALSE,
                                     keep_cross_validation_fold_assignment = FALSE,
                                     fold_assignment = c("AUTO", "Random", "Modulo", "Stratified"),
                                     fold_column = NULL,
                                     ignore_const_cols = TRUE,
                                     score_each_iteration = FALSE,
                                     weights_column = NULL,
                                     offset_column = NULL,
                                     balance_classes = FALSE,
                                     class_sampling_factors = NULL,
                                     max_after_balance_size = 5.0,
                                     checkpoint = NULL,
                                     pretrained_autoencoder = NULL,
                                     overwrite_with_best_model = TRUE,
                                     use_all_factor_levels = TRUE,
                                     standardize = TRUE,
                                     activation = c("Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),
                                     hidden = c(200, 200),
                                     epochs = 10,
                                     train_samples_per_iteration = -2,
                                     target_ratio_comm_to_comp = 0.05,
                                     seed = -1,
                                     adaptive_rate = TRUE,
                                     rho = 0.99,
                                     epsilon = 1e-08,
                                     rate = 0.005,
                                     rate_annealing = 1e-06,
                                     rate_decay = 1,
                                     momentum_start = 0,
                                     momentum_ramp = 1000000,
                                     momentum_stable = 0,
                                     nesterov_accelerated_gradient = TRUE,
                                     input_dropout_ratio = 0,
                                     hidden_dropout_ratios = NULL,
                                     l1 = 0,
                                     l2 = 0,
                                     max_w2 = 3.4028235e+38,
                                     initial_weight_distribution = c("UniformAdaptive", "Uniform", "Normal"),
                                     initial_weight_scale = 1,
                                     initial_weights = NULL,
                                     initial_biases = NULL,
                                     loss = c("Automatic", "CrossEntropy", "Quadratic", "Huber", "Absolute", "Quantile"),
                                     #distribution = c("AUTO", "bernoulli", "multinomial", "gaussian", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber"),
                                     quantile_alpha = 0.5,
                                     tweedie_power = 1.5,
                                     huber_alpha = 0.9,
                                     score_interval = 5,
                                     score_training_samples = 10000,
                                     score_validation_samples = 0,
                                     score_duty_cycle = 0.1,
                                     classification_stop = 0,
                                     regression_stop = 1e-06,
                                     stopping_rounds = 5,
                                     stopping_metric = c("AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "lift_top_group", "misclassification", "mean_per_class_error"),
                                     stopping_tolerance = 0,
                                     max_runtime_secs = 0,
                                     score_validation_sampling = c("Uniform", "Stratified"),
                                     diagnostics = TRUE,
                                     fast_mode = TRUE,
                                     force_load_balance = TRUE,
                                     variable_importances = TRUE,
                                     replicate_training_data = TRUE,
                                     single_node_mode = FALSE,
                                     shuffle_training_data = FALSE,
                                     missing_values_handling = c("MeanImputation", "Skip"),
                                     quiet_mode = FALSE,
                                     autoencoder = FALSE,
                                     sparse = FALSE,
                                     col_major = FALSE,
                                     average_activation = 0,
                                     sparsity_beta = 0,
                                     max_categorical_features = 2147483647,
                                     reproducible = FALSE,
                                     export_weights_and_biases = FALSE,
                                     mini_batch_size = 1,
                                     categorical_encoding = c("AUTO", "Enum", "OneHotInternal", "OneHotExplicit", "Binary", "Eigen", "LabelEncoder", "SortByResponse", "EnumLimited"),
                                     elastic_averaging = FALSE,
                                     elastic_averaging_moving_rate = 0.9,
                                     elastic_averaging_regularization = 0.001,
                                     #verbose = FALSE,  #remove so that this is compatible with earlier versions of H2O
                                     ...) {
  
  # If family is not specified, set it using the datatype of the response column
  # GBM uses `distribution` instead of `family` so we set `distribution` here instead
  if (family == "AUTO") {
    if (is.factor(training_frame[,y])) {
      distribution <- "bernoulli"
    } else {
      distribution <- "gaussian"
    }
  } else if (family %in% c("laplace", "quantile", "huber")) { # extra distributions for DL
    distribution <- family
  } else if (family == "binomial") {
    distribution <- "bernoulli"
  } else {
    # not supported by DL, so we set to "gaussian"
    distribution <- "gaussian"
  }
  
  h2o.deeplearning(x = x, 
                   y = y, 
                   training_frame = training_frame, 
                   model_id = model_id,
                   validation_frame = NULL,
                   nfolds = nfolds,
                   keep_cross_validation_predictions = TRUE,  #must have for stacking
                   keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment,
                   fold_assignment = match.arg(fold_assignment),
                   fold_column = fold_column,
                   ignore_const_cols = ignore_const_cols,
                   score_each_iteration = score_each_iteration,
                   weights_column = weights_column,
                   offset_column = offset_column,
                   balance_classes = balance_classes,
                   class_sampling_factors = class_sampling_factors,
                   max_after_balance_size = max_after_balance_size,
                   checkpoint = checkpoint,
                   pretrained_autoencoder = pretrained_autoencoder,
                   overwrite_with_best_model = overwrite_with_best_model,
                   use_all_factor_levels = use_all_factor_levels,
                   standardize = standardize,
                   activation = match.arg(activation),
                   hidden = hidden,
                   epochs = epochs,
                   train_samples_per_iteration = train_samples_per_iteration,
                   target_ratio_comm_to_comp = target_ratio_comm_to_comp,
                   seed = seed,
                   adaptive_rate = adaptive_rate,
                   rho = rho,
                   epsilon = epsilon,
                   rate = rate,
                   rate_annealing = rate_annealing,
                   rate_decay = rate_decay,
                   momentum_start = momentum_start,
                   momentum_ramp = momentum_ramp,
                   momentum_stable = momentum_stable,
                   nesterov_accelerated_gradient = nesterov_accelerated_gradient,
                   input_dropout_ratio = input_dropout_ratio,
                   hidden_dropout_ratios = hidden_dropout_ratios,
                   l1 = l1,
                   l2 = l2,
                   max_w2 = max_w2,
                   initial_weight_distribution = match.arg(initial_weight_distribution),
                   initial_weight_scale = initial_weight_scale,
                   initial_weights = initial_weights,
                   initial_biases = initial_biases,
                   loss = match.arg(loss),
                   distribution = distribution,
                   quantile_alpha = quantile_alpha,
                   tweedie_power = tweedie_power,
                   huber_alpha = huber_alpha,
                   score_interval = score_interval,
                   score_training_samples = score_training_samples,
                   score_validation_samples = score_validation_samples,
                   score_duty_cycle = score_duty_cycle,
                   classification_stop = classification_stop,
                   regression_stop = regression_stop,
                   stopping_rounds = stopping_rounds,
                   stopping_metric = match.arg(stopping_metric),
                   stopping_tolerance = stopping_tolerance,
                   max_runtime_secs = max_runtime_secs,
                   score_validation_sampling = match.arg(score_validation_sampling),
                   diagnostics = diagnostics,
                   fast_mode = fast_mode,
                   force_load_balance = force_load_balance,
                   variable_importances = variable_importances,
                   replicate_training_data = replicate_training_data,
                   single_node_mode = single_node_mode,
                   shuffle_training_data = shuffle_training_data,
                   missing_values_handling = match.arg(missing_values_handling),
                   quiet_mode = quiet_mode,
                   autoencoder = autoencoder,
                   sparse = sparse,
                   col_major = col_major,
                   average_activation = average_activation,
                   sparsity_beta = sparsity_beta,
                   max_categorical_features = max_categorical_features,
                   reproducible = reproducible,
                   export_weights_and_biases = export_weights_and_biases,
                   mini_batch_size = mini_batch_size,
                   categorical_encoding = match.arg(categorical_encoding),
                   elastic_averaging = elastic_averaging,
                   elastic_averaging_moving_rate = elastic_averaging_moving_rate,
                   elastic_averaging_regularization = elastic_averaging_regularization) 
}


# Note: Naive Bayes is classification only; not available for regression
h2o.naiveBayes.wrapper <- function(x, y, training_frame, model_id = NULL,
                                   family = "AUTO",
                                   nfolds = 0,
                                   seed = -1,
                                   fold_assignment = c("AUTO", "Random", "Modulo", "Stratified"),
                                   fold_column = NULL,
                                   keep_cross_validation_predictions = TRUE,
                                   keep_cross_validation_fold_assignment = FALSE,
                                   validation_frame = NULL,
                                   ignore_const_cols = TRUE,
                                   score_each_iteration = FALSE,
                                   balance_classes = FALSE,
                                   class_sampling_factors = NULL,
                                   max_after_balance_size = 5.0,
                                   laplace = 0,
                                   #threshold = 0.001,  #deprecated
                                   min_sdev = 0.001,
                                   #eps = 0,  #deprecated
                                   eps_sdev = 0,
                                   min_prob = 0.001,
                                   eps_prob = 0,
                                   compute_metrics = TRUE,
                                   max_runtime_secs = 0,
                                   ...) {
  
  # If family is not specified, set it using the datatype of the response column
  if (family == "AUTO") {
    if (is.factor(training_frame[,y])) {
      family <- "binomial"
    } 
  }
  if (family != "binomial") {
    # TO DO: Add a check in the h2o.stack and h2o.ensemble code so that this will fail early
    stop("Naive Bayes cannot be used as a base learner for regression problems.\nThe response variable must be categorical and family must be binomial.")
  }
  h2o.naiveBayes(x = x, 
                 y = y, 
                 training_frame = training_frame, 
                 model_id = model_id,
                 nfolds = nfolds,
                 seed = seed,
                 fold_assignment = match.arg(fold_assignment),
                 fold_column = fold_column,
                 keep_cross_validation_predictions = TRUE,  #must have for stacking
                 keep_cross_validation_fold_assignment = keep_cross_validation_fold_assignment,
                 validation_frame = validation_frame,
                 ignore_const_cols = ignore_const_cols,
                 score_each_iteration = score_each_iteration,
                 balance_classes = balance_classes,
                 class_sampling_factors = class_sampling_factors,
                 max_after_balance_size = max_after_balance_size,
                 laplace = laplace,
                 #threshold = threshold,  #deprecated
                 min_sdev = min_sdev,
                 #eps = eps,  #deprecated
                 eps_sdev = eps_sdev,
                 min_prob = min_prob,
                 eps_prob = eps_prob,
                 compute_metrics = compute_metrics,
                 max_runtime_secs = max_runtime_secs)
}
