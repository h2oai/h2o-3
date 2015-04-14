#' Gradient Boosted Machines
#'
#' Builds gradient boosted classification trees, and gradient boosted regression trees on a parsed data set.
#'
#' The default loss function will guess the model type
#' based on the response column typerun properly the response column must be an numeric for "gaussian" or an
#' enum for "bernoulli" or "multinomial".
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the GBM model.
#' @param y The name or index of the response variable. If the data does not contain a header, this is the column index
#'        number starting at 0, and increasing from left to right. (The response must be either an integer or a
#'        categorical variable).
#' @param training_frame An \code{\linkS4class{H2OFrame}} object containing the variables in the model.
#' @param destination_key (Optional) The unique hex key assigned to the resulting model. If
#'        none is given, a key will automatically be generated.
#' @param loss A \code{character} string. The loss function to be implemented.
#'        Must be "AUTO", "bernoulli", "multinomial", or "gaussian"
#' @param ntrees A nonnegative integer that determines the number of trees to grow.
#' @param max_depth Maximum depth to grow the tree.
#' @param min_rows Minimum number of rows to assign to teminal nodes.
#' @param learn_rate An \code{interger} from \code{0.0} to \code{1.0}
#' @param nbins Number of bins to use in building histogram.
#' @param validation_frame An \code{\link{H2OFrame}} object indicating the validation dataset used to contruct the
#'        confusion matrix. If left blank, this defaults to the training data when \code{nfolds = 0}
#' @param balance_classes logical, indicates whether or not to balance training data class
#'        counts via over/under-sampling (for imbalanced data)
#' @param max_after_balance_size Maximum relative size of the training data after balancing class counts (can be less
#'        than 1.0)
#' @param seed Seed for random numbers (affects sampling) - Note: only reproducible when running single threaded
#' @param nfolds (Optional) Number of folds for cross-validation. If \code{nfolds >= 2}, then \code{validation} must remain empty. **Currently not supported**
#' @param score_each_iteration Attempts to score each tree.
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
                    destination_key,
                    loss = c("AUTO","gaussian", "bernoulli", "multinomial"),
                    ntrees = 50,
                    max_depth = 5,
                    min_rows = 10,
                    learn_rate = 0.1,
                    nbins = 20,
                    validation_frame = NULL,
                    balance_classes = FALSE,
                    max_after_balance_size = 1,
                    seed,
                    score_each_iteration,
                    ...)
{
  # Required maps for different names params, including deprecated params
  .gbm.map <- c("x" = "ignored_columns",
                "y" = "response_column")

  # Pass over ellipse parameters
  if(length(list(...)) > 0)
    dots <- .model.ellipses(list(...))

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
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  if (!missing(destination_key))
    parms$destination_key <- destination_key
  if (!missing(loss))
    parms$loss <- loss
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
  if (!missing(validation_frame))
    parms$validation_frame <- validation_frame
  if (!missing(balance_classes))
    parms$balance_classes <- balance_classes
  if (!missing(max_after_balance_size))
    parms$max_after_balance_size <- max_after_balance_size
  if (!missing(seed))
    parms$seed <- seed
  if (!missing(score_each_iteration))
    parms$score_each_iteration <- score_each_iteration

  .h2o.createModel(training_frame@conn, 'gbm', parms)
}

# Function call for R sided cross validation of h2o objects
#' @export
h2o.gbm.cv <- function(x, y, training_frame, nfolds = 2,
           #AUTOGENERATED params
           key,
           loss = c("bernoulli"),
           ntrees = 50,
           max_depth = 5,
           min_rows = 10,
           learn_rate = 0.1,
           nbins = 20,
           variable_importance = FALSE,
           balance_classes = FALSE,
           max_after_balance_size = 1,
           seed
           )
{
  env <- parent.frame()
  parms <- lapply(as.list(match.call()[-1L]), eval, env)
  parms$nfolds <- NULL

  do.call("h2o.crossValidate", list(model.type = 'gbm', nfolds = nfolds, params = parms))
}
