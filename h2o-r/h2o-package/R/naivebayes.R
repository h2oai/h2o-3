#'
#' Naive Bayes Model in H2O
#'
#' Compute naive Bayes probabilities on an H2O dataset.
#'
#' The naive Bayes classifier assumes independence between predictor variables conditional
#' on the response, and a Gaussian distribution of numeric predictors with mean and standard
#' deviation computed from the training dataset. When building a naive Bayes classifier,
#' every row in the training dataset that contains at least one NA will be skipped completely.
#' If the test dataset has missing values, then those predictors are omitted in the probability
#' calculation during prediction.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#' @param y The name or index of the response variable. If the data does not contain a header, this is the
#'        column index number starting at 0, and increasing from left to right. The response must be a categorical
#'        variable with at least two levels.
#' @param training_frame An H2OFrame object containing the variables in the model.
#' @param validation_frame An H2OFrame object containing the variables in the model.  Defaults to NULL.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param ignore_const_cols A logical value indicating whether or not to ignore all the constant columns in the training frame.
#' @param laplace A positive number controlling Laplace smoothing. The default zero disables smoothing.
#' @param threshold The minimum standard deviation to use for observations without enough data. Must be
#'        at least 1e-10.
#' @param eps A threshold cutoff to deal with numeric instability, must be positive.
#' @param nfolds (Optional) Number of folds for cross-validation.
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not
#'        specified, must be "AUTO", "Random",  "Modulo", or "Stratified".  The Stratified option will 
#'        stratify the folds based on the response variable, for classification problems.
#' @param seed Seed for random numbers (affects sampling).
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models
#' @param keep_cross_validation_fold_assignment Whether to keep the cross-validation fold assignment.
#' @param compute_metrics A logical value indicating whether model metrics should be computed. Set to
#'        FALSE to reduce the runtime of the algorithm.
#' @param max_runtime_secs Maximum allowed runtime in seconds for model training. Use 0 to disable.
#' @details The naive Bayes classifier assumes independence between predictor variables conditional
#'        on the response, and a Gaussian distribution of numeric predictors with mean and standard
#'        deviation computed from the training dataset. When building a naive Bayes classifier,
#'        every row in the training dataset that contains at least one NA will be skipped completely.
#'        If the test dataset has missing values, then those predictors are omitted in the probability
#'        calculation during prediction.
#' @return Returns an object of class \linkS4class{H2OBinomialModel} if the response has two categorical levels,
#'         and \linkS4class{H2OMultinomialModel} otherwise.
#' @examples
#' \donttest{
#'  h2o.init()
#'  votesPath <- system.file("extdata", "housevotes.csv", package="h2o")
#'  votes.hex <- h2o.uploadFile(path = votesPath, header = TRUE)
#'  h2o.naiveBayes(x = 2:17, y = 1, training_frame = votes.hex, laplace = 3)
#' }
#' @export
h2o.naiveBayes <- function(x, y, training_frame,
                           validation_frame = NULL,
                           model_id,
                           ignore_const_cols = TRUE,
                           laplace = 0,
                           threshold = 0.001,
                           eps = 0,
                           nfolds = 0,
                           fold_column = NULL,
                           fold_assignment = c("AUTO","Random","Modulo","Stratified"),
                           seed,
                           keep_cross_validation_predictions = FALSE,
                           keep_cross_validation_fold_assignment = FALSE,
                           compute_metrics = TRUE,
                           max_runtime_secs=0)
{
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

  .naivebayes.map <- c("x" = "ignored_columns", "y" = "response_column",
                       "threshold" = "min_sdev", "eps" = "eps_sdev")

  # Gather user input
  parms <- list()
  args <- .verify_dataxy(training_frame, x, y)
  if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  parms$training_frame <- training_frame
  parms$ignore_const_cols <- ignore_const_cols
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  if( !missing(keep_cross_validation_fold_assignment) )  parms$keep_cross_validation_fold_assignment  <- keep_cross_validation_fold_assignment
  if(!missing(model_id)) parms$model_id <- model_id
  if (!missing(validation_frame)) parms$validation_frame <- validation_frame
  if (!missing(nfolds)) parms$nfolds <- nfolds
  if (!missing(seed)) parms$seed <- seed
  if(!missing(laplace))
    parms$laplace <- laplace
  # TODO: These params have different names than h2o, don't think this should be the case
  if(!missing(threshold))
    parms$min_sdev <- threshold
  if(!missing(eps))
    parms$eps_sdev <- eps
  if(!missing(compute_metrics))
    parms$compute_metrics <- compute_metrics
  if(!missing(max_runtime_secs)) parms$max_runtime_secs <- max_runtime_secs

  # In R package, cutoff and threshold for probability and standard deviation are the same
  parms$min_prob <- threshold
  parms$eps_prob <- eps

  # Error check and build model
  .h2o.modelJob('naivebayes', parms)
}
