# Set of default wrappers to create a uniform interface for h2o supervised ML functions (H2O 3.0 and above)

# Example of a wrapper function:
h2o.example.wrapper <- function(x, y, training_frame, model_id = "", family = c("gaussian", "binomial"), ...) {
  # This function is just an example.  
  # You can wrap any H2O learner inside a wrapper function, example: h2o.glm
  h2o.glm(x = x, y = y, training_frame = training_frame, family = family)
}



# Wrappers for: h2o.glm, h2o.randomForest, h2o.gbm, h2o.deeplearning:


# This is a version of the h2o.glm.wrapper which doesn't pass along all the args
# Use this version until this is resolved: https://0xdata.atlassian.net/browse/PUBDEV-1558
# beta_constraints currently causing a bug: https://0xdata.atlassian.net/browse/PUBDEV-1556
h2o.glm.wrapper <- function(x, y, training_frame, model_id = "", validation_frame = NULL, max_iterations = 50,
                            beta_epsilon = 0, solver = c("IRLSM", "L_BFGS"), standardize = TRUE,
                            family = c("gaussian", "binomial", "poisson", "gamma", "tweedie"),
                            link = c("family_default", "identity", "logit", "log", "inverse", "tweedie"), 
                            tweedie_variance_power = NaN, tweedie_link_power = NaN,
                            alpha = 0.5, prior = 0, lambda = 1e-05, lambda_search = FALSE,
                            nlambdas = -1, lambda_min_ratio = -1, nfolds = 0, fold_column = NULL,
                            fold_assignment = c("AUTO", "Random", "Modulo"),
                            keep_cross_validation_predictions = TRUE, beta_constraints = NULL,
                            offset_column = NULL, weights_column = NULL, intercept = TRUE, ...) {
  
  # Also, offset_column, weights_column, intercept not implemented at the moment
  h2o.glm(x = x, y = y, training_frame = training_frame, model_id = model_id, 
          validation_frame = validation_frame, max_iterations = max_iterations,
          beta_epsilon = beta_epsilon, solver = match.arg(solver), standardize = standardize,
          family = match.arg(family), 
          link = match.arg(link), 
          alpha = alpha, prior = prior, lambda = lambda, lambda_search = lambda_search, 
          nlambdas = nlambdas, lambda_min_ratio = lambda_min_ratio, nfolds = nfolds, fold_column = fold_column,
          fold_assignment = match.arg(fold_assignment),
          #keep_cross_validation_predictions = keep_cross_validation_predictions, beta_constraints = beta_constraints)
          keep_cross_validation_predictions = keep_cross_validation_predictions)#, beta_constraints = beta_constraints)
}



h2o.gbm.wrapper <- function(x, y, training_frame, model_id = "", #checkpoint
                            family = c("AUTO", "gaussian", "bernoulli", "binomial", "multinomial", "poisson", "gamma", "tweedie"),
                            tweedie_power = 1.5, ntrees = 50, max_depth = 5, min_rows = 10,
                            learn_rate = 0.1, sample_rate = 1, col_sample_rate = 1,
                            nbins = 20, nbins_cats = 1024,
                            validation_frame = NULL, balance_classes = FALSE,
                            max_after_balance_size = 1, seed, build_tree_one_node = FALSE,
                            nfolds = 0, fold_column = NULL,
                            fold_assignment = c("AUTO", "Random", "Modulo"),
                            keep_cross_validation_predictions = TRUE,
                            score_each_iteration = FALSE, offset_column = NULL,
                            weights_column = NULL, ...) {
  
  family <- match.arg(family)
  if (family == "binomial") {
    distribution <- "bernoulli"
  } else if (family %in% c("AUTO","gaussian", "bernoulli","multinomial","poisson","gamma","tweedie")) {
    distribution <- family
  } else {
    stop("`family` not supported in `h2o.gbm.wrapper`")
  }
  h2o.gbm(x = x, y = y, training_frame = training_frame, model_id = model_id, 
          distribution = distribution, tweedie_power = tweedie_power, ntrees = ntrees, max_depth = max_depth, min_rows = min_rows, 
          learn_rate = learn_rate, sample_rate = sample_rate, col_sample_rate = col_sample_rate,
          nbins = nbins, nbins_cats = nbins_cats, 
          validation_frame = validation_frame, balance_classes = balance_classes, 
          max_after_balance_size = max_after_balance_size, seed = seed, build_tree_one_node = build_tree_one_node, 
          nfolds = nfolds, fold_column = fold_column, fold_assignment = match.arg(fold_assignment), 
          keep_cross_validation_predictions = keep_cross_validation_predictions, 
          score_each_iteration = score_each_iteration)#, #offset_column = offset_column,
          #weights_column = weights_column)
}


