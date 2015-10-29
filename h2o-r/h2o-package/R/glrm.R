#'
#' Generalized Low Rank Model
#'
#' Generalized low rank decomposition of a H2O dataset.
#'
#'
#' @param training_frame An H2O Frame object containing the
#'        variables in the model.
#' @param cols (Optional) A vector containing the data columns on
#'        which k-means operates.
#' @param k The rank of the resulting decomposition. This must be
#'        between 1 and the number of columns in the training frame, inclusive.
#' @param model_id (Optional) The unique id assigned to the resulting model.
#'        If none is given, an id will automatically be generated.
#' @param validation_frame An H2O Frame object containing the
#'        variables in the model.
#' @param loading_name (Optional) The unique name assigned to the loading matrix X
#'        in the XY decomposition. Automatically generated if none is provided.
#' @param ignore_const_cols (Optional) A logical value indicating whether to ignore
#'        constant columns in the training frame. A column is constant if all of its
#'        non-missing values are the same value.
#' @param transform A character string that indicates how the training data
#'        should be transformed before running PCA. Possible values are "NONE":
#'        for no transformation, "DEMEAN": for subtracting the mean of each
#'        column, "DESCALE": for dividing by the standard deviation of each
#'        column, "STANDARDIZE": for demeaning and descaling, and "NORMALIZE":
#'        for demeaning and dividing each column by its range (max - min).
#' @param loss A character string indicating the default loss function for numeric columns.
#'        Possible values are "Quadratic" (default), "L1", "Huber", "Poisson", "Hinge"
#'        and "Logistic".
#' @param multi_loss A character string indicating the default loss function for enum columns.
#'        Possible values are "Categorical" and "Ordinal".
#' @param loss_by_col A vector of strings indicating the loss function for specific
#'        columns by corresponding index in loss_by_col_idx. Will override loss for
#'        numeric columns and multi_loss for enum columns.
#' @param loss_by_col_idx A vector of column indices to which the corresponding loss
#'        functions in loss_by_col are assigned. Must be zero indexed.
#' @param regularization_x A character string indicating the regularization function for
#'        the X matrix. Possible values are "None" (default), "Quadratic", "L2", "L1",
#'        "NonNegative", "OneSparse", "UnitOneSparse", and "Simplex".
#' @param regularization_y A character string indicating the regularization function for
#'        the Y matrix. Possible values are "None" (default), "Quadratic", "L2", "L1",
#'        "NonNegative", "OneSparse", "UnitOneSparse", and "Simplex".
#' @param gamma_x The weight on the X matrix regularization term.
#' @param gamma_y The weight on the Y matrix regularization term.
#' @param max_iterations The maximum number of iterations to run the optimization loop.
#'        Each iteration consists of an update of the X matrix, followed by an update
#'        of the Y matrix.
#' @param init_step_size Initial step size. Divided by number of columns in the training
#'        frame when calculating the proximal gradient update. The algorithm begins at
#'        init_step_size and decreases the step size at each iteration until a
#'        termination condition is reached.
#' @param min_step_size Minimum step size upon which the algorithm is terminated.
#' @param init A character string indicating how to select the initial Y matrix.
#'        Possible values are "Random": for initialization to a random array from the
#'        standard normal distribution, "PlusPlus": for initialization using the clusters
#'        from k-means++ initialization, or "SVD": for initialization using the
#'        first k right singular vectors. Additionally, the user may specify the
#'        initial Y as a matrix, data.frame, Frame, or list of vectors.
#' @param svd_method (Optional) A character string that indicates how SVD should be 
#'        calculated during initialization. Possible values are "GramSVD": distributed 
#'        computation of the Gram matrix followed by a local SVD using the JAMA package, 
#'        "Power": computation of the SVD using the power iteration method, "Randomized": 
#'        (default) approximate SVD by projecting onto a random subspace (see references).
#' @param user_x (Optional) A matrix, data.frame, Frame, or list of vectors specifying the 
#'        initial X. Only used when init = "User". The number of columns must equal k.
#' @param user_y (Optional) A matrix, data.frame, Frame, or list of vectors specifying the 
#'        initial Y. Only used when init = "User". The number of rows must equal k.
#' @param expand_user_y A logical value indicating whether the categorical columns of user_y
#'        should be one-hot expanded. Only used when init = "User" and user_y is specified.
#' @param impute_original A logical value indicating whether to reconstruct the original training
#'        data by reversing the transformation during prediction. Model metrics are calculated
#'        with respect to the original data.
#' @param recover_svd A logical value indicating whether the singular values and eigenvectors
#'        should be recovered during post-processing of the generalized low rank decomposition.
#' @param seed (Optional) Random seed used to initialize the X and Y matrices.
#' @return Returns an object of class \linkS4class{H2ODimReductionModel}.
#' @seealso \code{\link{h2o.kmeans}, \link{h2o.svd}}, \code{\link{h2o.prcomp}}
#' @references M. Udell, C. Horn, R. Zadeh, S. Boyd (2014). {Generalized Low Rank Models}[http://arxiv.org/abs/1410.0342]. Unpublished manuscript, Stanford Electrical Engineering Department.
#'             N. Halko, P.G. Martinsson, J.A. Tropp. {Finding structure with randomness: Probabilistic algorithms for constructing approximate matrix decompositions}[http://arxiv.org/abs/0909.4061]. SIAM Rev., Survey and Review section, Vol. 53, num. 2, pp. 217-288, June 2011.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' ausPath <- system.file("extdata", "australia.csv", package="h2o")
#' australia.hex <- h2o.uploadFile(path = ausPath)
#' h2o.glrm(training_frame = australia.hex, k = 5, loss = "Quadratic", regularization_x = "L1",
#'          gamma_x = 0.5, gamma_y = 0, max_iterations = 1000)
#' }
#' @export
h2o.glrm <- function(training_frame, cols, k, model_id,
                     ## AUTOGENERATED PARAMETERS ##     # these defaults are not read by h2o
                     validation_frame,                  # h2o generates its own default parameters
                     loading_name,
                     ignore_const_cols,
                     transform = c("NONE", "DEMEAN", "DESCALE", "STANDARDIZE", "NORMALIZE"),
                     loss = c("Quadratic", "L1", "Huber", "Poisson", "Hinge", "Logistic"),
                     multi_loss = c("Categorical", "Ordinal"),
                     loss_by_col = NULL,
                     loss_by_col_idx = NULL,
                     regularization_x = c("None", "Quadratic", "L2", "L1", "NonNegative", "OneSparse", "UnitOneSparse", "Simplex"),
                     regularization_y = c("None", "Quadratic", "L2", "L1", "NonNegative", "OneSparse", "UnitOneSparse", "Simplex"),
                     gamma_x = 0,
                     gamma_y = 0,
                     max_iterations = 1000,
                     init_step_size = 1.0,
                     min_step_size = 0.001,
                     init = c("Random", "PlusPlus", "SVD"),
                     svd_method = c("GramSVD", "Power", "Randomized"),
                     user_y = NULL,
                     user_x = NULL,
                     expand_user_y = TRUE,
                     impute_original = FALSE,
                     recover_svd = FALSE,
                     seed)
{
  # Required args: training_frame
  if( missing(training_frame) ) stop("argument \"training_frame\" is missing, with no default")
  
  # Training_frame may be a key or an H2O Frame object
  if (!is.Frame(training_frame))
    tryCatch(training_frame <- h2o.getFrame(training_frame),
             error = function(err) {
               stop("argument \"training_frame\" must be a valid Frame or key")
             })

  # Gather user input
  parms <- list()
  parms$training_frame <- training_frame
  if(!missing(cols))
    parms$ignored_columns <- .verify_datacols(training_frame, cols)$cols_ignore
  if(!missing(k))
    parms$k <- as.numeric(k)
  if(!missing(model_id))
    parms$model_id <- model_id
  if(!missing(validation_frame))
    parms$validation_frame <- validation_frame
  if(!missing(loading_name))
    parms$loading_name <- loading_name
  if(!missing(ignore_const_cols))
    parms$ignore_const_cols <- ignore_const_cols
  if(!missing(transform))
    parms$transform <- transform
  if(!missing(loss))
    parms$loss <- loss
  if(!missing(multi_loss))
    parms$multi_loss <- multi_loss
  if(!(missing(loss_by_col) || is.null(loss_by_col)))
    parms$loss_by_col <- .collapse(loss_by_col)
  if(!(missing(loss_by_col_idx) || is.null(loss_by_col_idx)))
    parms$loss_by_col_idx <- .collapse(loss_by_col_idx)
  if(!missing(regularization_x))
    parms$regularization_x <- regularization_x
  if(!missing(regularization_y))
    parms$regularization_y <- regularization_y
  if(!missing(gamma_x))
    parms$gamma_x <- gamma_x
  if(!missing(gamma_y))
    parms$gamma_y <- gamma_y
  if(!missing(max_iterations))
    parms$max_iterations <- max_iterations
  if(!missing(init_step_size))
    parms$init_step_size <- init_step_size
  if(!missing(min_step_size))
    parms$min_step_size <- min_step_size
  if(!missing(init))
    parms$init <- init
  if(!missing(expand_user_y))
    parms$expand_user_y <- expand_user_y
  if(!missing(impute_original))
    parms$impute_original <- impute_original
  if(!missing(recover_svd))
    parms$recover_svd <- recover_svd
  if(!missing(seed))
    parms$seed <- seed
  
  # Check if user_y is an acceptable set of user-specified starting points
  if( is.data.frame(user_y) || is.matrix(user_y) || is.list(user_y) || is.Frame(user_y) ) {
    # Convert user-specified starting points to Frame
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
  if( is.data.frame(user_x) || is.matrix(user_x) || is.list(user_x) || is.Frame(user_x) ) {
    # Convert user-specified starting points to Frame
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
  
  # Error check and build model
  .h2o.modelJob('glrm', parms, h2oRestApiVersion=3)
}
