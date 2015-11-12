# ---------------------------- Deep Learning - Neural Network ---------------- #
#' Build a Deep Learning Neural Network
#'
#' Performs Deep Learning neural networks on an H2O Frame
#'
#' @param x A vector containing the \code{character} names of the predictors in the model.
#' @param y The name of the response variable in the model.
#' @param training_frame An H2O Frame object containing the variables in the model.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param overwrite_with_best_model Logical. If \code{TRUE}, overwrite the final model with the best model found during training. Defaults to \code{TRUE}.
#' @param validation_frame An H2O Frame object indicating the validation dataset used to construct the confusion matrix. Defaults to NULL.  If left as NULL, this defaults to the training data when \code{nfolds = 0}.
#' @param checkpoint "Model checkpoint (either key or H2ODeepLearningModel) to resume training with."
#' @param autoencoder Enable auto-encoder for model building.
#' @param use_all_factor_levels \code{Logical}. Use all factor levels of categorical variance.
#'        Otherwise the first factor level is omitted (without loss of accuracy). Useful for
#'        variable importances and auto-enabled for autoencoder.
#' @param activation A string indicating the activation function to use. Must be either "Tanh",
#'        "TanhWithDropout", "Rectifier", "RectifierWithDropout", "Maxout", or "MaxoutWithDropout"
#' @param hidden Hidden layer sizes (e.g. c(100,100))
#' @param epochs How many times the dataset should be iterated (streamed), can be fractional
#' @param train_samples_per_iteration Number of training samples (globally) per MapReduce iteration.
#'        Special values are: \bold{0} one epoch; \bold{-1} all available data (e.g., replicated
#'        training data); or \bold{-2} auto-tuning (default)
#' @param target_ratio_comm_to_comp Target ratio of communication overhead to computation.
#'        Only for multi-node operation and train_samples_per_iteration=-2 (auto-tuning).
#'        Higher values can lead to faster convergence.
#' @param seed Seed for random numbers (affects sampling) - Note: only reproducible when running
#'        single threaded
#' @param adaptive_rate \code{Logical}. Adaptive learning rate (ADAELTA)
#' @param rho Adaptive learning rate time decay factor (similarity to prior updates)
#' @param epsilon Adaptive learning rate parameter, similar to learn rate annealing during initial
#'        training phase. Typical values are between \code{1.0e-10} and \code{1.0e-4}
#' @param rate Learning rate (higher => less stable, lower => slower convergence)
#' @param rate_annealing Learning rate annealing: \eqn{(rate)/(1 + rate_annealing*samples)}
#' @param rate_decay Learning rate decay factor between layers (N-th layer: \eqn{rate*\alpha^(N-1)})
#' @param momentum_start Initial momentum at the beginning of training (try 0.5)
#' @param momentum_ramp Number of training samples for which momentum increases
#' @param momentum_stable Final momentum after the amp is over (try 0.99)
#' @param nesterov_accelerated_gradient \code{Logical}. Use Nesterov accelerated gradient
#'        (recommended)
#' @param input_dropout_ratio A fraction of the features for each training row to be omitted from
#'        training in order to improve generalization (dimension sampling).
#' @param hidden_dropout_ratios Input layer dropout ratio (can improve generalization) specify one
#'        value per hidden layer, defaults to 0.5
#' @param l1 L1 regularization (can add stability and improve generalization, causes many weights to
#'        become 0)
#' @param l2 L2 regularization (can add stability and improve generalization, causes many weights to
#'        be small)
#' @param max_w2 Constraint for squared sum of incoming weights per unit (e.g. Rectifier)
#' @param initial_weight_distribution Can be "Uniform", "UniformAdaptive", or "Normal"
#' @param initial_weight_scale Uniform: -value ... value, Normal: stddev
#' @param loss Loss function: "Automatic", "CrossEntropy" (for classification only), "Quadratic", "Absolute"
#'        (experimental) or "Huber" (experimental)
#' @param distribution A \code{character} string. The distribution function of the response.
#'        Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma", "tweedie",
#'        "laplace", "huber" or "gaussian"
#' @param tweedie_power Tweedie power (only for Tweedie distribution, must be between 1 and 2)
#' @param score_interval Shortest time interval (in secs) between model scoring
#' @param score_training_samples Number of training set samples for scoring (0 for all)
#' @param score_validation_samples Number of validation set samples for scoring (0 for all)
#' @param score_duty_cycle Maximum duty cycle fraction for scoring (lower: more training, higher:
#'        more scoring)
#' @param classification_stop Stopping criterion for classification error fraction on training data
#'        (-1 to disable)
#' @param regression_stop Stopping criterion for regression error (MSE) on training data (-1 to
#'        disable)
#' @param stopping_rounds Early stopping based on convergence of stopping_metric.
#'        Stop if simple moving average of length k of the stopping_metric does not improve
#'        (by stopping_tolerance) for k=stopping_rounds scoring events.
#'        Can only trigger after at least 2k scoring events. Use 0 to disable.
#' @param stopping_metric Metric to use for convergence checking, only for _stopping_rounds > 0
#'        Can be one of "AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification".
#' @param stopping_tolerance Relative tolerance for metric-based stopping criterion (if relative
#'        improvement is not at least this much, stop)
#' @param quiet_mode Enable quiet mode for less output to standard output
#' @param max_confusion_matrix_size Max. size (number of classes) for confusion matrices to be shown
#' @param max_hit_ratio_k Max number (top K) of predictions to use for hit ratio computation(for
#'        multi-class only, 0 to disable)
#' @param balance_classes Balance training data class counts via over/under-sampling (for imbalanced
#'        data)
#' @param class_sampling_factors Desired over/under-sampling ratios per class (in lexicographic
#'        order). If not specified, sampling factors will be automatically computed to obtain class
#'        balance during training. Requires balance_classes.
#' @param max_after_balance_size Maximum relative size of the training data after balancing class
#'        counts (can be less than 1.0)
#' @param score_validation_sampling Method used to sample validation dataset for scoring
#' @param diagnostics Enable diagnostics for hidden layers
#' @param variable_importances Compute variable importances for input features (Gedeon method) - can
#'        be slow for large networks)
#' @param fast_mode Enable fast mode (minor approximations in back-propagation)
#' @param ignore_const_cols Ignore constant columns (no information can be gained anyway)
#' @param force_load_balance Force extra load balancing to increase training speed for small
#'        datasets (to keep all cores busy)
#' @param replicate_training_data Replicate the entire training dataset onto every node for faster
#'        training
#' @param single_node_mode Run on a single node for fine-tuning of model parameters
#' @param shuffle_training_data Enable shuffling of training data (recommended if training data is
#'        replicated and train_samples_per_iteration is close to \eqn{numRows*numNodes}
#' @param sparse Sparse data handling (more efficient for data with lots of 0 values)
#' @param col_major Use a column major weight matrix for input layer. Can speed up forward
#'        propagation, but might slow down backpropagation (Experimental)
#' @param average_activation Average activation for sparse auto-encoder (Experimental)
#' @param sparsity_beta Sparsity regularization (Experimental)
#' @param max_categorical_features Max. number of categorical features, enforced via hashing
#'        Experimental)
#' @param reproducible Force reproducibility on small data (requires setting the \code{seed} argument and this will be slow - only uses 1 thread)
#' @param export_weights_and_biases Whether to export Neural Network weights and biases to H2O
#'        Frames"
#' @param offset_column Specify the offset column.
#' @param weights_column Specify the weights column.
#' @param nfolds (Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty.
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not specified
#'        Must be "AUTO", "Random" or "Modulo"
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models
#' @param ... extra parameters to pass onto functions (not implemented)
#' @seealso \code{\link{predict.H2OModel}} for prediction.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' iris.hex <- as.h2o(iris)
#' iris.dl <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris.hex)
#'
#' # now make a prediction
#' predictions <- h2o.predict(iris.dl, iris.hex)
#' }
#'
#' @export
h2o.deeplearning <- function(x, y, training_frame,
                             model_id = "",
                             overwrite_with_best_model,
                             validation_frame = NULL,
                             checkpoint,
                             autoencoder = FALSE,
                             use_all_factor_levels = TRUE,
                             activation = c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"),
                             hidden= c(200, 200),
                             epochs = 10,
                             train_samples_per_iteration = -2,
                             target_ratio_comm_to_comp = 0.05,
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
                             loss = c("Automatic", "CrossEntropy", "Quadratic", "Absolute", "Huber"),
                             distribution = c("AUTO","gaussian", "bernoulli", "multinomial", "poisson", "gamma", "tweedie", "laplace", "huber"),
                             tweedie_power = 1.5,
                             score_interval = 5,
                             score_training_samples,
                             score_validation_samples,
                             score_duty_cycle,
                             classification_stop,
                             regression_stop,
                             stopping_rounds=5,
                             stopping_metric=c("AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification"),
                             stopping_tolerance=0,
                             quiet_mode,
                             max_confusion_matrix_size,
                             max_hit_ratio_k,
                             balance_classes = FALSE,
                             class_sampling_factors,
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
                             reproducible=FALSE,
                             export_weights_and_biases=FALSE,
                             offset_column = NULL,
                             weights_column = NULL,
                             nfolds = 0,
                             fold_column = NULL,
                             fold_assignment = c("AUTO","Random","Modulo"),
                             keep_cross_validation_predictions = FALSE)
{

  # Training_frame and validation_frame may be a key or an H2O Frame object
  if (!is.Frame(training_frame))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid Frame or key")
             })
  if (!is.null(validation_frame)) {
    if (!is.Frame(validation_frame))
        tryCatch(validation_frame <- h2o.getFrame(validation_frame),
                 error = function(err) {
                   stop("argument \"validation_frame\" must be a valid Frame or key")
                 })
  }
  # Parameter list to send to model builder
  parms <- list()
  parms$training_frame <- training_frame
  args <- .verify_dataxy(training_frame, x, y, autoencoder)
  if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
  if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$response_column <- args$y
  parms$ignored_columns <- args$x_ignore
  if(!missing(model_id))
    parms$model_id <- model_id
  if(!missing(overwrite_with_best_model))
    parms$overwrite_with_best_model <- overwrite_with_best_model
  if(!missing(nfolds))
    parms$nfolds <- nfolds
  if(!missing(validation_frame))
    parms$validation_frame <- validation_frame
  if(!missing(checkpoint))
    parms$checkpoint <- checkpoint
  if(!missing(autoencoder))
    parms$autoencoder <- autoencoder
  if(!missing(use_all_factor_levels))
    parms$use_all_factor_levels <- use_all_factor_levels
  if(!missing(activation))
    parms$activation <- activation
  if(!missing(hidden))
    parms$hidden <- hidden
  if(!missing(epochs))
    parms$epochs <- epochs
  if(!missing(train_samples_per_iteration))
    parms$train_samples_per_iteration <- train_samples_per_iteration
  if(!missing(target_ratio_comm_to_comp))
    parms$target_ratio_comm_to_comp <- target_ratio_comm_to_comp
  if(!missing(seed))
    parms$seed <- seed
  if(!missing(adaptive_rate))
    parms$adaptive_rate <- adaptive_rate
  if(!missing(rho))
    parms$rho <- rho
  if(!missing(epsilon))
    parms$epsilon <- epsilon
  if(!missing(rate))
    parms$rate <- rate
  if(!missing(rate_annealing))
    parms$rate_annealing <- rate_annealing
  if(!missing(rate_decay))
    parms$rate_decay <- rate_decay
  if(!missing(momentum_start))
    parms$momentum_start <- momentum_start
  if(!missing(momentum_ramp))
    parms$momentum_ramp <- momentum_ramp
  if(!missing(momentum_stable))
    parms$momentum_stable <- momentum_stable
  if(!missing(nesterov_accelerated_gradient))
    parms$nesterov_accelerated_gradient <- nesterov_accelerated_gradient
  if(!missing(input_dropout_ratio))
    parms$input_dropout_ratio <- input_dropout_ratio
  if(!missing(hidden_dropout_ratios))
    parms$hidden_dropout_ratios <- hidden_dropout_ratios
  if(!missing(l1))
    parms$l1 <- l1
  if(!missing(l2))
    parms$l2 <- l2
  if(!missing(max_w2))
    parms$max_w2 <- max_w2
  if(!missing(initial_weight_distribution))
    parms$initial_weight_distribution <- initial_weight_distribution
  if(!missing(initial_weight_scale))
    parms$initial_weight_scale <- initial_weight_scale
  if(!missing(loss)) {
    if(loss == "MeanSquare") {
      warning("Loss name 'MeanSquare' is deprecated; please use 'Quadratic' instead.")
      parms$loss <- "Quadratic"
    } else
      parms$loss <- loss
  }
  if (!missing(distribution))
    parms$distribution <- distribution
  if (!missing(tweedie_power))
    parms$tweedie_power <- tweedie_power
  if(!missing(score_interval))
    parms$score_interval <- score_interval
  if(!missing(score_training_samples))
    parms$score_training_samples <- score_training_samples
  if(!missing(score_validation_samples))
    parms$score_validation_samples <- score_validation_samples
  if(!missing(score_duty_cycle))
    parms$score_duty_cycle <- score_duty_cycle
  if(!missing(classification_stop))
    parms$classification_stop <- classification_stop
  if(!missing(regression_stop))
    parms$regression_stop <- regression_stop
  if(!missing(stopping_rounds)) parms$stopping_rounds <- stopping_rounds
  if(!missing(stopping_metric)) parms$stopping_metric <- stopping_metric
  if(!missing(stopping_tolerance)) parms$stopping_tolerance <- stopping_tolerance
  if(!missing(quiet_mode))
    parms$quiet_mode <- quiet_mode
  if(!missing(max_confusion_matrix_size))
    parms$max_confusion_matrix_size <- max_confusion_matrix_size
  if(!missing(max_hit_ratio_k))
    parms$max_hit_ratio_k <- max_hit_ratio_k
  if(!missing(balance_classes))
    parms$balance_classes <- balance_classes
  if(!missing(class_sampling_factors))
    parms$class_sampling_factors <- class_sampling_factors
  if(!missing(max_after_balance_size))
    parms$max_after_balance_size <- max_after_balance_size
  if(!missing(score_validation_sampling))
    parms$score_validation_sampling <- score_validation_sampling
  if(!missing(diagnostics))
    parms$diagnostics <- diagnostics
  if(!missing(variable_importances))
    parms$variable_importances <- variable_importances
  if(!missing(fast_mode))
    parms$fast_mode <- fast_mode
  if(!missing(ignore_const_cols))
    parms$ignore_const_cols <- ignore_const_cols
  if(!missing(force_load_balance))
    parms$force_load_balance <- force_load_balance
  if(!missing(replicate_training_data))
    parms$replicate_training_data <- replicate_training_data
  if(!missing(single_node_mode))
    parms$single_node_mode <- single_node_mode
  if(!missing(shuffle_training_data))
    parms$shuffle_training_data <- shuffle_training_data
  if(!missing(sparse))
    parms$sparse <- sparse
  if(!missing(col_major))
    parms$col_major <- col_major
  if(!missing(average_activation))
    parms$average_activation <- average_activation
  if(!missing(sparsity_beta))
    parms$sparsity_beta <- sparsity_beta
  if(!missing(max_categorical_features))
    parms$max_categorical_features <- max_categorical_features
  if(!missing(reproducible))
    parms$reproducible <- reproducible
  if(!missing(export_weights_and_biases))
    parms$export_weights_and_biases <- export_weights_and_biases
  if( !missing(offset_column) )             parms$offset_column          <- offset_column
  if( !missing(weights_column) )            parms$weights_column         <- weights_column
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  .h2o.modelJob('deeplearning', parms)
}

