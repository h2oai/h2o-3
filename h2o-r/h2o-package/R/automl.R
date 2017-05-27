#' Automatic Machine Learning
#'
#' The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
#' The current version of AutoML trains and cross-validates a Random Forest, an Extremely-Randomized Forest,
#' a random grid of Gradient Boosting Machines (GBMs), a random grid of Deep Neural Nets,
#' and a Stacked Ensemble of all the models.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing, then all columns except y are used.
#' @param y The name or index of the response variable in the model. For classification, the y column must be a factor, otherwise regression will be performed. Indexes are 1-based in R.
#' @param training_frame Training data frame (or ID).
#' @param validation_frame Validation data frame (or ID); Optional.
#' @param leaderboard_frame Leaderboard data frame (or ID).  The Leaderboard will be scored using this data set. Optional.
#' @param build_control List of custom build parameters. Optional. 
#' @param max_runtime_secs Maximum allowed runtime in seconds for the entire model training process. Use 0 to disable. Defaults to 3600 secs (1 hour).
#' @param max_models Maximum number of models to build in the AutoML process (does not include Stacked Ensembles). Defaults to NULL.
#' @details AutoML finds the best model, given a training frame and response, and returns an H2OAutoML object,
#'          which contains a leaderboard of all the models that were trained in the process, ranked by a default model performance metric.  Note that
#'          Stacked Ensemble will be trained for regression and binary classification problems since multiclass stacking is not yet supported.
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
                       build_control = NULL,
                       max_runtime_secs = 3600,
                       max_models = NULL)
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

  # Training frame id
  training_frame_id <- h2o.getId(training_frame)

  # Validation frame must be a key or an H2OFrame object
  validation_frame_id <- NULL
  if (!is.null(validation_frame)) {
    validation_frame_id <- h2o.getId(validation_frame)
  }

  # Test frame must be a key or an H2OFrame object
  leaderboard_frame_id <- NULL
  if (!is.null(leaderboard_frame)) {
    leaderboard_frame_id <- h2o.getId(leaderboard_frame)
  }

  # Input/data parameters to send to the AutoML backend
  input_spec <- list()
  input_spec$response_column <- ifelse(is.numeric(y),names(training_frame[y]),y)
  input_spec$training_frame <- training_frame_id
  input_spec$validation_frame <- validation_frame_id
  input_spec$leaderboard_frame <- leaderboard_frame_id

  # If x is specified, set ignored_columns; otherwise do not send ignored_columns in the POST
  if (!missing(x)) {
    args <- .verify_dataxy(training_frame, x, y)
    ignored_columns <- setdiff(names(training_frame), c(args$x,args$y)) #Remove x and y to create ignored_columns
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

  # Create the parameter list to POST to the AutoMLBuilder 
  params <- list(input_spec = input_spec, build_control = build_control)

  # POST call to AutoMLBuilder
  res <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "POST", page = "AutoMLBuilder", autoML = TRUE, .params = params)
  .h2o.__waitOnJob(res$job$key$name)

  # GET AutoML job and leaderboard for project
  automl_job <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "GET", page = paste0("AutoML/", res$job$dest$name))
  project <- automl_job$project
  leaderboard <- as.data.frame(automl_job["leaderboard_table"]$leaderboard_table)
  row.names(leaderboard) <- seq(nrow(leaderboard))
  leader <- h2o.getModel(automl_job$leaderboard$models[[1]]$name)

  # Make AutoML object
  new("H2OAutoML",
      project_name = project,
      leader = leader,
      leaderboard = leaderboard
  )
}
