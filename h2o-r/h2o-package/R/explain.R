########################################### UTILS ##################################################
#' Suppresses h2o progress output from \code{expr}
#'
#' @param expr expression
#'
#' @return result of \code{expr}
with_no_h2o_progress <- function(expr) {
  show_progress <- environment(h2o.no_progress)$
    .pkg.env$
    PROGRESS_BAR
  if (length(show_progress) == 0L || show_progress) {
    on.exit(h2o.show_progress())
  } else {
    on.exit(h2o.no_progress())
  }
  h2o.no_progress()
  force(expr)
}

#' Get the algoritm used by the model_or_model_id
#'
#' @param model_or_model_id Model object or a string containing model id
#' @param treat_xrt_as_algorithm Try to find out if a model is XRT and if so report it as xrt
#' @return algorithm name
.get_algorithm <- function(model_or_model_id, treat_xrt_as_algorithm = FALSE) {
  known_algos <- c("deeplearning", "drf", "glm", "gam", "gbm", "naivebayes", "stackedensemble", "rulefit", "xgboost", "xrt")
  if (is.character(model_or_model_id)) {
    algorithm <- sub("^(DeepLearning|DRF|GAM|GBM|GLM|NaiveBayes|StackedEnsemble|RuleFit|XGBoost|XRT)_.*",
                     "\\L\\1", model_or_model_id, perl = TRUE)
    if (algorithm == "xrt" && !treat_xrt_as_algorithm)
      algorithm <- "drf"
    if (algorithm %in% known_algos) {
      return(algorithm)
    }
    model_or_model_id <- h2o.getModel(model_or_model_id)
  }
  if (treat_xrt_as_algorithm && model_or_model_id@algorithm == "drf") {
    if (model_or_model_id@parameters$histogram_type == "Random")
      return("xrt")
  }
  return(model_or_model_id@algorithm)
}

#' Get first of family models
#'
#' @param models models or model ids
#' @param all_stackedensembles if TRUE, select all stacked ensembles
.get_first_of_family <- function(models, all_stackedensembles = TRUE) {
  selected_models <- character()
  included_families <- character()
  for (model in models) {
    family <- .get_algorithm(model, treat_xrt_as_algorithm = TRUE)
    if (!family %in% included_families || ("stackedensemble" == family && all_stackedensembles)) {
      included_families <- c(included_families, family)
      selected_models <- c(selected_models, model)
    }
  }
  return(selected_models)
}

#' Is the \code{model} an H2O model?
#'
#' @param model Either H2O model/model id => TRUE, or something else => FALSE
#'
#' @return boolean
.is_h2o_model <- function(model) {
  classes <- class(model)
  return(any(startsWith(classes, "H2O") & endsWith(classes, "Model")))
}

#' Is the \code{model} a Tree-based H2O Model?
#'
#' @param model Either tree-based H2O model/model id => TRUE, or something else => FALSE
#'
#' @return boolean
.is_h2o_tree_model <- function(model) {
  return(.get_algorithm(model) %in% c("drf", "gbm", "xgboost"))
}

#' Is the model considered to be interpretable, i.e., simple enough.
#'
#' @param model model or a string containing model id
#' @return boolean
.interpretable <- function(model) {
  return(.get_algorithm(model) %in% c("gam", "glm", "rulefit"))
}

#' Get feature count sorted by the count descending.
#'
#' @param column H2OFrame column
#' @return named vector with feature counts
.get_feature_count <- function(column) {
  desc <- Count <- NULL  # To keep R check as CRAN quiet
  tbl <- h2o.arrange(h2o.table(column), desc(Count))
  return(unlist(stats::setNames(as.list(tbl[, 2]), as.list(tbl[, 1]))))
}

#' Get Model Ids
#'
#' When provided with list of models it will extract model ids.
#' When provided with model ids it won't change anything.
#' Works for mixed list as well.
#'
#' @param models list or vector of models/model_ids
#'
#' @return a vector of \code{model_id}s
.model_ids <- function(models) {
  sapply(models, function(model) {
    if (!is.character(model) && .is_h2o_model(model)) {
      model@model_id
    } else if (is.character(model)) {
      model
    } else {
      stop("Expected either model or model_id")
    }
  })
}

#' Has the model variable importance?
#' @param model model or a string containing model id
#' @return boolean
.has_varimp <- function(model) {
  return(!.get_algorithm(model) %in% c("stackedensemble", "naivebayes"))
}

#' Shortens model ids if possible (iff there will be same amount of unique model_ids as before)
#' @param model_ids character vector
#' @return character vector
.shorten_model_ids <- function(model_ids) {
  shortened_model_ids <- gsub("(.*)_AutoML_\\d{8}_\\d{6}(.*)", "\\1\\2", model_ids)
  if (length(unique(shortened_model_ids)) == length(unique(model_ids))) {
    return(shortened_model_ids)
  }
  return(model_ids)
}


#' Needed to be able to memoise the models
.model_cache <- setRefClass(
  "model_cache",
        fields = "models",
        methods = list(
        init = function(){
          models <<- list()
        },
        get_model = function(model_id) {
            if (model_id %in% names(models)) {
              return(models[[model_id]])
            } else {
              mdl <- h2o.getModel(model_id)
              models[[model_id]] <<- mdl
              return(mdl)
            }
        },
        add_model = function(model) {
          models[[model@model_id]] <<- model
        })
  )

.models_info <- setRefClass(
  "models_info",
  fields = c("is_automl",
             "leaderboard",
             "model_ids",
             "is_classification",
             "is_multinomial_classification",
             "x",
             "y",
             "model",
             "memoised_models"),
  methods = list(
    initialize = function(newdata, is_automl, leaderboard, model_ids, models = NULL) {
      if (!missing(newdata) && !missing(is_automl) && !missing(leaderboard) && !missing(model_ids)) {
        memoised_models <<- .model_cache$new()
        memoised_models$init()
        is_automl <<- is_automl
        leaderboard <<- leaderboard
        model_ids <<- model_ids
        model <<- model_ids[[1]]
        if (is.null(models) || length(models) == 0) {
          single_model <- .self$get_model(model_ids[[1]])
        } else {
          single_model <- models[[1]]
          for (m in models)
            memoised_models$add_model(m)
        }
        x <<- single_model@allparameters$x
        y <<- single_model@allparameters$y
        if (is.null(newdata)) {
          is_classification <<- NA
          is_multinomial_classification <<- NA
        } else {
          y_col <- newdata[[.self$y]]
          is_classification <<- is.factor(y_col)
          is_multinomial_classification <<- is.factor(y_col) && h2o.nlevels(y_col) > 2
        }
      }
      .self
    },
    get_model = function(model_id) {
      return(memoised_models$get_model(model_id))
    }
  )
)


#' Do basic validation and transform \code{object} to a "standardized" list containing models, and
#' their properties such as \code{x}, \code{y}, whether it is a (multinomial) clasification or not etc.
#'
#' @param object Can be a single model/model_id, vector of model_id, list of models, H2OAutoML object
#' @param newdata An H2OFrame with the same format as training frame
#' @param require_single_model If true, make sure we were provided only one model
#' @param require_multiple_models If true, make sure we were provided at least two models
#' @param top_n_from_AutoML If set, don't return more than top_n models (applies only for AutoML object)
#' @param only_with_varimp If TRUE, return only models that have variable importance
#' @param best_of_family If TRUE, return only the best of family models; if FALSE return all models in \code{object}
#' @param require_newdata If TRUE, require newdata to be specified; otherwise allow NULL instead, this can be used when
#'                        there is no need to know if the problem is (multinomial) classification.
#'
#' @return a list with the following names \code{leader}, \code{is_automl}, \code{models},
#'   \code{is_classification}, \code{is_multinomial_classification}, \code{x}, \code{y}, \code{model}
.process_models_or_automl <- function(object, newdata,
                                      require_single_model = FALSE,
                                      require_multiple_models = FALSE,
                                      top_n_from_AutoML = NA,
                                      only_with_varimp = FALSE,
                                      best_of_family = FALSE,
                                      require_newdata = TRUE) {
  if (missing(object))
    stop("object must be specified!")
  if (missing(newdata))
    stop("newdata must be specified!")
  if ("H2OFrame" %in% class(object)) {
    object <- as.list(object[["model_id"]])
  }
  newdata_name <- deparse(substitute(newdata, environment()))
  if (!"H2OFrame" %in% class(newdata) && require_newdata) {
    stop(paste(newdata_name, "must be an H2OFrame!"))
  }

  make_models_info <- function(newdata, is_automl, leaderboard, model_ids, models = NULL) {
    return(.models_info$new(
      newdata = newdata,
      is_automl = is_automl,
      leaderboard = leaderboard,
      model_ids = model_ids,
      models = models
    ))
  }

  .get_MSE <- function(model) {
    if (!is.null(model@model$validation_metrics@metrics$MSE)) {
      model@model$validation_metrics@metrics$MSE
    } else {
      model@model$training_metrics@metrics$MSE
    }
  }

  if ("models_info" %in% class(object)) {
    object <- object$copy(shallow = TRUE)
    if (best_of_family) {
      object$model_ids <- .get_first_of_family(object$model_ids)
    }

    if (only_with_varimp) {
      object$model_ids <- Filter(.has_varimp, object$model_ids)
    }

    if (!is.na(top_n_from_AutoML)) {
      if (object$is_automl) {
        object$model_ids <- head(object$model_ids, n = min(top_n_from_AutoML, length(object$model_ids)))
      }
    }

    return(object)
  }


  if ("H2OAutoML" %in% class(object)) {
    if (require_single_model && nrow(object@leaderboard) > 1) {
      stop("Only one model is allowed!")
    }
    if (require_multiple_models && nrow(object@leaderboard) <= 1) {
      stop("More than one model is needed!")
    }
    model_ids <- unlist(as.list(object@leaderboard$model_id))
    if (only_with_varimp) {
      model_ids <- Filter(.has_varimp, model_ids)
    }
    if (best_of_family) {
      model_ids <- .get_first_of_family(model_ids)
    }
    if (is.na(top_n_from_AutoML)) {
      top_n_from_AutoML <- length(model_ids)
    } else {
      top_n_from_AutoML <- min(top_n_from_AutoML, length(model_ids))
    }
    return(make_models_info(
      newdata = newdata,
      is_automl = TRUE,
      leaderboard = as.data.frame(h2o.get_leaderboard(object, extra_columns = "ALL")),
      model_ids = head(model_ids, top_n_from_AutoML)
    ))
  } else {
    if (length(object) == 1) {
      if (require_multiple_models) {
        stop("More than one model is needed!")
      }
      if (class(object) == "list") {
        object <- object[[1]]
      }
      if (!is.character(object)) {
        model <- object
        model_id <- object@model_id
      } else {
        model_id <- object
        model <- h2o.getModel(model_id)
      }

      if (only_with_varimp && !.has_varimp(model)) {
        stop(model_id, " doesn't have variable importance!")
      }

      mi <- make_models_info(
          newdata = newdata,
          is_automl = FALSE,
          leaderboard = NULL,
          model_ids = model_id,
          models = list(model)
      )
      return(mi)
    } else {
      if (require_single_model) {
        stop("Only one model is allowed!")
      }

      if (only_with_varimp) {
        object <- Filter(.has_varimp, object)
      }

      memoised_models <- list()
      object <- sapply(object, function(m) {
        if (is.character(m)) {
          m
        } else {
          memoised_models[[m@model_id]] <- m
          m@model_id
        }
      })

      if (best_of_family) {
        object <- object[order(sapply(sapply(object, function(m_id) {
          if (m_id %in% memoised_models) {
            return(memoised_models[[m_id]])
          } else {
            m <- h2o.getModel(m_id)
            memoised_models[[m_id]] <- m
            return(m)
          }
        }), .get_MSE))]
        object <- .get_first_of_family(object)
      }

      mi <- make_models_info(
        newdata = newdata,
        is_automl = FALSE,
        model_ids = .model_ids(object),
        leaderboard = NULL,
        models = memoised_models
      )
      x <- mi$x
      y <- mi$y

      for (model in object) {
        model <- mi$get_model(model)
        if (any(sort(model@allparameters$x) != sort(x))) {
          stop(sprintf(
            "Model \"%s\" has different x from model\"%s\"! (%s != %s)",
            model@model_id,
            object[[1]]@model_id,
            paste(model@allparameters$x, collapse = ", "),
            paste(x, collapse = ", ")
          ))
        }
        if (any(sort(model@allparameters$y) != sort(y))) {
          stop(sprintf(
            "Model \"y\" has different x from model\"%s\"! (%s != %s)", model@model_id,
            object[[1]]@model_id,
            paste(model@allparameters$y, collapse = ", "),
            paste(y, collapse = ", ")
          ))
        }
      }

      return(mi)
    }
  }
}

#' A helper function that makes it easier to override/add params in a function call.
#'
#' @param fun Function to be called
#' @param ... Parameters that can't be overridden
#' @param overridable_defaults List of parameters and values that can be overridden
#' @param overrides Parameters to add/override.
#'
#' @return result of \code{fun}
.customized_call <- function(fun, ..., overridable_defaults = NULL, overrides = NULL) {
  unchangeable_params <- list(...)
  if (any(names(overrides) %in% names(unchangeable_params))) {
    stop(sprintf(
      "Parameter %s cannot be overridden!",
      paste(names(overrides)[
              names(overrides) %in% names(unchangeable_params)
            ], collapse = ", ")
    ))
  }
  if (is.null(overrides)) {
    overrides <- list()
  }
  if (is.null(overridable_defaults)) {
    overridable_defaults <- list()
  }
  args <- utils::modifyList(
    utils::modifyList(overridable_defaults, overrides),
    unchangeable_params
  )
  result <- do.call(fun, args)
  return(result)
}

