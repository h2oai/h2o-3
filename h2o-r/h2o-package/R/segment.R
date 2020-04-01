#'
#' H2O Segmented-Data Bulk Model Training
#'
#' Provides a set of functions to train a group of models on different
#' segments (subpopulations) of the training set. 

#--------------------------------------------
# Segmented-data bulk model training function
#--------------------------------------------

#'
#' Launch grid search with given algorithm and parameters.
#'
#' @param algorithm  Name of algorithm to use in training segment models (gbm, randomForest, kmeans, glm, deeplearning, naivebayes, psvm,
#'        xgboost, pca, svd, targetencoder, aggregator, word2vec, coxph, isolationforest, kmeans, stackedensemble, glrm, gam).
#' @param segment_columns A list of columns to segment-by. H2O will group the training (and validation) dataset by the segment-by columns
#'        and train a separate model for each segment (group of rows).
#' @param segment_models_id Identifier for the returned collection of Segment Models. If not specified it will be automatically generated.
#' @param parallelism Level of parallelism of bulk model building, it is the maximum number of models each H2O node will be building in parallel, defaults to 1.
#' @param x (Optional) A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing, then all columns except y are used.
#' @param y The name or column index of the response variable in the data. The response must be either a numeric or a
#'        categorical/factor variable. If the response is numeric, then a regression model will be trained, otherwise it will train a classification model.
#' @param training_frame Id of the training data frame.
#' @param ...  Use to pass along non-default parameter values to the algorithm (i.e., balance_classes, ntrees, alpha).
#'        Look at the specific algorithm - h2o.gbm, h2o.glm, h2o.kmeans, h2o.deepLearning - for available parameters.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' models <- h2o.segment_train(algorithm = "gbm", 
#'                             segment_columns = "Species",
#'                             x = c(1:3), y = 4, 
#'                             training_frame = iris_hf,
#'                             ntrees = 100, 
#'                             max_depth = 4)
#' }
#' @export
h2o.segment_train <- function(algorithm,
                              segment_columns,
                              segment_models_id,
                              parallelism = 1,
                              x,
                              y,
                              training_frame,
                              ...)
{
  
  # Validate required training_frame first and other frame args: should be a valid key or an H2OFrame object
  training_frame <- .validate.H2OFrame(training_frame, required=TRUE)
  #validation_frame <- .validate.H2OFrame(validation_frame, required=FALSE)
  
  #Unsupervised algos to account for in grid (these algos do not need response)
  # TO DO: Check/update this list
  unsupervised_algos <- c("kmeans", "pca", "svd", "glrm")
  # Parameter list
  dots <- list(...)
  # Add x, y, and training_frame
  if(!(algorithm %in% c(unsupervised_algos, toupper(unsupervised_algos)))) {
    if(!missing(y)) {
      dots$y <- y
    } else {
      # deeplearning with autoencoder param set to T is also okay.  Check this case before whining
      if (!((algorithm %in% c("deeplearning") && dots$autoencoder==TRUE))) { # only complain if not DL autoencoder
        stop("Must specify response, y")
      }
    }
  }
  if(!missing(training_frame)) {
    dots$training_frame <- training_frame
  } else {
    stop("Must specify training frame, training_frame")
  }
  print(class(dots$training_frame))
  # If x is missing, then assume user wants to use all columns as features for supervised models only
  if(!(algorithm %in% c(unsupervised_algos, toupper(unsupervised_algos)))) {
    if (missing(x)) {
      if (is.numeric(y)) {
        dots$x <- setdiff(col(training_frame), y)
      } else {
        dots$x <- setdiff(colnames(training_frame), y)
      }
    } else {
      dots$x <- x
    }
  # Since we removed is_supervised from the arguments (for now), set it here
    is_supervised <- TRUE
  }
  algorithm <- .h2o.unifyAlgoName(algorithm)
  model_param_names <- names(dots)
  # hyper_param_names <- names(hyper_params)
  # # Reject overlapping definition of parameters, this part is now done in Java backend
  # #   if (any(model_param_names %in% hyper_param_names)) {
  # #     overlapping_params <- intersect(model_param_names, hyper_param_names)
  # #     stop(paste0("The following parameters are defined as common model parameters and also as hyper parameters: ",
  # #                 .collapse(overlapping_params), "! Please choose only one way!"))
  # #   }
  # # Get model builder parameters for this model
  all_params <- .h2o.getModelParameters(algo = algorithm)
  
  # Prepare model parameters
  params <- .h2o.prepareModelParameters(algo = algorithm, params = dots, is_supervised = is_supervised)
  # Validation of input key
  .key.validate(params$key_value)
  # TO DO: Add validation of the hyperparam values on client
  # # Validate all hyper parameters against REST API end-point
  # if (do_hyper_params_check) {
  #   lparams <- params
  #   # # Generate all combination of hyper parameters
  #   # expanded_grid <- expand.grid(lapply(hyper_params, function(o) { 1:length(o) }))
  #   # Get algo REST version
  #   algo_rest_version <- .h2o.getAlgoVersion(algo = algorithm)
  #   # Verify each defined point in hyper space against REST API
  #   apply(expanded_grid,
  #         MARGIN = 1,
  #         FUN = function(permutation) {
  #           # Fill hyper parameters for this permutation
  #           hparams <- lapply(hyper_param_names, function(name) { hyper_params[[name]][[permutation[[name]]]] })
  #           names(hparams) <- hyper_param_names
  #           params_for_validation <- lapply(append(lparams, hparams), function(x) { if(is.integer(x)) x <- as.numeric(x); x })
  #           # We have to repeat part of work used by model builders
  #           params_for_validation <- .h2o.checkAndUnifyModelParameters(algo = algorithm, allParams = all_params, params = params_for_validation)
  #           .h2o.validateModelParameters(algorithm, params_for_validation, h2oRestApiVersion = algo_rest_version)
  #         })
  # }
  
  # print(class(params$training_frame))
  # print(algorithm)
  # #print(all_params)
  # print(params)
  # # Verify and unify the parameters
  # params <- .h2o.checkAndUnifyModelParameters(algo = algorithm, allParams = all_params,
  #                                             params = params)
  # # Verify and unify the parameters
  # params <- .h2o.checkAndUnifyModelParameters(algo = algorithm, allParams = all_params,
  #                                             params = params, hyper_params = hyper_params)
  # # Validate and unify hyper parameters
  # hyper_values <- .h2o.checkAndUnifyHyperParameters(algo = algorithm,
  #                                                   allParams = all_params, hyper_params = hyper_params,
  #                                                   do_hyper_params_check = do_hyper_params_check)
  # # Append grid parameters in JSON form
  # params$hyper_parameters <- toJSON(hyper_values, digits=99)
  
  # # Set directory for checkpoints export
  # if(!is.null(export_checkpoints_dir)){
  #   params$export_checkpoints_dir = export_checkpoints_dir
  # }
  # 
  # # Set directory for checkpoints export
  # if(!is.null(parallelism)){
  #   params$parallelism = parallelism
  # }
  # 
  # if( !is.null(search_criteria)) {
  #   # Append grid search criteria in JSON form. 
  #   # jsonlite unfortunately doesn't handle scalar values so we need to serialize ourselves.
  #   keys = paste0("\"", names(search_criteria), "\"", "=")
  #   vals <- lapply(search_criteria, function(val) { if(is.numeric(val)) val else paste0("\"", val, "\"") })
  #   body <- paste0(paste0(keys, vals), collapse=",")
  #   js <- paste0("{", body, "}", collapse="")
  #   params$search_criteria <- js
  # }
  # 
  # # Append grid_id if it is specified
  # if (!missing(grid_id)) params$grid_id <- grid_id
  
  # TO DO: clean this up
  # Are we missing the extra args in the models?  Iterate through the params passed to ...
  
  # Build segment-models specific parameters
  segment_params <- list()
  if (!missing(segment_columns))
    segment_params$segment_columns <- segment_columns
  if (!missing(segment_models_id))
    segment_params$segment_models_id <- segment_models_id
  segment_params$parallelism <- parallelism
  
  # Error check and build segment models
  segment_models <- .h2o.segmentModelsJob(algo = algorithm, 
                                          segment_params = segment_params, 
                                          params = params, 
                                          h2oRestApiVersion = 3)
  return(segment_models)
}