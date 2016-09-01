#' Gradient Boosted Machines
#'
#' Builds gradient boosted classification trees, and gradient boosted regression trees on a parsed data set.
#'
#' The default distribution function will guess the model type
#' based on the response column type. In order to run properly, the response column must be an numeric for "gaussian" or an
#' enum for "bernoulli" or "multinomial".
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the GBM model.
#'        If x is missing,then all columns except y are used.
#' @param y The name or index of the response variable. If the data does not contain a header, this is the column index
#'        number starting at 0, and increasing from left to right. (The response must be either an integer or a
#'        categorical variable).
#' @param training_frame An H2OFrame object containing the variables in the model.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param checkpoint "Model checkpoint (provide the model_id) to resume training with."
#' @param ignore_const_cols A logical value indicating whether or not to ignore all the constant columns in the training frame.
#' @param distribution A \code{character} string. The distribution function of the response.
#'        Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber" or "gaussian"
#' @param quantile_alpha Desired quantile for Quantile regression, must be between 0 and 1.  Default is 0.5.
#' @param huber_alpha Desired quantile for Huber/M-regression (threshold between quadratic and linear loss, must be between 0 and 1).  Default is 0.9.
#' @param tweedie_power Tweedie power for Tweedie regression, must be between 1 and 2.  Default is 1.5.
#' @param ntrees A nonnegative integer that determines the number of trees to grow.  Default is 50.
#' @param max_depth Maximum depth to grow the tree.  Default is 5.
#' @param min_rows Minimum number of rows to assign to teminal nodes.  Default is 10.
#' @param learn_rate Learning rate (from \code{0.0} to \code{1.0}).  Default is 0.1.
#' @param learn_rate_annealing Scale the learning rate by this factor after each tree (e.g., 0.99 or 0.999).  Default is 1.
#' @param sample_rate Row sample rate per tree (from \code{0.0} to \code{1.0}).  Default is 1.
#' @param sample_rate_per_class Row sample rate per tree per class (one per class, from \code{0.0} to \code{1.0}).
#' @param col_sample_rate Column sample rate per split (from \code{0.0} to \code{1.0}).  Default is 1.
#' @param col_sample_rate_change_per_level Relative change of the column sampling rate for every level (from 0.0 to 2.0).  Default is 1.
#' @param col_sample_rate_per_tree Column sample rate per tree (from \code{0.0} to \code{1.0}).  Default is 1.
#' @param nbins For numerical columns (real/int), build a histogram of (at least) this many bins, then split at the best point.  Default is 20.
#' @param nbins_top_level For numerical columns (real/int), build a histogram of (at most) this many bins at the root
#'        level, then decrease by factor of two per level.  Default is 1024.
#' @param nbins_cats For categorical columns (factors), build a histogram of this many bins, then split at the best point.
#'        Higher values can lead to more overfitting.  Default is 1024.
#' @param validation_frame An H2OFrame object indicating the validation dataset used to contruct the
#'        confusion matrix. Defaults to NULL.  If left as NULL, this defaults to the training data when \code{nfolds = 0}.
#' @param balance_classes logical, indicates whether or not to balance training data class
#'        counts via over/under-sampling (for imbalanced data).  Default is \code{FALSE}.
#' @param class_sampling_factors Desired over/under-sampling ratios per class (in lexicographic
#'        order). If not specified, sampling factors will be automatically computed to obtain class
#'        balance during training. Requires balance_classes.
#' @param max_after_balance_size Maximum relative size of the training data after balancing class counts (can be less
#'        than 1.0). Ignored if balance_classes is FALSE, which is the default behavior.  Default is 5.
#' @param seed Seed for random numbers (affects sampling).
#' @param build_tree_one_node Run on one node only; no network overhead but
#'        fewer cpus used.  Suitable for small datasets.  Default is \code{FALSE}.
#' @param nfolds (Optional) Number of folds for cross-validation.  Default is 0 (no cross-validation).
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation.  Defaults to NULL.
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not
#'        specified, must be "AUTO", "Random",  "Modulo", or "Stratified".  The Stratified option will 
#'        stratify the folds based on the response variable, for classification problems.
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models.  Default is \code{FALSE}.
#' @param keep_cross_validation_fold_assignment Whether to keep the cross-validation fold assignment.  Default is \code{FALSE}.
#' @param score_each_iteration Attempts to score each tree.  Default is \code{FALSE}.
#' @param score_tree_interval Score the model after every so many trees. Default is 0 (disabled).
#' @param stopping_rounds Early stopping based on convergence of stopping_metric.  Default is 0 (disabled).
#'        Stop if simple moving average of length k of the stopping_metric does not improve
#'        (by stopping_tolerance) for k=stopping_rounds scoring events.
#'        Can only trigger after at least 2k scoring events.
#' @param stopping_metric Metric to use for convergence checking, only for _stopping_rounds > 0
#'        Can be one of "AUTO", "deviance", "logloss", "MSE", "AUC", "misclassification", or "mean_per_class_error".
#' @param stopping_tolerance Relative tolerance for metric-based stopping criterion (if relative
#'        improvement is not at least this much, stop).  Default is 0.001.
#' @param max_runtime_secs Maximum allowed runtime in seconds for model training. Default is 0 (disabled).
#' @param offset_column Specify the offset column.  Defaults to \code{NULL}.
#' @param weights_column Specify the weights column.  Defaults to \code{NULL}.
#' @param min_split_improvement Minimum relative improvement in squared error reduction for a split to happen.  Default is 1e-5 and the value must be >=0.
#' @param histogram_type What type of histogram to use for finding optimal split points
#'        Can be one of "AUTO", "UniformAdaptive", "Random", "QuantilesGlobal" or "RoundRobin".
#' @param max_abs_leafnode_pred Maximum absolute value of a leaf node prediction.  Defaults to 1.79769313486e+308.
#' @param pred_noise_bandwidth Bandwidth (sigma) of gaussian multiplicative noise for tree contributions. Default is 0.
#' @seealso \code{\link{predict.H2OModel}} for prediction.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' # Run regression GBM on australia.hex data
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.uploadFile(path = ausPath)
#' independent <- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst",
#'                  "maxsoilmoist", "Max_czcs")
#' dependent <- "runoffnew"
#' h2o.gbm(y = dependent, x = independent, training_frame = australia.hex,
#'         ntrees = 3, max_depth = 3, min_rows = 2)
#' }
#' @export
h2o.gbm <- function(x, y, training_frame,
                    model_id,
                    checkpoint,
                    ignore_const_cols = TRUE,
                    distribution = c("AUTO","gaussian", "bernoulli", "multinomial", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber"),
                    quantile_alpha = 0.5,
                    tweedie_power = 1.5,
                    huber_alpha = 0.9,
                    ntrees = 50,
                    max_depth = 5,
                    min_rows = 10,
                    learn_rate = 0.1,
                    learn_rate_annealing = 1.0,
                    sample_rate = 1.0,
                    sample_rate_per_class,
                    col_sample_rate = 1.0,
                    col_sample_rate_change_per_level = 1.0,
                    col_sample_rate_per_tree = 1.0,
                    nbins = 20,
                    nbins_top_level = 1024,
                    nbins_cats = 1024,
                    validation_frame = NULL,
                    balance_classes = FALSE,
                    class_sampling_factors,
                    max_after_balance_size = 5,
                    seed,
                    build_tree_one_node = FALSE,
                    nfolds = 0,
                    fold_column = NULL,
                    fold_assignment = c("AUTO","Random","Modulo","Stratified"),
                    keep_cross_validation_predictions = FALSE,
                    keep_cross_validation_fold_assignment = FALSE,
                    score_each_iteration = FALSE,
                    score_tree_interval = 0,
                    stopping_rounds = 0,
                    stopping_metric = c("AUTO", "deviance", "logloss", "MSE", "AUC", "misclassification", "mean_per_class_error"),
                    stopping_tolerance = 1e-3,
                    max_runtime_secs = 0,
                    offset_column = NULL,
                    weights_column = NULL,
                    min_split_improvement = 1e-05,
                    histogram_type = c("AUTO","UniformAdaptive","Random","QuantilesGlobal","RoundRobin"),
                    max_abs_leafnode_pred,
                    pred_noise_bandwidth=0
                    )
{
   #If x is missing, then assume user wants to use all columns as features.
   if(missing(x)){
     if(is.numeric(y)){
       x <- setdiff(col(training_frame),y)
     }else{
       x <- setdiff(colnames(training_frame),y)
     }
   }
  # Required maps for different names params, including deprecated params
  .gbm.map <- c("x" = "ignored_columns",
                "y" = "response_column")

  # Training_frame may be a key or an H2OFrame object
  if (!is.H2OFrame(training_frame))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2OFrame or key")
             })
  if (!is.null(validation_frame)) {
    if (!is.H2OFrame(validation_frame))
        tryCatch(validation_frame <- h2o.getFrame(validation_frame),
                 error = function(err) {
                   stop("argument \"validation_frame\" must be a valid H2OFrame or key")
                 })
  }

  # Parameter list to send to model builder
  parms <- list()
  parms$training_frame <- training_frame
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
  if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  if (!missing(model_id))
    parms$model_id <- model_id
  if(!missing(checkpoint))
    parms$checkpoint <- checkpoint
  if(!missing(ignore_const_cols))
    parms$ignore_const_cols <- ignore_const_cols
  if (!missing(distribution))
    parms$distribution <- distribution
  if (!missing(quantile_alpha))
    parms$quantile_alpha <- quantile_alpha
  if (!missing(tweedie_power))
    parms$tweedie_power <- tweedie_power
  if (!missing(huber_alpha))
    parms$huber_alpha <- huber_alpha
  if (!missing(ntrees))
    parms$ntrees <- ntrees
  if (!missing(max_depth))
    parms$max_depth <- max_depth
  if (!missing(min_rows))
    parms$min_rows <- min_rows
  if (!missing(learn_rate))
    parms$learn_rate <- learn_rate
  if (!missing(learn_rate_annealing))
    parms$learn_rate_annealing <- learn_rate_annealing
  if (!missing(sample_rate))
    parms$sample_rate <- sample_rate
  if (!missing(sample_rate_per_class))
    parms$sample_rate_per_class <- sample_rate_per_class
  if (!missing(col_sample_rate))
    parms$col_sample_rate <- col_sample_rate
  if(!missing(col_sample_rate_change_per_level))
    parms$col_sample_rate_change_per_level <- col_sample_rate_change_per_level
  if (!missing(col_sample_rate_per_tree))
    parms$col_sample_rate_per_tree <- col_sample_rate_per_tree
  if (!missing(nbins))
    parms$nbins <- nbins
  if (!missing(nbins_top_level))
    parms$nbins_top_level <- nbins_top_level
  if(!missing(nbins_cats))
    parms$nbins_cats <- nbins_cats
  if (!missing(validation_frame))
    parms$validation_frame <- validation_frame
  if (!missing(balance_classes))
    parms$balance_classes <- balance_classes
  if(!missing(class_sampling_factors))
    parms$class_sampling_factors <- class_sampling_factors
  if (!missing(max_after_balance_size))
    parms$max_after_balance_size <- max_after_balance_size
  if (!missing(seed))
    parms$seed <- seed
  if(!missing(build_tree_one_node))
    parms$build_tree_one_node <- build_tree_one_node
  if (!missing(nfolds))
    parms$nfolds <- nfolds
  if (!missing(score_each_iteration)) parms$score_each_iteration <- score_each_iteration
  if (!missing(score_tree_interval)) parms$score_tree_interval <- score_tree_interval
  if( !missing(offset_column) )             parms$offset_column          <- offset_column
  if( !missing(weights_column) )            parms$weights_column         <- weights_column
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  if( !missing(keep_cross_validation_fold_assignment) )  parms$keep_cross_validation_fold_assignment  <- keep_cross_validation_fold_assignment
  if(!missing(stopping_rounds)) parms$stopping_rounds <- stopping_rounds
  if(!missing(stopping_metric)) parms$stopping_metric <- stopping_metric
  if(!missing(stopping_tolerance)) parms$stopping_tolerance <- stopping_tolerance
  if(!missing(max_runtime_secs)) parms$max_runtime_secs <- max_runtime_secs
  if(!missing(min_split_improvement)) parms$min_split_improvement <- min_split_improvement
  if(!missing(histogram_type)) parms$histogram_type <- histogram_type
  if(!missing(max_abs_leafnode_pred)) parms$max_abs_leafnode_pred <- max_abs_leafnode_pred
  if(!missing(pred_noise_bandwidth)) parms$pred_noise_bandwidth <- pred_noise_bandwidth

  .h2o.modelJob('gbm', parms)
}

