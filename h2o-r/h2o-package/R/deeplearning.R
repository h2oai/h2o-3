# ---------------------------- Deep Learning - Neural Network ---------------- #
#' Build a Deep Learning Neural Network
#'
#' Performs Deep Learning neural networks on an \linkS4class{H2OFrame}
#'
#' @param x A vector containing the \code{character} names of the predictors in the model.
#' @param y The name of the response variable in the model.
#' @param data An \linkS4class{H2OFrame} object containing the variables in the model.
#' @param key (Optional) The unique \code{character} hex key assigned to the resulting model. If none is given, a key will automatically be generated.
#' @param override_with_best_model Logcial. If \code{TRUE}, override the final model with the best model found during traning. Defaults to \code{TRUE}.
#' @param classification Logical. Indicates whether the algorithm should conduct classification.
#' @param nfolds (Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty.
#' @param validation (Optional) An \code{\link{H2OFrame}} object indicating the validation dataset used to contruct the confusion matrix. If left blank, this defaults to the training data when \code{nfolds = 0}
#' @param checkpoint "Model checkpoint (either key or H2ODeepLearningModel) to resume training with."
#' @param autoencoder Enable auto-encoder for model building.
#' @param use_all_factor_levels \code{Logical}. Use all factor levels of categorical variance. Otherwise the first factor level is omittted (without loss of accuracy). Useful for variable imporotances and auto-enabled for autoencoder.
#' @param activation A string indicating the activation function to use. Must be either "Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", or "MaxoutWithDropout"
#' @param hidden Hidden layer sizes (e.g. c(100,100))
#' @param epochs How many times the dataset shoud be iterated (streamed), can be fractional
#' @param train_samples_per_iteration Number of training samples (globally) per MapReduce iteration. Special values are: \bold{0} one epoch; \bold{-1} all available data (e.g., replicated training data); or \bold{-2} auto-tuning (default)
#' @param seed Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded
#' @param adaptive_rate \code{Logical}. Adaptive learning rate (ADAELTA)
#' @param rho Adaptive learning rate time decay factor (similarity to prior updates)
#' @param rate Learning rate (higher => less stable, lower => slower convergence)
#' @param rate_annealing Learning rate annealing: \eqn{(rate)/(1 + rate_annealing*samples)}
#' @param rate_decay Learning rate decay factor between layers (N-th layer: \eqn{rate*\alpha^(N-1)})
#' @param momentum_start Initial momentum at the beginning of traning (try 0.5)
#' @param momentum_ramp Number of training samples for which momentum increases
#' @param momentum_stable Final momentum after ther amp is over (try 0.99)
#' @param nesterov_accelarated_gradient \code{Logical}. Use Nesterov accelerated gradient (reccomended)
#' @param input_dropout_ratios Input layer dropout ration (can improve generalization) specify one value per hidden layer, defaults to 0.5
#' @param l1 L1 regularization (can add stability and imporve generalization, cause many weights to become 0)
#' @param l2 L2 regularization (can add stability and improve generalization, causes many weights to be small)
#' @param max_w2 Constraint for squared sum of incoming weights per unit (e.g. Rectifier)
#' @param initial_weight_distribution Can be "Uniform", "UniformAdaptive", or "Normal"
#' @param initial_weight_scale Unifrom: -value ... value, Normal: stddev
#' @param loss Loss function. Can be "Automatic", "MeanSquare", or "CrossEntropy"
#' @param score_interval Shortest time interval (in secs) between model scoring
#' @param score_training_samples Number of training set samples for scoring (0 for all)
#' @param score_validation_samples Number of validation set samples for scoring (0 for all)
#' @param score_duty_cycle Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring)
#' @param classification_stop Stopping criterion for classification error fraction on training data (-1 to disable)
#' @param regression_stop Stopping criterion for regression error (MSE) on training data (-1 to disable)
#' @param quiet_mode Enable quiet mode for less output to standard output
#' @param max_confusion_matrix_size Max. size (number of classes) for confusion matrices to be shown
#' @param max_hit_ratio_k Max number (top K) of predictions to use for hit ration computation(for multi-class only, 0 to disable)
#' @param balance_classes Balance training data class counts via over/under-sampling (for imbalanced data)
#' @param max_after_balance_size Maximum relative size of the training data after balancing class counts (can be less than 1.0)
#' @param score_validation_sampling Method used to sample validation dataset for scoring
#' @param diagnostics Enable diagnostics for hidden layers
#' @param variable_importances Compute variable importances for input features (Gedeon method) - can be slow for large networks)
#' @param fast_mode Enable fast mode (minor approximations in back-propagation)
#' @param ignore_const_cols Igrnore constant training columns (no information can be gained anwyay)
#' @param force_load_balance Force extra load balancing to increase training speed for small datasets (to keep all cores busy)
#' @param replicate_training_data Replicate the entire training dataset onto every node for faster training
#' @param single_node_mode Run on a single node for fine-tuning of model parameters
#' @param shuffle_training_data Enable shuffling of training data (recommended if training data is replicated and train_samples_per_iteration is close to \eqn{numRows*numNodes}
#' @param sparse Sparse data handling (Experimental)
#' @param col_major Use a column major weight matrix for input layer. Can speed up forward proagation, but might slow down backpropagation (Experimental)
#' @seealso \code{\link{predict.H2ODeepLearningModel}} for prediction.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#'
#' irisPath <- system.file("extdata", "iris.csv", package = "h2o")
#' iris.hex <- h2o.uploadFile(localH2O, path = irisPath)
#' indep <- names(iris.hex)[1:4]
#' dep <- names(iris.hex)[5]
#' iris.dl <- h2o.deeplearning(x = indep, y = dep, data = iris.hex, activation = "Tanh", epochs = 5)

