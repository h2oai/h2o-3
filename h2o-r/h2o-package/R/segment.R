#
# H2O Segmented-Data Bulk Model Training
#
# Provides a set of functions to train a group of models on different
# segments (subpopulations) of the training set. 

#--------------------------------------------
# Segmented-data bulk model training function
#--------------------------------------------

#'
#' Start Segmented-Data bulk Model Training for a given algorithm and parameters.
#'
#' @param algorithm  Name of algorithm to use in training segment models (gbm, randomForest, kmeans, glm, deeplearning, naivebayes, psvm,
#'        xgboost, pca, svd, targetencoder, aggregator, word2vec, coxph, isolationforest, kmeans, stackedensemble, glrm, gam, anovaglm, modelselection).
#' @param segment_columns A list of columns to segment-by. H2O will group the training (and validation) dataset by the segment-by columns
#'        and train a separate model for each segment (group of rows).
#' @param segment_models_id Identifier for the returned collection of Segment Models. If not specified it will be automatically generated.
#' @param parallelism Level of parallelism of bulk model building, it is the maximum number of models each H2O node will be building in parallel, defaults to 1.
#' @param ...  Use to pass along training_frame parameter, x, y, and all non-default parameter values to the algorithm 
          # (i.e., balance_classes, ntrees, alpha).
#'        Look at the specific algorithm - h2o.gbm, h2o.glm, h2o.kmeans, h2o.deepLearning - for available parameters.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' models <- h2o.train_segments(algorithm = "gbm", 
#'                              segment_columns = "Species",
#'                              x = c(1:3), y = 4, 
#'                              training_frame = iris_hf,
#'                              ntrees = 5, 
#'                              max_depth = 4)
#' as.data.frame(models)
#' }
#' @export
h2o.train_segments <- function(algorithm,
                               segment_columns,
                               segment_models_id,
                               parallelism = 1,
                               ...)
{
    train_segments_fun_name <- sprintf(".h2o.train_segments_%s", tolower(algorithm))
  if (!exists(train_segments_fun_name)) {
    stop(sprintf("Algorithm %s is not recognized, please check the spelling. For the name to be valid, a function h2o.%s needs to exist as well).", algorithm, algorithm))
  }
    
  params <- list(...)
  if (!missing(segment_columns))
    params$segment_columns <- segment_columns
  if (!missing(segment_models_id))
    params$segment_models_id <- segment_models_id
  params$parallelism <- parallelism

  return(do.call(train_segments_fun_name, args = params))
}

#' @rdname H2OSegmentModels-class
#' @param object an \code{H2OModel} object.
#' @export
setMethod("show", "H2OSegmentModels", 
          function(object) {
            cat("Segment Models ID:", object@segment_models_id, "\n")
            cat("Individual Segment Models:\n")
            df <- as.data.frame(object)
            print(df)
            invisible(object)
        })

