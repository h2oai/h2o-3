#' Gradient Boosted Machines
#'
#' Builds gradient boosted classification trees, and gradient boosted regression trees on a parsed data set.
#'
#' The default distribution function will guess the model type
#' based on the response column typerun properly the response column must be an numeric for "gaussian" or an
#' enum for "bernoulli" or "multinomial".
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the GBM model.
#' @param y The name or index of the response variable. If the data does not contain a header, this is the column index
#'        number starting at 0, and increasing from left to right. (The response must be either an integer or a
#'        categorical variable).
#' @param training_frame An \code{\linkS4class{H2OFrame}} object containing the variables in the model.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param checkpoint "Model checkpoint (either key or H2ODeepLearningModel) to resume training with."
#' @param distribution A \code{character} string. The distribution function of the response.
#'        Must be "AUTO", "bernoulli", "multinomial", "poisson", "gamma", "tweedie" or "gaussian"
#' @param tweedie_power Tweedie power (only for Tweedie distribution, must be between 1 and 2)
#' @param ntrees A nonnegative integer that determines the number of trees to grow.
#' @param max_depth Maximum depth to grow the tree.
#' @param min_rows Minimum number of rows to assign to teminal nodes.
#' @param learn_rate An \code{integer} from \code{0.0} to \code{1.0}
#' @param nbins For numerical columns (real/int), build a histogram of this many bins, then split at the best point
#' @param nbins_cats For categorical columns (enum), build a histogram of this many bins, then split at the best point. Higher values can lead to more overfitting.
#' @param validation_frame An \code{\link{H2OFrame}} object indicating the validation dataset used to contruct the
#'        confusion matrix. If left blank, this defaults to the training data when \code{nfolds = 0}
#' @param balance_classes logical, indicates whether or not to balance training data class
#'        counts via over/under-sampling (for imbalanced data)
#' @param max_after_balance_size Maximum relative size of the training data after balancing class counts (can be less
#'        than 1.0)
#' @param seed Seed for random numbers (affects sampling when balance_classes=T)
#' @param build_tree_one_node Run on one node only; no network overhead but
#'        fewer cpus used.  Suitable for small datasets.
#' @param nfolds (Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty.
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not specified
#'        Must be "AUTO", "Random" or "Modulo"
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models
#' @param score_each_iteration Attempts to score each tree.
#' @param offset_column Specify the offset column.
#' @param weights_column Specify the weights column.
#' @param ... extra arguments to pass on (currently no implemented)
#' @seealso \code{\link{predict.H2OModel}} for prediction.
#' @examples
#' library(h2o)
#' localH2O = h2o.init()
#'
#' # Run regression GBM on australia.hex data
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.uploadFile(localH2O, path = ausPath)
#' independent <- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst",
#'                  "maxsoilmoist", "Max_czcs")
#' dependent <- "runoffnew"
#' h2o.gbm(y = dependent, x = independent, training_frame = australia.hex,
#'         ntrees = 3, max_depth = 3, min_rows = 2)
#' @export
h2o.gbm <- function(x, y, training_frame,
                    model_id,
                    checkpoint,
                    distribution = c("AUTO","gaussian", "bernoulli", "multinomial", "poisson", "gamma", "tweedie"),
                    tweedie_power = 1.5,
                    ntrees = 50,
                    max_depth = 5,
                    min_rows = 10,
                    learn_rate = 0.1,
                    nbins = 20,
                    nbins_cats = 1024,
                    validation_frame = NULL,
                    balance_classes = FALSE,
                    max_after_balance_size = 1,
                    seed,
                    build_tree_one_node = FALSE,
                    nfolds = 0,
                    fold_column = NULL,
                    fold_assignment = c("AUTO","Random","Modulo"),
                    keep_cross_validation_predictions = FALSE,
                    score_each_iteration = FALSE,
                    offset_column = NULL,
                    weights_column = NULL,
                    ...)
{
  # Required maps for different names params, including deprecated params
  .gbm.map <- c("x" = "ignored_columns",
                "y" = "response_column")

  # Pass over ellipse parameters
  do_future <- FALSE
  if (length(list(...)) > 0) {
#    browser()
    dots <- list(...) #.model.ellipses( list(...))
    if( !is.null(dots$future) ) do_future <- TRUE
  }

  # Training_frame may be a key or an H2OFrame object
  if (!inherits(training_frame, "H2OFrame"))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2OFrame or key")
             })
  if (!is.null(validation_frame)) {
    if (!inherits(validation_frame, "H2OFrame"))
        tryCatch(validation_frame <- h2o.getFrame(validation_frame),
                 error = function(err) {
                   stop("argument \"validation_frame\" must be a valid H2OFrame or key")
                 })
  }

  # Parameter list to send to model builder
  parms <- list()
  parms$training_frame <- training_frame
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(offset_column) )  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
  if( !missing(weights_column) ) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
  if( !missing(fold_column) ) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  if (!missing(model_id))
    parms$model_id <- model_id
  if(!missing(checkpoint))
    parms$checkpoint <- checkpoint
  if (!missing(distribution))
    parms$distribution <- distribution
  if (!missing(tweedie_power))
    parms$tweedie_power <- tweedie_power
  if (!missing(ntrees))
    parms$ntrees <- ntrees
  if (!missing(max_depth))
    parms$max_depth <- max_depth
  if (!missing(min_rows))
    parms$min_rows <- min_rows
  if (!missing(learn_rate))
    parms$learn_rate <- learn_rate
  if (!missing(nbins))
    parms$nbins <- nbins
  if(!missing(nbins_cats))
    parms$nbins_cats <- nbins_cats
  if (!missing(validation_frame))
    parms$validation_frame <- validation_frame
  if (!missing(balance_classes))
    parms$balance_classes <- balance_classes
  if (!missing(max_after_balance_size))
    parms$max_after_balance_size <- max_after_balance_size
  if (!missing(seed))
    parms$seed <- seed
  if(!missing(build_tree_one_node))
    parms$build_tree_one_node <- build_tree_one_node
  if (!missing(nfolds))
    parms$nfolds <- nfolds
  if (!missing(score_each_iteration))
    parms$score_each_iteration <- score_each_iteration
  if( !missing(offset_column) )             parms$offset_column          <- offset_column
  if( !missing(weights_column) )            parms$weights_column         <- weights_column
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  if( do_future ) .h2o.startModelJob(training_frame@conn, 'gbm', parms)
  else            .h2o.createModel(training_frame@conn, 'gbm', parms)
}