#' Tries to match a \code{fuzzy_col_name} with a column name that exists in \code{cols}.
#'
#' @param fuzzy_col_name a name to be decoded
#' @param cols vector of columns that contain all possible column names, i.e., decode
#'             fuzzy_col_name must be in cols
#'
#' @return a correct column name
.find_appropriate_column_name <- function(fuzzy_col_name, cols) {
  if (!fuzzy_col_name %in% cols) {
    if (tolower(fuzzy_col_name) %in% tolower(make.names(cols))) {
      return(cols[tolower(fuzzy_col_name) == tolower(make.names(cols))])
    }
    if (tolower(fuzzy_col_name) %in% tolower(gsub(" ", "", cols))) {
      return(cols[tolower(fuzzy_col_name) == tolower(gsub(" ", "", cols))])
    }
    stop(paste("Can't find", fuzzy_col_name, "in", paste(cols, collapse = ", "), traceback(300)))
  }
  return(fuzzy_col_name)
}

#' Create a leaderboard like data frame for \code{models}
#'
#' @param models_info H2OAutoML object or list of models
#' @param leaderboard_frame when provided with list of models, use this frame to calculate metrics
#' @param top_n create leaderboard with just top_n models
#' @return a data.frame
.create_leaderboard <- function(models_info, leaderboard_frame, top_n = 20) {
  if (models_info$is_automl) {
    leaderboard <- models_info$leaderboard
    return(head(leaderboard, n = min(top_n, nrow(leaderboard))))
  }
  leaderboard <-
    as.data.frame(t(sapply(models_info$model_ids, function(m) {
      m <- models_info$get_model(m)
      unlist(h2o.performance(m, leaderboard_frame)@metrics[c(
        "MSE",
        "RMSE",
        "mae",
        "rmsle",
        "mean_per_class_error",
        "logloss"
      )])
    })))
  leaderboard <- cbind(data.frame(model_id = .model_ids(models_info$model_ids), stringsAsFactors = FALSE),
                       leaderboard)
  leaderboard <- leaderboard[order(leaderboard[[2]]),]
  names(leaderboard) <- tolower(names(leaderboard))
  return(head(leaderboard, n = min(top_n, nrow(leaderboard))))
}

#' Consolidate variable importances
#'
#' Consolidation works in the following way:
#' 1. if varimp variable is in x => add it to consolidated_varimps
#' 2. for all remaining varimp variables:
#'    1. find the longest prefix of varimp variable that is in x and add it to the consolidated varimp
#'    2. if there was no match, throw an error
#' 3. normalize the consolidated_varimps so they sum up to 1
#'
#' @param model H2OModel
#' @return sorted named vector
.consolidate_varimps <- function(model) {
  varimps_hdf <- h2o.varimp(model)
  varimps <- stats::setNames(varimps_hdf$percentage, varimps_hdf$variable)
  x <- model@allparameters$x

  consolidated_varimps <- varimps[names(varimps) %in% x]
  to_process <- varimps[!names(varimps) %in% x]

  for (col in x) {
    if (!col %in% names(consolidated_varimps)) {
      consolidated_varimps[[col]] <- 0
    }
  }

  for (feature in names(to_process)) {
    col_parts <- strsplit(feature, ".", fixed = TRUE)[[1]]
    found <- FALSE
    for (prefix_len in seq(from = length(col_parts), to = 1, by = -1)){
      prefix <- paste0(head(col_parts, n = prefix_len), collapse = ".")
      if (prefix %in% x) {
        consolidated_varimps[[prefix]] <- consolidated_varimps[[prefix]] + varimps[[feature]]
        found <- TRUE
        break
      }
    }
    if (!found)
      stop(feature, " was not found in x!")
  }

  total_value <- sum(consolidated_varimps, na.rm = TRUE)
  if (total_value != 1)
    consolidated_varimps <- consolidated_varimps / total_value

  names(consolidated_varimps) <- make.names(names(consolidated_varimps))
  return(sort(consolidated_varimps))
}

#' Get variable importance in a standardized way.
#'
#' @param model H2OModel
#'
#' @return A named vector
.varimp <- function(model) {
  if (!.has_varimp(model)) {
    stop("Can't get variable importance from: ", model@model_id)
  } else {
    varimp <- h2o.varimp(model)
    if (is.null(varimp)) {
      res <- rep_len(0, length(model@allparameters$x))
      names(res) <- make.names(model@allparameters$x)
      return(res)
    }
    return(sort(.consolidate_varimps(model)))
  }
}

#' Plot variable importances with ggplot2
#'
#' @param model H2OModel
#' @param top_n Plot just top_n features
#' @return list of variable importance, groupped variable importance, and variable importance plot
.plot_varimp <- function(model, top_n = 10) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  with_no_h2o_progress({
    suppressWarnings({
      varimp <- h2o.varimp(model)
    })
    if (is.null(varimp)) {
      return(NULL)
    } else {
      varimp <- as.data.frame(varimp)
      varimp <- head(varimp, n = min(top_n, nrow(varimp)))
      p <- ggplot2::ggplot(ggplot2::aes(.data$variable, .data$scaled_importance), data = varimp) +
        ggplot2::geom_col(fill = "#1F77B4") +
        ggplot2::scale_x_discrete("Variable", limits = rev(varimp$variable)) +
        ggplot2::labs(y = "Variable Importance", title = sprintf("Variable importance\nfor \"%s\"", model@model_id)) +
        ggplot2::coord_flip() +
        ggplot2::theme_bw() +
        ggplot2::theme(plot.title = ggplot2::element_text(hjust = 0.5))
      return(list(varimp = varimp, grouped_varimp = .varimp(model), plot = p))
    }
  })
}

#' Enhance leaderboard with per-model predictions.
#'
#' @param models_info models_info object
#' @param newdata H2OFrame
#' @param row_index index of the inspected row
#' @param top_n leaderboard will contain top_n models
#'
#' @return H2OFrame
.leaderboard_for_row <- function(models_info, newdata, row_index, top_n = 20) {
  leaderboard <- .create_leaderboard(models_info, newdata)
  top_n <- min(top_n, nrow(leaderboard))
  indices <- which(!duplicated(substr(leaderboard$model_id, 1, 3)))
  indices <- c(seq_len(top_n), indices[indices > top_n])
  leaderboard <- leaderboard[indices,]
  with_no_h2o_progress({
    leaderboard <-
      cbind(leaderboard,
            do.call(
              rbind,
              lapply(
                leaderboard[["model_id"]],
                function(model_id) {
                  as.data.frame(stats::predict(models_info$get_model(model_id), newdata[row_index,]))
                }
              )
            )
      )
  })
  row.names(leaderboard) <- seq_len(nrow(leaderboard))
  return(leaderboard)
}

#' Min-max normalization.
#' @param col numeric vector
#' @return normalized numeric vector
.min_max <- function(col) {
  rng <- range(col, na.rm = TRUE)
  if (rng[[2]] == rng[[1]]) {
    return(0.5)
  }
  return((col - rng[[1]]) / (rng[[2]] - rng[[1]]))
}

#' Convert to quantiles when provided with numeric vector.
#' When col is a factor vector assign uniformly value between 0 and 1 to each level.
#'
#' @param col vector
#' @return vector with values between 0 and 1
.uniformize <- function(col) {
  if (is.factor(col)) {
    return(.min_max(as.numeric(col) / nlevels(col)))
  }
  if (is.character(col) || all(is.na(col))) {
    if (is.character(col) && !all(is.na(col))) {
      fct <- as.factor(col)
      return(.min_max(as.numeric(fct) / nlevels(fct)))
    }
    return(rep_len(0, length(col)))
  }
  res <- stats::ecdf(col)(col)
  res[is.na(res)] <- 0
  return(res)
}

.render_df_to_html <- function(df) {
  if (requireNamespace("DT", quietly = TRUE)) {
    DT::datatable(df, width = "940px", options = list(scrollX = TRUE))
  } else {
    stop("Can't render a table. Please install DT.")
  }
}

.render_df_to_jupyter <- function(df) {
  IRdisplay::display(as.data.frame(df))
}

.render_df_to_md <- function(df) {
  cat("\n")
  cat("| ", names(df), sep = " | ")
  cat("\n")
  cat("", rep(":---:", ncol(df) + 1), "", sep = "|")
  cat("\n")
  for (row in seq_len(nrow(df))) {
    cat("| **", row.names(df)[row], "** |", sep = "")
    cat(unlist(df[row,]), "", sep = " | ")
    cat("\n")
  }
}

.plotlyfy <- function(gg) {
  gp <- plotly::ggplotly(gg, width = 940, height = 700, tooltip = "text")

  # Hack to fix plotly legend - without this hack some legends are shown in
  # a format of "(actual legend, 1)"
  for (i in seq_len(length(gp$x$data))) {
    if (!is.null(gp$x$data[[i]]$name))
      gp$x$data[[i]]$name <- gsub("^\\((.*)\\s*,\\s*\\d+(?:,\\s*NA\\s*)?\\)$",
                                  "\\1", gp$x$data[[i]]$name)
  }
  return(gp)
}