#' Anomaly Detection via H2O Deep Learning Model
#'
#' Detect anomalies in an H2O dataset using an H2O deep learning model with
#' auto-encoding.
#'
#' @param object An \linkS4class{H2OAutoEncoderModel} object that represents the
#'        model to be used for anomaly detection.
#' @param data An H2O Frame object.
#' @param per_feature Whether to return the per-feature squared reconstruction error
#' @return Returns an H2O Frame object containing the
#'         reconstruction MSE or the per-feature squared error.
#' @seealso \code{\link{h2o.deeplearning}} for making an H2OAutoEncoderModel.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex = h2o.importFile(path = prosPath)
#' prostate.dl = h2o.deeplearning(x = 3:9, training_frame = prostate.hex, autoencoder = TRUE,
#'                                hidden = c(10, 10), epochs = 5)
#' prostate.anon = h2o.anomaly(prostate.dl, prostate.hex)
#' head(prostate.anon)
#' prostate.anon.per.feature = h2o.anomaly(prostate.dl, prostate.hex, per_feature=TRUE)
#' head(prostate.anon.per.feature)
#' }
#' @export
h2o.anomaly <- function(object, data, per_feature=FALSE) {
  url <- paste0('Predictions/models/', object@model_id, '/frames/',h2o.getId(data))
  res <- .h2o.__remoteSend(url, method = "POST", reconstruction_error=TRUE, reconstruction_error_per_feature=per_feature)
  key <- res$model_metrics[[1L]]$predictions$frame_id$name
  h2o.getFrame(key)
}

