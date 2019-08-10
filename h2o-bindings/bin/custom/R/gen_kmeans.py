extensions = dict(
    required_params=dict(training_frame=None, x=None),
    validate_required_params="",
    set_required_params="""
if(!missing(x)){
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
  if(!missing(fold_column)){
    parms$ignored_columns <- setdiff(parms$ignored_columns, fold_column)
  }
}
""",
    set_params="""
# Check if user_points is an acceptable set of user-specified starting points
if( is.data.frame(user_points) || is.matrix(user_points) || is.list(user_points) || is.H2OFrame(user_points) ) {
  if ( length(init) > 1 || init == 'User') {
    parms[["init"]] <- "User"
  } else {
    warning(paste0("Parameter init must equal 'User' when user_points is set. Ignoring init = '", init, "'. Setting init = 'User'."))
  }
  parms[["init"]] <- "User"
  
  # Convert user-specified starting points to H2OFrame
  if( is.data.frame(user_points) || is.matrix(user_points) || is.list(user_points) ) {
    if( !is.data.frame(user_points) && !is.matrix(user_points) ) user_points <- t(as.data.frame(user_points))
    user_points <- as.h2o(user_points)
  }
  parms[["user_points"]] <- user_points
  
  # Set k
  if( !(missing(k)) && k!=as.integer(nrow(user_points)) ) {
    warning("Parameter k is not equal to the number of user-specified starting points. Ignoring k. Using specified starting points.")
  }
  parms[["k"]] <- as.numeric(nrow(user_points))
} else if ( is.character(init) ) { # Furthest, Random, PlusPlus{
  parms[["user_points"]] <- NULL
} else{
  stop ("argument init must be set to Furthest, Random, PlusPlus, or a valid set of user-defined starting points.")
}
""",
)


doc = dict(
    preamble="""
Performs k-means clustering on an H2O dataset
""",
    params=dict(
        x="""A vector containing the \code{character} names of the predictors in the model."""
    ),
    returns="""
an object of class \linkS4class{H2OClusteringModel}.
""",
    seealso="""
\code{\link{h2o.cluster_sizes}}, \code{\link{h2o.totss}}, \code{\link{h2o.num_iterations}}, \code{\link{h2o.betweenss}}, \code{\link{h2o.tot_withinss}}, \code{\link{h2o.withinss}}, \code{\link{h2o.centersSTD}}, \code{\link{h2o.centers}}
""",
    examples="""
library(h2o)
h2o.init()
prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
prostate <- h2o.uploadFile(path = prostate_path)
h2o.kmeans(training_frame = prostate, k = 10, x = c("AGE", "RACE", "VOL", "GLEASON"))
"""
)