.render <- function(object, render) {
  if (all(class(object) == "H2OExplanation" | class(object) == "list")) {
    return(lapply(object, .render, render = render))
  } else {
    if (render == "interactive" && any(class(object) == "gg")) {
      on.exit({
        input <- readline("Hit <Return> to continue, to quit press \"q\": ")
        if (tolower(input) == "q") stop("Aborted by user.")
      })
    }
    if (render == "html") {
      switch(
        class(object)[[1]],
        data.frame = .render_df_to_html(object),
        H2OFrame = .render_df_to_html(as.data.frame(object)),
        H2OTable = .render_df_to_html(as.data.frame(object)),
        H2OExplanationHeader = htmltools::tags$h1(object),
        H2OExplanationSubsection = htmltools::tags$h2(object),
        H2OExplanationDescription = htmltools::tags$blockquote(object),
        gg = .plotlyfy(object),
        character = cat(object, "\n")
      )
    } else if (render == "markdown") {
      switch(
        class(object)[[1]],
        data.frame = .render_df_to_md(object),
        H2OFrame = .render_df_to_md(as.data.frame(object)),
        H2OTable = .render_df_to_md(as.data.frame(object)),
        H2OExplanationHeader = cat("\n\n", object, "\n", strrep("=", nchar(object)), "\n", sep = ""),
        H2OExplanationSubsection = cat("\n\n", object, "\n", strrep("-", nchar(object)), "\n", sep = ""),
        H2OExplanationDescription = cat("\n> ", paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n> "), "\n\n", sep = ""),
        character = cat(object, "\n"),
        print(object)
      )
    } else if (render == "notebook") {
      if (.is_using_jupyter()) {
        switch(
          class(object)[[1]],
          data.frame = .render_df_to_jupyter(object),
          H2OFrame = .render_df_to_jupyter(object),
          H2OTable = .render_df_to_jupyter(object),
          H2OExplanationHeader = IRdisplay::display_html(sprintf("<h1>%s</h1>\n", object)),
          H2OExplanationSubsection = IRdisplay::display_html(sprintf("<h2>%s</h2>\n", object)),
          H2OExplanationDescription = IRdisplay::display_html(
            sprintf(
              "<blockquote>%s</blockquote>\n",
              paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n ")
            )
          ),
          gg = {
            # necessary hack to force the plots being shown in order
            file <- tempfile()
            ggplot2::ggsave(file, object,
                            device = "png", units = "in",
                            width = options()$repr.plot.width,
                            height = options()$repr.plot.height
            )
            tryCatch(
            {
              IRdisplay::display_png(file = file)
            },
              finally = {
                unlink(file)
              }
            )
          },
          IRdisplay::display(object)
        )
      } else {
        switch(
          class(object)[[1]],
          H2OExplanationHeader = cat("\n\n", object, "\n", strrep("=", nchar(object)), "\n", sep = ""),
          H2OExplanationSubsection = cat("\n\n", object, "\n", strrep("-", nchar(object)), "\n", sep = ""),
          H2OExplanationDescription = cat("\n> ", paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n> "), "\n\n", sep = ""),
          character = cat(object, "\n"),
          print(object)
        )
      }
    } else if (render == "interactive") {
      switch(
        class(object)[[1]],
        H2OExplanationHeader = cat("\n\n", object, "\n", strrep("=", nchar(object)), "\n", sep = ""),
        H2OExplanationSubsection = cat("\n\n", object, "\n", strrep("-", nchar(object)), "\n", sep = ""),
        H2OExplanationDescription = message(paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n")),
        character = cat(object, "\n"),
        print(object)
      )
    } else {
      stop("Unknown render type \"", render, "\". Chose one of: html, markdown, notebook, interactive.")
    }
  }
}

#' Check if we are plotting in to r notebook.
#' @return boolean
.is_plotting_to_rnotebook <- function() {
  grDevices::graphics.off()
  # dev.capabilities()$locator is T when chunk output is set to the console
  .Platform$GUI == "RStudio" && !grDevices::dev.capabilities()$locator
}

.is_using_jupyter <- function() {
  !is.null(options()$jupyter.rich_display) &&
    options()$jupyter.rich_display &&
    requireNamespace("IRdisplay", quietly = TRUE)
}

.h2o_explanation_header <- function(string, subsection = FALSE) {
  class(string) <- if (subsection) "H2OExplanationSubsection" else "H2OExplanationHeader"
  string <- list(string)
  return(string)
}

.h2o_explanation_description <- function(string) {
  class(string) <- "H2OExplanationDescription"
  string <- list(string)
  return(string)
}

.describe <- function(explanation) {
  .h2o_explanation_description(
    switch(explanation,
           leaderboard = paste0("Leaderboard shows models with their metrics. When provided with H2OAutoML object, ",
                                "the leaderboard shows 5-fold cross-validated metrics by default (depending on the ",
                                "H2OAutoML settings), otherwise it shows metrics computed on the newdata. ",
                                "At most 20 models are shown by default."),
           leaderboard_row = paste0("Leaderboard shows models with their metrics and their predictions for a given row. ",
                                    "When provided with H2OAutoML object, the leaderboard shows 5-fold cross-validated ",
                                    "metrics by default (depending on the H2OAutoML settings), otherwise it shows ",
                                    "metrics computed on the newdata. ", "At most 20 models are shown by default."),
           confusion_matrix = "Confusion matrix shows a predicted class vs an actual class.",
           residual_analysis = paste0("Residual Analysis plots the fitted values vs residuals on a test dataset. ",
                                      "Ideally, residuals should be randomly distributed. Patterns in this plot can ",
                                      "indicate potential problems with the model selection, e.g., using simpler ",
                                      "model than necessary, not accounting for heteroscedasticity, autocorrelation, ",
                                      "etc. Note that if you see \"striped\" lines of residuals, that is an artifact ",
                                      "of having an integer valued (vs a real valued) response variable."),
           variable_importance = paste0("The variable importance plot shows the relative importance of the most ",
                                        "important variables in the model."),
           varimp_heatmap = paste0("Variable importance heatmap shows variable importance across multiple models. ",
                                   "Some models in H2O return variable importance for one-hot (binary indicator) ",
                                   "encoded versions of categorical columns (e.g. Deep Learning, XGBoost). In order ",
                                   "for the variable importance of categorical columns to be compared across all ",
                                   "model types we compute a summarization of the the variable importance across ",
                                   "all one-hot encoded features and return a single variable importance for the ",
                                   "original categorical feature. By default, the models and variables are ordered ",
                                   "by their similarity."),
           model_correlation_heatmap = paste0("This plot shows the correlation between the predictions of the models. ",
                                              "For classification, frequency of identical predictions is used. ",
                                              "By default, models are ordered by their similarity (as computed by ",
                                              "hierarchical clustering)."),
           shap_summary = paste0("SHAP summary plot shows the contribution of the features for each instance ",
                                 "(row of data). The sum of the feature contributions and the bias term is ",
                                 "equal to the raw prediction of the model, i.e., prediction before applying ",
                                 "inverse link function."),
           pdp = paste0("Partial dependence plot (PDP) gives a graphical depiction of the marginal effect of ",
                        "a variable on the response. The effect of a variable is measured in change in the mean ",
                        "response. PDP assumes independence between the feature for which is the PDP computed ",
                        "and the rest."),
           ice = paste0("An Individual Conditional Expectation (ICE) plot gives a graphical depiction of the marginal ",
                        "effect of a variable on the response. ICE plots are similar to partial dependence plots ",
                        "(PDP); PDP shows the average effect of a feature while ICE plot shows the effect for a ",
                        "single instance. This function will plot the effect for each decile. In contrast to the PDP, ",
                        "ICE plots can provide more insight, especially when there is stronger feature interaction."),
           ice_row = paste0("Individual conditional expectations (ICE) plot gives a graphical depiction of the marginal ",
                            "effect of a variable on the response for a given row. ICE plot is similar to partial ",
                            "dependence plot (PDP), PDP shows the average effect of a feature while ICE plot shows ",
                            "the effect for a single instance."),
           shap_explain_row = paste0("SHAP explanation shows contribution of features for a given instance. The sum ",
                                     "of the feature contributions and the bias term is equal to the raw prediction ",
                                     "of the model, i.e., prediction before applying inverse link function. H2O ",
                                     "implements TreeSHAP which when the features are correlated, can increase ",
                                     "contribution of a feature that had no influence on the prediction."),
           stop("Unknown model explanation \"", explanation, "\".")
    )
  )
}

print.H2OExplanation <- function(object, ..., render = "AUTO") {
  if (render == "html") {
    if (!(requireNamespace("htmltools", quietly = TRUE) &&
      requireNamespace("plotly", quietly = TRUE) &&
      requireNamespace("DT", quietly = TRUE))) {
      warning("The following packgages are required to render an explanation to HTML:",
              " htmltools, plotly, DT",
              call. = FALSE
      )
      render <- "AUTO"
    }
  }
  if (render == "AUTO") {
    if (!is.null(options()$knitr.in.progress) && options()$knitr.in.progress) {
      render <- "markdown"
    } else if (.is_plotting_to_rnotebook()) {
      render <- "notebook"
    } else if (interactive()) {
      render <- "interactive"
    } else if (.is_using_jupyter()) {
      if (requireNamespace("htmltools", quietly = TRUE) &&
        requireNamespace("plotly", quietly = TRUE) &&
        requireNamespace("DT", quietly = TRUE)) {
        render <- "html"
      } else {
        message("For Jupyter installing plotly, htmltools, and DT is recommended.")
        render <- "notebook"
      }
    } else {
      render <- "notebook"
    }
  }

  if (render == "html") {
    result <- htmltools::browsable(htmltools::tagList(.render(object, render = render)))
    if (.is_plotting_to_rnotebook()) {
      return(invisible(print(result)))
    }
    return(result)
  } else {
    invisible(tryCatch(.render(object, render = render), error = function(e) message(e$message)))
  }
}

# For Jupyter(IRkernel)
repr_text.H2OExplanation <- function(object, ...) {
  return("Use print(explanation, render = \"notebook\")")
}

repr_html.H2OExplanation <- function(object, ...) {
  if (requireNamespace("htmltools", quietly = TRUE) &&
    requireNamespace("plotly", quietly = TRUE) &&
    requireNamespace("DT", quietly = TRUE)) {
    repr::repr_html(htmltools::browsable(htmltools::tagList(.render(object, render = "html"))))
  } else {
    print(object, render = "notebook")
    return(" ")
  }
}

########################################## GGPLOT2 #################################################
position_jitter_density <-
  function(width = 0.4, bins = 100, kernel = "gaussian", bandwidth = 1) {
    # PositionJitterDensity has to be defined in the function otherwise
    # R Check as CRAN fails on missing ggplot2
    # Inspired by ggbeeswarm's position_quasirandom
    PositionJitterDensity <- ggplot2::ggproto(
      "PositionJitterDensity",
      ggplot2::Position,
      required_aes = c("x", "y"),
      setup_params = function(self, data) {
        list(
          width = self$width,
          bins = self$bins,
          kernel = self$kernel,
          bandwidth = self$bandwidth
        )
      },
      compute_panel = function(data, params, scales) {
        data <-
          ggplot2::remove_missing(data, vars = c("x", "y"), name = "position_jitter_density")
        if (nrow(data) == 0) {
          return(data.frame())
        }

        if (is.null(params$width)) {
          params$width <-
            ggplot2::resolution(data[, "x"], zero = FALSE) * 0.4
        }

        trans_x <- NULL
        trans_y <- NULL

        trans_xy <- function(X) {

          trans_single_group <- function(df) {
            d <- stats::density(
              df[, "y"],
              adjust = params$bandwidth,
              kernel = params$kernel,
              n = params$bins
            )
            d$y <- d$y / max(d$y)
            point_densities <- stats::approx(d$x, d$y, df[, "y"])$y
            noise <- runif(nrow(df), min = -params$width, max = params$width)
            return(point_densities * noise)
          }

          return(X + unsplit(lapply(split(data, data[, "x"]), FUN = trans_single_group), data[, "x"]))
        }

        if (params$width > 0) {
          trans_x <- trans_xy
        }
        return(ggplot2::transform_position(data, trans_x, trans_y))
      }
    )
    ggplot2::ggproto(
      NULL,
      PositionJitterDensity,
      width = width,
      bins = bins,
      kernel = kernel,
      bandwidth = bandwidth
    )
  }


geom_point_or_line <- function(draw_point, ...) {
  if (draw_point) {
    ggplot2::geom_point(..., size = 2)
  } else {
    ggplot2::geom_line(...)
  }
}

geom_pointrange_or_ribbon <- function(draw_point, ...) {
  if (draw_point) {
    ggplot2::geom_pointrange(..., alpha = 0.8)
  } else {
    ggplot2::geom_ribbon(..., alpha = 0.2)
  }
}

stat_count_or_bin <- function(use_count, ..., data) {
  stopifnot("Expecting data frame with just one column." = ncol(data) == 1)
  data <- data[is.finite(data[[1]]), , drop = FALSE]
  if (use_count) {
    ggplot2::stat_count(..., data = data)
  } else {
    # PDP uses 20 bins by default
    ggplot2::stat_bin(..., bins = 20, data = data)
  }
}


########################################## Explanation PLOTS ###############################################

#' SHAP Summary Plot
#'
#' SHAP summary plot shows the contribution of the features for each instance (row of data). 
#' The sum of the feature contributions and the bias term is equal to the raw prediction
#' of the model, i.e., prediction before applying inverse link function.
#'
#' @param model An H2O tree-based model. This includes Random Forest, GBM and XGboost
#'              only. Must be a binary classification or regression model.
#' @param newdata An H2O Frame, used to determine feature contributions.
#' @param columns List of columns or list of indices of columns to show. 
#'                If specified, then the \code{top_n_features} parameter will be ignored.
#' @param top_n_features Integer specifying the maximum number of columns to show (ranked by variable importance).
#' @param sample_size Integer specifying the maximum number of observations to be plotted.
#'
#' @return A ggplot2 object
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' gbm <- h2o.gbm(y = response,
#'                training_frame = train)
#'
#' # Create the SHAP summary plot
#' shap_summary_plot <- h2o.shap_summary_plot(gbm, test)
#' print(shap_summary_plot)
#' }
#' @export
h2o.shap_summary_plot <-
  function(model,
           newdata,
           columns = NULL,
           top_n_features = 20,
           sample_size = 1000) {
    # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
    .data <- NULL
    if (!.is_h2o_model(model) || !.is_h2o_tree_model(model)) {
      stop("SHAP summary plot requires a tree-based model!")
    }
    if (!missing(columns) && !missing(top_n_features)) {
      warning("Parameters columns, and top_n_features are mutually exclusive. Parameter top_n_features will be ignored.")
    }
    if (!(is.null(columns) ||
      is.character(columns) ||
      is.numeric(columns))) {
      stop("Parameter columns must be either a character or numeric vector or NULL.")
    }
    x <- model@allparameters$x

    title <- sprintf("Summary Plot\nfor \"%s\"", model@model_id)
    indices <- row.names(newdata)
    if (nrow(newdata) > sample_size) {
      indices <- sort(sample(seq_len(nrow(newdata)), sample_size))
      newdata <- newdata[indices,]
    }

    with_no_h2o_progress({
      newdata_df <- as.data.frame(newdata)

      contributions <- as.data.frame(h2o.predict_contributions(model, newdata))
      contributions_names <- names(contributions)

      encode_cols <- !all(contributions_names[contributions_names != "BiasTerm"] %in% names(newdata_df))

      for (fct in names(newdata[, x])[is.factor(newdata[, x])]) {
        newdata_df[[fct]] <- as.factor(newdata_df[[fct]])
        if (encode_cols) {
          # encode categoricals, e.g., for xgboost
          for (cat in c(NA, h2o.levels(newdata[[fct]]))) {
            cat_name <- cat
            if (is.na(cat_name)) {
              cat_name <- "missing.NA."
              newdata_df[[paste(fct, cat_name, sep = ".")]] <-
                as.numeric(is.na(newdata_df[[fct]]))
            } else {
              cat_name <- gsub("[^0-9a-zA-Z]", ".", cat_name)
              newdata_df[[paste(fct, cat_name, sep = ".")]] <-
                as.numeric(newdata_df[[fct]] == cat)
            }
          }
        }
      }

    })
    contr <-
      stats::reshape(
        contributions,
        direction = "long",
        varying = contributions_names,
        v.names = "contribution",
        times = contributions_names,
        timevar = "feature"
      )

    values <- stats::reshape(
      data.frame(apply(newdata_df, 2, .uniformize)),
      direction = "long",
      varying = names(newdata_df),
      v.names = "normalized_value",
      times = names(newdata_df),
      timevar = "feature"
    )
    values[["original_value"]] <- stats::reshape(
      data.frame(apply(newdata_df, 2, as.character)),
      direction = "long",
      varying = names(newdata_df),
      v.names = "original_value",
      times = names(newdata_df),
      timevar = "feature"
    )$original_value


    contr <- merge(
      contr,
      values,
      by = c("row.names", "feature")
    )

    contr[["row_index"]] <- indices[as.numeric(sapply(
      strsplit(contr[["Row.names"]], ".", fixed = TRUE),
      function(row) row[[1]]
    ))]


    contr[["feature"]] <- factor(contr[["feature"]],
                                 levels = names(sort(sapply(contributions, function(x) mean(abs(x)))))
    )

    contr[["row"]] <- paste0(
      "Feature: ", contr[["feature"]], "\n",
      "Feature Value: ", contr[["original_value"]], "\n",
      "Row Index: ", contr[["row_index"]], "\n",
      "Contribution: ", contr[["contribution"]]
    )

    features <- levels(contr[["feature"]])
    if (is.null(columns)) {
      if (length(features) > top_n_features) {
        features <- features[seq(from = length(features), to = length(features) - top_n_features)]
        contr <- contr[contr[["feature"]] %in% features,]
      }
    } else {
      encoded_columns <- features
      features <- c()
      for (col in columns) {
        if (is.numeric(col))
          col <- names(newdata)[[col]]
        possible_column_name <- c(col, make.names(col), make.names(gsub(" ", "", col)))
        found <- FALSE
        for (pcol in possible_column_name) {
          if (pcol %in% encoded_columns) {
            features <- c(features, pcol)
            found <- TRUE
            break
          }
        }
        if (!found) {  # possibly contains a category name as well
          for (pcol in possible_column_name) {
            prefix_matches <- startsWith(encoded_columns, paste0(pcol, "."))
            if (any(prefix_matches)) {
              features <- c(features, encoded_columns[prefix_matches])
              break
            }
          }
        }
      }
      contr <- contr[contr[["feature"]] %in% features,]
    }

    p <-
      ggplot2::ggplot(ggplot2::aes(.data$feature, .data$contribution,
                                   color = .data$normalized_value,
                                   text = .data$row
      ),
                      data = contr[sample(nrow(contr)),]
      ) +
        ggplot2::geom_hline(yintercept = 0, linetype = "dashed") +
        ggplot2::geom_point(position = position_jitter_density(), alpha = 0.5) +
        ggplot2::scale_color_gradient(low = "#00AAEE", high = "#FF1166") +
        ggplot2::coord_flip() +
        ggplot2::labs(title = title, y = "SHAP Contribution", x = "Feature") +
        ggplot2::theme_bw() +
        ggplot2::theme(plot.title = ggplot2::element_text(hjust = 0.5))

    return(p)
  }

#' SHAP Local Explanation
#'
#' SHAP explanation shows contribution of features for a given instance. The sum
#' of the feature contributions and the bias term is equal to the raw prediction
#' of the model, i.e., prediction before applying inverse link function. H2O implements
#' TreeSHAP which when the features are correlated, can increase contribution of a feature
#' that had no influence on the prediction.
#'
#' @param model An H2O tree-based model. This includes Random Forest, GBM and XGboost
#'              only. Must be a binary classification or regression model.
#' @param newdata An H2O Frame, used to determine feature contributions.
#' @param row_index Instance row index.
#' @param columns List of columns or list of indices of columns to show. 
#'                If specified, then the \code{top_n_features} parameter will be ignored.
#' @param top_n_features Integer specifying the maximum number of columns to show (ranked by their contributions).
#'        When \code{plot_type = "barplot"}, then \code{top_n_features} features will be chosen
#'        for each contribution_type.
#' @param plot_type Either "barplot" or "breakdown".  Defaults to "barplot".
#' @param contribution_type When \code{plot_type == "barplot"}, plot one of "negative", 
#'                          "positive", or "both" contributions.  Defaults to "both".
#' @return A ggplot2 object.
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' gbm <- h2o.gbm(y = response,
#'                training_frame = train)
#'
#' # Create the SHAP row explanation plot
#' shap_explain_row_plot <- h2o.shap_explain_row_plot(gbm, test, row_index = 1)
#' print(shap_explain_row_plot)
#' }
#' @export
h2o.shap_explain_row_plot <-
  function(model, newdata, row_index, columns = NULL, top_n_features = 10,
           plot_type = c("barplot", "breakdown"),
           contribution_type = c("both", "positive", "negative")) {
    # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
    .data <- NULL
    if (!.is_h2o_model(model) || !.is_h2o_tree_model(model)) {
      stop("SHAP explain_row plot requires a tree-based model!")
    }

    if (!missing(columns) && !missing(top_n_features)) {
      warning("Parameters columns, and top_n_features are mutually exclusive. Parameter top_n_features will be ignored.")
    }
    if (!(is.null(columns) ||
      is.character(columns) ||
      is.numeric(columns))) {
      stop("Parameter columns must be either a character or numeric vector or NULL.")
    }

    plot_type <- match.arg(plot_type)
    if (plot_type == "barplot") {
      contribution_type <- match.arg(contribution_type)
      if (contribution_type == "both")
        contribution_type <- c("positive", "negative")
    }

    x <- model@allparameters$x

    with_no_h2o_progress({
      contributions <-
        as.data.frame(h2o.predict_contributions(model, newdata[row_index,]))
      contributions_names <- names(contributions)
      prediction <- as.data.frame(h2o.predict(model, newdata[row_index,]))
      newdata_df <- as.data.frame(newdata[row_index,])
      encode_cols <- !all(contributions_names[contributions_names != "BiasTerm"] %in% names(newdata_df))

      for (fct in names(newdata[, x])[is.factor(newdata[, x])]) {
        newdata_df[[fct]] <- as.factor(newdata_df[[fct]])
        if (encode_cols) {
          # encode categoricals, e.g., for xgboost
          for (cat in c(NA, h2o.levels(newdata[[fct]]))) {
            cat_name <- cat
            if (is.na(cat_name)) {
              cat_name <- "missing.NA."
              newdata_df[[paste(fct, cat_name, sep = ".")]] <-
                as.numeric(is.na(newdata_df[[fct]]))
            } else {
              cat_name <- gsub("[^0-9a-zA-Z]", ".", cat_name)
              newdata_df[[paste(fct, cat_name, sep = ".")]] <-
                as.numeric(newdata_df[[fct]] == cat)
            }
          }
        }
      }
    })

    if (plot_type == "barplot") {
      contributions <- contributions[, names(contributions) != "BiasTerm"]

      ordered_features <- contributions[order(contributions)]
      features <- character()
      if (is.null(columns)) {
        if ("positive" %in% contribution_type) {
          positive_features <- names(ordered_features)[ordered_features > 0]
          features <- c(features, tail(positive_features, n = min(
            top_n_features,
            length(positive_features)
          )))
        }
        if ("negative" %in% contribution_type) {
          negative_features <- names(ordered_features)[ordered_features < 0]
          features <- c(features, head(negative_features, n = min(
            top_n_features,
            length(negative_features)
          )))
        }
      } else {
        encoded_columns <- names(ordered_features)
        features <- c()
        for (col in columns) {
          if (is.numeric(col))
            col <- names(newdata)[[col]]
          possible_column_name <- c(col, make.names(col), make.names(gsub(" ", "", col)))
          found <- FALSE
          for (pcol in possible_column_name) {
            if (pcol %in% encoded_columns) {
              features <- c(features, pcol)
              found <- TRUE
              break
            }
          }
          if (!found) {  # possibly contains a category name as well
            for (pcol in possible_column_name) {
              prefix_matches <- startsWith(encoded_columns, paste0(pcol, "."))
              if (any(prefix_matches)) {
                features <- c(features, encoded_columns[prefix_matches])
                break
              }
            }
          }
        }
      }

      if (length(features) == 0) {
        stop("No feature contributions to show.", if (length(contribution_type) < 2) {
          "Changing contribution_type to c(\"positive\", \"negative\") might help."
        } else {
          ""
        })
      }
      contributions <- contributions[, features, drop = FALSE]
      contributions <- data.frame(contribution = t(contributions))
      contributions$feature <- paste0(
        row.names(contributions), "=",
        sapply(newdata_df[, row.names(contributions)], as.character)
      )
      contributions <- contributions[order(contributions$contribution),]
      contributions$text <- paste(
        "Feature:", row.names(contributions), "\n",
        "Feature Value:", unlist(sapply(newdata_df[, row.names(contributions)], as.character)), "\n",
        "Contribution:", contributions$contribution
      )

      p <- ggplot2::ggplot(ggplot2::aes(.data$feature, .data$contribution, text = .data$text),
                           data = contributions
      ) +
        ggplot2::geom_col(fill = "#b3ddf2") +
        ggplot2::coord_flip() +
        ggplot2::scale_x_discrete(limits = contributions$feature) +
        ggplot2::labs(
          y = "SHAP Contribution", x = "Feature",
          title = sprintf(
            "SHAP explanation\nfor \"%s\" on row %d\nprediction: %s",
            model@model_id, row_index, as.character(prediction$predict)
          )
        ) +
        ggplot2::theme_bw() +
        ggplot2::theme(plot.margin = ggplot2::margin(16.5, 5.5, 5.5, 5.5),
                       plot.title = ggplot2::element_text(hjust = 0.5))
      return(p)
    } else if (plot_type == "breakdown") {
      contributions <- contributions[, names(contributions)[order(abs(t(contributions)))]]
      bias_term <- contributions$BiasTerm
      contributions <- contributions[, names(contributions) != "BiasTerm"]
      if (is.null(columns)) {
        if (ncol(contributions) > top_n_features) {
          rest <- rowSums(contributions[, names(contributions)[seq(from = 1, to = ncol(contributions) - top_n_features, by = 1)]])
          contributions <- contributions[, tail(names(contributions), n = top_n_features), drop = FALSE]
          contributions[["rest_of_the_features"]] <- rest
        }
      } else {
        encoded_columns <- names(contributions)
        features <- c()
        for (col in columns) {
          if (is.numeric(col))
            col <- names(newdata)[[col]]
          possible_column_name <- c(col, make.names(col), make.names(gsub(" ", "", col)))
          found <- FALSE
          for (pcol in possible_column_name) {
            if (pcol %in% encoded_columns) {
              features <- c(features, pcol)
              found <- TRUE
              break
            }
          }
          if (!found) {  # possible contains a category name as well
            for (pcol in possible_column_name) {
              prefix_matches <- startsWith(encoded_columns, paste0(pcol, "."))
              if (any(prefix_matches)) {
                features <- c(features, encoded_columns[prefix_matches])
                break
              }
            }
          }
        }
        rest <- rowSums(contributions[, !names(contributions) %in% features])
        contributions <- contributions[, features, drop = FALSE]
        contributions[["rest_of_the_features"]] <- rest
      }

      contributions <- t(contributions)
      contributions <- as.matrix(contributions[order(contributions[, 1]),])
      contributions <- data.frame(
        id = seq_along(contributions),
        id_next = c(seq_len(length(contributions) - 1) + 1, length(contributions)),
        feature = row.names(contributions),
        start = c(0, head(cumsum(contributions[, 1]), n = -1)) + bias_term,
        end = cumsum(contributions[, 1]) + bias_term,
        color = contributions[, 1] > 0
      )

      newdata_df[["rest_of_the_features"]] <- NA
      contributions$feature_value <- paste("Feature Value:", as.character(t(newdata_df)[contributions$feature,]))
      p <- ggplot2::ggplot(ggplot2::aes(
        x = .data$feature, fill = .data$color,
        xmin = .data$id - 0.4, xmax = .data$id + 0.4,
        ymin = .data$start, ymax = .data$end,
        text = .data$feature_value
      ),
                           data = contributions
      ) +
        ggplot2::geom_hline(ggplot2::aes(linetype = "Bias Term", yintercept = bias_term)) +
        ggplot2::geom_rect(show.legend = FALSE) +
        ggplot2::geom_segment(ggplot2::aes(
          x = .data$id - 0.4, xend = .data$id_next + 0.4,
          y = .data$end, yend = .data$end
        )) +
        ggplot2::geom_hline(ggplot2::aes(
          linetype = "Prediction",
          yintercept = tail(contributions[, "end"], n = 1)
        )) +
        ggplot2::coord_flip() +
        ggplot2::scale_fill_manual(values = c("firebrick2", "springgreen3")) +
        ggplot2::scale_x_discrete(limits = contributions$feature) +
        ggplot2::xlab("Feature") +
        ggplot2::ylab("SHAP value") +
        ggplot2::ggtitle(sprintf("SHAP Explanation\nfor \"%s\" on row %d", model@model_id, row_index)) +
        ggplot2::theme_bw() +
        ggplot2::theme(legend.title = ggplot2::element_blank(), plot.title = ggplot2::element_text(hjust = 0.5))
      return(p)
    } else {
      stop("Unknown plot_type=", plot_type)
    }
  }


#' Variable Importance Heatmap across multiple models
#'
#' Variable importance heatmap shows variable importance across multiple models.
#' Some models in H2O return variable importance for one-hot (binary indicator) 
#' encoded versions of categorical columns (e.g. Deep Learning, XGBoost).  In order
#' for the variable importance of categorical columns to be compared across all model
#' types we compute a summarization of the the variable importance across all one-hot 
#' encoded features and return a single variable importance for the original categorical
#' feature. By default, the models and variables are ordered by their similarity.
#'
#' @param object An H2OAutoML object or list of H2O models.
#' @param top_n Integer specifying the number models shown in the heatmap 
#'              (based on leaderboard ranking). Defaults to 20.
#'
#' @return A ggplot2 object.
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' aml <- h2o.automl(y = response,
#'                   training_frame = train,
#'                   max_models = 10,
#'                   seed = 1)
#'
#' # Create the variable importance heatmap
#' varimp_heatmap <- h2o.varimp_heatmap(aml)
#' print(varimp_heatmap)
#' }
#' @export
h2o.varimp_heatmap <- function(object,
                               top_n = 20) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  models_info <- .process_models_or_automl(object, NULL,
                                           require_multiple_models = TRUE,
                                           top_n_from_AutoML = top_n, only_with_varimp = TRUE,
                                           require_newdata = FALSE)
  models <- Filter(.has_varimp, models_info$model_ids)
  varimps <- lapply(lapply(models, models_info$get_model), .varimp)
  names(varimps) <- .model_ids(models)

  res <- do.call(rbind, varimps)
  results <- as.data.frame(res)
  ordered <- row.names(results)
  y_ordered <- make.names(names(results))
  if (length(ordered) > 2) {
    ordered <- ordered[stats::hclust(stats::dist(results))$order]
  }
  if (length(y_ordered) > 2) {
    y_ordered <- y_ordered[stats::hclust(stats::dist(t(results)))$order]
  }
  results[["model_id"]] <- row.names(results)
  results <- stats::reshape(results,
                            direction = "long",
                            varying = Filter(function(col) col != "model_id", names(results)),
                            times = Filter(function(col) col != "model_id", names(results)),
                            v.names = "value",
                            timevar = "feature"
  )

  results$text <- paste0(
    "Model Id: ", results$model_id, "\n",
    "Feature: ", results$feature, "\n",
    "Feature Importance: ", results$value
  )

  margin <- ggplot2::margin(5.5, 5.5, 5.5, 5.5, "pt")
  if (max(nchar(.shorten_model_ids(.model_ids(models)))) > 30)
    margin <- ggplot2::margin(1, 1, 1, 7, "lines")
  p <- ggplot2::ggplot(ggplot2::aes(
    x = .shorten_model_ids(.data$model_id), y = .data$feature, fill = .data$value, text = .data$text
  ), data = results) +
    ggplot2::geom_tile() +
    ggplot2::labs(fill = "Variable Importance", x = "Model Id", y = "Feature", title = "Variable Importance") +
    ggplot2::scale_x_discrete(limits = .shorten_model_ids(ordered)) +
    ggplot2::scale_y_discrete(limits = y_ordered) +
    ggplot2::scale_fill_distiller(palette = "RdYlBu") +
    ggplot2::theme_bw() +
    ggplot2::theme(
      axis.text.x = ggplot2::element_text(angle = 45, hjust = 1),
      plot.margin = margin,
      legend.title = ggplot2::element_blank(),
      plot.title = ggplot2::element_text(hjust = 0.5)
    )
  return(p)
}

#' Model Prediction Correlation Heatmap
#'
#' This plot shows the correlation between the predictions of the models.
#' For classification, frequency of identical predictions is used. By default, models
#' are ordered by their similarity (as computed by hierarchical clustering).
#'
#' @param object An H2OAutoML object or list of H2O models.
#' @param newdata An H2O Frame.  Predictions from the models will be generated using this frame, 
#'                so this should be a holdout set.
#' @param top_n Integer specifying the number models shown in the heatmap (used only with an 
#'              AutoML object, and based on the leaderboard ranking.  Defaults to 20.
#' @param cluster_models Logical.  Order models based on their similarity.  Defaults to TRUE.
#' @param triangular Print just the lower triangular part of correlation matrix.  Defaults to TRUE.
#'
#' @return A ggplot2 object.
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' aml <- h2o.automl(y = response,
#'                   training_frame = train,
#'                   max_models = 10,
#'                   seed = 1)
#'
#' # Create the model correlation heatmap
#' model_correlation_heatmap <- h2o.model_correlation_heatmap(aml, test)
#' print(model_correlation_heatmap)
#' }
#' @export
h2o.model_correlation_heatmap <- function(object, newdata, top_n = 20,
                                          cluster_models = TRUE, triangular = TRUE) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  models_info <- .process_models_or_automl(object, newdata, require_multiple_models = TRUE, top_n_from_AutoML = top_n)
  models <- models_info$model_ids
  with_no_h2o_progress({
    preds <-
      sapply(models, function(m, df) {
        m <- models_info$get_model(m)
        list(predict = as.numeric(as.data.frame(stats::predict(m, df)[["predict"]])[["predict"]]))
      }, newdata)
    preds <- as.data.frame(do.call(cbind, preds))
    names(preds) <- unlist(.model_ids(models))
  })

  if (models_info$is_classification) {
    model_ids <- .model_ids(models)
    res <- matrix(0, length(models), length(models),
                  dimnames = list(model_ids, model_ids))
    for (i in seq_along(model_ids)) {
      for (j in seq_along(model_ids)) {
        if (i <= j) {
          res[i, j] <- mean(as.numeric(preds[[model_ids[i]]] == preds[[model_ids[j]]]))
          res[j, i] <- res[i, j]
        }
      }
    }
    res <- as.data.frame(res)
  } else {
    res <- as.data.frame(cor(preds))
  }
  ordered <- names(res)
  if (cluster_models) {
    ordered <- names(res)[stats::hclust(stats::dist(replace(res, is.na(res), 0)))$order]
  }
  res <- res[ordered, ordered]
  varying <- row.names(res)
  if (triangular) {
    res[lower.tri(res)] <- NA
  }

  res[["model_id_1"]] <- row.names(res)

  res <- na.omit(stats::reshape(res,
                                direction = "long", varying = varying,
                                v.names = "value", timevar = "model_id_2", times = varying
  ))

  res$text <- paste0(
    "Model Id: ", res$model_id_1, "\n",
    "Model Id: ", res$model_id_2, "\n",
    "Correlation: ", res$value
  )

  suppressWarnings({
    p <- ggplot2::ggplot(ggplot2::aes(
      x = .shorten_model_ids(.data$model_id_1), y = .shorten_model_ids(.data$model_id_2),
      fill = .data$value, text = .data$text
    ), data = res) +
      ggplot2::geom_tile() +
      ggplot2::labs(fill = "Correlation", x = "Model Id", y = "Model Id") +
      ggplot2::ggtitle("Model Correlation") +
      ggplot2::scale_x_discrete(limits = .shorten_model_ids(ordered)) +
      ggplot2::scale_y_discrete(limits = .shorten_model_ids(rev(ordered))) +
      ggplot2::scale_fill_distiller(limits = c(0.5, 1), palette = "RdYlBu") +
      ggplot2::coord_fixed() +
      (if (triangular) ggplot2::theme_classic() else ggplot2::theme_bw()) +
      ggplot2::theme(
        axis.text.x = ggplot2::element_text(
          angle = 45,
          hjust = 1),
        aspect.ratio = 1,
        legend.title = ggplot2::element_blank(),
        plot.title = ggplot2::element_text(hjust = 0.5)
      )
  })

  return(p)
}

#' Residual Analysis
#'
#' Do Residual Analysis and plot the fitted values vs residuals on a test dataset. 
#' Ideally, residuals should be randomly distributed. Patterns in this plot can indicate 
#' potential problems with the model selection, e.g., using simpler model than necessary, 
#' not accounting for heteroscedasticity, autocorrelation, etc.  If you notice "striped" 
#' lines of residuals, that is just an indication that your response variable was integer 
#' valued instead of real valued.
#'
#' @param model An H2OModel.
#' @param newdata An H2OFrame.  Used to calculate residuals.
#'
#' @return A ggplot2 object
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' gbm <- h2o.gbm(y = response,
#'                training_frame = train)
#'
#' # Create the residual analysis plot
#' residual_analysis_plot <- h2o.residual_analysis_plot(gbm, test)
#' print(residual_analysis_plot)
#' }
#' @export
h2o.residual_analysis_plot <- function(model, newdata) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  if (is.character(model))
    model <- h2o.getModel(model)
  if ("H2OAutoML" %in% class(model) || is.list(model))
    stop("Residual analysis works only on a single model!")
  if (h2o.isfactor(newdata[[model@allparameters$y]]))
    stop("Residual analysis is not implemented for classification.")

  with_no_h2o_progress({
    y <- model@allparameters$y

    predictions <- stats::predict(model, newdata)
    newdata[["residuals"]] <- predictions[["predict"]] - newdata[[y]]
    predictions <- as.data.frame(predictions[["predict"]])
    predictions["residuals"] <- predictions[["predict"]] - as.data.frame(newdata[[y]])[[y]]
    p <- ggplot2::ggplot(ggplot2::aes(.data$predict, .data$residuals), data = predictions) +
      ggplot2::geom_point(alpha = 0.2) +
      ggplot2::geom_smooth(method = "lm", formula = y ~ x) +
      ggplot2::geom_rug(alpha = 0.2) +
      ggplot2::geom_abline(intercept = 0, slope = 0) +
      ggplot2::labs(x = "Fitted", y = "Residuals", title = sprintf("Residual Analysis\nfor \"%s\"", model@model_id)) +
      ggplot2::theme_bw() +
      ggplot2::theme(plot.title = ggplot2::element_text(hjust = 0.5))
  })
  return(p)
}