#' Feature Generation via H2O Deep Learning Model
#'
#' Extract the non-linear feature from an H2O data set using an H2O deep learning
#' model.
#' @param object An \linkS4class{H2OModel} object that represents the deep
#' learning model to be used for feature extraction.
#' @param data An H2O Frame object.
#' @param layer Index of the hidden layer to extract.
#' @return Returns an H2O Frame object with as many features as the
#'         number of units in the hidden layer of the specified index.
#' @seealso \code{link{h2o.deeplearning}} for making deep learning models.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex = h2o.importFile(path = prosPath)
#' prostate.dl = h2o.deeplearning(x = 3:9, y = 2, training_frame = prostate.hex,
#'                                hidden = c(100, 200), epochs = 5)
#' prostate.deepfeatures_layer1 = h2o.deepfeatures(prostate.dl, prostate.hex, layer = 1)
#' prostate.deepfeatures_layer2 = h2o.deepfeatures(prostate.dl, prostate.hex, layer = 2)
#' head(prostate.deepfeatures_layer1)
#' head(prostate.deepfeatures_layer2)
#' }
#' @export
h2o.deepfeatures <- function(object, data, layer = 1) {
  index = layer - 1
  url <- paste0('Predictions/models/', object@model_id, '/frames/', h2o.getId(data))
  res <- .h2o.__remoteSend(url, method = "POST", deep_features_hidden_layer=index)
  key <- res$predictions$name

  h2o.getFrame(key)
}

