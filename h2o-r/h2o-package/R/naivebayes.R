#'
#' Naive Bayes Model in H2O
#'
#' Compute naive Bayes probabilities on an H2O dataset.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#' @param y The name or index of the response variable. If the data does not contain a header, this is the 
#'        column index number starting at 0, and increasing from left to right.
#' @param training_frame An \code{\linkS4class{H2OFrame}} object containing the variables in the model.
#' @param destination_key (Optional) The unique hex key assigned to the resulting model. Automatically generated 
#'        if none is provided.
#' @param laplace A positive number controlling Laplace smoothing. The default zero disables smoothing.
#' @param min_sdev The minimum standard deviation to use for observations without enough data. Must be
#'        at least 1e-10.
#' @details The naive Bayes classifier assumes independence between predictor variables conditional 
#'        on the response, and a Gaussian distribution of numeric predictors with mean and standard 
#'        deviation computed from the training dataset. When building a naive Bayes classifier, 
#'        every row in the training dataset that contains at least one NA will be skipped completely. 
#'        If the test dataset has missing values, then those predictors are omitted in the probability 
#'        calculation during prediction.
#' @return Returns an object of class \linkS4class{H2ONaiveBayesModel}.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' votesPath <- system.file("extdata", "housevotes.csv", package="h2o")
#' votes.hex <- h2o.uploadFile(localH2O, path = votesPath, header = TRUE)
#' h2o.naiveBayes(x = 2:17, y = 1, training_frame = votes.hex, laplace = 3)
h2o.naiveBayes <- function(x, y, training_frame, destination_key, 
                           laplace = 0, 
                           min_sdev = 1e-10) {
  # Required args: x, y, training_frame
  if( missing(x) ) stop("`x` is missing, with no default")
  if( missing(y) ) stop("`y` is missing, with no default")
  if( missing(training_frame) ) stop ("argument \"training_frame\" is missing, with no default")
  
  # Training_frame may be a key or an H2OFrame object
  if (!inherits(training_frame, "H2OFrame"))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2OFrame or key")
             })
  
  .naivebayes.map <- c("x" = "ignored_columns", "y" = "response_column")
  
  # Gather user input
  parms <- as.list(match.call()[-1L])
  args <- .verify_dataxy(training_frame, x, y)
  parms$x <- args$x_ignore
  parms$y <- args$y
  names(parms) <- lapply(names(parms), function(i) { if( i %in% names(.naivebayes.map) ) i <- .naivebayes.map[[i]]; i })
  
  if( !(missing(x)) ) parms[["ignored_columns"]] <- .verify_datacols(training_frame, x)$cols_ignore
  
  # Error check and build model
  .h2o.createModel(training_frame@conn, 'naivebayes', parms, parent.frame())
}