#' Plot partial dependence for a variable
#' 
#' Partial dependence plot (PDP) gives a graphical depiction of the marginal effect of a variable
#' on the response. The effect of a variable is measured in change in the mean response.
#' PDP assumes independence between the feature for which is the PDP computed and the rest.
#'
#' @param object An H2O model.
#' @param newdata An H2OFrame.  Used to generate predictions used in Partial Dependence calculations.
#' @param column A feature column name to inspect.  Character string.
#' @param target If multinomial, plot PDP just for \code{target} category.  Character string.
#' @param row_index Optional. Calculate Individual Conditional Expectation (ICE) for row, \code{row_index}.  Integer.
#' @param max_levels An integer specifying the maximum number of factor levels to show.
#'                   Defaults to 30.
#'
#' @return A ggplot2 object
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' gbm <- h2o.gbm(y = response,
#'                training_frame = train)
#'
#' # Create the partial dependence plot
#' pdp <- h2o.pd_plot(gbm, test, column = "alcohol")
#' print(pdp)
#' }
#' @export
h2o.pd_plot <- function(object,
                        newdata,
                        column,
                        target = NULL,
                        row_index = NULL,
                        max_levels = 30) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  if (missing(column))
    stop("Column has to be specified!")
  if (!column %in% names(newdata))
    stop("Column was not found in the provided data set!")
  if (is.null(row_index))
    row_index <- -1
  models_info <- .process_models_or_automl(object, newdata, require_single_model = TRUE)
  if (h2o.nlevels(newdata[[column]]) > max_levels) {
    factor_frequencies <- .get_feature_count(newdata[[column]])
    factors_to_merge <- tail(names(factor_frequencies), n = -max_levels)
    newdata[[column]] <- ifelse(newdata[[column]] %in% factors_to_merge, NA_character_,
                                newdata[[column]])
    message(length(factor_frequencies) - max_levels, " least common factor levels were omitted from \"",
            column, "\" feature.")
  }
  margin <- ggplot2::margin(5.5, 5.5, 5.5, 5.5)
  if (h2o.isfactor(newdata[[column]]))
    margin <- ggplot2::margin(5.5, 5.5, 5.5, max(5.5, max(nchar(h2o.levels(newdata[[column]])))))

  targets <- NULL
  if (models_info$is_multinomial_classification) {
    targets <- h2o.levels(newdata[[models_info$y]])
  }
  with_no_h2o_progress({
    pdps <-
      h2o.partialPlot(models_info$get_model(models_info$model_ids[[1]]), newdata, column,
                      plot = FALSE, targets = targets,
                      nbins = if (is.factor(newdata[[column]])) {
                        h2o.nlevels(newdata[[column]]) + 1
                      } else {
                        20
                      },
                      row_index = row_index
      )

    if (!is.null(targets)) {
      for (idx in seq_along(pdps)) {
        pdps[[idx]][["target"]] <- targets[[idx]]
      }
    } else {
      pdps[["target"]] <- "Partial Depencence"
    }
    if (is(pdps, "H2OTable")) {
      pdp <- as.data.frame(pdps)
    } else {
      pdp <- do.call(rbind, lapply(pdps, as.data.frame))
    }
    names(pdp) <- make.names(names(pdp))

    pdp[["text"]] <- paste0(
      "Feature Value: ", pdp[[make.names(column)]], "\n",
      "Mean Response: ", pdp[["mean_response"]], "\n",
      "Target: ", pdp[["target"]]
    )

    col_name <- make.names(column)
    rug_data <- stats::setNames(as.data.frame(newdata[[column]]), col_name)
    rug_data[["text"]] <- paste0("Feature Value: ", rug_data[[col_name]])
    y_range <- c(min(pdp$mean_response - pdp$stddev_response), max(pdp$mean_response + pdp$stddev_response))

    p <- ggplot2::ggplot(ggplot2::aes(
      x = .data[[make.names(column)]],
      y = .data$mean_response,
      color = .data$target, fill = .data$target, text = .data$text
    ), data = pdp) +
      stat_count_or_bin(!is.numeric(newdata[[column]]),
                        ggplot2::aes(x = .data[[col_name]], y = (.data$..count.. / max(.data$..count..)) * diff(y_range) / 1.61),
                        position = ggplot2::position_nudge(y = y_range[[1]] - 0.05 * diff(y_range)), alpha = 0.2,
                        inherit.aes = FALSE, data = as.data.frame(newdata[[column]])) +
      geom_point_or_line(!is.numeric(newdata[[column]]), ggplot2::aes(group = .data$target)) +
      geom_pointrange_or_ribbon(!is.numeric(newdata[[column]]), ggplot2::aes(
        ymin = .data$mean_response - .data$stddev_response,
        ymax = .data$mean_response + .data$stddev_response,
        group = .data$target
      )) +
      ggplot2::geom_rug(ggplot2::aes(x = .data[[col_name]], y = NULL, fill = NULL),
                        sides = "b", alpha = 0.1, color = "black",
                        data = rug_data
      )
    if (row_index > -1) {
      p <- p + ggplot2::geom_vline(xintercept = newdata[row_index, column], linetype = "dashed")
    }
    p <- p +
      ggplot2::labs(
        title = sprintf(
          "%s on \"%s\"%s",
          if (row_index == -1) {
            "Partial Dependence"
          } else {
            sprintf("Individual Conditional Expectation on row %d", row_index)
          },
          column,
          if (!is.null(target)) {
            sprintf(" with target = \"%s\"", target)
          } else {
            ""
          }
        ),
        x = column,
        y = "Mean Response"
      ) +
      ggplot2::scale_color_brewer(type = "qual", palette = "Dark2") +
      ggplot2::scale_fill_brewer(type = "qual", palette = "Dark2") +
      # make the histogram closer to the axis. (0.05 is the default value)
      ggplot2::scale_y_continuous(expand = ggplot2::expansion(mult = c(0, 0.05))) +
      ggplot2::theme_bw() +
      ggplot2::theme(
        legend.title = ggplot2::element_blank(),
        axis.text.x = ggplot2::element_text(angle = if (h2o.isfactor(newdata[[column]])) 45 else 0, hjust = 1),
        plot.margin = margin,
        plot.title = ggplot2::element_text(hjust = 0.5)
      )
    return(p)
  })
}


