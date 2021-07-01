#' Admissible Machine Learning: Infogram & admissible feature detection.
#'
#' The infogram is an information-theoretic graphical tool which allows the user to quickly spot the "core" decision-making variables. 
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing, then all columns except y are used.
#' @param y The name or index of the response variable in the model. For classification, the y column must be a
#'        factor, otherwise regression will be performed. Indexes are 1-based in R.
#' @param training_frame Training frame (H2OFrame or ID).
#' @param validation_frame Validation frame (H2OFrame or ID); Optional.  
#' @param nfolds Number of folds for k-fold cross-validation. Defaults to 5. Use 0 to disable cross-validation; this will also disable Stacked Ensemble (thus decreasing the overall model performance).
#' @param protected_columns Columns that are protected or "sensitive" and that should not be allowed to drive the prediction of the response (e.g. race, gender, age).
#' @param fold_column Column with cross-validation fold index assignment per observation; used to override the default, randomized, 5-fold cross-validation scheme for individual models in the AutoML run.
#' @param weights_column Column with observation weights. Giving some observation a weight of zero is equivalent to excluding it from
#'        the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice. Negative weights are not allowed.
#' @param algorithm Type of algorithm to use to estimate the conditional mutual information. Options include 'AUTO' (GBM with default parameters),
#'        'deeplearning' (Deep Learning with default
#'        parameters), 'drf' (Random Forest with default parameters), 'gbm' (GBM with default parameters), 'glm' (GLM
#'        with default parameters), 'naivebayes' (NaiveBayes with default parameters), or 'xgboost' (if available,
#'        XGBoost with default parameters). Must be one of: "AUTO", "deeplearning", "drf", "gbm", "glm", "naivebayes",
#'        "xgboost". Defaults to AUTO.
#' @param algorithm_params Parameters to pass to the algorithm.
#' @param top_n_features Number of top features, ranked by variable importance, to evaluate the admissibility of.  Defaults to 50.  
#' @param thresholds Numeric vector of thresholds for admissibility on the (x,y) axes of the infogram.  Defaults to c(0.1, 0.1).  
#' @param destination_frame (Optional) The unique hex key assigned to the admissibility index frame (infogram data). If none is given, a key will automatically be generated based on the training data ID.
#' @param seed Integer. Set a seed for reproducibility.  Defaults to NULL.
#' @param ... Additional (experimental) arguments to be passed through to the plot; Optional.        
#' @details Admissible ML finds the admissible (core or fair) feattures, which can be used to train
#'         a parsimonious and more interpretable and compact model.  It returns an H2OInfogram object,
#'          which contains a list of admissible columns as well as admissibility scores and the infogram plot.
#' @return An \linkS4class{H2OInfogram} object.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(path = prostate_path, header = TRUE)
#' y <- "CAPSULE"
#' prostate[,y] <- as.factor(prostate[,y])  #convert to factor for classification
#' ig <- h2o.infogram(y = y, training_frame = prostate)
#' plot(ig)
#' # TO DO: add next steps
#' }
#' @export
h2o.infogram <- function(x, y, training_frame,
                         validation_frame = NULL,
                         nfolds = 0,  #do we want this?
                         protected_columns = NULL,
                         fold_column = NULL,
                         weights_column = NULL,
                         algorithm = c("AUTO", "deeplearning", "drf", "gbm", "glm", "naivebayes","xgboost"),
                         top_n_features = 50,
                         thresholds = c(0.1, 0.1),
                         seed = -1,
                         destination_frame = "",                         
                         ...)
{
  dots <- list(...)
  # algo_parameters <- NULL  
  # for (arg in names(dots)) {
  #   if (arg == 'algo_parameters') {
  #     algo_parameters <- dots$algo_parameters  
  #   } else {
  #     stop(paste("unused argument", arg, "=", dots[[arg]]))
  #   }
  # }

  tryCatch({
    .h2o.__remoteSend(h2oRestApiVersion = 99, method="GET", page = "Metadata/schemas/InfogramV99")
    #.h2o.__remoteSend(h2oRestApiVersion = 99, method="GET", page = "Metadata/schemas/AdmissibleMLV99")
  },
  error = function(cond){
    message("
         ***************************************************************************\n
         * Please verify that your H2O jar has the proper AdmissibleML extensions. *\n
         ***************************************************************************\n
         \nVerbose Error Message:")
    message(cond)
  })

  # Required args: training_frame & response column (y)
  if (missing(y)) stop("The response column (y) is not set; please set it to the name of the column that you are trying to predict in your data.")
  training_frame <- .validate.H2OFrame(training_frame, required=TRUE)

  # ensure all passed frames are a H2OFrame or a valid key
  validation_frame <- .validate.H2OFrame(validation_frame)

  training_frame_id <- h2o.getId(training_frame)
  validation_frame_id <- if (is.null(validation_frame)) NULL else h2o.getId(validation_frame)

  # Input/data parameters to send to the Infogram backend
  input_spec <- list()
  input_spec$response_column <- ifelse(is.numeric(y),names(training_frame[y]),y)
  input_spec$training_frame <- training_frame_id
  input_spec$validation_frame <- validation_frame_id
  if (!is.null(protected_columns)) {
    input_spec$protected_columns <- protected_columns
  }
  if (!is.null(fold_column)) {
    input_spec$fold_column <- fold_column
  }
  if (!is.null(weights_column)) {
    input_spec$weights_column <- weights_column
  }

  # If x is specified, set ignored_columns; otherwise do not send ignored_columns in the POST
  if (!missing(x)) {
    args <- .verify_dataxy(training_frame, x, y)
    # Create keep_columns to track which columns to keep (vs ignore)
    keep_columns <- c(args$x, args$y)
    # If fold_column or weights_column is specified, add them to the keep_columns list
    # otherwise H2O won't be able to find it in the training frame and will give an error
    if (!is.null(fold_column)) {
      keep_columns <- c(keep_columns, fold_column)
    }
    if (!is.null(weights_column)) {
      keep_columns <- c(keep_columns, weights_column)
    }
    ignored_columns <- setdiff(names(training_frame), keep_columns)
    if (length(ignored_columns) == 1) {
      input_spec$ignored_columns <- list(ignored_columns)
    } else if (length(ignored_columns) > 1) {
      input_spec$ignored_columns <- ignored_columns
    } # else: length(ignored_columns) == 0; don't send ignored_columns
  }


  # Update build_control list with top level build control args
  build_control <- list()

  # Update build_control with nfolds
  if (nfolds < 0) {
    stop("nfolds cannot be negative. Use nfolds >=2 if you want cross-valiated metrics and Stacked Ensembles or use nfolds = 0 to disable.")
  }
  if (nfolds == 1) {
    stop("nfolds = 1 is an invalid value. Use nfolds >=2 if you want cross-valiated metrics and Stacked Ensembles or use nfolds = 0 to disable.")
  }
  build_control$nfolds <- nfolds


  # Create the parameter list to POST to the IngogramBuilder
  # TO DO: this is from AutoML -- input_spec and build_control, may need to update for infogram
  params <- list(input_spec = input_spec, build_control = build_control)
  

  # POST call to AutoMLBuilder (executes the AutoML job)
  res <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "POST", page = "InfogramBuilder", .params = params)
  
  
  # poll_state <- list()
  # poll_updates <- function(job) {
  #   poll_state <<- do.call(.automl.poll_updates, list(job, verbosity=verbosity, state=poll_state))
  # }
  # .h2o.__waitOnJob(res$job$key$name, pollUpdates=poll_updates)
  # .automl.poll_updates(h2o.get_job(res$job$key$name), verbosity, poll_state) # ensure the last update is retrieved

  # TO DO: process all other input vars
  
  # GET Infogram frame
  #ig <- h2o.getFrame(id = "TODO")
  # TO DO:
  # Create H2OInfogram object and populate it with ig frame and other data
  #attr(ig, "id") <- res$job$dest$name
  return(ig)
}
