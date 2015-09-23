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
#' @param training_frame An Frame object containing the variables in the model.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param laplace A positive number controlling Laplace smoothing. The default zero disables smoothing.
#' @param threshold The minimum standard deviation to use for observations without enough data. Must be
#'        at least 1e-10.
#' @param eps A threshold cutoff to deal with numeric instability, must be positive.
#' @param compute_metrics A logical value indicating whether model metrics should be computed. Set to
#'        FALSE to reduce the runtime of the algorithm.
#' @details The naive Bayes classifier assumes independence between predictor variables conditional
#'        on the response, and a Gaussian distribution of numeric predictors with mean and standard
#'        deviation computed from the training dataset. When building a naive Bayes classifier,
#'        every row in the training dataset that contains at least one NA will be skipped completely.
#'        If the test dataset has missing values, then those predictors are omitted in the probability
#'        calculation during prediction.
#' @return Returns an object of class \linkS4class{H2OBinomialModel} if the response has two categorical levels, 
#'         and \linkS4class{H2OMultinomialModel} otherwise.
#' @examples
#' \dontrun{
#'  h2o.init()
#'  votesPath <- system.file("extdata", "housevotes.csv", package="h2o")
#'  votes.hex <- h2o.uploadFile(path = votesPath, header = TRUE)
#'  h2o.naiveBayes(x = 2:17, y = 1, training_frame = votes.hex, laplace = 3)
#' }
#' @export
h2o.naiveBayes <- function(x, y, training_frame,
                           model_id,
                           laplace = 0,
                           threshold = 0.001,
                           eps = 0,
                           compute_metrics = TRUE)
{
  # Training_frame may be a key or an Frame object
  if (!is.Frame(training_frame))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid Frame or key")
             })

  .naivebayes.map <- c("x" = "ignored_columns", "y" = "response_column",
                       "threshold" = "min_sdev", "eps" = "eps_sdev")

  # Gather user input
  parms <- list()
  args <- .verify_dataxy(training_frame, x, y)
  parms$ignored_columns <- args$x_ignore
  parms$response_column <- args$y
  parms$training_frame <- training_frame
  if(!missing(model_id))
    parms$model_id <- model_id
  if(!missing(laplace))
    parms$laplace <- laplace
  # TODO: These params have different names than h2o, don't think this should be the case
  if(!missing(threshold))
    parms$min_sdev <- threshold
  if(!missing(eps))
    parms$eps_sdev <- eps
  if(!missing(compute_metrics))
    parms$compute_metrics <- compute_metrics

  # In R package, cutoff and threshold for probability and standard deviation are the same
  parms$min_prob <- threshold
  parms$eps_prob <- eps

  # Error check and build model
  .h2o.modelJob('naivebayes', parms, do_future=FALSE)
}
