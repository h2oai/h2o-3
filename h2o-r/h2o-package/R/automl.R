#' Automatic Machine Learning
#'
#' The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
#' The current version of AutoML trains and cross-validates a Random Forest, an Extremely-Randomized Forest,
#' a random grid of Gradient Boosting Machines (GBMs), a random grid of Deep Neural Nets, and then trains a
#' Stacked Ensemble using all of the models.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing, then all columns except y are used.
#' @param y The name or index of the response variable in the model. For classification, the y column must be a factor, otherwise regression will be performed. Indexes are 1-based in R.
#' @param training_frame Training data frame (or ID).
#' @param validation_frame Validation data frame (or ID); Optional.
#' @param leaderboard_frame Leaderboard data frame (or ID).  The Leaderboard will be scored using this data set. Optional.
#' @param fold_column Column with cross-validation fold index assignment per observation; used to override the default, randomized, 5-fold cross-validation scheme for individual models in the AutoML run.
#' @param weights_column Column with observation weights. Giving some observation a weight of zero is equivalent to excluding it from 
#'        the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice. Negative weights are not allowed.
#' @param max_runtime_secs Maximum allowed runtime in seconds for the entire model training process. Use 0 to disable. Defaults to 3600 secs (1 hour).
#' @param max_models Maximum number of models to build in the AutoML process (does not include Stacked Ensembles). Defaults to NULL.
#' @param stopping_metric Metric to use for early stopping (AUTO is logloss for classification, deviance for regression).  
#'        Must be one of "AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "lift_top_group", "misclassification", "mean_per_class_error". Defaults to AUTO.
#' @param stopping_tolerance Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much). This value defaults to 0.001 if the 
#'        dataset is at least 1 million rows; otherwise it defaults to a bigger value determined by the size of the dataset and the non-NA-rate.  In that case, the value is computed 
#'        as 1/sqrt(nrows * non-NA-rate).
#' @param stopping_rounds Integer. Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric 
#'        does not improve for k (stopping_rounds) scoring events. Defaults to 3 and must be an non-zero integer.  Use 0 to disable early stopping.
#' @param seed Integer. Set a seed for reproducibility. AutoML can only guarantee reproducibility if max_models or early stopping is used 
#'        because max_runtime_secs is resource limited, meaning that if the resources are not the same between runs, AutoML may be able to train more models on one run vs another.
#' @param project_name Character string to identify an AutoML project.  Defaults to NULL, which means a project name will be auto-generated based on the training frame ID.
#' @details AutoML finds the best model, given a training frame and response, and returns an H2OAutoML object,
#'          which contains a leaderboard of all the models that were trained in the process, ranked by a default model performance metric.  Note that a
#'          Stacked Ensemble will be trained for regression and binary classification problems only since multiclass stacking is not yet supported.
#' @return An \linkS4class{H2OAutoML} object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' votes_path <- system.file("extdata", "housevotes.csv", package="h2o")
#' votes_hf <- h2o.uploadFile(path = votes_path, header = TRUE)
#' aml <- h2o.automl(y = "Class", training_frame = votes_hf, max_runtime_secs = 30)
#' }
#' @export
h2o.automl <- function(x, y, training_frame,
                       validation_frame = NULL,
                       leaderboard_frame = NULL,
                       fold_column = NULL,
                       weights_column = NULL,
                       max_runtime_secs = 3600,
                       max_models = NULL,
                       stopping_metric = c("AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "lift_top_group", "misclassification", "mean_per_class_error"),
                       stopping_tolerance = NULL,
                       stopping_rounds = 3,
                       seed = NULL,
                       project_name = NULL)
{

  tryCatch({
    .h2o.__remoteSend(h2oRestApiVersion = 3, method="GET", page = "Metadata/schemas/AutoMLV99")
  },
  error = function(cond){
    message("
         *********************************************************************\n
         * Please verify that your H2O jar has the proper AutoML extensions. *\n
         *********************************************************************\n
         \nVerbose Error Message:")
    message(cond)
  })

  # Required args: training_frame & response column (y)
  if (missing(training_frame)) stop("argument 'training_frame' is missing")
  if (missing(y)) stop("The response column (y) is not set; please set it to the name of the column that you are trying to predict in your data.")

  # Training frame must be a key or an H2OFrame object
  if (!is.H2OFrame(training_frame)) {
    tryCatch(training_frame <- h2o.getFrame(training_frame), 
             error = function(err) {
               stop("argument 'training_frame' must be a valid H2OFrame or key")
             }) 
  }
  training_frame_id <- h2o.getId(training_frame)

  # Validation frame must be a key or an H2OFrame object
  validation_frame_id <- NULL
  if (!is.null(validation_frame)) {
    if (!is.H2OFrame(validation_frame)) {
      tryCatch(validation_frame <- h2o.getFrame(validation_frame), 
               error = function(err) {
                 stop("argument 'validation_frame' must be a valid H2OFrame or key")
               }) 
    }
    validation_frame_id <- h2o.getId(validation_frame)
  }

  # Leaderboard/test frame must be a key or an H2OFrame object
  leaderboard_frame_id <- NULL
  if (!is.null(leaderboard_frame)) {
    if (!is.H2OFrame(leaderboard_frame)) {
      tryCatch(leaderboard_frame <- h2o.getFrame(leaderboard_frame), 
               error = function(err) {
                 stop("argument 'leaderboard_frame' must be a valid H2OFrame or key")
               }) 
    }
    leaderboard_frame_id <- h2o.getId(leaderboard_frame)
  }

  # Input/data parameters to send to the AutoML backend
  input_spec <- list()
  input_spec$response_column <- ifelse(is.numeric(y),names(training_frame[y]),y)
  input_spec$training_frame <- training_frame_id
  input_spec$validation_frame <- validation_frame_id
  input_spec$leaderboard_frame <- leaderboard_frame_id
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
  build_control <- list(stopping_criteria = list(max_runtime_secs = max_runtime_secs))
  if (!is.null(max_models)) {
    build_control$stopping_criteria$max_models <- max_models
  }
  build_control$stopping_criteria$stopping_metric <- match.arg(stopping_metric)
  if (!is.null(stopping_tolerance)) {
    build_control$stopping_criteria$stopping_tolerance <- stopping_tolerance
  }
  build_control$stopping_criteria$stopping_rounds <- stopping_rounds
  if (!is.null(seed)) {
    build_control$stopping_criteria$seed <- seed
  }

  # If project_name is NULL, auto-gen based on training_frame ID
  if (is.null(project_name)) {
    build_control$project_name <- paste0("automl_", training_frame_id)
  } else {
    build_control$project_name <- project_name
  }
  
  # Create the parameter list to POST to the AutoMLBuilder 
  params <- list(input_spec = input_spec, build_control = build_control)

  # POST call to AutoMLBuilder
  res <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "POST", page = "AutoMLBuilder", autoML = TRUE, .params = params)
  .h2o.__waitOnJob(res$job$key$name)

  # GET AutoML job and leaderboard for project
  automl_job <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "GET", page = paste0("AutoML/", res$job$dest$name))
  #project <- automl_job$project  # This is not functional right now, we can get project_name from user input instead
  leaderboard <- as.data.frame(automl_job["leaderboard_table"]$leaderboard_table)
  row.names(leaderboard) <- seq(nrow(leaderboard))
  leaderboard <- as.h2o(leaderboard)
  leaderboard[,2:length(leaderboard)] <- as.numeric(leaderboard[,2:length(leaderboard)])
  leader <- h2o.getModel(automl_job$leaderboard$models[[1]]$name)

  # Make AutoML object
  new("H2OAutoML",
      project_name = build_control$project_name,
      leader = leader,
      leaderboard = leaderboard
  )
}

#' Predict on an AutoML object
#'
#' Obtains predictions from an AutoML object.
#'
#' This method generated predictions on the leader model from an AutoML run.
#' The order of the rows in the results is the same as the order in which the
#' data was loaded, even if some rows fail (for example, due to missing
#' values or unseen factor levels).
#'
#' @param object a fitted \linkS4class{H2OAutoML} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame object with probabilites and
#'         default predictions.
#' @export
predict.H2OAutoML <- function(object, newdata, ...) {
  if (missing(newdata)) {
    stop("predictions with a missing `newdata` argument is not implemented yet")
  }
  
  model <- object@leader
  
  # Send keys to create predictions
  url <- paste0('Predictions/models/', model@model_id, '/frames/',  h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method = "POST", h2oRestApiVersion = 4)
  job_key <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