h2o.randomForest.wrapper <- function(x, y, training_frame, model_id = "",
                                     family = c("binomial", "multinomial", "gaussian"), 
                                     validation_frame = NULL, #checkpoint 
                                     mtries = -1, sample_rate = 0.632, build_tree_one_node = FALSE,
                                     ntrees = 50, max_depth = 20, min_rows = 1, nbins = 20,
                                     nbins_cats = 1024, binomial_double_trees = FALSE,
                                     balance_classes = FALSE, max_after_balance_size = 5, seed, 
                                     offset_column = NULL, weights_column = NULL, nfolds = 0, 
                                     fold_column = NULL,
                                     fold_assignment = c("AUTO", "Random", "Modulo"), 
                                     keep_cross_validation_predictions = TRUE, ...) {
  
  # Currently ignoring the `family` arg, will get class from outcome in H2OFrame
  # TO DO: Add a check to make sure that outcome/family type is correct
  h2o.randomForest(x = x, y = y, training_frame = training_frame, model_id = model_id, 
                   validation_frame = validation_frame, mtries = mtries, sample_rate = sample_rate, 
                   build_tree_one_node = build_tree_one_node, ntrees = ntrees, max_depth = max_depth, 
                   min_rows = min_rows, nbins = nbins, nbins_cats = nbins_cats, 
                   binomial_double_trees = binomial_double_trees,balance_classes = balance_classes, 
                   max_after_balance_size = max_after_balance_size, seed = seed,
                   offset_column = offset_column, weights_column = weights_column,
                   nfolds = nfolds, fold_column = fold_column, 
                   fold_assignment = match.arg(fold_assignment),
                   keep_cross_validation_predictions = keep_cross_validation_predictions)
}