h2o.deeplearning <- function(x, y, training_frame, key = "",
                             override_with_best_model,
                             classification = TRUE,
                             n_folds = 0,
                             validation_frame,
                             ...,
                             # ----- AUTOGENERATED PARAMETERS BEGIN -----
                             checkpoint,
                             autoencoder = FALSE,
                             use_all_factor_levels = TRUE,
                             activation = c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),
                             hidden= c(200, 200),
                             epochs = 10.0,
                             train_samples_per_iteration = -2,
                             seed,
                             adaptive_rate = TRUE,
                             rho = 0.99,
                             epsilon = 1e-8,
                             rate = 0.005,
                             rate_annealing = 1e-6,
                             rate_decay = 1.0,
                             momentum_start = 0,
                             momentum_ramp = 1e6,
                             momentum_stable = 0,
                             nesterov_accelerated_gradient = TRUE,
                             input_dropout_ratio = 0,
                             hidden_dropout_ratios,
                             l1 = 0,
                             l2 = 0,
                             max_w2 = Inf,
                             initial_weight_distribution = c("UniformAdaptive", "Uniform", "Normal"),
                             initial_weight_scale = 1,
                             loss,
                             score_interval = 5,
                             score_training_samples,
                             score_validation_samples,
                             score_duty_cycle,
                             classification_stop,
                             regression_stop,
                             quiet_mode,
                             max_confusion_matrix_size,
                             max_hit_ratio_k,
                             balance_classes = FALSE,
                             max_after_balance_size,
                             score_validation_sampling,
                             diagnostics,
                             variable_importances,
                             fast_mode,
                             ignore_const_cols,
                             force_load_balance,
                             replicate_training_data,
                             single_node_mode,
                             shuffle_training_data,
                             sparse,
                             col_major
                             # ----- AUTOGENERATED PARAMETERS END -----
)
{
  dots <- list(...)
  
  for(type in dots)
    if (is.environment(type))
    {
      dots$envir <- type
      type <- NULL
    }
  if (is.null(dots$envir)) 
    dots$envir <- parent.frame()
  
  if( missing(x) ) stop("`x` is missing, with no default")
  if( missing(y) ) stop("`y` is missing, with no default")
  if( missing(training_frame) ) stop("`training_frame` is missing, with no default")

  colargs <- .verify_dataxy(training_frame, x, y, autoencoder)

  .deeplearning.map <-  c("x" = "ignored_columns",
                          "y" = "response_column")

  parms <- as.list(match.call(expand.dots = FALSE)[-1L])
  parms$... <- NULL
  
  parms$y <- colargs$y
  parms$x <- colargs$x_ignore
  names(parms) <- lapply(names(parms), function(i) { if( i %in% names(.deeplearning.map) ) i <- .deeplearning.map[[i]]; i })
  parms$max_after_balance_size <- 1 #hard-code max_after_balance_size until Inf fixed
  # parms$max_w2 <- 1e6 #hard code max_w2 until Inf fixed

  .run(training_frame@h2o, 'deeplearning', parms, dots$envir)

#  if(nfolds == 1) stop("nfolds cannot be 1")
#  if(!missing(validation) && class(validation) != "H2OFrame")
#    stop("validation must be an H2O parsed dataset")
#
#  if(missing(validation) && nfolds == 0) {
#    # validation = data
#    # parms$validation = validation@key
#    validation <- new ("H2OFrame", key = as.character(NA))
#    parms$n_folds <- nfolds
#  } else if(missing(validation) && nfolds >= 2) {
#    validation <- new("H2OFrame", key = as.character(NA))
#    parms$n_folds <- nfolds
#  } else if(!missing(validation) && nfolds == 0)
#    parms$validation <- validation@key
#  else stop("Cannot set both validation and nfolds at the same time")
##
#  if (missing(checkpoint)) {
#    parms$checkpoint <- ""
#  } else {
#    if(is.character(checkpoint)) {
#      if(nchar(checkpoint) > 0 && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", checkpoint)[1] == -1)
#        stop("checkpoint must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
#      parms$checkpoint <- checkpoint
#    } else {
#      if (class(checkpoint) != "H2ODeepLearningModel") stop('checkpoint must be valid key or an object of type H2ODeepLearningModel')
#      parms$checkpoint <- checkpoint@key
#    }
#  }
#
#  # ----- Check AUTOGENERATED PARAMETERS -----
#
#  # verify activation
#  if (!missing(activation)) {
#    if(!(activation %in% c("Tanh", "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"))) stop("activation must be \"Tanh\", \"TanhWithDropout\", \"Rectifier\", \"RectifierWithDropout\", \"Maxout\", or \"MaxoutWithDropout\".")
#  }
#
#  # ----- AUTOGENERATED PARAMETERS BEGIN -----
#  parms <- .addBooleanParm(parms, k="override_with_best_model", v=override_with_best_model)
#  parms <- .addBooleanParm(parms, k="autoencoder", v=autoencoder)
#  parms <- .addBooleanParm(parms, k="use_all_factor_levels", v=use_all_factor_levels)
#  parms <- .addStringParm(parms, k="activation", v=activation)
#  parms <- .addIntArrayParm(parms, k="hidden", v=hidden)
#  parms <- .addDoubleParm(parms, k="epochs", v=epochs)
#  parms <- .addLongParm(parms, k="train_samples_per_iteration", v=train_samples_per_iteration)
#  parms <- .addLongParm(parms, k="seed", v=seed)
#  parms <- .addBooleanParm(parms, k="adaptive_rate", v=adaptive_rate)
#  parms <- .addDoubleParm(parms, k="rho", v=rho)
#  parms <- .addDoubleParm(parms, k="epsilon", v=epsilon)
#  parms <- .addDoubleParm(parms, k="rate", v=rate)
#  parms <- .addDoubleParm(parms, k="rate_annealing", v=rate_annealing)
#  parms <- .addDoubleParm(parms, k="rate_decay", v=rate_decay)
#  parms <- .addDoubleParm(parms, k="momentum_start", v=momentum_start)
#  parms <- .addDoubleParm(parms, k="momentum_ramp", v=momentum_ramp)
#  parms <- .addDoubleParm(parms, k="momentum_stable", v=momentum_stable)
#  parms <- .addBooleanParm(parms, k="nesterov_accelerated_gradient", v=nesterov_accelerated_gradient)
#  parms <- .addDoubleParm(parms, k="input_dropout_ratio", v=input_dropout_ratio)
#  parms <- .addDoubleArrayParm(parms, k="hidden_dropout_ratios", v=hidden_dropout_ratios)
#  parms <- .addDoubleParm(parms, k="l1", v=l1)
#  parms <- .addDoubleParm(parms, k="l2", v=l2)
#  parms <- .addFloatParm(parms, k="max_w2", v=max_w2)
#  parms <- .addStringParm(parms, k="initial_weight_distribution", v=initial_weight_distribution)
#  parms <- .addDoubleParm(parms, k="initial_weight_scale", v=initial_weight_scale)
#  parms <- .addStringParm(parms, k="loss", v=loss)
#  parms <- .addDoubleParm(parms, k="score_interval", v=score_interval)
#  parms <- .addLongParm(parms, k="score_training_samples", v=score_training_samples)
#  parms <- .addLongParm(parms, k="score_validation_samples", v=score_validation_samples)
#  parms <- .addDoubleParm(parms, k="score_duty_cycle", v=score_duty_cycle)
#  parms <- .addDoubleParm(parms, k="classification_stop", v=classification_stop)
#  parms <- .addDoubleParm(parms, k="regression_stop", v=regression_stop)
#  parms <- .addBooleanParm(parms, k="quiet_mode", v=quiet_mode)
#  parms <- .addIntParm(parms, k="max_confusion_matrix_size", v=max_confusion_matrix_size)
#  parms <- .addIntParm(parms, k="max_hit_ratio_k", v=max_hit_ratio_k)
#  parms <- .addBooleanParm(parms, k="balance_classes", v=balance_classes)
#  parms <- .addFloatParm(parms, k="max_after_balance_size", v=max_after_balance_size)
#  parms <- .addStringParm(parms, k="score_validation_sampling", v=score_validation_sampling)
#  parms <- .addBooleanParm(parms, k="diagnostics", v=diagnostics)
#  parms <- .addBooleanParm(parms, k="variable_importances", v=variable_importances)
#  parms <- .addBooleanParm(parms, k="fast_mode", v=fast_mode)
#  parms <- .addBooleanParm(parms, k="ignore_const_cols", v=ignore_const_cols)
#  parms <- .addBooleanParm(parms, k="force_load_balance", v=force_load_balance)
#  parms <- .addBooleanParm(parms, k="replicate_training_data", v=replicate_training_data)
#  parms <- .addBooleanParm(parms, k="single_node_mode", v=single_node_mode)
#  parms <- .addBooleanParm(parms, k="shuffle_training_data", v=shuffle_training_data)
#  parms <- .addBooleanParm(parms, k="sparse", v=sparse)
#  parms <- .addBooleanParm(parms, k="col_major", v=col_major)
#  # ----- AUTOGENERATED PARAMETERS END -----
#
#  model_params <- .h2o.__remoteSend(data@h2o, '2/DeepLearning.json', .params = parms)
#  res <- .h2o.__remoteSend(data@h2o, method = "POST", .h2o.__MODEL_BUILDERS('deeplearning'), .params = parms)
#  parms$h2o <- data@h2o
#  parms$h2o <- data@h2o
#  noGrid <- missing(hidden) || !(is.list(hidden) && length(hidden) > 1)
#  noGrid <- noGrid && (missing(l1) || length(l1) == 1)
#  noGrid <- noGrid && (missing(l2) || length(l2) == 1)
#  noGrid <- noGrid && (missing(activation) || length(activation) == 1)
#  noGrid <- noGrid && (missing(rho) || length(rho) == 1) && (missing(epsilon) || length(epsilon) == 1)
#  noGrid <- noGrid && (missing(epochs) || length(epochs) == 1) && (missing(train_samples_per_iteration) || length(train_samples_per_iteration) == 1)
#  noGrid <- noGrid && (missing(adaptive_rate) || length(adaptive_rate) == 1) && (missing(rate_annealing) || length(rate_annealing) == 1)
#  noGrid <- noGrid && (missing(rate_decay) || length(rate_decay) == 1)
#  noGrid <- noGrid && (missing(momentum_ramp) || length(momentum_ramp) == 1)
#  noGrid <- noGrid && (missing(momentum_stable) || length(momentum_stable) == 1)
#  noGrid <- noGrid && (missing(momentum_start) || length(momentum_start) == 1)
#  noGrid <- noGrid && (missing(nesterov_accelerated_gradient) || length(nesterov_accelerated_gradient) == 1)
#
#  job_key <- res$key$name
#  dest_key <- res$jobs[[1]]$dest$name
#  .h2o.__waitOnJob(data@h2o, job_key)
#  res_model <- list()
#  res_model$params <- model_params
#  new("H2ODeepLearningModel", h2o = data@h2o, key = dest_key, model = res_model, valid = new("H2OFrame", h2o=data@h2o, key="NA"), xval = list())
#
##  if(noGrid)
##    .h2o.singlerun.internal("DeepLearning", data, res, nfolds, validation, parms)
##  else {
##    .h2o.gridsearch.internal("DeepLearning", data, res, nfolds, validation, parms)
##  }
}