#' Plot partial dependencies for a variable across multiple models
#' 
#' Partial dependence plot (PDP) gives a graphical depiction of the marginal effect of a variable
#' on the response. The effect of a variable is measured in change in the mean response.
#' PDP assumes independence between the feature for which is the PDP computed and the rest.
#'
#' @param object Either a list of H2O models/model_ids or an H2OAutoML object.
#' @param newdata An H2OFrame.
#' @param column A feature column name to inspect.  Character string.
#' @param best_of_family If TRUE, plot only the best model of each algorithm family; 
#'                       if FALSE, plot all models. Defaults to TRUE.
#' @param target If multinomial, plot PDP just for \code{target} category.
#' @param row_index Optional. Calculate Individual Conditional Expectation (ICE) for row, \code{row_index}.  Integer.
#' @param max_levels An integer specifying the maximum number of factor levels to show.
#'                   Defaults to 30.
#'
#' @return A ggplot2 object
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' aml <- h2o.automl(y = response,
#'                   training_frame = train,
#'                   max_models = 10,
#'                   seed = 1)
#'
#' # Create the partial dependence plot
#' pdp <- h2o.pd_multi_plot(aml, test, column = "alcohol")
#' print(pdp)
#' }
#' @export
h2o.pd_multi_plot <- function(object,
                              newdata,
                              column,
                              best_of_family = TRUE,
                              target = NULL,
                              row_index = NULL,
                              max_levels = 30) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  if (missing(column))
    stop("Column has to be specified!")
  if (!column %in% names(newdata))
    stop("Column was not found in the provided data set!")
  if (is.null(row_index))
    row_index <- -1
  models_info <- .process_models_or_automl(object, newdata, best_of_family = best_of_family)
  if (h2o.nlevels(newdata[[column]]) > max_levels) {
    factor_frequencies <- .get_feature_count(newdata[[column]])
    factors_to_merge <- tail(names(factor_frequencies), n = -max_levels)
    newdata[[column]] <- ifelse(newdata[[column]] %in% factors_to_merge, NA_character_,
                                newdata[[column]])
    message(length(factor_frequencies) - max_levels, " least common factor levels were omitted from \"",
            column, "\" feature.")
  }
  margin <- ggplot2::margin(5.5, 5.5, 5.5, 5.5)
  if (h2o.isfactor(newdata[[column]]))
    margin <- ggplot2::margin(5.5, 5.5, 5.5, max(5.5, max(nchar(h2o.levels(newdata[[column]])))))

  if (length(models_info$model_ids) == 1) {
    targets <- NULL
    if (models_info$is_multinomial_classification) {
      targets <- h2o.levels(newdata[[models_info$y]])
    }
    with_no_h2o_progress({
      pdps <-
        h2o.partialPlot(models_info$get_model(models_info$model_ids[[1]]), newdata, column,
                        plot = FALSE, targets = targets,
                        nbins = if (is.factor(newdata[[column]])) {
                          h2o.nlevels(newdata[[column]]) + 1
                        } else {
                          20
                        },
                        row_index = row_index
        )

      if (!is.null(targets)) {
        for (idx in seq_along(pdps)) {
          pdps[[idx]][["target"]] <- targets[[idx]]
        }
      } else {
        pdps[["target"]] <- "Partial Depencence"
      }
      if (is(pdps, "H2OTable")) {
        pdp <- as.data.frame(pdps)
      } else {
        pdp <- do.call(rbind, lapply(pdps, as.data.frame))
      }
      names(pdp) <- make.names(names(pdp))

      pdp[["text"]] <- paste0(
        "Feature Value: ", pdp[[make.names(column)]], "\n",
        "Mean Response: ", pdp[["mean_response"]], "\n",
        "Target: ", pdp[["target"]]
      )

      col_name <- make.names(column)
      rug_data <- stats::setNames(as.data.frame(newdata[[column]]), col_name)
      rug_data[["text"]] <- paste0("Feature Value: ", rug_data[[col_name]])
      y_range <- c(min(pdp$mean_response - pdp$stddev_response), max(pdp$mean_response + pdp$stddev_response))

      p <- ggplot2::ggplot(ggplot2::aes(
        x = .data[[make.names(column)]],
        y = .data$mean_response,
        color = .data$target, fill = .data$target, text = .data$text
      ), data = pdp) +
        stat_count_or_bin(!is.numeric(newdata[[column]]),
                          ggplot2::aes(x = .data[[col_name]], y = (.data$..count.. / max(.data$..count..)) * diff(y_range) / 1.61),
                          position = ggplot2::position_nudge(y = y_range[[1]] - 0.05 * diff(y_range)), alpha = 0.2,
                          inherit.aes = FALSE, data = as.data.frame(newdata[[column]])) +
        geom_point_or_line(!is.numeric(newdata[[column]]), ggplot2::aes(group = .data$target)) +
        geom_pointrange_or_ribbon(!is.numeric(newdata[[column]]), ggplot2::aes(
          ymin = .data$mean_response - .data$stddev_response,
          ymax = .data$mean_response + .data$stddev_response,
          group = .data$target
        )) +
        ggplot2::geom_rug(ggplot2::aes(x = .data[[col_name]], y = NULL, fill = NULL),
                          sides = "b", alpha = 0.1, color = "black",
                          data = rug_data
        )
      if (row_index > -1) {
        p <- p + ggplot2::geom_vline(xintercept = newdata[row_index, column], linetype = "dashed")
      }
      p <- p +
        ggplot2::labs(
          title = sprintf(
            "%s on \"%s\"%s",
            if (row_index == -1) {
              "Partial Dependence"
            } else {
              sprintf("Individual Conditional Expectation on row %d", row_index)
            },
            column,
            if (!is.null(target)) {
              sprintf(" with target = \"%s\"", target)
            } else {
              ""
            }
          ),
          x = column,
          y = "Mean Response"
        ) +
        ggplot2::scale_color_brewer(type = "qual", palette = "Dark2") +
        ggplot2::scale_fill_brewer(type = "qual", palette = "Dark2") +
        # make the histogram closer to the axis. (0.05 is the default value)
        ggplot2::scale_y_continuous(expand = ggplot2::expansion(mult = c(0, 0.05))) +
        ggplot2::theme_bw() +
        ggplot2::theme(
          legend.title = ggplot2::element_blank(),
          axis.text.x = ggplot2::element_text(angle = if (h2o.isfactor(newdata[[column]])) 45 else 0, hjust = 1),
          plot.margin = margin,
          plot.title = ggplot2::element_text(hjust = 0.5)
        )
      return(p)
    })
  }

  with_no_h2o_progress({
    results <- NULL
    models_to_plot <- models_info$model_ids
    if (best_of_family)
      models_to_plot <- .get_first_of_family(models_to_plot)
    for (model in models_to_plot) {
      pdp <-
        h2o.partialPlot(models_info$get_model(model), newdata, column,
                        plot = FALSE, targets = target,
                        nbins = if (is.factor(newdata[[column]])) {
                          h2o.nlevels(newdata[[column]]) + 1
                        } else {
                          20
                        },
                        row_index = row_index
        )
      if (is.null(results)) {
        results <- pdp[column]
        names(results) <- make.names(names(results))
      }
      results[[model]] <- pdp$mean_response
    }

    data <- stats::reshape(results,
                           direction = "long",
                           varying = names(results)[-1],
                           times = names(results)[-1],
                           v.names = "values",
                           timevar = "model_id"
    )

    col_name <- make.names(column)

    data[["text"]] <- paste0(
      "Model Id: ", data[["model_id"]], "\n",
      "Feature Value: ", data[[col_name]], "\n",
      "Mean Response: ", data[["values"]], "\n"
    )

    rug_data <- stats::setNames(as.data.frame(newdata[[column]]), col_name)
    rug_data[["text"]] <- paste0("Feature Value: ", rug_data[[col_name]])
    y_range <- range(data$values)

    p <- ggplot2::ggplot(ggplot2::aes(
      x = .data[[col_name]],
      y = .data$values,
      color = .shorten_model_ids(.data$model_id),
      text = .data$text),
                         data = data
    ) +
      stat_count_or_bin(!is.numeric(newdata[[column]]),
                        ggplot2::aes(x = .data[[col_name]], y = (.data$..count.. / max(.data$..count..)) * diff(y_range) / 1.61),
                        position = ggplot2::position_nudge(y = y_range[[1]] - 0.05 * diff(y_range)), alpha = 0.2,
                        inherit.aes = FALSE, data = as.data.frame(newdata[[column]])) +
      geom_point_or_line(!is.numeric(newdata[[column]]), ggplot2::aes(group = .shorten_model_ids(.data$model_id))) +
      ggplot2::geom_rug(ggplot2::aes(x = .data[[col_name]], y = NULL),
                        sides = "b", alpha = 0.1, color = "black",
                        data = rug_data
      )
    if (row_index > -1) {
      p <- p + ggplot2::geom_vline(xintercept = newdata[row_index, column], linetype = "dashed")
    }
    p <- p +
      ggplot2::labs(y = "Mean Response", title = sprintf(
        "%s on \"%s\"%s",
        if (row_index == -1) {
          "Partial Dependence"
        } else {
          sprintf("Individual Conditional Expectation on row %d", row_index)
        },
        column,
        if (!is.null(target)) {
          sprintf(" with target = \"%s\"", target)
        } else {
          ""
        }
      )) +
      ggplot2::scale_color_brewer(type = "qual", palette = "Dark2") +
      # make the histogram closer to the axis. (0.05 is the default value)
      ggplot2::scale_y_continuous(expand = ggplot2::expansion(mult = c(0, 0.05))) +
      ggplot2::theme_bw() +
      ggplot2::theme(
        axis.text.x = ggplot2::element_text(
          angle = if (h2o.isfactor(newdata[[column]])) 45 else 0,
          hjust = 1
        ),
        plot.margin = margin,
        legend.title = ggplot2::element_blank(),
        plot.title = ggplot2::element_text(hjust = 0.5)
      )
    return(p)
  })
}

