# Set of default wrappers to create a uniform interface for h2o supervised ML functions (H2O 3.0 and above)
# Wrappers for: h2o.glm, h2o.randomForest, h2o.gbm, h2o.deeplearning

# TO DO: Normalize the ML "task" so that family and distribution args can be set...
# Currently still using the family argument (backward compatibility with h2o-2)
# however might just delete the family from the wrappers that don't use it such as `h2o.randomForest.wrapper`


# h2o.glm.wrapper <- function(x, y, training_frame = training_frame, model_id = "", validation_frame = NULL, max_iterations = 50,
#                             beta_epsilon = 0, solver = c("IRLSM", "L_BFGS"), standardize = TRUE,
#                             family = c("gaussian", "binomial", "poisson", "gamma", "tweedie"),
#                             link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"), 
#                             tweedie_variance_power = NaN, tweedie_link_power = NaN,
#                             alpha = 0.5, prior = 0, lambda = 1e-05, lambda_search = FALSE,
#                             nlambdas = -1, lambda_min_ratio = -1, nfolds = 0, beta_constraints = NULL,
#                             offset_column = NULL, weights_column = NULL, intercept = TRUE, ...) {
#   
#   print(match.arg(solver))
#   h2o.glm(x = x, y = y, training_frame = training_frame, model_id = model_id, 
#           validation_frame = validation_frame, max_iterations = max_iterations,
#           beta_epsilon = beta_epsilon, solver = match.arg(solver), standardize = standardize,
#           family = match.arg(family), link = match.arg(link), tweedie_variance_power = tweedie_variance_power, 
#           tweedie_link_power = tweedie_link_power, alpha = alpha, prior = prior, 
#           lambda = lambda, lambda_search = lambda_search, nlambdas = nlambdas, 
#           lambda_min_ratio = lambda_min_ratio, nfolds = nfolds, 
#           offset_column = offset_column, 
#           # beta_constraints currently causing a bug: https://0xdata.atlassian.net/browse/PUBDEV-1556
#           #          beta_constraints = beta_constraints, offset_column = offset_column, 
#           weights_column = weights_column, intercept = intercept)
# }


# This is the hacked version for now which doesn't pass along all the args
# Use this version until this is resolved: https://0xdata.atlassian.net/browse/PUBDEV-1558
h2o.glm.wrapper <- function(x, y, training_frame = training_frame, model_id = "", validation_frame = NULL, max_iterations = 50,
                            beta_epsilon = 0, solver = c("IRLSM", "L_BFGS"), standardize = TRUE,
                            family = c("gaussian", "binomial", "poisson", "gamma", "tweedie"),
                            link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"), 
                            tweedie_variance_power = NaN, tweedie_link_power = NaN,
                            alpha = 0.5, prior = 0, lambda = 1e-05, lambda_search = FALSE,
                            nlambdas = -1, lambda_min_ratio = -1, nfolds = 0, beta_constraints = NULL,
                            offset_column = NULL, weights_column = NULL, intercept = TRUE, ...) {
  
  h2o.glm(x = x, y = y, training_frame = training_frame, model_id = model_id, 
          validation_frame = validation_frame, max_iterations = max_iterations,
          beta_epsilon = beta_epsilon, solver = match.arg(solver), standardize = standardize,
          family = match.arg(family), link = match.arg(link), 
          alpha = alpha, prior = prior, 
          lambda = lambda, lambda_search = lambda_search, nlambdas = nlambdas, 
          lambda_min_ratio = lambda_min_ratio, nfolds = nfolds)
}



h2o.gbm.wrapper <- function(x, y, training_frame = training_frame , model_id, 
                            family = c("binomial", "multinomial", "gaussian"),
                            ntrees = 50, max_depth = 5, min_rows = 10,
                            learn_rate = 0.1, nbins = 20, nbins_cats = 1024,
                            validation_frame = NULL, balance_classes = FALSE,
                            max_after_balance_size = 1, seed, nfolds, score_each_iteration, ...) {
  
  family <- match.arg(family)
  if (family == "binomial") {
    distribution <- "bernoulli"
  } else if (family == "gaussian") {
    distribution <- "gaussian"
  } else {
    stop("`family` not supported in `h2o.gbm.wrapper`")
  }
  h2o.gbm(x = x, y = y, training_frame = training_frame, model_id = model_id, 
          distribution = distribution, ntrees = ntrees, max_depth = max_depth, 
          min_rows = min_rows, learn_rate = learn_rate, nbins = nbins, 
          nbins_cats = nbins_cats, validation_frame = validation_frame, 
          balance_classes = balance_classes, max_after_balance_size = max_after_balance_size, 
          seed = seed, nfolds = nfolds, score_each_iteration = score_each_iteration)
}


