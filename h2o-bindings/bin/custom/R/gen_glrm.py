
extensions = dict(
    required_params=dict(training_frame=None, cols="NULL"),
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(cols))
  parms$ignored_columns <- .verify_datacols(training_frame, cols)$cols_ignore  
""",
    set_params="""
# Check if user_y is an acceptable set of user-specified starting points
if( is.data.frame(user_y) || is.matrix(user_y) || is.list(user_y) || is.H2OFrame(user_y) ) {
  # Convert user-specified starting points to H2OFrame
  if( is.data.frame(user_y) || is.matrix(user_y) || is.list(user_y) ) {
    if( !is.data.frame(user_y) && !is.matrix(user_y) ) user_y <- t(as.data.frame(user_y))
    user_y <- as.h2o(user_y)
  }
  parms[["user_y"]] <- user_y

  # Set k
  if( !(missing(k)) && k!=as.integer(nrow(user_y)) ) {
    warning("Argument k is not equal to the number of rows in user-specified Y. Ignoring k. Using specified Y.")
  }
  parms[["k"]] <- as.numeric(nrow(user_y))
# } else if( is.null(user_y) ) {
#  if(!missing(init) && parms[["init"]] == "User")
#    warning("Initializing Y to a standard Gaussian random matrix.")
# } else
} else if( !is.null(user_y) )
  stop("Argument user_y must either be null or a valid user-defined starting Y matrix.")

# Check if user_x is an acceptable set of user-specified starting points
if( is.data.frame(user_x) || is.matrix(user_x) || is.list(user_x) || is.H2OFrame(user_x) ) {
  # Convert user-specified starting points to H2OFrame
  if( is.data.frame(user_x) || is.matrix(user_x) || is.list(user_x) ) {
    if( !is.data.frame(user_x) && !is.matrix(user_x) ) user_x <- t(as.data.frame(user_x))
    user_x <- as.h2o(user_x)
  }
  parms[["user_x"]] <- user_x
# } else if( is.null(user_x) ) {
#  if(!missing(init) && parms[["init"]] == "User")
#    warning("Initializing X to a standard Gaussian random matrix.")
# } else
} else if( !is.null(user_x) )
  stop("Argument user_x must either be null or a valid user-defined starting X matrix.")
""",
    module="""
#' Reconstruct Training Data via H2O GLRM Model
#'
#' Reconstruct the training data and impute missing values from the H2O GLRM model
#' by computing the matrix product of X and Y, and transforming back to the original
#' feature space by minimizing each column's loss function.
#'
#' @param object An \linkS4class{H2ODimReductionModel} object that represents the
#'        model to be used for reconstruction.
#' @param data An H2OFrame object representing the training data for the H2O GLRM model.
#'        Used to set the domain of each column in the reconstructed frame.
#' @param reverse_transform (Optional) A logical value indicating whether to reverse the
#'        transformation from model-building by re-scaling columns and adding back the
#'        offset to each column of the reconstructed frame.
#' @return Returns an H2OFrame object containing the approximate reconstruction of the
#'         training data;
#' @seealso \code{\link{h2o.glrm}} for making an H2ODimReductionModel.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' iris_glrm <- h2o.glrm(training_frame = iris_hf, k = 4, transform = "STANDARDIZE",
#'                       loss = "Quadratic", multi_loss = "Categorical", max_iterations = 1000)
#' iris_rec <- h2o.reconstruct(iris_glrm, iris_hf, reverse_transform = TRUE)
#' head(iris_rec)
#' }
#' @export
h2o.reconstruct <- function(object, data, reverse_transform=FALSE) {
  url <- paste0('Predictions/models/', object@model_id, '/frames/',h2o.getId(data))
  res <- .h2o.__remoteSend(url, method = "POST", reconstruct_train=TRUE, reverse_transform=reverse_transform)
  key <- res$model_metrics[[1L]]$predictions$frame_id$name
  h2o.getFrame(key)
}

#' Convert Archetypes to Features from H2O GLRM Model
#'
#' Project each archetype in an H2O GLRM model into the corresponding feature
#' space from the H2O training frame.
#'
#' @param object An \linkS4class{H2ODimReductionModel} object that represents the
#'        model containing archetypes to be projected.
#' @param data An H2OFrame object representing the training data for the H2O GLRM model.
#' @param reverse_transform (Optional) A logical value indicating whether to reverse the
#'        transformation from model-building by re-scaling columns and adding back the
#'        offset to each column of the projected archetypes.
#' @return Returns an H2OFrame object containing the projection of the archetypes
#'         down into the original feature space, where each row is one archetype.
#' @seealso \code{\link{h2o.glrm}} for making an H2ODimReductionModel.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' iris_glrm <- h2o.glrm(training_frame = iris_hf, k = 4, loss = "Quadratic",
#'                       multi_loss = "Categorical", max_iterations = 1000)
#' iris_parch <- h2o.proj_archetypes(iris_glrm, iris_hf)
#' head(iris_parch)
#' }
#' @export
h2o.proj_archetypes <- function(object, data, reverse_transform=FALSE) {
  url <- paste0('Predictions/models/', object@model_id, '/frames/',h2o.getId(data))
  res <- .h2o.__remoteSend(url, method = "POST", project_archetypes=TRUE, reverse_transform=reverse_transform)
  key <- res$model_metrics[[1L]]$predictions$frame_id$name
  h2o.getFrame(key)
}
""",
)

doc = dict(
    preamble="""
Generalized low rank decomposition of an H2O data frame

Builds a generalized low rank decomposition of an H2O data frame
""",
    params=dict(
        cols="(Optional) A vector containing the data columns on which k-means operates."
    ),
    returns="""
an object of class \linkS4class{H2ODimReductionModel}.
""",
    seealso="""
\code{\link{h2o.kmeans}, \link{h2o.svd}}, \code{\link{h2o.prcomp}}
""",
    references="""
M. Udell, C. Horn, R. Zadeh, S. Boyd (2014). {Generalized Low Rank Models}[http://arxiv.org/abs/1410.0342]. Unpublished manuscript, Stanford Electrical Engineering Department.
N. Halko, P.G. Martinsson, J.A. Tropp. {Finding structure with randomness: Probabilistic algorithms for constructing approximate matrix decompositions}[http://arxiv.org/abs/0909.4061]. SIAM Rev., Survey and Review section, Vol. 53, num. 2, pp. 217-288, June 2011.
""",
    examples="""
library(h2o)
h2o.init()
australia_path <- system.file("extdata", "australia.csv", package = "h2o")
australia <- h2o.uploadFile(path = australia_path)
h2o.glrm(training_frame = australia, k = 5, loss = "Quadratic", regularization_x = "L1",
         gamma_x = 0.5, gamma_y = 0, max_iterations = 1000)
"""
)