#' Plot Individual Conditional Expectation (ICE) for each decile
#' 
#' Individual Conditional Expectation (ICE) plot gives a graphical depiction of the marginal 
#' effect of a variable on the response. ICE plots are similar to partial dependence plots (PDP); 
#' PDP shows the average effect of a feature while ICE plot shows the effect for a single 
#' instance. This function will plot the effect for each decile. In contrast to the PDP, 
#' ICE plots can provide more insight, especially when there is stronger feature interaction.
#'
#' @param model An H2OModel.
#' @param newdata An H2OFrame.
#' @param column A feature column name to inspect.
#' @param target If multinomial, plot PDP just for \code{target} category.  Character string.
#' @param max_levels An integer specifying the maximum number of factor levels to show.
#'                   Defaults to 30.
#'
#' @return A ggplot2 object
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' gbm <- h2o.gbm(y = response,
#'                training_frame = train)
#'
#' # Create the individual conditional expectations plot
#' ice <- h2o.ice_plot(gbm, test, column = "alcohol")
#' print(ice)
#' }
#' @export
h2o.ice_plot <- function(model,
                         newdata,
                         column,
                         target = NULL,
                         max_levels = 30) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  if (missing(column))
    stop("Column has to be specified!")
  if (!column %in% names(newdata))
    stop("Column was not found in the provided data set!")

  models_info <- .process_models_or_automl(model, newdata, require_single_model = TRUE)

  with_no_h2o_progress({
    if (h2o.nlevels(newdata[[column]]) > max_levels) {
      factor_frequencies <- .get_feature_count(newdata[[column]])
      factors_to_merge <- tail(names(factor_frequencies), n = -max_levels)
      newdata[[column]] <- ifelse(newdata[[column]] %in% factors_to_merge, NA_character_,
                                  newdata[[column]])
      message(length(factor_frequencies) - max_levels, " least common factor levels were omitted from \"",
              column, "\" feature.") }

    margin <- ggplot2::margin(16.5, 5.5, 5.5, 5.5)
    if (h2o.isfactor(newdata[[column]]))
      margin <- ggplot2::margin(16.5, 5.5, 5.5, max(5.5, max(nchar(h2o.levels(newdata[[column]])))))

    quantiles <- order(as.data.frame(newdata[[models_info$y]])[[models_info$y]])
    quantiles <- quantiles[c(1, round((seq_len(11) - 1) * length(quantiles) / 10))]

    results <- data.frame()
    i <- 0
    for (idx in quantiles) {
      tmp <- as.data.frame(h2o.partialPlot(
        models_info$get_model(models_info$model),
        newdata,
        column,
        row_index = as.integer(idx),
        plot = FALSE,
        targets = target,
        nbins = if (is.factor(newdata[[column]])) {
          h2o.nlevels(newdata[[column]]) + 1
        } else {
          20
        }
      ))
      tmp[["name"]] <- sprintf("%dth Percentile", i * 10)
      i <- i + 1
      results <- rbind(results, tmp[, c(column, "name", "mean_response")])
    }
    results[["name"]] <- factor(
      results[["name"]],
      unlist(sapply(seq_len(11), function(i) sprintf("%dth Percentile", (i - 1) * 10)))
    )
    names(results) <- make.names(names(results))
    pdp <-
      as.data.frame(h2o.partialPlot(models_info$get_model(models_info$model),
                                    newdata,
                                    column,
                                    plot = FALSE,
                                    targets = target,
                                    nbins = if (is.factor(newdata[[column]])) {
                                      h2o.nlevels(newdata[[column]]) + 1
                                    } else {
                                      20
                                    }
      ))
    pdp[["name"]] <- "mean response"
    names(pdp) <- make.names(names(pdp))

    col_name <- make.names(column)

    if (is.character(results[[col_name]]) || is.character(pdp[[col_name]])) {
      pdp[[col_name]] <- as.factor(pdp[[col_name]])
      results[[col_name]] <- as.factor(results[[col_name]])
    }

    results[["text"]] <- paste0(
      "Percentile: ", results[["name"]], "\n",
      "Feature Value: ", results[[col_name]], "\n",
      "Mean Response: ", results[["mean_response"]], "\n"
    )
    pdp[["text"]] <- paste0(
      "Partial Depencence \n",
      "Feature Value: ", pdp[[col_name]], "\n",
      "Mean Response: ", pdp[["mean_response"]], "\n"
    )
    y_range <- range(results$mean_response)

    p <- ggplot2::ggplot(ggplot2::aes(x = .data[[col_name]],
                                      y = .data$mean_response,
                                      color = .data$name,
                                      text = .data$text),
                         data = results) +
      stat_count_or_bin(!is.numeric(newdata[[column]]),
                        ggplot2::aes(x = .data[[col_name]], y = (.data$..count.. / max(.data$..count..)) * diff(y_range) / 1.61),
                        position = ggplot2::position_nudge(y = y_range[[1]] - 0.05 * diff(y_range)), alpha = 0.2,
                        inherit.aes = FALSE, data = as.data.frame(newdata[[column]])) +
      geom_point_or_line(!is.numeric(newdata[[column]]), ggplot2::aes(group = .data$name)) +
      geom_point_or_line(!is.numeric(newdata[[column]]),
                         if (is.factor(pdp[[col_name]])) {
                           ggplot2::aes(shape = "Partial Dependence", group = "Partial Dependence")
                         } else {
                           ggplot2::aes(linetype = "Partial Dependence", group = "Partial Dependence")
                         },
                         data = pdp, color = "black"
      ) +
      ggplot2::geom_rug(ggplot2::aes(x = .data[[col_name]], y = NULL, text = NULL),
                        sides = "b", alpha = 0.1, color = "black",
                        data = stats::setNames(as.data.frame(newdata[[column]]), col_name)
      ) +
      ggplot2::labs(y = "Response", title = sprintf(
        "Individual Conditional Expectations on \"%s\"%s\nfor Model: \"%s\"", col_name,
        if (is.null(target)) {
          ""
        } else {
          sprintf(" with Target = \"%s\"", target)
        },
        model@model_id
      )) +
      ggplot2::scale_color_viridis_d(option = "plasma") +
      ggplot2::scale_linetype_manual(values = c("Partial Dependence" = "dashed")) +
      # make the histogram closer to the axis. (0.05 is the default value)
      ggplot2::scale_y_continuous(expand = ggplot2::expansion(mult = c(0, 0.05))) +
      ggplot2::theme_bw() +
      ggplot2::theme(
        legend.title = ggplot2::element_blank(),
        axis.text.x = ggplot2::element_text(angle = if (h2o.isfactor(newdata[[col_name]])) 45 else 0, hjust = 1),
        plot.margin = margin,
        plot.title = ggplot2::element_text(hjust = 0.5)
      )
    return(p)
  })
}

