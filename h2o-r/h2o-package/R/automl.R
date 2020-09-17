#' Automatic Machine Learning
#'
#' The Automatic Machine Learning (AutoML) function automates the supervised machine learning model training process.
#' The current version of AutoML trains and cross-validates a Random Forest, an Extremely-Randomized Forest,
#' a random grid of Gradient Boosting Machines (GBMs), a random grid of Deep Neural Nets, and then trains a
#' Stacked Ensemble using all of the models.
#'
#' @param x A vector containing the names or indices of the predictor variables to use in building the model.
#'        If x is missing, then all columns except y are used.
#' @param y The name or index of the response variable in the model. For classification, the y column must be a
#'        factor, otherwise regression will be performed. Indexes are 1-based in R.
#' @param training_frame Training frame (H2OFrame or ID).
#' @param validation_frame Validation frame (H2OFrame or ID); Optional.  This argument is ignored unless the user sets nfolds = 0.
#'        If cross-validation is turned off, then a validation frame can be specified and used for early stopping of individual models and early
#'        stopping of the grid searches.  By default and when nfolds > 1, cross-validation metrics will be used for early stopping and thus
#'        validation_frame will be ignored.
#' @param leaderboard_frame Leaderboard frame (H2OFrame or ID); Optional.  If provided, the Leaderboard will be scored using
#'        this data frame intead of using cross-validation metrics, which is the default.
#' @param blending_frame Blending frame (H2OFrame or ID) used to train the the metalearning algorithm in Stacked Ensembles (instead of relying on cross-validated predicted values); Optional.
#'        When provided, it also is recommended to disable cross validation by setting `nfolds=0` and to provide a leaderboard frame for scoring purposes.
#' @param nfolds Number of folds for k-fold cross-validation. Defaults to 5. Use 0 to disable cross-validation; this will also disable Stacked Ensemble (thus decreasing the overall model performance).
#' @param fold_column Column with cross-validation fold index assignment per observation; used to override the default, randomized, 5-fold cross-validation scheme for individual models in the AutoML run.
#' @param weights_column Column with observation weights. Giving some observation a weight of zero is equivalent to excluding it from
#'        the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice. Negative weights are not allowed.
#' @param balance_classes \code{Logical}. Balance training data class counts via over/under-sampling (for imbalanced data). Defaults to
#'        FALSE.
#' @param class_sampling_factors Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will
#'        be automatically computed to obtain class balance during training. Requires balance_classes.
#' @param max_after_balance_size Maximum relative size of the training data after balancing class counts (can be less than 1.0). Requires
#'        balance_classes. Defaults to 5.0.
#' @param max_runtime_secs This argument specifies the maximum time that the AutoML process will run for, prior to training the final Stacked Ensemble models. If neither `max_runtime_secs` nor `max_models` are specified by the user, then `max_runtime_secs` defaults to 3600 seconds (1 hour).
#' @param max_runtime_secs_per_model Maximum runtime in seconds dedicated to each individual model training process. Use 0 to disable. Defaults to 0.
#' @param max_models Maximum number of models to build in the AutoML process (does not include Stacked Ensembles). Defaults to NULL (no strict limit).
#' @param stopping_metric Metric to use for early stopping ("AUTO" is logloss for classification, deviance for regression).
#'        Must be one of "AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "AUCPR", "lift_top_group", "misclassification", "mean_per_class_error". Defaults to "AUTO".
#' @param stopping_tolerance Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much). This value defaults to 0.001 if the
#'        dataset is at least 1 million rows; otherwise it defaults to a bigger value determined by the size of the dataset and the non-NA-rate.  In that case, the value is computed
#'        as 1/sqrt(nrows * non-NA-rate).
#' @param stopping_rounds Integer. Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric
#'        does not improve for k (stopping_rounds) scoring events. Defaults to 3 and must be an non-zero integer.  Use 0 to disable early stopping.
#' @param seed Integer. Set a seed for reproducibility. AutoML can only guarantee reproducibility if max_models or early stopping is used
#'        because max_runtime_secs is resource limited, meaning that if the resources are not the same between runs, AutoML may be able to train more models on one run vs another.
#' @param project_name Character string to identify an AutoML project.  Defaults to NULL, which means a project name will be auto-generated.
#' @param exclude_algos Vector of character strings naming the algorithms to skip during the model-building phase.  An example use is exclude_algos = c("GLM", "DeepLearning", "DRF"),
#'        and the full list of options is: "DRF" (Random Forest and Extremely-Randomized Trees), "GLM", "XGBoost", "GBM", "DeepLearning" and "StackedEnsemble".
#'        Defaults to NULL, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow. Optional.
#' @param include_algos Vector of character strings naming the algorithms to restrict to during the model-building phase. This can't be used in combination with exclude_algos param.
#'        Defaults to NULL, which means that all appropriate H2O algorithms will be used, if the search stopping criteria allow. Optional.
#' @param exploitation_ratio The budget ratio (between 0 and 1) dedicated to the exploitation (vs exploration) phase. By default, the exploitation phase is disabled (exploitation_ratio=0) as this is still experimental; to activate it, it is recommended to try a ratio around 0.1. Note that the current exploitation phase only tries to fine-tune the best XGBoost and the best GBM found during exploration.
#' @param modeling_plan List. The list of modeling steps to be used by the AutoML engine (they may not all get executed, depending on other constraints). Optional (Expert usage only).
#' @param preprocessing List. The list of preprocessing steps to run. Only 'targetencoding' is currently supported.
#' @param monotone_constraints List. A mapping representing monotonic constraints.
#'        Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint.
#' @param keep_cross_validation_predictions \code{Logical}. Whether to keep the predictions of the cross-validation predictions. This needs to be set to TRUE if running the same AutoML object for repeated runs because CV predictions are required to build additional Stacked Ensemble models in AutoML. This option defaults to FALSE.
#' @param keep_cross_validation_models \code{Logical}. Whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster. This option defaults to FALSE.
#' @param keep_cross_validation_fold_assignment \code{Logical}. Whether to keep fold assignments in the models. Deleting them will save memory in the H2O cluster. Defaults to FALSE.
#' @param sort_metric Metric to sort the leaderboard by. For binomial classification choose between "AUC", "AUCPR", "logloss", "mean_per_class_error", "RMSE", "MSE".
#'        For regression choose between "mean_residual_deviance", "RMSE", "MSE", "MAE", and "RMSLE". For multinomial classification choose between
#'        "mean_per_class_error", "logloss", "RMSE", "MSE". Default is "AUTO". If set to "AUTO", then "AUC" will be used for binomial classification,
#'        "mean_per_class_error" for multinomial classification, and "mean_residual_deviance" for regression.
#' @param export_checkpoints_dir (Optional) Path to a directory where every model will be stored in binary form.
#' @param verbosity Verbosity of the backend messages printed during training; Optional.
#'        Must be one of NULL (live log disabled), "debug", "info", "warn". Defaults to "warn".
#' @details AutoML finds the best model, given a training frame and response, and returns an H2OAutoML object,
#'          which contains a leaderboard of all the models that were trained in the process, ranked by a default model performance metric.
#' @return An \linkS4class{H2OAutoML} object.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(path = prostate_path, header = TRUE)
#' y <- "CAPSULE"
#' prostate[,y] <- as.factor(prostate[,y])  #convert to factor for classification
#' aml <- h2o.automl(y = y, training_frame = prostate, max_runtime_secs = 30)
#' lb <- h2o.get_leaderboard(aml)
#' head(lb)
#' }
#' @export
h2o.automl <- function(x, y, training_frame,
                       validation_frame = NULL,
                       leaderboard_frame = NULL,
                       blending_frame = NULL,
                       nfolds = 5,
                       fold_column = NULL,
                       weights_column = NULL,
                       balance_classes = FALSE,
                       class_sampling_factors = NULL,
                       max_after_balance_size = 5.0,
                       max_runtime_secs = NULL,
                       max_runtime_secs_per_model = NULL,
                       max_models = NULL,
                       stopping_metric = c("AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "AUCPR", "lift_top_group", "misclassification", "mean_per_class_error"),
                       stopping_tolerance = NULL,
                       stopping_rounds = 3,
                       seed = NULL,
                       project_name = NULL,
                       exclude_algos = NULL,
                       include_algos = NULL,
                       modeling_plan = NULL,
                       preprocessing = NULL,
                       exploitation_ratio = 0.0,
                       monotone_constraints = NULL,
                       keep_cross_validation_predictions = FALSE,
                       keep_cross_validation_models = FALSE,
                       keep_cross_validation_fold_assignment = FALSE,
                       sort_metric = c("AUTO", "deviance", "logloss", "MSE", "RMSE", "MAE", "RMSLE", "AUC", "AUCPR", "mean_per_class_error"),
                       export_checkpoints_dir = NULL,
                       verbosity = "warn",
                       ...)
{
  dots <- list(...)
  algo_parameters <- NULL  
  for (arg in names(dots)) {
    if (arg == 'algo_parameters') {
      algo_parameters <- dots$algo_parameters  
    } else {
      stop(paste("unused argument", arg, "=", dots[[arg]]))
    }
  }

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
  if (missing(y)) stop("The response column (y) is not set; please set it to the name of the column that you are trying to predict in your data.")
  training_frame <- .validate.H2OFrame(training_frame, required=TRUE)

  # ensure all passed frames are a H2OFrame or a valid key
  validation_frame <- .validate.H2OFrame(validation_frame)
  leaderboard_frame <- .validate.H2OFrame(leaderboard_frame)
  blending_frame <- .validate.H2OFrame(blending_frame)

  training_frame_id <- h2o.getId(training_frame)
  validation_frame_id <- if (is.null(validation_frame)) NULL else h2o.getId(validation_frame)
  leaderboard_frame_id <- if (is.null(leaderboard_frame)) NULL else h2o.getId(leaderboard_frame)
  blending_frame_id <- if (is.null(blending_frame)) NULL else h2o.getId(blending_frame)

  # Input/data parameters to send to the AutoML backend
  input_spec <- list()
  input_spec$response_column <- ifelse(is.numeric(y),names(training_frame[y]),y)
  input_spec$training_frame <- training_frame_id
  input_spec$validation_frame <- validation_frame_id
  input_spec$leaderboard_frame <- leaderboard_frame_id
  input_spec$blending_frame <- blending_frame_id
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
  input_spec$sort_metric <- ifelse(length(sort_metric) == 1,
                                   match.arg(tolower(sort_metric), tolower(formals()$sort_metric)),
                                   match.arg(sort_metric))

  # Update build_control list with top level build control args
  build_control <- list(stopping_criteria = list())
  if (!is.null(max_runtime_secs)) {
    build_control$stopping_criteria$max_runtime_secs <- max_runtime_secs
  }
  if (!is.null(max_runtime_secs_per_model)) {
    build_control$stopping_criteria$max_runtime_secs_per_model <- max_runtime_secs_per_model
  }
  if (!is.null(max_models)) {
    build_control$stopping_criteria$max_models <- max_models
  }
  build_control$stopping_criteria$stopping_metric <- ifelse(length(stopping_metric) == 1,
                                                            match.arg(tolower(stopping_metric), tolower(formals()$stopping_metric)),
                                                            match.arg(stopping_metric))
  if (!is.null(stopping_tolerance)) {
    build_control$stopping_criteria$stopping_tolerance <- stopping_tolerance
  }
  build_control$stopping_criteria$stopping_rounds <- stopping_rounds
  if (!is.null(seed)) {
    build_control$stopping_criteria$seed <- seed
  }

  if (!is.null(project_name)) {
    .key.validate(project_name)
    build_control$project_name <- project_name
  }

  build_models <- list()
  if (!is.null(exclude_algos)) {
    if (!is.null(include_algos)) stop("Use either include_algos or exclude_algos, not both.")
    if (length(exclude_algos) == 1) {
      exclude_algos <- as.list(exclude_algos)
    }
    build_models$exclude_algos = exclude_algos
  } else if (!is.null(include_algos)) {
    if (length(include_algos) == 1) {
      include_algos <- as.list(include_algos)
    }
    build_models$include_algos <- include_algos
  }
  if (!is.null(exploitation_ratio)) {
    build_models$exploitation_ratio <- exploitation_ratio
  }
  if (!is.null(modeling_plan)) {
    is.string <- function(s) is.character(s) && length(s) == 1
    is.step <- function(s) is.string(s) || is.list(s) && !is.null(s$id)
    modeling_plan <- lapply(modeling_plan, function(step) {
      if (is.string(step)) {
        list(name=step)
      } else if (!(is.list(step)
                    && !is.null(step$name)
                    && (is.null(step$alias) || is.null(step$steps)))) {
        stop("Each steps definition must be a string or a list with a 'name' key and an optional 'alias' or 'steps' key.")
      } else if (!(is.null(step$alias) || step$alias %in% c('all', 'defaults', 'grids'))) {
        stop("alias key must be one of 'all', 'defaults', 'grids'.")
      } else if (!(is.null(step$steps)
                    || is.step(step$steps)
                    || is.vector(step$steps) && is.null(names(step$steps)) && all(sapply(step$steps, is.step)))){
        stop("steps key must be a vector, and each element must be a string or a list with an 'id' key.")
      } else if (is.string(step$steps)) {
        list(name=step$name, steps=list(list(id=step$steps)))
      } else if (is.list(step$steps) && !is.null(step$steps$id)) {
        list(name=step$name, steps=list(step$steps))
      } else if (!is.null(step$steps)) {
        list(name=step$name, steps=lapply(step$steps, function(s) {
          if (is.string(s))
            list(id=s)
          else
            s
        }))
      } else {
        step
      }
    })
    build_models$modeling_plan <- modeling_plan
  }
    
  if (!is.null(preprocessing)) {
    preprocessing <- lapply(preprocessing, function(step) {
      if (is.string(step)) {
        list(type=step)  
      } else {
        stop("preprocessing steps must be a string (only 'targetencoding' currently supported)")  
      } 
    })  
    build_models$preprocessing <- preprocessing  
  }

  if (!is.null(monotone_constraints)) {
    if(is.null(algo_parameters)) algo_parameters <- list()
    algo_parameters$monotone_constraints <- monotone_constraints
  }
  if (!is.null(algo_parameters)) {
    keys <- names(algo_parameters)
    algo_parameters_json <- lapply(keys, function(k) {
      tokens <- strsplit(k, "__")[[1]]
      if (length(tokens) == 1) {
        scope <- "any"
        name <- k
      } else {
        scope <- tokens[1]
        name <- paste0(tokens[2:length(tokens)], collapse="__")
      }
      value <- algo_parameters[[k]]
      if (is.list(value) && !is.null(names(value))) {
        vnames <- names(value)
        value <- lapply(vnames, function(n) list(key=n, value=value[[n]]))
      }
      list(scope=scope, name=name, value=value)
    })
    build_models$algo_parameters <- algo_parameters_json
  }

  # Update build_control with nfolds
  if (nfolds < 0) {
    stop("nfolds cannot be negative. Use nfolds >=2 if you want cross-valiated metrics and Stacked Ensembles or use nfolds = 0 to disable.")
  }
  if (nfolds == 1) {
    stop("nfolds = 1 is an invalid value. Use nfolds >=2 if you want cross-valiated metrics and Stacked Ensembles or use nfolds = 0 to disable.")
  }
  build_control$nfolds <- nfolds

  # Update build_control with balance_classes & related args
  if (balance_classes == TRUE) {
    build_control$balance_classes <- balance_classes
  }
  if (!is.null(class_sampling_factors)) {
    build_control$class_sampling_factors <- class_sampling_factors
  }
  if (max_after_balance_size != 5) {
    build_control$max_after_balance_size <- max_after_balance_size
  }

  # Update build_control with what to save
  build_control$keep_cross_validation_predictions <- keep_cross_validation_predictions
  build_control$keep_cross_validation_models <- keep_cross_validation_models
  build_control$keep_cross_validation_fold_assignment <- nfolds !=0  && keep_cross_validation_fold_assignment

  if (!is.null(export_checkpoints_dir)) {
    build_control$export_checkpoints_dir <- export_checkpoints_dir
  }

  # Create the parameter list to POST to the AutoMLBuilder
  if (length(build_models) == 0) {
      params <- list(input_spec = input_spec, build_control = build_control)
  } else {
      params <- list(input_spec = input_spec, build_control = build_control, build_models = build_models)
  }

  # POST call to AutoMLBuilder (executes the AutoML job)
  res <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "POST", page = "AutoMLBuilder", autoML = TRUE, .params = params)

  poll_state <- list()
  poll_updates <- function(job) {
    poll_state <<- do.call(.automl.poll_updates, list(job, verbosity=verbosity, state=poll_state))
  }
  .h2o.__waitOnJob(res$job$key$name, pollUpdates=poll_updates)
  .automl.poll_updates(h2o.get_job(res$job$key$name), verbosity, poll_state) # ensure the last update is retrieved

  # GET AutoML object
  aml <- h2o.get_automl(project_name = res$job$dest$name)
  attr(aml, "id") <- res$job$dest$name
  attr(aml, '_build_resp') <- res # hidden attribute for debugging/testing
  return(aml)
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
  h2o.predict.H2OAutoML(object, newdata, ...)
}