# Function call for R sided cross validation of h2o objects
h2o.deeplearning.cv <- function(x, y, training_frame, nfolds = 2, ...,
                                key = "",
                              override_with_best_model,
                             classification = TRUE,
                             validation_frame,
                             # ----- AUTOGENERATED PARAMETERS BEGIN -----
                             checkpoint,
                             autoencoder = FALSE,
                             use_all_factor_levels = TRUE,
                             activation = c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),
                             hidden= c(200, 200),
                             epochs = 10.0,
                             train_samples_per_iteration = -2,
                             seed,
                             adaptive_rate = TRUE,
                             rho = 0.99,
                             epsilon = 1e-8,
                             rate = 0.005,
                             rate_annealing = 1e-6,
                             rate_decay = 1.0,
                             momentum_start = 0,
                             momentum_ramp = 1e6,
                             momentum_stable = 0,
                             nesterov_accelerated_gradient = TRUE,
                             input_dropout_ratio = 0,
                             hidden_dropout_ratios,
                             l1 = 0,
                             l2 = 0,
                             max_w2 = Inf,
                             initial_weight_distribution = c("UniformAdaptive", "Uniform", "Normal"),
                             initial_weight_scale = 1,
                             loss,
                             score_interval = 5,
                             score_training_samples,
                             score_validation_samples,
                             score_duty_cycle,
                             classification_stop,
                             regression_stop,
                             quiet_mode,
                             max_confusion_matrix_size,
                             max_hit_ratio_k,
                             balance_classes = FALSE,
                             max_after_balance_size,
                             score_validation_sampling,
                             diagnostics,
                             variable_importances,
                             fast_mode,
                             ignore_const_cols,
                             force_load_balance,
                             replicate_training_data,
                             single_node_mode,
                             shuffle_training_data,
                             sparse,
                             col_major
                             # ----- AUTOGENERATED PARAMETERS END -----
                            )
{
  env <- parent.frame()
  parms <- lapply(as.list(match.call()[-1L]), eval, env)
  parms$nfolds <- NULL
  
  do.call("h2o.crossValidate", list(model.type = 'deeplearning', nfolds = nfolds, params = parms, envir = env))
}