#' Generate Learning Curve Plot
#'
#' ...
#'
#' @param model model
#' @param metric Metric to be used for the learning curve plot
#' @export
h2o.learning_curve_plot <- function(model,
                                    metric = c("AUTO", "convergence", "deviance", "logloss",
                                               "mse", "rmse", "mae", "rmsle",
                                               "auc", "pr_auc", "lift_top_group",
                                               "classification_error", "lift",
                                               "mean_per_class_error", "sumetaieta02",
                                               "negative_log_likelihood"),
                                    cv_ribbon = TRUE,
                                    cv_individual_lines = TRUE
                                    ) {
  metric <- match.arg(metric)

  if ("stackedensemble" == model@algorithm)
    model <- model@model$metalearner_model

  allowed_metrics <- c()
  allowed_timesteps <- c()
  sh <- model@model$scoring_history

  if (model@algorithm == "glm") {
    hglm <- model@parameters$HGLM
    if (model@allparameters$lambda_search) {
      allowed_metrics <- "deviance"
      allowed_timesteps <- "iteration"
      #FIXME: Uncomment me after https://github.com/h2oai/h2o-3/pull/5069 is merged
      #sh <- sh[sh["alpha"] == model@model$alpha_best,]
    } else if (!is.null(hglm) && hglm) {
      allowed_metrics <- c("convergence", "sumetaieta02")
      allowed_timesteps <- "iterations"
    } else {
      allowed_metrics <- c("objective", "negative_log_likelihood")
      allowed_timesteps <- "iterations"
    }
  } else if (model@algorithm == "glrm") {
    allowed_metrics <- c("objective", "step_size")
    allowed_timesteps <- "iteration"
  } else if (model@algorithm %in% c("deeplearning", "drf", "gbm")) {
    if (is(model, "H2OBinomialModel")) {
      allowed_metrics <- c("logloss", "auc", "classification_error", "rmse")
    } else if (is(model, "H2OMultinomialModel") || is(x, "H2OOrdinalModel")) {
        allowed_metrics <- c("classification_error", "logloss", "rmse")
    } else if (is(model, "H2ORegressionModel")) {
        allowed_metrics <- c("rmse","deviance","mae")
    }
  } else if (model@algorithm == "xgboost") {
    allowed_timesteps <- "number_of_trees"
    allowed_metrics <- c("logloss", "rmse", "auc", "pr_auc", "lift", "classification_error")
  }

  if (model@algorithm == "deeplearning") {
    allowed_timesteps <- c("epochs", "iterations", "samples")
  } else if (model@algorithm %in% c("drf", "gbm", "xgboost")) {
    allowed_timesteps <- c("number_of_trees")
  }

  if (metric == "AUTO") {
    metric <- allowed_metrics[[1]]
  }

  if (!(metric %in% allowed_metrics)) {
    stop("Metric must be one of: ", paste(allowed_metrics, collapse = ", "))
  }

  timestep <- allowed_timesteps[[1]]

  # FIXME: Inconsistencies in naming, find out what could be used and when
  training_metric <- sprintf(switch(metric,
                                    deviance = "%s_train",
                                    objective = "objective",
                                    convergence = "convergence",
                                    "training_%s"), metric)
  validation_metric <- sprintf(switch(metric,
                                      deviance = "%s_test",
                                      "validation_%s"), metric)

  selected_timestep_value <- switch(timestep,
                                    number_of_trees = model@parameters$ntrees,
                                    iterations = model@model$model_summary$number_of_iterations,
                                    iteration = model@model$model_summary$number_of_iterations,
                                    epochs = model@parameters$epochs
  )

  scoring_history <-
    data.frame(
      model = "Main Model",
      type = "Training",
      x = sh[[timestep]],
      metric = sh[[training_metric]]
    )
  if (validation_metric %in% names(sh)) {
    scoring_history <- rbind(
      scoring_history,
      data.frame(
        model = "Main Model",
        type = "Validation",
        x = sh[[timestep]],
        metric = sh[[validation_metric]]
      )
    )
  }

  if (!is.null(model@model$cv_scoring_history)) {
    cv_scoring_history <- data.frame()
    for (csh_idx in seq_along(model@model$cv_scoring_history)) {
      csh <- as.data.frame(model@model$cv_scoring_history[[csh_idx]])
      cv_scoring_history <- rbind(
        cv_scoring_history,
        data.frame(
          model = paste0("CV-", csh_idx),
          type = "CV-Training",
          x = csh[[timestep]],
          metric = csh[[training_metric]]
        )
      )
      if (validation_metric %in% names(csh)) {
        cv_scoring_history <- rbind(
          cv_scoring_history,
          data.frame(
            model = paste0("CV-", csh_idx),
            type = "CV-Validation",
            x = csh[[timestep]],
            metric = csh[[validation_metric]]
          )
        )
      }
    }

    cvsh_mean <-
      as.data.frame(tapply(cv_scoring_history[, "metric"], cv_scoring_history[, c("x", "type")], mean, na.rm = TRUE))
    names(cvsh_mean) <- paste0(names(cvsh_mean), "_mean")
    cvsh_sd <-
      as.data.frame(tapply(cv_scoring_history[, "metric"], cv_scoring_history[, c("x", "type")], sd, na.rm = TRUE))
    names(cvsh_sd) <- paste0(names(cvsh_sd), "_sd")
    cvsh <- cbind(cvsh_mean, cvsh_sd)
    cvsh$x <- as.numeric(row.names(cvsh))

    cvsh <- rbind(
      data.frame(
        x = cvsh$x,
        mean = cvsh[["CV-Training_mean"]],
        type = "CV-Training",
        lower_bound = cvsh[["CV-Training_mean"]] - cvsh[["CV-Training_sd"]],
        upper_bound = cvsh[["CV-Training_mean"]] + cvsh[["CV-Training_sd"]]
      ),
      if ("CV-Validation_mean" %in% names(cvsh))
        data.frame(
          x = cvsh$x,
          mean = cvsh[["CV-Validation_mean"]],
          type = "CV-Validation",
          lower_bound = cvsh[["CV-Validation_mean"]] - cvsh[["CV-Validation_sd"]],
          upper_bound = cvsh[["CV-Validation_mean"]] + cvsh[["CV-Validation_sd"]]
        )
    )
  } else {
    cv_ribbon <- FALSE
    cv_individual_lines <- FALSE
  }

  p <- ggplot2::ggplot(ggplot2::aes_string(
    x = "x",
    y = "metric",
    color = "type",
    fill = "type"
  ),
                  data = scoring_history[!(is.na(scoring_history$x) |
                    is.na(scoring_history$metric) |
                    is.na(scoring_history$type)
                  ), ]) +
    ggplot2::geom_line() +
    ggplot2::geom_point()
  if (cv_ribbon) {
    cvsh <- cvsh[!is.na(cvsh$lower_bound) &
                   !is.na(cvsh$upper_bound),]
    p <- p + ggplot2::geom_line(ggplot2::aes_string(y = "mean"), data = cvsh) +
    ggplot2::geom_ribbon(
      ggplot2::aes_string(
        ymin = "lower_bound",
        ymax = "upper_bound",
        y = NULL,
        color = NULL
      ),
      alpha = 0.25,
      data = cvsh
    )
  }
  if (cv_individual_lines) {
    p <- p + ggplot2::geom_line(ggplot2::aes(group = paste(model, type)),
                                linetype = "dotted",
                                data = cv_scoring_history[!is.na(cv_scoring_history$metric),])
  }
  p <- p + ggplot2::geom_vline(ggplot2::aes_(
      xintercept = selected_timestep_value,
      linetype = paste("Selected", timestep)
    )) +
    ggplot2::labs(
      x = timestep,
      y = metric,
      title = "Learning Curve",
      subtitle = paste("for", model@model_id)
    ) +
    ggplot2::scale_color_manual(values = c("#ff6666", "#66bb00", "#dd77ff", "#00cccc")) +
    ggplot2::scale_fill_manual(values = c("#ff6666", "#66bb00", "#dd77ff", "#00cccc")) +
    ggplot2::theme_bw() +
    ggplot2::theme(
      legend.title = ggplot2::element_blank(),
      plot.title = ggplot2::element_text(hjust = 0.5),
      plot.subtitle = ggplot2::element_text(hjust = 0.5)
    )

  return(p)
}

######################################## Explain ###################################################

#' Generate Model Explanations
#' 
#' The H2O Explainability Interface is a convenient wrapper to a number of explainabilty 
#' methods and visualizations in H2O.  The function can be applied to a single model or group 
#' of models and returns a list of explanations, which are individual units of explanation 
#' such as a partial dependence plot or a variable importance plot.  Most of the explanations 
#' are visual (ggplot plots).  These plots can also be created by individual utility functions 
#' as well.
#'
#' @param object One of the following: an H2O model, a list of H2O models, an H2OAutoML object or 
#'               an H2OAutoML Leaderboard slice.
#' @param newdata An H2OFrame.
#' @param columns A vector of column names or column indices to create plots with. If specified
#'                parameter top_n_features will be ignored.
#' @param top_n_features An integer specifying the number of columns to use, ranked by variable importance
#'                       (where applicable).
#' @param include_explanations If specified, return only the specified model explanations.
#'   (Mutually exclusive with exclude_explanations)
#' @param exclude_explanations Exclude specified model explanations.
#' @param plot_overrides Overrides for individual model explanations, e.g. 
#' \code{list(shap_summary_plot = list(columns = 50))}.
#'
#' @return List of outputs with class "H2OExplanation"
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' aml <- h2o.automl(y = response,
#'                   training_frame = train,
#'                   max_models = 10,
#'                   seed = 1)
#'
#' # Create the explanation for whole H2OAutoML object
#' exa <- h2o.explain(aml, test)
#' print(exa)
#'
#' # Create the explanation for the leader model
#' exm <- h2o.explain(aml@leader, test)
#' print(exm)
#' }
#' @export
h2o.explain <- function(object,
                        newdata,
                        columns = NULL,
                        top_n_features = 5,
                        include_explanations = "ALL",
                        exclude_explanations = NULL,
                        plot_overrides = NULL) {
  models_info <- .process_models_or_automl(object, newdata)
  multiple_models <- length(models_info$model_ids) > 1
  result <- list()

  possible_explanations <- c(
    "leaderboard",
    "confusion_matrix",
    "residual_analysis",
    "varimp",
    "varimp_heatmap",
    "model_correlation_heatmap",
    "shap_summary",
    "pdp",
    "ice"
  )

  if (!missing(include_explanations) && !missing(exclude_explanations)) {
    stop("You can't specify both include and exclude model explanations. Use just one of them.")
  }

  if (!missing(columns) && !missing(top_n_features)) {
    warning("Parameters columns, and top_n_features are mutually exclusive. Parameter top_n_features will be ignored.")
  }
  if (!(is.null(columns) ||
    is.character(columns) ||
    is.numeric(columns))) {
    stop("Parameter columns must be either a character or numeric vector or NULL.")
  }
  skip_explanations <- c()

  if (!missing(include_explanations)) {
    if ("ALL" %in% include_explanations) {
      include_explanations <- possible_explanations
    }
    for (ex in include_explanations) {
      if (!ex %in% possible_explanations) {
        stop(sprintf(
          "Unknown model explanation \"%s\"! Possible model explanations are: %s.",
          ex, paste0(possible_explanations, collapse = ", ")
        ))
      }
    }
    skip_explanations <- Filter(
      function(ex) !ex %in% tolower(include_explanations),
      possible_explanations
    )
  }
  else if (!missing(exclude_explanations)) {
    for (ex in exclude_explanations) {
      if (!ex %in% possible_explanations) {
        stop(sprintf(
          "Unknown model explanation \"%s\"! Possible model explanations are: %s.",
          ex, paste0(possible_explanations, collapse = ", ")
        ))
      }
    }
    skip_explanations <- tolower(exclude_explanations)
  }

  if (!is.null(columns)) {
    columns_of_interest <- sapply(columns, function(col) {
      if (is.character(col)) {
        col
      } else {
        names(newdata)[[col]]
      }
    })
    for (col in columns_of_interest) {
      if (!col %in% models_info$x) {
        stop(sprintf("Column \"%s\" is not in x.", col))
      }
    }
  } else {
    columns_of_interest <- models_info$x
    if (!any(sapply(models_info$model_ids, .has_varimp))) {
      warning(
        "StackedEnsemble does not have a variable importance. Picking all columns. ",
        "Set `columns` to a vector of columns to explain just a subset of columns.",
        call. = FALSE
      )
    } else {
      models_with_varimp <- Filter(.has_varimp, models_info$model_ids)
      varimp <- names(.varimp(models_info$get_model(models_with_varimp[[1]])))
      columns_of_interest <- rev(varimp)[seq_len(min(length(varimp), top_n_features))]
      # deal with encoded columns
      columns_of_interest <- sapply(columns_of_interest, .find_appropriate_column_name, cols = models_info$x)
    }
  }

  if (multiple_models && !"leaderboard" %in% skip_explanations) {
    result$leaderboard <- list(
      header = .h2o_explanation_header("Leaderboard"),
      description = .describe("leaderboard"),
      data = .create_leaderboard(models_info, newdata)
    )
  }

  # residual analysis /  confusion matrix
  if (models_info$is_classification) {
    if (!"confusion_matrix" %in% skip_explanations) {
      result$confusion_matrix <- list(
        header = .h2o_explanation_header("Confusion Matrix"),
        description = .describe("confusion_matrix"),
        subexplanations = list()
      )
      for (m in models_info$model_ids) {
        m <- models_info$get_model(m)
        result$confusion_matrix$subexplanations[[m@model_id]] <- list(
          subheader = .h2o_explanation_header(m@model_id, 2),
          data = .customized_call(
            h2o.confusionMatrix,
            object = m,
            overridable_defaults = list(newdata = newdata),
            overrides = plot_overrides$confusion_matrix
          )
        )
        if (models_info$is_automl) break
      }
    }
  } else {
    if (!"residual_analysis" %in% skip_explanations) {
      result$residual_analysis <- list(
        header = .h2o_explanation_header("Residual Analysis"),
        description = .describe("residual_analysis"),
        plots = list())

      for (m in models_info$model_ids) {
        m <- models_info$get_model(m)
        result$residual_analysis$plots[[m@model_id]] <- .customized_call(
          h2o.residual_analysis_plot,
          model = m,
          newdata = newdata,
          overrides = plot_overrides$residual_analysis
        )
        if (models_info$is_automl) break
      }
    }
  }

  # feature importance
  if (!"varimp" %in% skip_explanations) {
    if (any(sapply(models_info$model_ids, .has_varimp))) {
      result$varimp <- list(
        header = .h2o_explanation_header("Variable Importance"),
        description = .describe("variable_importance"),
        plots = list())
      varimp <- NULL
      for (m in models_info$model_ids) {
        m <- models_info$get_model(m)
        tmp <- .plot_varimp(m)
        if (!is.null(tmp$varimp)) {
          result$varimp$plots[[m@model_id]] <- tmp$plot
          if (is.null(varimp)) varimp <- names(tmp$grouped_varimp)
          if (models_info$is_automl) break
        }
      }
    }
  }

  if (multiple_models) {
    # Variable Importance Heatmap
    if (!"varimp_heatmap" %in% skip_explanations) {
      if (length(Filter(.has_varimp, models_info$model_ids)) > 1) {
        result$varimp_heatmap <- list(
          header = .h2o_explanation_header("Variable Importance Heatmap"),
          description = .describe("varimp_heatmap"),
          plots = list(.customized_call(h2o.varimp_heatmap,
                                        object = models_info,
                                        overrides = plot_overrides$varimp_heatmap
          )))
      }
    }

    # Model Correlation
    if (!"model_correlation_heatmap" %in% skip_explanations) {
      result$model_correlation_heatmap <- list(
        header = .h2o_explanation_header("Model Correlation"),
        description = .describe("model_correlation_heatmap"),
        plots = list(.customized_call(h2o.model_correlation_heatmap,
                                      object = models_info,
                                      newdata = newdata,
                                      overrides = plot_overrides$model_correlation_heatmap
        )))
      top_n <- if (is.null(plot_overrides$model_correlation_heatmap$top_n)) 20
      else plot_overrides$model_correlation_heatmap$top_n
      if (models_info$is_automl)
        top_n <- Inf
      interpretable_models <- unlist(Filter(.interpretable,
                                            .model_ids(
                                              head(models_info$model_ids,
                                                   n = min(top_n, length(models_info$model_ids))
                                              ))))
      if (length(interpretable_models) > 0) {
        result$
          model_correlation_heatmap$
          notes$
          interpretable_models <- sprintf(
          "Interpretable models: %s",
          paste(interpretable_models, collapse = ", ")
        )
      }
    }
  }

  # SHAP summary
  if (!"shap_summary" %in% skip_explanations && !models_info$is_multinomial_classification) {
    num_of_tree_models <- sum(sapply(models_info$model_ids, .is_h2o_tree_model))
    if (num_of_tree_models > 0) {
      result$shap_summary <- list(
        header = .h2o_explanation_header("SHAP Summary"),
        description = .describe("shap_summary"),
        plots = list())
      for (m in Filter(.is_h2o_tree_model, models_info$model_ids)) {
        m <- models_info$get_model(m)
        result$shap_summary$plots[[m@model_id]] <- .customized_call(
          h2o.shap_summary_plot,
          model = m,
          newdata = newdata,
          overrides = plot_overrides$shap_summary
        )
        if (models_info$is_automl) break
      }
    }
  }

  # PDP
  if (!"pdp" %in% skip_explanations) {
    result$pdp <- list(
      header = .h2o_explanation_header("Partial Dependence Plots"),
      description = .describe("pdp"),
      plots = list())
    for (col in columns_of_interest) {
      if (models_info$is_multinomial_classification) {
        targets <- h2o.levels(newdata[[models_info$y]])
        if (!is.null(plot_overrides$pdp[["target"]])) {
          targets <- plot_overrides$pdp[["target"]]
        }
        for (target in targets) {
          if (multiple_models) {
            result$pdp$plots[[col]][[target]] <- .customized_call(
              h2o.pd_multi_plot,
              object = models_info,
              newdata = newdata,
              column = col,
              target = target,
              overridable_defaults = list(best_of_family = models_info$is_automl),
              overrides = plot_overrides$pdp
            )
          } else {
            result$pdp$plots[[col]][[target]] <- .customized_call(
              h2o.pd_plot,
              object = models_info,
              newdata = newdata,
              column = col,
              target = target,
              overrides = plot_overrides$pdp
            )
          }
        }
      } else {
        if (multiple_models) {
          result$pdp$plots[[col]] <- .customized_call(
            h2o.pd_multi_plot,
            object = models_info,
            newdata = newdata,
            column = col,
            overridable_defaults = list(best_of_family = models_info$is_automl),
            overrides = plot_overrides$pdp
          )
        } else {
          result$pdp$plots[[col]] <- .customized_call(
            h2o.pd_plot,
            object = models_info,
            newdata = newdata,
            column = col,
            overrides = plot_overrides$pdp
          )
        }
      }

    }
  }

  # ICE quantiles
  if (!"ice" %in% skip_explanations && !models_info$is_classification) {
    result$ice <- list(
      header = .h2o_explanation_header("Individual Conditional Expectations"),
      description = .describe("ice"),
      plots = list())
    for (col in columns_of_interest) {
      for (m in models_info$model_ids) {
        m <- models_info$get_model(m)
        if (models_info$is_multinomial_classification) {
          targets <- h2o.levels(newdata[[models_info$y]])
          if (!is.null(plot_overrides$ice[["target"]])) {
            targets <- plot_overrides$ice[["target"]]
          }

          for (target in targets) {
            result$ice$plots[[col]][[m@model_id]][[target]] <- .customized_call(
              h2o.ice_plot,
              model = m,
              newdata = newdata,
              column = col,
              target = target,
              overrides = plot_overrides$ice
            )
          }
        } else {
          result$ice$plots[[col]][[m@model_id]] <- .customized_call(
            h2o.ice_plot,
            model = m,
            newdata = newdata,
            column = col,
            overrides = plot_overrides$ice
          )
        }
        if (models_info$is_automl) break
      }
    }
  }
  class(result) <- "H2OExplanation"
  return(result)
}