#'
#' @rdname predict.H2OAutoML
#' @export
h2o.predict.H2OAutoML <- function(object, newdata, ...) {
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

.automl.poll_updates <- function(job, verbosity=NULL, state=NULL) {
  levels <- c('Debug', 'Info', 'Warn')
  idx = ifelse(is.null(verbosity), NA, match(tolower(verbosity), tolower(levels)))
  if (is.na(idx)) return()

  levels <- levels[idx:length(levels)]
  try({
      if (job$progress > ifelse(is.null(state$last_job_progress), 0, state$last_job_progress)) {
        project_name <- job$dest$name
        events <- .automl.fetch_state(project_name, properties=c('event_log'))$event_log
        events <- events[events['level'] %in% levels,]
        last_nrows <- ifelse(is.null(state$last_events_nrows), 0, state$last_events_nrows)
        if (h2o.nrow(events) > last_nrows) {
          for (row in (last_nrows+1):nrow(events)) {
            cat(paste0("\n", events[row, 'timestamp'], ': ', events[row, 'message']))
          }
          state$last_events_nrows <- h2o.nrow(events)
        }
      }
      state$last_job_progress <- job$progress
  })
  return(state)
}

.automl.fetch_leaderboard <- function(run_id, extensions=NULL) {
  if (is.null(extensions)) {
    extensions <- list()
  } else if (is.character(extensions)) {
    extensions <- as.list(extensions)
  }
  extensions_str <- paste0("[", paste(extensions, collapse = ","), "]")
  resp <- .h2o.__remoteSend(h2oRestApiVersion=99, method="GET", page=paste0("Leaderboards/", run_id), .params=list(extensions=extensions_str))
  dest_key <- paste0(gsub("@.*", "", resp$project_name), "_extended_leaderboard")
  .automl.fetch_table(as.data.frame(resp$table), destination_frame=dest_key, show_progress=FALSE)
}

.automl.fetch_table <- function(table, destination_frame=NULL, show_progress=TRUE) {
  # disable the progress bar is show_progress is set to FALSE, e.g. since showing multiple progress bars is confusing to users.
  # In any case, revert back to user's original progress setting.
  is_progress <- isTRUE(as.logical(.h2o.is_progress()))
  if (show_progress) h2o.show_progress() else h2o.no_progress()
  frame <- tryCatch(
    as.h2o(table, destination_frame=destination_frame, use_datatable=FALSE),
    error = identity,
    finally = if (is_progress) h2o.show_progress() else h2o.no_progress()
  )
  return(frame)
}

.automl.fetch_state <- function(run_id, properties=NULL) {
  # GET AutoML job and leaderboard for project
  automl_job <- .h2o.__remoteSend(h2oRestApiVersion = 99, method = "GET", page = paste0("AutoML/", run_id))
  project_name <- automl_job$project_name
  automl_id <- automl_job$automl_id$name

  leaderboard <- as.data.frame(automl_job$leaderboard_table)
  row.names(leaderboard) <- seq(nrow(leaderboard))

  should_fetch <- function(prop) is.null(properties) | prop %in% properties

  if (should_fetch('leaderboard')) {
    leaderboard <- .automl.fetch_table(leaderboard, destination_frame=paste0(project_name, '_leaderboard'), show_progress=FALSE)
    # leaderboard[,2:length(leaderboard)] <- as.numeric(leaderboard[,2:length(leaderboard)])  # Convert metrics to numeric
    # If the leaderboard is empty, it creates a dummy row so let's remove it
    if (leaderboard$model_id[1,1] == "") {
      leaderboard <- leaderboard[-1,]
      warning("The leaderboard contains zero models: try running AutoML for longer (the default is 1 hour).")
    }
  }

  # If leaderboard is not empty, grab the leader model, otherwise create a "dummy" leader
  if (should_fetch('leader') & nrow(leaderboard) > 0) {
    leader <- h2o.getModel(automl_job$leaderboard$models[[1]]$name)
  } else {
    # create a phony leader
    Class <- paste0("H2OBinomialModel")
    leader <- .newH2OModel(Class = Class, model_id = "dummy")
  }

  if (should_fetch('event_log')) {
    event_log <- as.data.frame(automl_job$event_log_table)
    event_log <- .automl.fetch_table(event_log, destination_frame=paste0(project_name, '_eventlog'), show_progress=FALSE)
    # row.names(event_log) <- seq(nrow(event_log))
  } else {
    event_log <- NULL
  }

  if (should_fetch('modeling_steps')) {
    modeling_steps <- lapply(automl_job$modeling_steps, function(sdef) {
      list(name=sdef$name, steps=sdef$steps)
    })
  } else {
    modeling_steps <- NULL
  }

  return(list(
    automl_id=automl_id,
    project_name=project_name,
    leaderboard=leaderboard,
    leader=leader,
    event_log=event_log,
    modeling_steps=modeling_steps
  ))
}

.is.H2OAutoML <- function(object) base::`&&`(!missing(object), class(object)=="H2OAutoML")

#' Get an R object that is a subclass of \linkS4class{H2OAutoML}
#'
#' @param project_name A string indicating the project_name of the automl instance to retrieve.
#' @return Returns an object that is a subclass of \linkS4class{H2OAutoML}.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(path = prostate_path, header = TRUE)
#' y <- "CAPSULE"
#' prostate[,y] <- as.factor(prostate[,y])  #convert to factor for classification
#' aml <- h2o.automl(y = y, training_frame = prostate, 
#'                   max_runtime_secs = 30, project_name = "prostate")
#' aml2 <- h2o.get_automl("prostate")
#' }
#' @export
h2o.get_automl <- function(project_name) {

  state <- .automl.fetch_state(project_name)

  training_info <- list()
  for (i in seq(nrow(state$event_log))) {
    key <-state$event_log[i, 'name']
    if (!is.na(key)) training_info[key] <- state$event_log[i, 'value']
  }

  # Make AutoML object
  automl <- new("H2OAutoML",
             project_name = state$project,
             leader = state$leader,
             leaderboard = state$leaderboard,
             event_log = state$event_log,
             modeling_steps = state$modeling_steps,
             training_info = training_info
  )
  attr(automl, "id") <- state$automl_id
  return(automl)
}


#' @rdname h2o.get_automl
#' @export
h2o.getAutoML <- function(project_name) {
  .Deprecated("h2o.get_automl")
  h2o.get_automl(project_name)
}

#' Retrieve the leaderboard from the AutoML instance.
#'
#' Contrary to the default leaderboard attached to the automl instance, this one can return columns other than the metrics.
#'
#' @param object The object for which to return the leaderboard. Currently, only H2OAutoML instances are supported.
#' @param extra_columns A string or a list of string specifying which optional columns should be added to the leaderboard. Defaults to None.
#' Currently supported extensions are:
#' \itemize{
#' \item{'ALL': adds all columns below.}
#' \item{'training_time_ms': column providing the training time of each model in milliseconds (doesn't include the training of cross validation models).}
#' \item{'predict_time_per_row_ms': column providing the average prediction time by the model for a single row.}
#' }
#' @return An H2OFrame representing the leaderboard.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(path = prostate_path, header = TRUE)
#' y <- "CAPSULE"
#' prostate[,y] <- as.factor(prostate[,y])  #convert to factor for classification
#' aml <- h2o.automl(y = y, training_frame = prostate, max_runtime_secs = 30)
#' lb <- h2o.get_leaderboard(aml)
#' head(lb)
#' }
#' @export
h2o.get_leaderboard <- function(object, extra_columns=NULL) {
  if (!.is.H2OAutoML(object)) stop("Only H2OAutoML instances are currently supported.")
  return(.automl.fetch_leaderboard(attr(object, 'id'), extra_columns))
}
