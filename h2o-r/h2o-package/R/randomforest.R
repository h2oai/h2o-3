#' Build a Big Data Random Forest Model
#'
#' Builds a Random Forest Model on an H2O Frame
#'
#' @param x A vector containing the names or indices of the predictor variables
#'        to use in building the GBM model.
#' @param y The name or index of the response variable. If the data does not
#'        contain a header, this is the column index number starting at 1, and
#'        increasing from left to right. (The response must be either an integer
#'        or a categorical variable).
#' @param training_frame An H2O Frame object containing the
#'        variables in the model.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param validation_frame An H2O Frame object containing the variables in the model.  Default is NULL.
#' @param checkpoint "Model checkpoint (either key or H2ODeepLearningModel) to resume training with."
#' @param mtries Number of variables randomly sampled as candidates at each split.
#'        If set to -1, defaults to sqrt{p} for classification, and p/3 for regression,
#'        where p is the number of predictors.
#' @param sample_rate Sample rate, from 0 to 1.0.
#' @param build_tree_one_node Run on one node only; no network overhead but
#'        fewer cpus used.  Suitable for small datasets.
#' @param ntrees A nonnegative integer that determines the number of trees to
#'        grow.
#' @param max_depth Maximum depth to grow the tree.
#' @param min_rows Minimum number of rows to assign to teminal nodes.
#' @param nbins For numerical columns (real/int), build a histogram of (at least) this many bins, then split at the best point.
#' @param nbins_top_level For numerical columns (real/int), build a histogram of (at most) this many bins at the root
#'        level, then decrease by factor of two per level.
#' @param nbins_cats For categorical columns (factors), build a histogram of this many bins, then split at the best point.
#'        Higher values can lead to more overfitting.
#' @param binomial_double_trees For binary classification: Build 2x as many trees (one per class) - can lead to higher accuracy.
#' @param balance_classes logical, indicates whether or not to balance training
#'        data class counts via over/under-sampling (for imbalanced data)
#' @param max_after_balance_size Maximum relative size of the training data after balancing class counts (can be less
#'        than 1.0). Ignored if balance_classes is FALSE, which is the default behavior.
#' @param seed Seed for random numbers (affects sampling) - Note: only
#'        reproducible when running single threaded
#' @param offset_column Specify the offset column.
#' @param weights_column Specify the weights column.
#' @param nfolds (Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty.
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not specified
#'        Must be "AUTO", "Random" or "Modulo"
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models
#' @param score_each_iteration Attempts to score each tree.
#' @param stopping_rounds Early stopping based on convergence of stopping_metric.
#'        Stop if simple moving average of length k of the stopping_metric does not improve
#'        (by stopping_tolerance) for k=stopping_rounds scoring events.
#'        Can only trigger after at least 2k scoring events. Use 0 to disable.
#' @param stopping_metric Metric to use for convergence checking, only for _stopping_rounds > 0
#'        Can be one of "AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification".
#' @param stopping_tolerance Relative tolerance for metric-based stopping criterion (if relative
#'        improvement is not at least this much, stop)
#' @param ... (Currently Unimplemented)
#' @return Creates a \linkS4class{H2OModel} object of the right type.
#' @seealso \code{\link{predict.H2OModel}} for prediction.
#' @export
h2o.randomForest <- function(x, y, training_frame,
                             model_id,
                             validation_frame = NULL,
                             checkpoint,
                             mtries = -1,
                             sample_rate = 0.632,
                             build_tree_one_node = FALSE,
                             ntrees = 50,
                             max_depth = 20,
                             min_rows = 1,
                             nbins = 20,
                             nbins_top_level,
                             nbins_cats = 1024,
                             binomial_double_trees = FALSE,
                             balance_classes = FALSE,
                             max_after_balance_size = 5,
                             seed,
                             offset_column = NULL,
                             weights_column = NULL,
                             nfolds = 0,
                             fold_column = NULL,
                             fold_assignment = c("AUTO","Random","Modulo"),
                             keep_cross_validation_predictions = FALSE,
                             score_each_iteration = FALSE,
                             stopping_rounds=0,
                             stopping_metric=c("AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification"),
                             stopping_tolerance=1e-3
                             )
{
  # Training_frame and validation_frame may be a key or a Frame object
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
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
  if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  if(!missing(model_id))
    parms$model_id <- model_id
  if(!missing(validation_frame))
    parms$validation_frame <- validation_frame
  if(!missing(checkpoint))
    parms$checkpoint <- checkpoint
  if(!missing(mtries))
    parms$mtries <- mtries
  if(!missing(sample_rate))
    parms$sample_rate <- sample_rate
  if(!missing(build_tree_one_node))
    parms$build_tree_one_node <- build_tree_one_node
  if(!missing(binomial_double_trees))
    parms$binomial_double_trees <- binomial_double_trees
  if(!missing(ntrees))
    parms$ntrees <- ntrees
  if(!missing(max_depth))
    parms$max_depth <- max_depth
  if(!missing(min_rows))
    parms$min_rows <- min_rows
  if(!missing(nbins))
    parms$nbins <- nbins
  if(!missing(nbins_top_level))
    parms$nbins_top_level <- nbins_top_level
  if(!missing(nbins_cats))
    parms$nbins_cats <- nbins_cats
  if(!missing(balance_classes))
    parms$balance_classes <- balance_classes
  if(!missing(max_after_balance_size))
    parms$max_after_balance_size <- max_after_balance_size
  if(!missing(seed))
    parms$seed <- seed
  if (!missing(nfolds))
    parms$nfolds <- nfolds
  if( !missing(offset_column) )             parms$offset_column          <- offset_column
  if( !missing(weights_column) )            parms$weights_column         <- weights_column
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  if (!missing(score_each_iteration))
    parms$score_each_iteration <- score_each_iteration
  if(!missing(stopping_rounds)) parms$stopping_rounds <- stopping_rounds
  if(!missing(stopping_metric)) parms$stopping_metric <- stopping_metric
  if(!missing(stopping_tolerance)) parms$stopping_tolerance <- stopping_tolerance

  .h2o.modelJob('drf', parms)
}
