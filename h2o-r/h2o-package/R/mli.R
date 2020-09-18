########################################### UTILS ##################################################
#' Suppresses h2o progress output from \code{expr}
#'
#' @param expr expression
#'
#' @return result of \code{expr}
with_no_h2o_progress <- function(expr) {
  show_progress <- environment(h2o.no_progress)$.pkg.env$PROGRESS_BAR
  if (length(show_progress) == 0L || show_progress) {
    on.exit(h2o.show_progress())
  } else {
    on.exit(h2o.no_progress())
  }
  h2o.no_progress()
  force(expr)
}

#' Is the \code{model} an H2O model?
#'
#' @param model Either H2O model/model id => TRUE, or something else => FALSE
#'
#' @return boolean
.is_h2o_model <- function(model) {
  if (is.character(model)) {
    model <- h2o.getModel(model)
  }

  classes <- class(model)
  return(any(startsWith(classes, "H2O") & endsWith(classes, "Model")))
}

#' Is the \code{model} a Tree-based H2O Model?
#'
#' @param model Either tree-based H2O model/model id => TRUE, or something else => FALSE
#'
#' @return boolean
.is_h2o_tree_model <- function(model) {
  if (is.character(model)) {
    model <- h2o.getModel(model)
  }

  if (!.is_h2o_model(model)) {
    return(FALSE)
  }

  return(startsWith(model@model_id, "DRF") || startsWith(model@model_id, "GBM") ||
           startsWith(model@model_id, "XGB") || startsWith(model@model_id, "XRT"))
}

.interpretable <- function(model_id) {
  grepl("^(GLM|GAM)", model_id)
}