#' Generate Model Explanations for a single row
#' 
#' Explain the behavior of a model or group of models with respect to a single row of data. 
#' The function returns a list of explanations, which are individual units of explanation 
#' such as a partial dependence plot or a variable importance plot.  Most of the explanations 
#' are visual (ggplot plots).  These plots can also be created by individual utility functions 
#' as well.
#' 
#' @param object One of the following: an H2O model, a list of H2O models, an H2OAutoML object 
#'               or an H2OAutoML Leaderboard slice.
#' @param newdata An H2OFrame.
#' @param row_index A row index of the instance to explain.
#' @param columns A vector of column names or column indices to create plots with. If specified
#'                parameter top_n_features will be ignored.
#' @param top_n_features An integer specifying the number of columns to use, ranked by variable importance
#'                       (where applicable).
#' @param include_explanations If specified, return only the specified model explanations. 
#'                             (Mutually exclusive with exclude_explanations)
#' @param exclude_explanations Exclude specified model explanations.
#' @param plot_overrides Overrides for individual model explanations, e.g.,
#'                       \code{list(shap_explain_row = list(columns = 5))}
#'
#' @return List of outputs with class "H2OExplanation"
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the wine dataset into H2O:
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/wine/winequality-redwhite-no-BOM.csv"
#' df <-  h2o.importFile(f)
#'
#' # Set the response
#' response <- "quality"
#'
#' # Split the dataset into a train and test set:
#' splits <- h2o.splitFrame(df, ratios = 0.8, seed = 1)
#' train <- splits[[1]]
#' test <- splits[[2]]
#'
#' # Build and train the model:
#' aml <- h2o.automl(y = response,
#'                   training_frame = train,
#'                   max_models = 10,
#'                   seed = 1)
#'
#' # Create the explanation for whole H2OAutoML object
#' exa <- h2o.explain_row(aml, test, row_index = 1)
#' print(exa)
#'
#' # Create the explanation for the leader model
#' exm <- h2o.explain_row(aml@leader, test, row_index = 1)
#' print(exm)
#' }
#' @export
h2o.explain_row <- function(object,
                            newdata,
                            row_index,
                            columns = NULL,
                            top_n_features = 5,
                            include_explanations = "ALL",
                            exclude_explanations = NULL,
                            plot_overrides = NULL) {
  models_info <- .process_models_or_automl(object, newdata)
  if (missing(row_index))
    stop("row_index must be specified!")
  multiple_models <- length(models_info$model_ids) > 1
  result <- list()

  possible_explanations <- c(
    "leaderboard",
    "shap_explain_row",
    "ice"
  )

  if (!missing(include_explanations) && !missing(exclude_explanations)) {
    stop("You can't specify both include and exclude explanations. Use just one of them.")
  }
  if (!missing(columns) && !missing(top_n_features)) {
    warning("Parameters columns, and top_n_features are mutually exclusive. Parameter top_n_features will be ignored.")
  }
  if (!(is.null(columns) ||
    is.character(columns) ||
    is.numeric(columns))) {
    stop("Parameter columns must be either a character or numeric vector or NULL.")
  }
  skip_explanations <- c()

  if (!missing(include_explanations)) {
    if ("ALL" %in% include_explanations) {
      include_explanations <- possible_explanations
    }
    for (ex in include_explanations) {
      if (!ex %in% possible_explanations) {
        stop(sprintf(
          "Unknown model explanation \"%s\"! Possible model explanations are: %s.",
          ex, paste0(possible_explanations, collapse = ", ")
        ))
      }
    }
    skip_explanations <- Filter(
      function(ex) !ex %in% tolower(include_explanations),
      possible_explanations
    )
  }
  else if (!missing(exclude_explanations)) {
    for (ex in exclude_explanations) {
      if (!ex %in% possible_explanations) {
        stop(sprintf(
          "Unknown model explanation \"%s\"! Possible model explanations are: %s.",
          ex, paste0(possible_explanations, collapse = ", ")
        ))
      }
    }
    skip_explanations <- tolower(exclude_explanations)
  }


  if (!is.null(columns)) {
    columns_of_interest <- sapply(columns, function(col) {
      if (is.character(col)) {
        col
      } else {
        names(newdata)[[col]]
      }
    })
    for (col in columns_of_interest) {
      if (!col %in% models_info$x) {
        stop(sprintf("Column \"%s\" is not in x.", col))
      }
    }
  } else {
    columns_of_interest <- models_info$x
    if (!any(sapply(models_info$model_ids, .has_varimp))) {
      warning(
        "StackedEnsemble does not have a variable importance. Picking all columns. ",
        "Set `columns` to a vector of columns to explain just a subset of columns.",
        call. = FALSE
      )
    } else {
      models_with_varimp <- Filter(.has_varimp, models_info$model_ids)
      varimp <- names(.varimp(models_info$get_model(models_with_varimp[[1]])))
      columns_of_interest <- rev(varimp)[seq_len(min(length(varimp), top_n_features))]
      # deal with encoded columns
      columns_of_interest <- sapply(columns_of_interest, .find_appropriate_column_name, cols = models_info$x)
    }
  }

  if (multiple_models && !"leaderboard" %in% skip_explanations) {
    result$leaderboard <- list(
      header = .h2o_explanation_header("Leaderboard"),
      description = .describe("leaderboard_row"),
      data = .leaderboard_for_row(models_info, newdata, row_index))
  }

  if (!"shap_explain_row" %in% skip_explanations && !models_info$is_multinomial_classification) {
    num_of_tree_models <- sum(sapply(models_info$model_ids, .is_h2o_tree_model))
    if (num_of_tree_models > 0) {
      result$shap_explain_row <- list(
        header = .h2o_explanation_header("SHAP explanation"),
        description = .describe("shap_explain_row"),
        plots = list()
      )
      tree_models <- Filter(.is_h2o_tree_model, models_info$model_ids)
      for (m in tree_models) {
        m <- models_info$get_model(m)
        result$shap_explain_row$plots[[m@model_id]] <- .customized_call(
          h2o.shap_explain_row_plot,
          model = m,
          newdata = newdata,
          row_index = row_index,
          overrides = plot_overrides$shap_explain_row
        )
        if (models_info$is_automl) break
      }
    }
  }

  if (!"ice" %in% skip_explanations && !multiple_models) {
    result$ice <- list(
      header = .h2o_explanation_header("Individual Conditional Expectations"),
      description = .describe("ice_row"),
      plots = list()
    )
    for (col in columns_of_interest) {
      if (models_info$is_multinomial_classification) {
        targets <- h2o.levels(newdata[[models_info$y]])
        if (!is.null(plot_overrides$ice[["target"]])) {
          targets <- plot_overrides$ice[["target"]]
        }
        for (target in targets) {
          result$ice$plots[[col]][[target]] <- .customized_call(
            h2o.pd_plot,
            object = models_info,
            newdata = newdata,
            column = col,
            target = target,
            row_index = row_index,
            overrides = plot_overrides$ice
          )
        }
      } else {
        result$ice$plots[[col]] <- .customized_call(
          h2o.pd_plot,
          object = models_info,
          newdata = newdata,
          column = col,
          row_index = row_index,
          overrides = plot_overrides$ice
        )
      }
    }
  }
  class(result) <- "H2OExplanation"
  return(result)
}


#### On Load ####
# Inspired by vctrs' s3_register function for registering s3 methods for generics from suggested packages
.s3_register <- function(package, generic, class) {
  method_env <- if (isNamespace(topenv())) asNamespace(environmentName(topenv())) else parent.frame()
  method <- get(paste(generic, class, sep = "."), envir = method_env)

  # Register hook in case package is unloaded & reloaded
  setHook(packageEvent(package, "onLoad"),
          function(...) {
            registerS3method(generic, class, method, envir = asNamespace(package))
          }
  )

  # Don't register if the package is not present
  if (!isNamespaceLoaded(package)) {
    return(invisible())
  }

  # Register iff generic exists in the package environment
  if (exists(generic, asNamespace(package))) {
    registerS3method(generic, class, method, envir = asNamespace(package))
  }

  invisible()
}

.onLoad <- function(...) {
  registerS3method("print", "H2OExplanation", "print.H2OExplanation")
  .s3_register("repr", "repr_text", "H2OExplanation")
  .s3_register("repr", "repr_html", "H2OExplanation")
  invisible()
}
