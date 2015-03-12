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
#' @param average_activation Average activation for sparse auto-encoder (Experimental)
#' @param sparsity_beta Sparsity regularization (Experimental)
#' @param max_categorical_features Max. number of categorical features, enforced via hashing (Experimental)
#' @param reproducible Force reproducibility on small data (will be slow - only uses 1 thread)
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

h2o.deeplearning <- function(x, y, training_frame, destination_key = "",
                             override_with_best_model,
                             n_folds = 0,
                             validation_frame,
                             ...,
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
                             col_major,
                             average_activation,
                             sparsity_beta,
                             max_categorical_features,
                             reproducible
)
{
  dots <- list(...)
  
  for(type in names(dots))
    if (is.environment(dots[[type]]))
    {
    dots$envir <- type
    type <- NULL
    } else {
      stop(paste0("\n  unused argument (", type, " = ", dots[[type]], ")"))
    }
  if (is.null(dots$envir)) 
    dots$envir <- parent.frame()
  
  if( missing(x) ) stop("`x` is missing, with no default")
  if( missing(y) ) stop("`y` is missing, with no default")
  if( missing(training_frame) ) stop("`training_frame` is missing, with no default")
  
  # Training_frame may be a key or an H2OFrame object
  if (!inherits(training_frame, "H2OFrame"))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2OFrame or key")
             })

  colargs <- .verify_dataxy(training_frame, x, y, autoencoder)

  .deeplearning.map <-  c("x" = "ignored_columns",
                          "y" = "response_column")

  parms <- as.list(match.call(expand.dots = FALSE)[-1L])
  parms$... <- NULL
  
  parms$y <- colargs$y
  parms$x <- colargs$x_ignore
  names(parms) <- lapply(names(parms), function(i) { if( i %in% names(.deeplearning.map) ) i <- .deeplearning.map[[i]]; i })
  parms$max_after_balance_size <- 1 #hard-code max_after_balance_size until Inf fixed

  .h2o.createModel(training_frame@conn, 'deeplearning', parms, dots$envir)
}

# Function call for R sided cross validation of h2o objects
h2o.deeplearning.cv <- function(x, y, training_frame, nfolds = 2,
                                key = "",
                                override_with_best_model,
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
                                col_major,
                                average_activation,
                                sparsity_beta,
                                max_categorical_features,
                                reproducible
                            )
{
  env <- parent.frame()
  parms <- lapply(as.list(match.call()[-1L]), eval, env)
  parms$nfolds <- NULL
  
  do.call("h2o.crossValidate", list(model.type = 'deeplearning', nfolds = nfolds, params = parms, envir = env))
}

h2o.anomaly <- function(object, data) {
  url <- paste0('Predictions.json/models/', object@key, '/frames/', data@key)
  res <- .h2o.__remoteSend(object@conn, url, method = "POST", reconstruction_error=TRUE)
  res <- res$model_metrics[[1L]]$predictions$key$name
  
  h2o.getFrame(res)
}

