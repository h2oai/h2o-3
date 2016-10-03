#'
#' KMeans Model in H2O
#'
#' Performs k-means clustering on an H2O dataset.
#'
#'
#' @param training_frame An H2OFrame object containing the
#'        variables in the model.
#' @param x (Optional) A vector containing the data columns on
#'        which k-means operates.
#' @param k The max. number of clusters. If estimate_k is disabled, the model will find k centroids, otherwise it will find up to k centroids.
#' @param estimate_k Whether to estimate the number of clusters (<=k) iteratively and deterministically.
#' @param model_id (Optional) The unique id assigned to the resulting model. If
#'        none is given, an id will automatically be generated.
#' @param ignore_const_cols A logical value indicating whether or not to ignore all the constant columns in the training frame.
#' @param max_iterations Maximum training iterations (if estimate_k is enabled, then this is for each inner Lloyds iteration).
#'        Must be between 0 and 1e6 inclusive.
#' @param standardize Logical, indicates whether the data should be
#'        standardized before running k-means.
#' @param init A character string that selects the initial set of k cluster
#'        centers. Possible values are "Random": for random initialization,
#'        "PlusPlus": for k-means plus initialization, or "Furthest": for
#'        initialization at the furthest point from each successive center.
#'        Additionally, the user may specify a the initial centers as a matrix,
#'        data.frame, H2OFrame, or list of vectors. For matrices,
#'        data.frames, and Frames, each row of the respective structure
#'        is an initial center. For lists of vectors, each vector is an
#'        initial center.
#' @param seed (Optional) Random seed used to initialize the cluster centroids.
#' @param nfolds (Optional) Number of folds for cross-validation.
#' @param fold_column (Optional) Column with cross-validation fold index assignment per observation
#' @param fold_assignment Cross-validation fold assignment scheme, if fold_column is not
#'        specified, must be "AUTO", "Random",  "Modulo", or "Stratified".  The Stratified option will 
#'        stratify the folds based on the response variable, for classification problems.
#' @param keep_cross_validation_predictions Whether to keep the predictions of the cross-validation models
#' @param keep_cross_validation_fold_assignment Whether to keep the cross-validation fold assignment.
#' @param categorical_encoding Encoding scheme for categorical features
#'        Can be one of "AUTO", "Enum", "OneHotInternal", "OneHotExplicit", "Binary", "Eigen". Default is "AUTO", which is "Enum".
#' @param max_runtime_secs Maximum allowed runtime in seconds for model training. Use 0 to disable.
#' @return Returns an object of class \linkS4class{H2OClusteringModel}.
#' @seealso \code{\link{h2o.cluster_sizes}}, \code{\link{h2o.totss}}, \code{\link{h2o.num_iterations}},
#'          \code{\link{h2o.betweenss}}, \code{\link{h2o.tot_withinss}}, \code{\link{h2o.withinss}},
#'          \code{\link{h2o.centersSTD}}, \code{\link{h2o.centers}}
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.kmeans(training_frame = prostate.hex, k = 10, x = c("AGE", "RACE", "VOL", "GLEASON"))
#' }
#' @export
h2o.kmeans <- function(training_frame, x,
                       k,
                       estimate_k = FALSE,
                       model_id,
                       ignore_const_cols = TRUE,
                       max_iterations = 10,
                       standardize = TRUE,
                       init = c("Furthest","Random", "PlusPlus"),
                       seed,
                       nfolds = 0,
                       fold_column = NULL,
                       fold_assignment = c("AUTO","Random","Modulo","Stratified"),
                       keep_cross_validation_predictions = FALSE,
                       keep_cross_validation_fold_assignment = FALSE,
                       categorical_encoding=c("AUTO", "Enum", "OneHotInternal", "OneHotExplicit", "Binary", "Eigen"),
                       max_runtime_secs=0)
{
  # Training_frame may be a key or an H2OFrame object
  if( !is.H2OFrame(training_frame) )
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid H2OFrame or key")
             })

  # Gather user input
  parms <- list()
  if( !(missing(x)) )
    parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
  if(!missing(k))
    parms$k <- k
  if(!missing(estimate_k))
    parms$estimate_k <- estimate_k
  parms$training_frame <- training_frame
  if(!missing(model_id))
    parms$model_id <- model_id
  if(!missing(ignore_const_cols))
    parms$ignore_const_cols <- ignore_const_cols
  if(!missing(max_iterations))
    parms$max_iterations <- max_iterations
  if(!missing(standardize))
    parms$standardize <- standardize
  if(!missing(init))
    parms$init <- init
  if(!missing(seed))
    parms$seed <- seed
  if (!missing(nfolds))
    parms$nfolds <- nfolds
  if( !missing(fold_column) )               parms$fold_column            <- fold_column
  if( !missing(fold_assignment) )           parms$fold_assignment        <- fold_assignment
  if( !missing(keep_cross_validation_predictions) )  parms$keep_cross_validation_predictions  <- keep_cross_validation_predictions
  if( !missing(keep_cross_validation_fold_assignment) )  parms$keep_cross_validation_fold_assignment  <- keep_cross_validation_fold_assignment
  if(!missing(categorical_encoding)) parms$categorical_encoding <- categorical_encoding
  if(!missing(max_runtime_secs)) parms$max_runtime_secs <- max_runtime_secs

  # Check if init is an acceptable set of user-specified starting points
  if( is.data.frame(init) || is.matrix(init) || is.list(init) || is.H2OFrame(init) ) {
    parms[["init"]] <- "User"
    # Convert user-specified starting points to H2OFrame
    if( is.data.frame(init) || is.matrix(init) || is.list(init) ) {
      if( !is.data.frame(init) && !is.matrix(init) ) init <- t(as.data.frame(init))
      init <- as.h2o(init)
    }
    parms[["user_points"]] <- init
    # Set k
    if( !(missing(k)) && k!=as.integer(nrow(init)) ) {
      warning("Parameter k is not equal to the number of user-specified starting points. Ignoring k. Using specified starting points.")
    }
    parms[["k"]] <- as.numeric(nrow(init))
  }
  else if ( is.character(init) ) { # Furthest, Random, PlusPlus
    parms[["user_points"]] <- NULL
  }
  else{
    stop ("argument init must be set to Furthest, Random, PlusPlus, or a valid set of user-defined starting points.")
  }

  # Error check and build model
  .h2o.modelJob('kmeans', parms)
}