h2o.randomForest.wrapper <- function(x, y, training_frame = training_frame, model_id = "",
                                     family = c("binomial", "multinomial", "gaussian"), 
                                     validation_frame = validation_frame,
                                     mtries = -1, sample_rate = 0.632, build_tree_one_node = FALSE,
                                     ntrees = 50, max_depth = 20, min_rows = 1, nbins = 20,
                                     nbins_cats = 1024, binomial_double_trees = TRUE,
                                     balance_classes = FALSE, max_after_balance_size = 5, seed, ...) {
  
  # Currently ignoring the `family` arg, will get class from outcome in H2OFrame
  # TO DO: Add a check to make sure that outcome/family type is correct
  h2o.randomForest(x = x, y = y, training_frame = training_frame, model_id = model_id, 
                   validation_frame = validation_frame, mtries = mtries, sample_rate = sample_rate, 
                   build_tree_one_node = build_tree_one_node, ntrees = ntrees, max_depth = max_depth, 
                   min_rows = min_rows, nbins = nbins, nbins_cats = nbins_cats, 
                   binomial_double_trees = binomial_double_trees,balance_classes = balance_classes, 
                   max_after_balance_size = max_after_balance_size, seed = seed)
}


h2o.deeplearning.wrapper <- function(x, y, training_frame = training_frame, model_id = "",
                                     family = c("binomial", "multinomial", "gaussian"), 
                                     overwrite_with_best_model, validation_frame, checkpoint,
                                     autoencoder = FALSE, use_all_factor_levels = TRUE,
                                     #activation = c("Rectifier"),                                     
                                     activation = c("Rectifier", "Tanh", "TanhWithDropout",
                                                   "RectifierWithDropout", "Maxout", "MaxoutWithDropout"), 
                                     hidden = c(200, 200), epochs = 10, train_samples_per_iteration = -2, seed, 
                                     adaptive_rate = TRUE, rho = 0.99, epsilon = 1e-08, rate = 0.005,
                                     rate_annealing = 1e-06, rate_decay = 1, momentum_start = 0,
                                     momentum_ramp = 1e+06, momentum_stable = 0,
                                     nesterov_accelerated_gradient = TRUE, input_dropout_ratio = 0,
                                     hidden_dropout_ratios, l1 = 0, l2 = 0, max_w2 = Inf,
                                     initial_weight_distribution = c("UniformAdaptive", "Uniform", "Normal"),
                                     #initial_weight_distribution = c("UniformAdaptive"),
                                     initial_weight_scale = 1,
                                     #loss = c("Automatic"),
                                     loss = c("Automatic", "CrossEntropy", "MeanSquare", "Absolute", "Huber"), 
                                     score_interval = 5,
                                     score_training_samples, score_validation_samples, score_duty_cycle,
                                     classification_stop, regression_stop, quiet_mode, max_confusion_matrix_size,
                                     max_hit_ratio_k, balance_classes = FALSE, class_sampling_factors,
                                     max_after_balance_size, score_validation_sampling, diagnostics,
                                     variable_importances, fast_mode, ignore_const_cols, force_load_balance,
                                     replicate_training_data, single_node_mode, shuffle_training_data, sparse,
                                     col_major, average_activation, sparsity_beta, max_categorical_features,
                                     reproducible = FALSE, export_weights_and_biases = FALSE, ...) {
  
  # Currently ignoring the `family` arg, will get class from outcome in H2OFrame
  h2o.deeplearning(x = x, y = y, training_frame = training_frame, model_id = model_id,
                   overwrite_with_best_model = overwrite_with_best_model, 
                   validation_frame = validation_frame, checkpoint = checkpoint, autoencoder = autoencoder, 
                   use_all_factor_levels = use_all_factor_levels, activation = match.arg(activation), 
                   hidden = hidden, epochs = epochs, train_samples_per_iteration = train_samples_per_iteration, 
                   seed = seed, adaptive_rate = adaptive_rate, rho = rho, epsilon = epsilon, rate = rate,
                   rate_annealing = rate_annealing, rate_decay = rate_decay, momentum_start = momentum_start,
                   momentum_ramp = momentum_ramp, momentum_stable = momentum_stable, nesterov_accelerated_gradient = nesterov_accelerated_gradient, 
                   input_dropout_ratio = input_dropout_ratio, hidden_dropout_ratios = hidden_dropout_ratios, l1 = l1, l2 = l2, max_w2 = max_w2,
                   initial_weight_distribution = match.arg(initial_weight_distribution), initial_weight_scale = initial_weight_scale, loss = match.arg(loss), 
                   score_interval = score_interval, score_training_samples = score_training_samples, score_validation_samples = score_validation_samples, 
                   score_duty_cycle = score_duty_cycle, classification_stop = classification_stop, regression_stop = regression_stop, 
                   quiet_mode = quiet_mode, max_confusion_matrix_size = max_confusion_matrix_size, max_hit_ratio_k = max_hit_ratio_k, 
                   balance_classes = balance_classes, class_sampling_factors = class_sampling_factors, max_after_balance_size = max_after_balance_size, 
                   score_validation_sampling = score_validation_sampling, diagnostics = diagnostics, variable_importances = variable_importances, 
                   fast_mode = fast_mode, ignore_const_cols = ignore_const_cols, force_load_balance = force_load_balance,
                   replicate_training_data = replicate_training_data, single_node_mode = single_node_mode, shuffle_training_data = shuffle_training_data, 
                   sparse = sparse, col_major = col_major, average_activation = average_activation, sparsity_beta = sparsity_beta, 
                   max_categorical_features = max_categorical_features, reproducible = reproducible, export_weights_and_biases = export_weights_and_biases) 
}