h2o.deeplearning.wrapper <- function(x, y, training_frame, model_id = "",
                                     family = c("binomial", "multinomial", "gaussian"), 
                                     overwrite_with_best_model, validation_frame = NULL, checkpoint,
                                     autoencoder = FALSE, use_all_factor_levels = TRUE,
                                     activation = c("Rectifier", "Tanh", "TanhWithDropout",
                                                   "RectifierWithDropout", "Maxout", "MaxoutWithDropout"), 
                                     hidden = c(200, 200), epochs = 10, train_samples_per_iteration = -2, 
                                     #target_ratio_comm_to_comp = 0.05,  #not on stable yet
                                     seed, 
                                     adaptive_rate = TRUE, rho = 0.99, epsilon = 1e-08, rate = 0.005,
                                     rate_annealing = 1e-06, rate_decay = 1, momentum_start = 0,
                                     momentum_ramp = 1e+06, momentum_stable = 0,
                                     nesterov_accelerated_gradient = TRUE, input_dropout_ratio = 0,
                                     hidden_dropout_ratios, l1 = 0, l2 = 0, max_w2 = Inf,
                                     initial_weight_distribution = c("UniformAdaptive", "Uniform", "Normal"),
                                     initial_weight_scale = 1,
                                     loss = c("Automatic", "CrossEntropy", "Quadratic", "Absolute", "Huber"), 
                                     distribution = c("AUTO", "gaussian", "bernoulli", "multinomial", 
                                                      "poisson", "gamma", "tweedie", "laplace", "huber"),
                                     tweedie_power = 1.5, score_interval = 5,
                                     score_training_samples, score_validation_samples, score_duty_cycle,
                                     classification_stop, regression_stop, quiet_mode, max_confusion_matrix_size,
                                     max_hit_ratio_k, balance_classes = FALSE, class_sampling_factors,
                                     max_after_balance_size, score_validation_sampling, diagnostics,
                                     variable_importances, fast_mode, ignore_const_cols, force_load_balance,
                                     replicate_training_data, single_node_mode, shuffle_training_data, sparse,
                                     col_major, average_activation, sparsity_beta, max_categorical_features,
                                     reproducible = FALSE, export_weights_and_biases = FALSE, 
                                     offset_column = NULL, weights_column = NULL, nfolds = 0, 
                                     fold_column = NULL, fold_assignment  = c("AUTO", "Random", "Modulo"),
                                     keep_cross_validation_predictions = TRUE, ...) {
  
  # Currently ignoring the `family` arg, will get class from outcome in H2OFrame
  h2o.deeplearning(x = x, y = y, training_frame = training_frame, model_id = model_id,
                   overwrite_with_best_model = overwrite_with_best_model, 
                   validation_frame = validation_frame, checkpoint = checkpoint, 
                   autoencoder = autoencoder, use_all_factor_levels = use_all_factor_levels, 
                   activation = match.arg(activation), 
                   hidden = hidden, epochs = epochs, train_samples_per_iteration = train_samples_per_iteration, 
                   #target_ratio_comm_to_comp = target_ratio_comm_to_comp, 
                   seed = seed, 
                   adaptive_rate = adaptive_rate, rho = rho, epsilon = epsilon, rate = rate,
                   rate_annealing = rate_annealing, rate_decay = rate_decay, momentum_start = momentum_start,
                   momentum_ramp = momentum_ramp, momentum_stable = momentum_stable, 
                   nesterov_accelerated_gradient = nesterov_accelerated_gradient, 
                   input_dropout_ratio = input_dropout_ratio, 
                   hidden_dropout_ratios = hidden_dropout_ratios, l1 = l1, l2 = l2, max_w2 = max_w2,
                   initial_weight_distribution = match.arg(initial_weight_distribution), 
                   initial_weight_scale = initial_weight_scale, 
                   loss = match.arg(loss), 
                   distribution = match.arg(distribution),
                   tweedie_power = tweedie_power, score_interval = score_interval,
                   score_training_samples = score_training_samples, score_validation_samples = score_validation_samples, score_duty_cycle = score_duty_cycle,
                   classification_stop = classification_stop, regression_stop = regression_stop, quiet_mode = quiet_mode, max_confusion_matrix_size = max_confusion_matrix_size,
                   max_hit_ratio_k = max_hit_ratio_k, balance_classes = balance_classes, class_sampling_factors = class_sampling_factors,
                   max_after_balance_size = max_after_balance_size, score_validation_sampling = score_validation_sampling, diagnostics = diagnostics,
                   variable_importances = variable_importances, fast_mode = fast_mode, ignore_const_cols = ignore_const_cols, force_load_balance = force_load_balance,
                   replicate_training_data = replicate_training_data, single_node_mode = single_node_mode, shuffle_training_data = shuffle_training_data, sparse = sparse,
                   col_major = col_major, average_activation = average_activation, sparsity_beta = sparsity_beta, max_categorical_features = max_categorical_features,
                   reproducible = reproducible, export_weights_and_biases = export_weights_and_biases, 
                   offset_column = offset_column, weights_column = weights_column, nfolds = nfolds, 
                   fold_column = fold_column, fold_assignment = match.arg(fold_assignment),
                   keep_cross_validation_predictions = keep_cross_validation_predictions) 
}

