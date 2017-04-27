#' H2O Automatic Machine Learning
#'
#' @param response_column The name of the response variable in the model.If the data does not contain a header, this is the column index
#'        number starting at 0, and increasing from left to right. (The response must be either an integer or a
#'        categorical variable).
#' @param training_frame Id of the training data frame (Not required, to allow initial validation of model parameters).
#' @param validation_frame Id of the validation data frame (Not required).
#' @param test_frame Id of the test data frame.  The Leaderboard will be scored using this test data.
#' @param build_control TO DO.
#' @param ignored_columns TO DO.
#' @details The H2O AutoML function finds the best model, given a target metric and response, and returns an H2OAutoModel object,
#'          which contains a leaderboard of all the models that were trained in the process, ranked by a default model performance metric.
#' @return Creates a \linkS4class{H2OAutoModel} object.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' votes_path <- system.file("extdata", "housevotes.csv", package="h2o")
#' votes_hf <- h2o.uploadFile(path = votes_path, header = TRUE)
#' aml <- h2o.automl(response_column = "Class", training_frame = votes_hf)
#' }
#' @export
h2o.automl <- function(response_column = NULL,
                       training_frame = NULL,
                       validation_frame = NULL,
                       test_frame = NULL,
                       build_control = NULL,
                       ignored_columns = NULL)
{

    tryCatch({
        .h2o.__remoteSend(h2oRestApiVersion = 3, method="GET",page = "Metadata/schemas/AutoMLV99")
    },
    error = function(cond){
        message("
         *******************************************************************\n
         *Please verify that your H2O jar has the proper AutoML extensions.*\n
         *******************************************************************\n
         \nVerbose Error Message:")
        message(cond)
    })

    # Required args: training_frame
    if( missing(training_frame) ) stop("argument 'training_frame' is missing")

    # Training frame must be a key or an H2OFrame object
    if (!is.null(training_frame)) {
        training_frame <- h2o.getId(training_frame)
    }

    # Validation frame must be a key or an H2OFrame object
    if (!is.null(validation_frame)) {
        validation_frame <- h2o.getId(validation_frame)
    }

    #Test frame
    if (!is.null(test_frame)) {
        test_frame <- h2o.getId(test_frame)
    }

    #Parameter list to send to AutoH2O
    parms <- list()
    input_spec <- list()
    input_spec$response_column <- response_column
    input_spec$training_frame <- training_frame
    input_spec$validation_frame <- validation_frame
    input_spec$test_frame <- test_frame
    input_spec$ignored_columns <- ignored_columns

    parms = list(input_spec = input_spec,build_control = build_control)

    #POST call to AutoMLBuilder
    res <- .h2o.__remoteSend(h2oRestApiVersion = 99, method="POST",page = "AutoMLBuilder",autoML = TRUE,.params = parms)
    .h2o.__waitOnJob(res$job$key$name)

    #GET AutoML job and leaderboard for project
    automl_job <- .h2o.__remoteSend(h2oRestApiVersion = 99, method="GET",page = paste0("AutoML/",res$job$dest$name))
    project <- automl_job$project
    leaderboard <- as.data.frame(automl_job["leaderboard_table"]$leaderboard_table)
    user_feedback <- automl_job["user_feedback_table"]
    leader <- automl_job$leaderboard$models[[1]]$name

    #Make AutoML object
    new("H2OAutoML",
        project_name = project,
        user_feedback= user_feedback,
        leader = leader,
        leaderboard = leaderboard
    )
}
