#' Automatic Machine Learning
#'
#' The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
#' The current version of AutoML trains and cross-validates a Random Forest, an Extremely-Randomized Forest, 
#' a random grid of Gradient Boosting Machines (GBMs), a random grid of Deep Neural Nets, 
#' and a Stacked Ensemble of all the models.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing,then all columns except y are used.
#' @param y The name of the response variable in the model. If the data does not contain a header, this is the column index
#'        number starting at 0, and increasing from left to right. (The response must be either an integer or a
#'        categorical variable).
#' @param training_frame Id of the training data frame (Not required, to allow initial validation of model parameters).
#' @param validation_frame Id of the validation data frame (Not required).
#' @param test_frame Id of the test data frame.  The Leaderboard will be scored using this test data.
#' @param build_control List of custom build parameters.
#' @param max_runtime_secs Maximum allowed runtime in seconds for the entire model training process.  Use 0 to disable. Defaults to 600 secs (10 min).
#' @details AutoML finds the best model, given a training frame and response, and returns an H2OAutoML object,
#'          which contains a leaderboard of all the models that were trained in the process, ranked by a default model performance metric.  Note that 
#'          Stacked Ensemble will be trained for regression and binary classification problems since multiclass stacking is not yet supported.
#' @return Creates a \linkS4class{H2OAutoML} object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' votes_path <- system.file("extdata", "housevotes.csv", package="h2o")
#' votes_hf <- h2o.uploadFile(path = votes_path, header = TRUE)
#' aml <- h2o.automl(y = "Class", training_frame = votes_hf, max_runtime_secs = 30)
#' aml@leaderboard
#' }
#' @export
h2o.automl <- function(x, y, training_frame,
                       validation_frame = NULL,
                       test_frame = NULL,
                       build_control = NULL,
                       max_runtime_secs = 600)
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
  
  # TO DO: Add more parameter checking 
  # maybe something like this as well: args <- .verify_dataxy(training_frame, x, y)

  
  # Required args: training_frame
  if (missing(training_frame)) stop("argument 'training_frame' is missing")
  
  # Training frame must be a key or an H2OFrame object
  if (!is.null(training_frame)) {
    training_frame <- h2o.getId(training_frame)
  }
  
  # If x is missing, then assume user wants to use all columns as features
  # TO DO: test that x / ignored_columns is working as expected
  if (missing(x)) {
    #if (is.numeric(y)) {
    #  #x <- setdiff(col(training_frame),y)
    #  ignored_columns <- setdiff(1:ncol(trainin_frame), y)
    #} else{
    #  #x <- setdiff(colnames(training_frame),y)
    #  ignored_columns <- setdiff(names(training_frame), y)
    #}
    ignored_columns <- NULL
  } else {
    if (is.numeric(x)) {
      ignored_columns <- setdiff(1:ncol(training_frame), x)
      
    } else {
      ignored_columns <- setdiff(names(training_frame), x)
    }
  }
  # Maybe just subset the training columns by x and set ignored_columns to NULL
  #training_frame <- training_frame[,x]  
  
  # Validation frame must be a key or an H2OFrame object
  if (!is.null(validation_frame)) {
    validation_frame <- h2o.getId(validation_frame)
  }
  
  #Test frame must be a key or an H2OFrame object
  if (!is.null(test_frame)) {
    test_frame <- h2o.getId(test_frame)
  }
  
  # Parameter list to send to the AutoML backend
  parms <- list()
  input_spec <- list()
  input_spec$response_column <- y  #y is called response_column in the REST API
  input_spec$training_frame <- training_frame
  input_spec$validation_frame <- validation_frame
  input_spec$test_frame <- test_frame
  input_spec$ignored_columns <- ignored_columns  #expose and use x to create this
  
  # Update build_control list with top level args
  build_control$stopping_criteria$max_runtime_secs <- max_runtime_secs
  # TO DO: Add other top level args and set them here
  
  parms = list(input_spec = input_spec, build_control = build_control)
  
  #POST call to AutoMLBuilder
  res <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "POST", page = "AutoMLBuilder", autoML = TRUE, .params = parms)
  .h2o.__waitOnJob(res$job$key$name)
  
  #GET AutoML job and leaderboard for project
  automl_job <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "GET",page = paste0("AutoML/",res$job$dest$name))
  project <- automl_job$project
  leaderboard <- as.data.frame(automl_job["leaderboard_table"]$leaderboard_table)
  user_feedback <- automl_job["user_feedback_table"]
  leader <- automl_job$leaderboard$models[[1]]$name
  
  #Make AutoML object
  new("H2OAutoML",
      project_name = project,
      user_feedback = user_feedback,
      leader = leader,
      leaderboard = leaderboard
  )
}