.h2o.__getDeepLearningSummary <- function(res) {
    result <- list()
    model_params <- res$model_info$job
    model_params$Request2 <- NULL; model_params$response_info <- NULL
    model_params$'source' <- NULL; model_params$validation <- NULL
    model_params$job_key <- NULL;
    model_params$start_time <- NULL; model_params$end_time <- NULL
    model_params$response <- NULL; model_params$description <- NULL
    if(!is.null(model_params$exception)) stop(model_params$exception)
    model_params$exception <- NULL; model_params$state <- NULL

    # Remove all NULL elements and cast to logical value
    if(length(model_params) > 0)
      model_params <- model_params[!sapply(model_params, is.null)]
    for(i in 1:length(model_params)) {
      x <- model_params[[i]]
      if(length(x) == 1 && is.character(x))
        model_params[[i]] <- switch(x, true = TRUE, false = FALSE, "Inf" = Inf, "-Inf" = -Inf, x)
    }
    result <- model_params

    #for backward-compatibility
    result$l1_reg <- result$l1
    result$l2_reg <- result$l2
    result$model_key <- result$destination_key

    return(result)
}

.h2o.__getDeepLearningResults <- function(res, params = list()) {
  result <- list()
  model_params <- res$model_info$job
  model_params$Request2 <- NULL; model_params$response_info = NULL
  model_params$'source' <- NULL; model_params$validation = NULL
  model_params$job_key <- NULL; model_params$destination_key = NULL
  model_params$response <- NULL; model_params$description = NULL
  if(!is.null(model_params$exception)) stop(model_params$exception)
  model_params$exception <- NULL; model_params$state = NULL

  # Remove all NULL elements and cast to logical value
  if(length(model_params) > 0)
    model_params <- model_params[!sapply(model_params, is.null)]
  for(i in 1:length(model_params)) {
    x <- model_params[[i]]
    if(length(x) == 1 && is.character(x))
      model_params[[i]] <- switch(x, true = TRUE, false = FALSE, "Inf" = Inf, "-Inf" = -Inf, x)
  }
  result$params <- model_params
  # result$params = unlist(model_params, recursive = FALSE)
  # result$params = lapply(model_params, function(x) { if(is.character(x)) { switch(x, true = TRUE, false = FALSE, "Inf" = Inf, "-Inf" = -Inf, x) }
  #                                                    else return(x) })
  result$params$nfolds <- model_params$n_folds
  result$params$n_folds <- NULL
  extra_json <- .fetchJSON(params$h2o, res$'_key')
  result$validationKey <- extra_json$deeplearning_model$"_validationKey"
  result$priorDistribution <- extra_json$deeplearning_model$"_priorClassDist"
  result$modelDistribution <- extra_json$deeplearning_model$"_modelClassDist"
  errs <- tail(res$errors, 1)[[1]]

  if(is.null(errs$valid_confusion_matrix))
    confusion <- errs$train_confusion_matrix
  else
    confusion <- errs$valid_confusion_matrix

  if(!is.null(confusion$cm)) {
    cm <- confusion$cm[-length(confusion$cm)]
    cm <- lapply(cm, function(x) { x[-length(x)] })
    # result$confusion = .build_cm(cm, confusion$actual_domain, confusion$predicted_domain)
    result$confusion <- .build_cm(cm, confusion$domain)
  }

  if (result$params$classification == 0) {
    result$train_sqr_error <- as.numeric(errs$train_mse)
    result$valid_sqr_error <- as.numeric(errs$valid_mse)
    result$train_class_error <- NULL
    result$valid_class_error <- NULL
  } else {
    result$train_sqr_error <- NULL
    result$valid_sqr_error <- NULL
    result$train_class_error <- as.numeric(errs$train_err)
    result$valid_class_error <- as.numeric(errs$valid_err)
  }

  if(!is.null(errs$validAUC)) {
    tmp <- .h2o.__getPerfResults(errs$validAUC)
    tmp$confusion <- NULL
    result <- c(result, tmp)
  }

  result$train_auc <- res$errors[[length(res$errors)]]$trainAUC$AUC

  if(!is.null(errs$valid_hitratio)) {
    max_k <- errs$valid_hitratio$max_k
    hit_ratios <- errs$valid_hitratio$hit_ratios
    result$hit_ratios <- data.frame(k = 1:max_k, hit_ratios = hit_ratios)
  }

  if(!is.null(errs$variable_importances)) {
    result$varimp <- errs$variable_importances$varimp
    names(result$varimp) <- errs$variable_importances$variables
    result$varimp <- sort(result$varimp, decreasing = TRUE)
  }
  return(result)
}