.get_feature_frequencies <- function(column) {
  desc <- Count <- NULL  # To keep R check as CRAN quiet
  tbl <- h2o.arrange(h2o.table(column), desc(Count))
  unlist(stats::setNames(as.list(tbl[, 2]), as.list(tbl[, 1])))
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

.has_varimp <- function(model_id) {
  !startsWith(model_id, "StackedEnsemble") && !startsWith(model_id, "Naive")
}


#' Do basic validation and transform \code{object} to a "standardized" list containing models, and
#' their properties such as \code{x}, \code{y}, whether it is a (multinomial) clasification or not etc.
#'
#' @param object Can be a single model/model_id, vector of model_id, list of models, H2OAutoML object
#' @param newdata An H2OFrame with the same format as training frame
#' @param require_single_model If true, make sure we were provided only one model
#' @param require_multiple_models If true, make sure we were provided at least two models
#' @param top_n If set, don't return more than top_n models
#' @param only_with_varimp If TRUE, return only models that have variable importance
#' @param best_of_family If TRUE, return only best of family models
#'
#' @return a list with the following names \code{leader}, \code{has_leaderboard}, \code{models},
#'   \code{is_classification}, \code{is_multinomial_classification}, \code{x}, \code{y}, \code{model}
.process_models_or_automl <- function(object, newdata,
                                      require_single_model = FALSE,
                                      require_multiple_models = FALSE,
                                      top_n = NA,
                                      only_with_varimp = FALSE,
                                      best_of_family = FALSE) {
  if ("H2OFrame" %in% class(object)) {
    object <- as.list(object[["model_id"]])
  }
  newdata_name <- deparse(substitute(newdata, environment()))
  if (!"H2OFrame" %in% class(newdata)) {
    stop(paste(newdata_name, "must be an H2OFrame!"))
  }

  make_models_info <- function(...) {
    result <- list(...)
    class(result) <- c("models_info", "list")
    return(result)
  }

  .get_MSE <- function(model) {
    if (!is.null(model@model$validation_metrics@metrics$MSE)) {
      model@model$validation_metrics@metrics$MSE
    } else {
      model@model$training_metrics@metrics$MSE
    }
  }


  if ("models_info" %in% class(object)) {
    if (best_of_family) {
      selected_models <- character()
      included_families <- character()
      for (model_id in .model_ids(object$models)) {
        family <- substr(model_id, 1, 3)
        if (!family %in% included_families || "Sta" == family) {
          included_families <- c(included_families, family)
          selected_models <- c(new_models, model_id)
        }
      }
      object$models <- Filter(function(model) model@model_id %in% selected_models, object$models)
    }

    if (only_with_varimp) {
      object@models <- Filter(function(model) .has_varimp(model@model_id), object$models)
    }

    if (!is.na(top_n)) {
      if (object$has_leaderboard) {
        object$models <- head(object$models, n = min(top_n, length(object$models)))
      } else {
        object$models <- head(object$models[order(unlist(sapply(object$models, .get_MSE)))],
                              n = min(top_n, length(object$models))
        )
      }
    }

    return(object)
  }


  if ("H2OAutoML" %in% class(object)) {
    y_col <- newdata[[object@leader@allparameters$y]]
    if (require_single_model && nrow(object@leaderboard) > 1) {
      stop("Only one model is allowed!")
    }
    if (require_multiple_models && nrow(object@leaderboard) <= 1) {
      stop("More than one model is needed!")
    }
    models <- unlist(as.list(object@leaderboard$model_id))
    if (only_with_varimp) {
      models <- Filter(function(m_id) .has_varimp(m_id), models)
    }
    if (best_of_family) {
      new_models <- character()
      included_families <- character()
      for (model_id in models) {
        family <- substr(model_id, 1, 3)
        if (!family %in% included_families || "Sta" == family) {
          included_families <- c(included_families, family)
          new_models <- c(new_models, model_id)
        }
      }
      models <- new_models
    }
    if (is.na(top_n)) {
      top_n <- length(models)
    } else {
      top_n <- min(top_n, length(models))
    }
    return(make_models_info(
      leader = object@leader,
      has_leaderboard = TRUE,
      leaderboard = as.data.frame(h2o.get_leaderboard(object, extra_columns = "ALL")),
      models = sapply(head(models, top_n), h2o.getModel),
      is_classification = is.factor(y_col),
      is_multinomial_classification = is.factor(y_col) && h2o.nlevels(y_col) > 2,
      x = object@leader@allparameters$x,
      y = object@leader@allparameters$y,
      model = if (require_single_model) object@leader
    ))
  } else {
    if (length(object) == 1) {
      if (require_multiple_models) {
        stop("More than one model is needed!")
      }
      if (class(object) == "list") {
        object <- object[[1]]
      }
      if (!.is_h2o_model(object)) {
        object <- h2o.getModel(object)
      }

      if (only_with_varimp && !.has_varimp(object@model_id)) {
        stop(object@model_id, " doesn't have variable importance!")
      }

      y_col <- newdata[[object@allparameters$y]]

      return(make_models_info(
        leader = object,
        has_leaderboard = FALSE,
        models = list(object),
        is_classification = is.factor(y_col),
        is_multinomial_classification = is.factor(y_col) && h2o.nlevels(y_col) > 2,
        x = object@allparameters$x,
        y = object@allparameters$y,
        model = if (require_single_model) object
      ))
    } else {
      if (require_single_model) {
        stop("Only one model is allowed!")
      }

      if (only_with_varimp) {
        object <- Filter(function(model) .has_varimp(model), .model_ids(object))
      }

      if (is.na(top_n)) {
        top_n <- length(object)
      } else {
        top_n <- min(top_n, length(object))
      }

      object <- sapply(head(object, n = top_n), function(m) {
        if (is.character(m)) {
          h2o.getModel(m)
        } else {
          m
        }
      })
      x <- object[[1]]@allparameters$x
      y <- object[[1]]@allparameters$y

      for (model in object) {
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

      if (best_of_family) {
        object <- object[order(sapply(object, .get_MSE))]
        new_models <- list()
        included_families <- character()
        for (model in object) {
          family <- substr(model@model_id, 1, 3)
          if (!family %in% included_families || "Sta" == family) {
            included_families <- c(included_families, family)
            new_models <- c(new_models, model)
          }
        }
        models <- new_models
      }

      return(make_models_info(
        leader = object[[1]],
        has_leaderboard = FALSE,
        models = object,
        is_classification = is.factor(newdata[[y]]),
        is_multinomial_classification = is.factor(newdata[[y]]) && h2o.nlevels(newdata[[y]]) > 2,
        x = x,
        y = y,
        model = NULL
      ))
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
.customized_call <- function(fun, ..., overridable_defaults = list(), overrides = list()) {
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
#'
#' @return a data.frame
.create_leaderboard <- function(models_info, leaderboard_frame) {
  if (models_info$has_leaderboard) {
    return(models_info$leaderboard)
  }
  models <- models_info$models
  leaderboard <-
    as.data.frame(t(sapply(models, function(m) {
      unlist(h2o.performance(m, leaderboard_frame)@metrics[c(
        "MSE",
        "RMSE",
        "mae",
        "rmsle",
        "mean_per_class_error",
        "logloss"
      )])
    })))
  row.names(leaderboard) <- sapply(models, function(m) m@model_id)
  leaderboard <- leaderboard[order(leaderboard[[1]]), ]
  names(leaderboard) <- tolower(names(leaderboard))
  return(leaderboard)
}


#' Get variable importance in a standardized way.
#'
#' @param model H2OModel
#' @param newdata H2OFrame
#'
#' @return A named vector
.varimp <- function(model, newdata) {
  if (!.has_varimp(model@model_id)) {
    stop("Can't get variable importance from: ", model@model_id)
  } else {
    varimp <- h2o.varimp(model)
    if (is.null(varimp)) {
      res <- rep_len(0, length(model@allparameters$x))
      names(res) <- model@allparameters$x
      return(res)
    }

    res <- as.list(varimp$scaled_importance)
    names(res) <- varimp$variable
    # sum one hot encoded variable importances
    factorial_vars <- names(newdata[, is.factor(newdata)])
    factorial_vars <- factorial_vars[factorial_vars %in% model@allparameters$x]
    categoricals <- sapply(
      factorial_vars,
      function(col) {
        Filter(
          function(x) {
            substr(x, 1, nchar(col) + 1) == paste0(col, ".")
          },
          names(res)
        )
      },
      simplify = FALSE
    )

    for (cat in names(categoricals)) {
      if (cat != model@allparameters$y) {
        if (!cat %in% names(res)) {
          res[[cat]] <- sum(unlist(res[categoricals[[cat]]]))
          for (fct in categoricals[[cat]]) {
            res[[fct]] <- NULL
          }
        }
      }
    }
    results <- unlist(res)
    names(results) <- sapply(names(results), .find_appropriate_column_name, names(newdata))

    for (col in model@allparameters$x) {
      if (!col %in% names(results)) {
        results[col] <- 0
      }
    }

    return(sort(results / sum(results, na.rm = TRUE)))
  }
}

.plot_varimp <- function(model, newdata) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  with_no_h2o_progress({
    suppressWarnings({
      varimp <- h2o.varimp(model)
    })
    if (is.null(varimp)) {
      warning("Variable importance is not available for: ", model@model_id, call. = FALSE)
      return(NULL)
    } else {
      varimp <- as.data.frame(varimp)
      p <- ggplot2::ggplot(ggplot2::aes(.data$variable, .data$scaled_importance), data = varimp) +
        ggplot2::geom_col(fill = "#1F77B4") +
        ggplot2::scale_x_discrete("Variable", limits = rev(varimp$variable)) +
        ggplot2::labs(y = "Variable Importance", title = sprintf("Variable importance for %s", model@model_id)) +
        ggplot2::coord_flip() +
        ggplot2::theme_bw()
      return(list(varimp = varimp, grouped_varimp = .varimp(model, newdata), plot = p))
    }
  })
}

.leaderboard_for_row <- function(models_info, test_frame, row_index, top_n = 10) {
  leaderboard <- .create_leaderboard(models_info, test_frame)
  top_n <- min(top_n, nrow(leaderboard))
  indices <- which(!duplicated(substr(leaderboard$model_id, 1, 3)))
  indices <- c(seq_len(top_n), indices[indices > top_n])
  leaderboard <- leaderboard[indices, ]
  with_no_h2o_progress({
    leaderboard <-
      cbind(leaderboard,
            prediction = do.call(
              rbind,
              sapply(
                leaderboard[["model_id"]],
                function(model_id) {
                  as.data.frame(stats::predict(h2o.getModel(model_id), test_frame[row_index, ])[["predict"]])
                }
              )
            )
      )
  })
  row.names(leaderboard) <- seq_len(nrow(leaderboard))
  return(leaderboard)
}

.min_max <- function(col) {
  rng <- range(col, na.rm = TRUE)
  if (rng[[2]] == rng[[1]]) {
    return(0.5)
  }
  return((col - rng[[1]]) / (rng[[2]] - rng[[1]]))
}

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
    cat(unlist(df[row, ]), "", sep = " | ")
    cat("\n")
  }
}

.plotlyfy <- function(gg) {
  gp <- plotly::ggplotly(gg, width = 940, height = 700, tooltip = "text")

  # Hack to fix plotly legend
  for (i in seq_len(length(gp$x$data))) {
    if (!is.null(gp$x$data[[i]]$name))
      gp$x$data[[i]]$name <- gsub("^\\((.*)\\s*,\\s*\\d+(?:,\\s*NA\\s*)?\\)$", "\\1", gp$x$data[[i]]$name)
  }
  return(gp)
}

.render <- function(object, render) {
  if (all(class(object) == "explanation" | class(object) == "list")) {
    return(lapply(object, .render, render = render))
  } else {
    if (render == "interactive" && any(class(object) == "gg")) {
      input <- readline("Hit <Return> to see next plot, to quit press \"q\": ")
      if (tolower(input) == "q") stop("Aborted by user.")
    }
    if (render == "html") {
      switch(
        class(object)[[1]],
        data.frame = .render_df_to_html(object),
        H2OFrame = .render_df_to_html(as.data.frame(object)),
        H2OTable = .render_df_to_html(as.data.frame(object)),
        explanation_header = htmltools::tags$h1(object),
        explanation_subsection = htmltools::tags$h2(object),
        explanation_description = htmltools::tags$blockquote(object),
        gg = .plotlyfy(object)
      )
    } else if (render == "markdown") {
      switch(
        class(object)[[1]],
        data.frame = .render_df_to_md(object),
        H2OFrame = .render_df_to_md(as.data.frame(object)),
        H2OTable = .render_df_to_md(as.data.frame(object)),
        explanation_header =  cat("\n\n", object, "\n", strrep("=", nchar(object)), "\n", sep = ""),
        explanation_subsection =  cat("\n\n", object, "\n", strrep("-", nchar(object)), "\n", sep = ""),
        explanation_description = cat("\n> ", paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n> "), "\n\n", sep = ""),
        print(object)
      )
    } else if (render == "notebook") {
      if (.is_using_jupyter()) {
        switch(
          class(object)[[1]],
          data.frame = .render_df_to_jupyter(object),
          H2OFrame = .render_df_to_jupyter(object),
          H2OTable = .render_df_to_jupyter(object),
          explanation_header = IRdisplay::display_html(sprintf("<h1>%s</h1>\n", object)),
          explanation_subsection = IRdisplay::display_html(sprintf("<h2>%s</h2>\n", object)),
          explanation_description = IRdisplay::display_html(
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
          explanation_header = cat("\n\n", object, "\n", strrep("=", nchar(object)), "\n", sep = ""),
          explanation_subsection =  cat("\n\n", object, "\n", strrep("-", nchar(object)), "\n", sep = ""),
          explanation_description = cat("\n> ", paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n> "), "\n\n", sep = ""),
          print(object)
        )
      }
    } else if (render == "interactive") {
      switch(
        class(object)[[1]],
        explanation_header = cat("\n\n", object, "\n", strrep("=", nchar(object)), "\n", sep = ""),
        explanation_subsection =  cat("\n\n", object, "\n", strrep("-", nchar(object)), "\n", sep = ""),
        explanation_description = message(paste0(strsplit(object, "\\n\\s*")[[1]], collapse = "\n")),
        print(object)
      )
    } else {
      stop("Unknown render type \"", render, "\". Chose one of: html, markdown, notebook, interactive.")
    }
  }
}

.is_plotting_to_rnotebook <- function() {
  grDevices::graphics.off()
  .Platform$GUI == "RStudio" && !grDevices::dev.capabilities()$locator
}

.is_using_jupyter <- function() {
  !is.null(options()$jupyter.rich_display) &&
    options()$jupyter.rich_display &&
    requireNamespace("IRdisplay", quietly = TRUE)
}

.explanation_header <- function(string, subsection = FALSE) {
  class(string) <- if (subsection) "explanation_subsection" else "explanation_header"
  string <- list(string)
  return(string)
}

.explanation_description <- function(string) {
  class(string) <- "explanation_description"
  string <- list(string)
  return(string)
}

.describe <- function(func) {
  if (!is.character(func)) {
    func <- as.character(substitute(func, environment()))
  }
  # TODO: make sure to put some meaningful descriptions
  .explanation_description(
    switch(func,
           h2o.shap_summary_plot =
             "SHAP summary plot shows contribution of features for each instance.",
           h2o.shap_explain_row =
             "SHAP explain single instance shows contribution of features for a given instance.",
           h2o.variable_importance_heatmap =
             "Variable importance heatmap shows variable importances on multiple models.
           By default, the models are ordered by their similarity.",
           h2o.model_correlation =
             "Model correlation matrix shows correlation between prediction of the models.
           For classification, frequency of same predictions is used.",
           h2o.residual_analysis =
             "Residual analysis plot shows predicted vs fitted values.",
           h2o.partial_dependences =
             "Partial dependence plot gives a graphical depiction of the marginal effect
           of a variable on the response. The effect of a variable is measured in change in
           the mean response.",
           h2o.individual_conditional_expectations =
             "Individual conditional expectations plot gives a graphical depiction of the marginal
             effect of a variable on the response for a given row.",
           h2o.confusionMatrix =
             "Confusion matrix shows a predicted class vs an actual class.",
           h2o.varimp =
             "Variable importance shows how much do the predictions depend on what variable.",
           h2o.get_leaderboard =
             "Leaderboard shows models with their metrics.",
           .leaderboard_for_row =
             "Leaderboard shows models with their metrics and their predictions for a given row.",

           stop("Unknown function \"", func, "\".")
    )
  )
}

print.explanation <- function(object, ..., render = "AUTO") {
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
    } else if (.is_using_jupyter()) {
      return(result)
    }
    return(result)
  } else {
    invisible(.render(object, render = render))
  }
}

# For Jupyter(IRkernel)
repr_text.explanation <- function(object, ...) {
  return("Use print(explanation, render = \"notebook\")")
}

repr_html.explanation <- function(object, ...) {
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


########################################## MLI PLOTS ###############################################

#' SHAP Summary Plot
#'
#' @param model An H2O model
#' @param newdata An H2O Frame
#' @param top_n_features Plot only top_n features
#' @param sample_size Maximum number of observations to be plotted
#'
#' @return A ggplot2 object
#' @export
h2o.shap_summary_plot <-
  function(model,
           newdata,
           top_n_features = 20,
           sample_size = 1000) {
    # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
    .data <- NULL
    if (!.is_h2o_tree_model(model)) {
      stop("SHAP summary plot requires a tree-based model!")
    }
    x <- model@allparameters$x

    title <- sprintf("Summary Plot for \"%s\"", model@model_id)
    indices <- row.names(newdata)
    if (nrow(newdata) > sample_size) {
      indices <- sort(sample(seq_len(nrow(newdata)), sample_size))
      newdata <- newdata[indices, ]
    }

    with_no_h2o_progress({
      newdata_df <- as.data.frame(newdata)

      for (fct in names(newdata[, x])[is.factor(newdata[, x])]) {
        newdata_df[[fct]] <- as.factor(newdata_df[[fct]])
        if (substr(model@model_id, 1, 3) == "XGB") {
          # encode categoricals for xgboost
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

      contributions <- as.data.frame(h2o.predict_contributions(model, newdata))
      contributions_names <- names(contributions)
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
    if (length(features) > top_n_features) {
      features <- features[seq(from = length(features), to = length(features) - top_n_features)]
      contr <- contr[contr[["feature"]] %in% features, ]
    }

    p <-
      ggplot2::ggplot(ggplot2::aes(.data$feature, .data$contribution,
                                          color = .data$normalized_value,
                                          text = .data$row
      ),
                      data = contr[sample(nrow(contr)), ]
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
#' @param model An H2O model
#' @param newdata An H2O Frame
#' @param row_index Instance row index
#' @param top_n_features Maximum number of features to show.
#' @param plot_type Either "barplot" or "breakdown".
#' @param contribution_type When plot_type=="barplot", plot one of "negative", "positive", c("negative", "positive")
#' @return A ggplot2 object
#'
#' @export
h2o.shap_explain_row <-
  function(model, newdata, row_index, top_n_features = 10, plot_type = "barplot",
           contribution_type = c("positive", "negative")) {
    # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
    .data <- NULL
    if (!.is_h2o_tree_model(model)) {
      stop("SHAP explain_row plot requires a tree-based model!")
    }

    if (plot_type == "barplot") {
      if (length(contribution_type) == 0) {
        stop("contribution_type must be specified for plot_type=\"barplot\"")
      }
      if (any(!contribution_type %in% c("positive", "negative"))) {
        stop(
          "Unknown contribution type(s): ",
          contribution_type[!contribution_type %in% c("positive", "negative")]
        )
      }
    }

    x <- model@allparameters$x

    with_no_h2o_progress({
      contributions <-
        as.data.frame(h2o.predict_contributions(model, newdata[row_index, ]))
      prediction <- as.data.frame(h2o.predict(model, newdata[row_index, ]))
      newdata_df <- as.data.frame(newdata[row_index, ])
      for (fct in names(newdata[, x])[is.factor(newdata[, x])]) {
        newdata_df[[fct]] <- as.factor(newdata_df[[fct]])
        if (substr(model@model_id, 1, 3) == "XGB") {
          # encode categoricals for xgboost
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

      ordered_features <- names(contributions)[order(contributions)]
      features <- character()
      if ("positive" %in% contribution_type) {
        positive_features <- ordered_features[ordered_features > 0]
        features <- c(features, tail(positive_features, n = min(
          top_n_features,
          length(positive_features)
        )))
      }
      if ("negative" %in% contribution_type) {
        negative_features <- ordered_features[ordered_features < 0]
        features <- c(features, head(negative_features, n = min(
          top_n_features,
          length(negative_features)
        )))
      }

      if (length(features) == 0) {
        stop("No feature contributions to show.", if (length(contribution_type) < 2) {
          "Changing contribution_type to c(\"positive\", \"negative\") might help."
        } else {
          ""
        })
      }
      contributions <- contributions[, features]
      contributions <- data.frame(contribution = t(contributions))
      contributions$feature <- paste0(
        row.names(contributions), "=",
        as.list(newdata_df[, row.names(contributions)])
      )
      contributions <- contributions[order(contributions$contribution), ]
      contributions$text <- paste(
        "Feature:", row.names(contributions), "\n",
        "Feature Value:", unlist(newdata_df[, row.names(contributions)]), "\n",
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
            "SHAP explanation for %s on row %d\nprediction: %s",
            model@model_id, row_index, as.character(prediction$predict)
          )
        ) +
        ggplot2::theme_bw() +
        ggplot2::theme(plot.margin = ggplot2::margin(16.5, 5.5, 5.5, 5.5))
      return(p)
    } else if (plot_type == "breakdown") {
      contributions <- contributions[, names(contributions)[order(abs(t(contributions)))]]
      bias_term <- contributions$BiasTerm
      contributions <- contributions[, names(contributions) != "BiasTerm"]
      if (ncol(contributions) > top_n_features) {
        rest <- rowSums(contributions[, names(contributions)[seq(from = 1, to = ncol(contributions) - top_n_features, by = 1)]])
        contributions <- contributions[, tail(names(contributions), n = top_n_features)]
        contributions[["rest_of_the_features"]] <- rest
      }
      contributions <- t(contributions)

      contributions <- data.frame(
        id = seq_along(contributions),
        id_next = c(seq_len(length(contributions) - 1) + 1, length(contributions)),
        feature = row.names(contributions),
        start = c(0, head(cumsum(contributions[, 1]), n = -1)) + bias_term,
        end = cumsum(contributions[, 1]) + bias_term,
        color = contributions[, 1] > 0
      )

      newdata_df[["rest_of_the_features"]] <- NA
      contributions$feature_value <- paste("Feature Value:", t(newdata_df)[contributions$feature, ])
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
        ggplot2::xlab("Feature") +
        ggplot2::ylab("SHAP value") +
        ggplot2::ggtitle(sprintf("SHAP Explanation of \"%s\" on row %d", model@model_id, row_index)) +
        ggplot2::theme_bw() +
        ggplot2::theme(legend.title = ggplot2::element_blank())
      return(p)
    } else {
      stop("Unknown plot_type=", plot_type)
    }
  }


#' Variable Importance Heatmap of Individual Models Created by H2OAutoML
#'
#' @param object An H2OAutoML object or list of models
#' @param newdata An H2O Frame
#' @param top_n Plot only top_n models
#'
#' @return A ggplot2 object
#' @export
h2o.variable_importance_heatmap <- function(object, newdata, top_n = 20) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  models_info <- .process_models_or_automl(object, newdata,
                                           require_multiple_models = TRUE,
                                           top_n = 20, only_with_varimp = TRUE
  )
  models <- Filter(function(m) .has_varimp(m@model_id), models_info$models)
  varimps <- lapply(models, .varimp, newdata)
  names(varimps) <- .model_ids(models)
  varimps <- lapply(varimps, function(varimp) {
    varimp[models_info$x[!models_info$x %in% names(varimp)]] <- 0
    varimp[models_info$x]
  })
  res <- do.call(rbind, varimps)
  results <- as.data.frame(res)
  ordered <- row.names(results)
  if (length(ordered) > 2) {
    ordered <- ordered[stats::hclust(stats::dist(results))$order]
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

  p <- ggplot2::ggplot(ggplot2::aes(
    x = .data$model_id, y = .data$feature, fill = .data$value, text = .data$text
  ), data = results) +
    ggplot2::geom_tile() +
    ggplot2::labs(fill = "Variable Importance", x = "Model Id", y = "Feature", title = "Variable Imporance") +
    ggplot2::scale_x_discrete(limits = ordered) +
    ggplot2::scale_fill_distiller(palette = "RdYlBu") +
    ggplot2::theme_bw() +
    ggplot2::theme(
      axis.text.x = ggplot2::element_text(angle = 45, hjust = 1),
      plot.margin = ggplot2::margin(1, 1, 1, 7, "lines")
    )
  return(p)
}

#' Model Prediction Correlation
#'
#' @param object An H2OAutoML object or list of models
#' @param newdata An H2O Frame
#' @param top_n How many models to include.
#' @param cluster Order models based on their similarity
#' @param triangular Print just triangular part of correlation matrix
#'
#' @return A ggplot2 object
#' @export
h2o.model_correlation <- function(object, newdata, top_n = 20,
                                  cluster = TRUE, triangular = TRUE) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  models_info <- .process_models_or_automl(object, newdata, require_multiple_models = TRUE, top_n = top_n)

  if (is.finite(top_n)) {
    models <- head(models_info$models, n = min(length(models_info$models), top_n))
  }

  with_no_h2o_progress({
    preds <-
      sapply(models, function(m, df) {
        list(predict = as.numeric(as.data.frame(stats::predict(m, df)[["predict"]])[["predict"]]))
      }, newdata)
    preds <- as.data.frame(do.call(cbind, preds))
    names(preds) <- unlist(sapply(models, function(m) m@model_id))
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
  if (cluster) {
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

  p <- ggplot2::ggplot(ggplot2::aes(
    x = .data$model_id_1, y = .data$model_id_2, fill = .data$value, text = .data$text
  ), data = res) +
    ggplot2::geom_tile() +
    ggplot2::labs(fill = "Correlation", x = "Model Id", y = "Model Id") +
    ggplot2::ggtitle("Model Correlation") +
    ggplot2::scale_x_discrete(limits = ordered) +
    ggplot2::scale_y_discrete(limits = rev(ordered)) +
    ggplot2::scale_fill_distiller(limits = c(0, 1), palette = "RdYlBu") +
    ggplot2::coord_fixed() +
    (if (triangular) ggplot2::theme_classic() else ggplot2::theme_bw()) +
    ggplot2::theme(
      axis.text.x = ggplot2::element_text(
        angle = 45,
        hjust = 1 ),
      aspect.ratio = 1
    )

  return(p)
}

#' Do Residual Analysis.
#' This create a plot "Fitted vs Residuals" and tries to fit a surrogate model
#' if \code{use_surrogate_tree} is TRUE.
#'
#' @param model An H2OModel
#' @param newdata An H2OFrame
#'
#' @return A ggplot2 object
#' @export
h2o.residual_analysis <- function(model, newdata) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  if (is.character(model)) {
    model <- h2o.getModel(model)
  }
  if (any(class(model) == "H2OAutoML")) {
    model <- model@leader
  }
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
      ggplot2::labs(x = "Fitted", y = "Residuals", title = sprintf("Residual Analysis for \"%s\"", model@model_id)) +
      ggplot2::theme_bw()
  })
  return(p)
}

#' Make a plot of partial dependences of multiple models.
#'
#' @param object Either a list of models/model_ids or H2OAutoML.
#' @param newdata An H2OFrame.
#' @param column A feature column name to inspect.
#' @param best_of_family If TRUE, plot only best model of each family
#' @param target If multinomial, plot PDP just for \code{target} category.
#' @param row_index Calculate Individual Conditional Expectation for row \code{row_index}
#' @param max_factors Maximum number of factor levels to show.
#'
#' @return A ggplot2 object
#' @export
h2o.partial_dependences <- function(object,
                                    newdata,
                                    column,
                                    best_of_family = TRUE,
                                    target = NULL,
                                    row_index = -1,
                                    max_factors = 30) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  models_info <- .process_models_or_automl(object, newdata, best_of_family = best_of_family)
  if (h2o.nlevels(newdata[[column]]) > max_factors) {
    factor_frequencies <- .get_feature_frequencies(newdata[[column]])
    factors_to_merge <- tail(names(factor_frequencies), n = -max_factors)
    newdata[[column]] <- ifelse(newdata[[column]] %in% factors_to_merge, NA_character_,
                                newdata[[column]])
    message(length(factor_frequencies) - max_factors, " least common factor levels were omitted from \"",
            column, "\" feature.")
  }
  margin <- ggplot2::margin(5.5, 5.5, 5.5, 5.5)
  if (h2o.isfactor(newdata[[column]]))
    margin <- ggplot2::margin(5.5, 5.5, 5.5, max(5.5, max(nchar(h2o.levels(newdata[[column]])))))

  if (length(models_info$models) == 1) {
    targets <- NULL
    if (models_info$is_multinomial_classification) {
      targets <- h2o.levels(newdata[[models_info$y]])
    }
    with_no_h2o_progress({
      pdps <-
        h2o.partialPlot(models_info$models[[1]], newdata, column,
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

      p <- ggplot2::ggplot(ggplot2::aes(
        x = .data[[make.names(column)]],
        y = .data$mean_response,
        color = .data$target, fill = .data$target, text = .data$text
      ), data = pdp) +
        geom_point_or_line(!is.numeric(newdata[[column]]), ggplot2::aes(group = .data$target)) +
        geom_pointrange_or_ribbon(!is.numeric(newdata[[column]]), ggplot2::aes(
          ymin = .data$mean_response - .data$stddev_response,
          ymax = .data$mean_response + .data$stddev_response,
          group = .data$target
        )) +
        ggplot2::labs(
          title = sprintf(
            "%s on \"%s\"%s",
            if (row_index == -1) {
              "Partial Dependence"
            } else {
              "Individual Conditional Expectation"
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
        ggplot2::theme_bw() +
        ggplot2::theme(
          legend.title = ggplot2::element_blank(),
          axis.text.x = ggplot2::element_text(angle = if (h2o.isfactor(newdata[[column]])) 45 else 0, hjust = 1),
          plot.margin = margin
        )
      return(p)
    })
  }

  with_no_h2o_progress({
    evaluated_models <- c()
    results <- NULL
    for (model in models_info$models) {
      family <- substr(model@model_id, 0, 3)
      if (!best_of_family ||
        !family %in% evaluated_models || "Sta" == family) {
        evaluated_models <- c(evaluated_models, family)
        pdp <-
          h2o.partialPlot(model, newdata, column,
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
        results[[model@model_id]] <- pdp$mean_response
      }
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

    p <- ggplot2::ggplot(ggplot2::aes(
      x = .data[[col_name]],
      y = .data$values,
      color = .data$model_id,
      text = .data$text),
                         data = data
    ) +
      geom_point_or_line(!is.numeric(newdata[[column]]), ggplot2::aes(group = .data$model_id)) +
      ggplot2::geom_rug(ggplot2::aes(x = .data[[col_name]], y = NULL),
                        sides = "b", alpha = 0.1, color = "black",
                        data = rug_data
      ) +
      ggplot2::labs(y = "Mean Response", title = sprintf(
        "%s on \"%s\"%s",
        if (row_index == -1) {
          "Partial Dependence"
        } else {
          "Individual Conditional Expectation"
        },
        column,
        if (!is.null(target)) {
          sprintf(" with target = \"%s\"", target)
        } else {
          ""
        }
      )) +
      ggplot2::scale_color_brewer(type = "qual", palette = "Dark2") +
      ggplot2::theme_bw() +
      ggplot2::theme(
        axis.text.x = ggplot2::element_text(
          angle = if (h2o.isfactor(newdata[[column]])) 45 else 0,
          hjust = 1
        ),
        plot.margin = margin,
        legend.title = ggplot2::element_blank()
      )
    return(p)
  })
}

#' Make a plot of Individual Conditional Expectations for each decile.
#'
#' @param model An H2OModel.
#' @param newdata An H2OFrame.
#' @param column A feature column name to inspect.
#' @param target If multinomial, plot PDP just for \code{target} category.
#' @param max_factors Maximum number of factor levels to show.
#'
#' @return A ggplot2 object
#' @export
h2o.individual_conditional_expectations <- function(model,
                                                    newdata,
                                                    column,
                                                    target = NULL,
                                                    max_factors = 30) {
  # Used by tidy evaluation in ggplot2, since rlang is not required #' @importFrom rlang hack can't be used
  .data <- NULL
  models_info <- .process_models_or_automl(model, newdata, require_single_model = TRUE)

  with_no_h2o_progress({
    if (h2o.nlevels(newdata[[column]]) > max_factors) {
      factor_frequencies <- .get_feature_frequencies(newdata[[column]])
      factors_to_merge <- tail(names(factor_frequencies), n = -max_factors)
      newdata[[column]] <- ifelse(newdata[[column]] %in% factors_to_merge, NA_character_,
                                  newdata[[column]])
      message(length(factor_frequencies) - max_factors, " least common factor levels were omitted from \"",
              column, "\" feature.")    }

    margin <- ggplot2::margin(16.5, 5.5, 5.5, 5.5)
    if (h2o.isfactor(newdata[[column]]))
      margin <- ggplot2::margin(16.5, 5.5, 5.5, max(5.5, max(nchar(h2o.levels(newdata[[column]])))))

    quantiles <- order(as.data.frame(newdata[[models_info$y]])[[models_info$y]])
    quantiles <- quantiles[c(1, round((seq_len(11) - 1) * length(quantiles) / 10))]

    results <- data.frame()
    i <- 0
    for (idx in quantiles) {
      tmp <- as.data.frame(h2o.partialPlot(
        models_info$model,
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
      as.data.frame(h2o.partialPlot(models_info$model,
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

    p <- ggplot2::ggplot(ggplot2::aes(x = .data[[col_name]],
                                      y = .data$mean_response,
                                      color = .data$name,
                                      text = .data$text),
                         data = results) +
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
      ggplot2::theme_bw() +
      ggplot2::theme(
        legend.title = ggplot2::element_blank(),
        axis.text.x = ggplot2::element_text(angle = if (h2o.isfactor(newdata[[col_name]])) 45 else 0, hjust = 1),
        plot.margin = margin
      )
    return(p)
  })
}

######################################## Explain ###################################################

#' Generate explanations for \code{object} on \code{test_frame}.
#'
#' @param object One of the following: an H2OAutoML, an H2OAutoML Leaderboard slice, a model, a list of models.
#' @param test_frame An H2OFrame.
#' @param columns_of_interrest A vector of feature names. If specified, plot just these columns.
#' @param include_explanations If specified, do only the specified explanations.
#'   (Mutually exclusive with exclude_explanations)
#' @param exclude_explanations Exclude specified explanations.
#' @param top_n_features If \code{columns_of_interrest} is missing, use top n features.
#' @param best_of_family If True, explain only best of family models
#' @param user_overrides Overrides for individual explanations, e.g.,
#'   list(shap_summary_plot = list(top_n_features = 50))
#'
#' @return List of outputs with class "explanation"
#' @export
h2o.explain <- function(object,
                        test_frame,
                        columns_of_interrest = NULL,
                        include_explanations = "ALL",
                        exclude_explanations = character(),
                        top_n_features = 5,
                        best_of_family = TRUE,
                        user_overrides = list()) {
  models_info <- .process_models_or_automl(object, test_frame)
  multiple_models <- length(models_info$models) > 1
  result <- list()

  possible_explanations <- c(
    "leaderboard",
    "confusion_matrix",
    "residual_analysis",
    "variable_importance",
    "variable_importance_heatmap",
    "model_correlation",
    "shap_summary",
    "pdp",
    "ice"
  )

  if (!missing(include_explanations) && !missing(exclude_explanations)) {
    stop("You can't specify both include and exclude explanations. Use just one of them.")
  }

  skip_explanations <- c()

  if (!missing(include_explanations)) {
    if ("ALL" %in% include_explanations) {
      include_explanations <- possible_explanations
    }
    for (ex in include_explanations) {
      if (!ex %in% possible_explanations) {
        stop(sprintf(
          "Unknown explanation \"%s\"! Possible explanations are: %s.",
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
          "Unknown explanation \"%s\"! Possible explanations are: %s.",
          ex, paste0(possible_explanations, collapse = ", ")
        ))
      }
    }
    skip_explanations <- tolower(exclude_explanations)
  }

  if (!is.null(columns_of_interrest)) {
    for (col in columns_of_interrest) {
      if (!col %in% models_info$x) {
        stop(sprintf("Column \"%s\" is not in x.", col))
      }
    }
  } else {
    columns_of_interrest <- models_info$x

    if (all(startsWith(.model_ids(models_info$models), "StackedEnsemble"))) {
      warning(
        "StackedEnsemble does not have a variable importance. Picking all features. ",
        "Set `columns_of_interrest` to explain just a subset of features.",
        call. = FALSE
      )
    } else if ("feature_importance" %in% skip_explanations) {
      message("Either columns_of_interrest should be specified or feature importance must be enabled.",
              "Picking all features.")
    }
  }

  if (models_info$has_leaderboard && !"leaderboard" %in% skip_explanations) {
    result <- append(result, .explanation_header("Leaderboard"))
    result <- append(result, .describe("h2o.get_leaderboard"))
    result$leaderboard <- .create_leaderboard(models_info, test_frame)
  }

  # residual analysis /  confusion matrix
  if (models_info$is_classification) {
    if (!"confusion_matrix" %in% skip_explanations) {
      result <- append(result, .explanation_header("Confusion Matrix"))
      result <- append(result, .describe("h2o.confusionMatrix"))
      for (m in models_info$models) {
        result$confusion_matrix[[m@model_id]][[1]] <- .explanation_header(m@model_id, 2)
        result$confusion_matrix[[m@model_id]][[2]] <- .customized_call(
          h2o.confusionMatrix,
          object = m,
          overridable_defaults = list(newdata = test_frame),
          overrides = user_overrides$confusion_matrix
        )
        if (models_info$has_leaderboard) break
      }
    }
  } else {
    if (!"residual_analysis" %in% skip_explanations) {
      result <- append(result, .explanation_header("Residual Analysis"))
      result <- append(result, .describe("h2o.residual_analysis"))

      for (m in models_info$models) {
        result$residual_analysis[[m@model_id]] <- .customized_call(
          h2o.residual_analysis,
          model = m,
          newdata = test_frame,
          overrides = user_overrides$residual_analysis
        )
        if (models_info$has_leaderboard) break
      }
    }
  }

  # feature importance
  if (!"variable_importance" %in% skip_explanations) {
    result <- append(result, .explanation_header("Variable Importance"))
    result <- append(result, .describe("h2o.varimp"))
    varimp <- NULL
    for (m in models_info$models) {
      tmp <- .plot_varimp(m, test_frame)
      if (!is.null(tmp$varimp)) {
        result$feature_importance[[m@model_id]] <- tmp$p
        if (is.null(varimp)) varimp <- names(tmp$grouped_varimp)
        if (models_info$has_leaderboard) break
      }
    }
  } else {
    varimp <- models_info$x
  }

  if (is.null(columns_of_interrest)) {
    columns_of_interrest <- varimp[seq_len(min(length(varimp), top_n_features))]
    # deal with encoded columns
    columns_of_interrest <- sapply(columns_of_interrest, .find_appropriate_column_name, cols = models_info$x)
  }

  if (multiple_models) {
    # Variable Importance Heatmap
    if (!"variable_importance_heatmap" %in% skip_explanations) {
      if (length(Filter(function(m_id) !startsWith(m_id, "Stacked"), .model_ids(models_info$models))) > 1) {
        result <- append(result, .explanation_header("Variable Importance Heatmap"))
        result <- append(result, .describe("h2o.variable_importance_heatmap"))
        result$variable_importance_heatmap <- .customized_call(h2o.variable_importance_heatmap,
                                                               object = models_info,
                                                               newdata = test_frame,
                                                               overrides = user_overrides$variable_importance_heatmap
        )
      }
    }

    # Model Correlation
    if (!"model_correlation" %in% skip_explanations) {
      result <- append(result, .explanation_header("Model Correlation"))
      result <- append(result, .describe("h2o.model_correlation"))
      result$model_correlation <- .customized_call(h2o.model_correlation,
                                                   object = models_info,
                                                   newdata = test_frame,
                                                   overrides = user_overrides$model_correlation
      )
      top_n <- if (is.null(user_overrides$model_correlation$top_n)) 20
      else user_overrides$model_correlation$top_n
      result$model_correlation_interpretable_models <- sprintf(
        "Interpretable models: %s",
        paste(unlist(Filter(.interpretable,
                            .model_ids(
                              head(models_info$models,
                                   n = min(top_n, length(models_info$models))
                              )))),sep = ", ")
      )
    }
  }

  # SHAP summary
  if (!"shap_summary" %in% skip_explanations && !models_info$is_multinomial_classification) {
    num_of_tree_models <- sum(sapply(models_info$models, .is_h2o_tree_model))
    if (num_of_tree_models > 0) {
      result <- append(result, .explanation_header("SHAP Summary"))
      result <- append(result, .describe("h2o.shap_summary_plot"))
      for (m in Filter(.is_h2o_tree_model, models_info$models)) {
        result$shap_summary[[m@model_id]] <- .customized_call(
          h2o.shap_summary_plot,
          model = m,
          newdata = test_frame,
          overrides = user_overrides$shap_summary_plot
        )
        if (models_info$has_leaderboard) break
      }
    }
  }

  # PDP
  if (!"pdp" %in% skip_explanations) {
    result <- append(result, .explanation_header("Partial Dependence Plots"))
    result <- append(result, .describe("h2o.partial_dependences"))
    for (col in columns_of_interrest) {
      if (!multiple_models) {
        result$partial_dependences[[col]] <- .customized_call(
          h2o.partial_dependences,
          object = models_info$models,
          newdata = test_frame,
          column = col,
          overrides = user_overrides$partial_dependences
        )
      } else {
        if (models_info$is_multinomial_classification) {
          targets <- h2o.levels(test_frame[[models_info$y]])
          if (!is.null(user_overrides$partial_dependences[["target"]])) {
            targets <- user_overrides$partial_dependences[["target"]]
          }
          for (target in targets) {
            result$partial_dependeces[[col]][[target]] <- .customized_call(
              h2o.partial_dependences,
              object = models_info$models,
              newdata = test_frame,
              column = col,
              target = target,
              overridable_defaults = list(best_of_family = best_of_family),
              overrides = user_overrides$partial_dependences
            )
          }
        } else {
          result$partial_dependences[[col]] <- .customized_call(
            h2o.partial_dependences,
            object = models_info$models,
            newdata = test_frame,
            column = col,
            overridable_defaults = list(best_of_family = best_of_family),
            overrides = user_overrides$partial_dependences
          )
        }
      }
    }
  }

  # ICE quantiles
  if (!"ice" %in% skip_explanations) {
    result <- append(result, .explanation_header("Individual Conditional Expectations"))
    result <- append(result, .describe("h2o.individual_conditional_expectations"))
    for (col in columns_of_interrest) {
      for (m in models_info$models) {
        if (models_info$is_multinomial_classification) {
          targets <- h2o.levels(test_frame[[models_info$y]])
          if (!is.null(user_overrides$individual_conditional_expectations[["target"]])) {
            targets <- user_overrides$individual_conditional_expectations[["target"]]
          }

          for (target in targets) {
            result$individual_conditional_expectations[[col]][[m@model_id]][[target]] <- .customized_call(
              h2o.individual_conditional_expectations,
              model = m,
              newdata = test_frame,
              column = col,
              target = target,
              overrides = user_overrides$individual_conditional_expectations
            )
          }
        } else {
          result$individual_conditional_expectations[[col]][[m@model_id]] <- .customized_call(
            h2o.individual_conditional_expectations,
            model = m,
            newdata = test_frame,
            column = col,
            overrides = user_overrides$individual_conditional_expectations
          )
        }
        if (models_info$has_leaderboard) break
      }
    }
  }
  class(result) <- "explanation"
  return(result)
}

#' Explain models' behavior on a single row.
#'
#' @param object One of the following: an H2OAutoML, an H2OAutoML Leaderboard slice, a model, a list of models.
#' @param test_frame An H2OFrame.
#' @param row_index A row index of the instance to explain.
#' @param columns_of_interrest A vector of feature names. If specified, plot just these columns.
#' @param include_explanations If specified, do only the specified explanations.
#'   (Mutually exclusive with exclude_explanations)
#' @param exclude_explanations Exclude specified explanations.
#' @param user_overrides Overrides for individual explanations, e.g.,
#'   list(shap_explain_row=list(top_n_features=5))
#'
#' @return List of outputs with class "explanation"
#' @export
h2o.explain_row <- function(object,
                            test_frame,
                            row_index,
                            columns_of_interrest = NULL,
                            include_explanations = "ALL",
                            exclude_explanations = character(),
                            user_overrides = list()) {
  models_info <- .process_models_or_automl(object, test_frame)
  result <- list()

  possible_explanations <- c(
    "leaderboard",
    "shap_explanation",
    "ice"
  )

  if (!missing(include_explanations) && !missing(exclude_explanations)) {
    stop("You can't specify both include and exclude explanations. Use just one of them.")
  }

  skip_explanations <- c()

  if (!missing(include_explanations)) {
    if ("ALL" %in% include_explanations) {
      include_explanations <- possible_explanations
    }
    for (ex in include_explanations) {
      if (!ex %in% possible_explanations) {
        stop(sprintf(
          "Unknown explanation \"%s\"! Possible explanations are: %s.",
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
          "Unknown explanation \"%s\"! Possible explanations are: %s.",
          ex, paste0(possible_explanations, collapse = ", ")
        ))
      }
    }
    skip_explanations <- tolower(exclude_explanations)
  }


  if (!is.null(columns_of_interrest)) {
    for (col in columns_of_interrest) {
      if (!col %in% models_info$x) {
        stop(sprintf("Column \"%s\" is not in x.", col))
      }
    }
  } else {
    columns_of_interrest <- models_info$x
  }

  if (models_info$has_leaderboard && !"leaderboard" %in% skip_explanations) {
    result <- append(result, .explanation_header("Leaderboard"))
    result <- append(result, .describe(".leaderboard_for_row"))
    result$leaderboard <- .leaderboard_for_row(models_info, test_frame, row_index)
  }

  if (!"shap_explanation" %in% skip_explanations && !models_info$is_multinomial_classification) {
    result <- append(result, .explanation_header("SHAP explanation"))
    result <- append(result, .describe("h2o.shap_explain_row"))
    tree_models <- Filter(.is_h2o_tree_model, models_info$models)
    for (m in tree_models) {
      result$shap_explain_row[[m@model_id]] <- .customized_call(
        h2o.shap_explain_row,
        model = m,
        newdata = test_frame,
        row_index = row_index,
        overrides = user_overrides$shap_explain_row
      )
      if (models_info$has_leaderboard) break
    }
  }

  if (!"ice" %in% skip_explanations) {
    result <- append(result, .explanation_header("Individual Conditional Expectations"))
    for (col in columns_of_interrest) {
      if (models_info$is_multinomial_classification) {
        targets <- h2o.levels(test_frame[[models_info$y]])
        if (!is.null(user_overrides$partial_dependences[["target"]])) {
          targets <- user_overrides$partial_dependences[["target"]]
        }
        for (target in targets) {
          result$individual_conditional_expectations[[col]][[target]] <- .customized_call(
            h2o.partial_dependences,
            object = models_info, newdata = test_frame,
            column = col,
            best_of_family = models_info$has_leaderboard,
            target = target,
            row_index = row_index,
            overrides = user_overrides$partial_dependences
          )
        }
      } else {
        result$individual_conditional_expectations[[col]] <- .customized_call(
          h2o.partial_dependences,
          object = models_info,
          newdata = test_frame,
          column = col,
          best_of_family = models_info$has_leaderboard,
          row_index = row_index,
          overrides = user_overrides$partial_dependences
        )
      }
    }
  }
  class(result) <- "explanation"
  return(result)
}


#### On Load ####
.onLoad <- function(...) {
  registerS3method("print", "explanation", "print.explanation")
  if ("repr" %in% loadedNamespaces()) {
    registerS3method("repr_text", "explanation", "repr_text.explanation", envir=asNamespace("repr"))
    registerS3method("repr_html", "explanation", "repr_html.explanation", envir=asNamespace("repr"))
  }
  invisible()
}