#' Calculate intersectional fairness metrics.
#'
#' @param model H2O Model
#' @param frame Frame used to calculate the metrics.
#' @param protected_cols List of categorical columns that contain sensitive information
#'                          such as race, gender, age etc.
#' @param reference List of values corresponding to a reference for each protected columns.
#'        If set to NULL, it will use the biggest group as the reference.
#' @param favorable_class Positive/favorable outcome class of the response.
#'
#' @return Dictionary of frames. One frame is the overview, other frames contain dependence
#'         of performance on threshold for each protected group.
#' @export
h2o.calculate_fairness_metrics <- function(model, frame, protected_cols, reference, favorable_class) {
  model_id <- if (is.character(model)) model else model@model_id
  if (is.null(h2o.keyof(frame)))
    head(frame, n = 1) # force evaluation of frame (in case it was manipulated before (e.g. subset))
  list_to_string <- function(entries) paste0("[\"", paste0(entries, collapse = "\", \"") ,"\"]")
  expr <- sprintf("(fairnessMetrics %s %s %s %s \"%s\")",
                  model_id ,
                  h2o.keyof(frame),
                  list_to_string(protected_cols),
                  list_to_string(reference),
                  favorable_class)
  lst <- h2o.rapids(expr)
  res <- list()
  for (i in seq_along(lst$map_keys$string)) {
    res[[lst$map_keys$string[[i]]]] <- as.data.frame(h2o.getFrame(lst$frames[[i]]$key$name))
    h2o.rm(lst$frames[[i]]$key$name, cascade = TRUE)
  }
  res
}


.get_corrected_variance <- function(fm) {
    # From De-biasing bias measurement https://arxiv.org/pdf/2205.05770.pdf
    max(0, var(fm[["accuracy"]], na.rm = TRUE) - mean((fm[["accuracy"]] * (1 - fm[["accuracy"]])) /
                                                        fm[["total"]], na.rm = TRUE))
}


#' Create a frame containing aggregations of intersectional fairness across the models.
#'
#' @param models List of H2O Models
#' @param newdata H2OFrame
#' @param protected_cols List of categorical columns that contain sensitive information such as race, gender, age etc.
#' @param reference List of values corresponding to a reference for each protected columns.
#'                  If set to NULL, it will use the biggest group as the reference.
#' @param favorable_class Positive/favorable outcome class of the response.
#' @return frame containing aggregations of intersectional fairness across the models
#' @export
h2o.disparate_analysis <-
  function(models,
           newdata,
           protected_cols,
           reference,
           favorable_class) {
    models_info <- .process_models_or_automl(
        models,
        newdata,
        check_x_y_consistency = FALSE,
        require_multiple_models = TRUE
      )
    y <- models_info$y
    return(cbind(
      .create_leaderboard(models_info, newdata, top_n = Inf),
      t(sapply(models, function(model) {
        capture.output({
          dm <-
            h2o.calculate_fairness_metrics(
              model = model,
              frame = newdata,
              protected_cols = protected_cols,
              reference = reference,
              favorable_class = favorable_class
            )$overview
        })
        return(
          c(
            var = var(dm[["accuracy"]], na.rm = TRUE),
            corrected_var = .get_corrected_variance(dm),
            air_min = min(dm$AIR_selectedRatio, na.rm = TRUE),
            air_mean = mean(dm$AIR_selectedRatio, na.rm = TRUE),
            air_median = stats::median(dm$AIR_selectedRatio, na.rm = TRUE),
            air_max = max(dm$AIR_selectedRatio, na.rm = TRUE),
            cair = stats::weighted.mean(dm$AIR_selectedRatio, dm$relativeSize, na.rm = TRUE),
            `p.value_min` = min(dm[["p.value"]], na.rm = TRUE),
            `p.value_median` = stats::median(dm[["p.value"]], na.rm = TRUE),
            `p.value_mean` = mean(dm[["p.value"]], na.rm = TRUE),
            `p.value_max` = max(dm[["p.value"]], na.rm = TRUE)
          )
        )
      }))
    ))
  }


.get_model_list <-
  function(
    model_fun,
    x,
    y,
    training_frame,
    ...
  ) {
    obj <- model_fun(x = x, y = y, training_frame = training_frame, ...)
    models_info <- .process_models_or_automl(obj, train, check_x_y_consistency=FALSE)
    lapply(models_info$model_ids, models_info$get_model)
  }

#' Train models over subsets selected using infogram
#'
#' @param ig Infogram object trained with the same protected columns
#' @param model_fun Function that creates models. This can be something like h2o.automl, h2o.gbm, etc.
#' @param training_frame Training frame
#' @param test_frame Test frame
#' @param y Response column
#' @param protected_columns Protected columns
#' @param reference List of values corresponding to a reference for each protected columns.
#'                  If set to NULL, it will use the biggest group as the reference.
#' @param favorable_class Positive/favorable outcome class of the response.
#' @param ... Parameters that are passed to the model_fun.
#' @return frame containing aggregations of intersectional fairness across the models
#'
#' @export
h2o.infogram_train_subset_models <-
  function(ig,
           model_fun,
           training_frame,
           test_frame,
           y,
           protected_columns,
           reference,
           favorable_class,
           ...
  ) {
    score <- as.data.frame(ig@admissible_score)
    stopifnot("Infogram has to be trained with protected columns" = "safety_index" %in% names(score))
    score <- score[order(score$safety_index, decreasing = TRUE), ]
    xs <- lapply(seq_len(nrow(score)), function(n) score$column[seq_len(n)])
    models <-
      do.call(c, lapply(xs, function(cols)
        .get_model_list(
          model_fun,
          x = cols,
          y = y,
          training_frame = training_frame,
          ...
        )))
    h2o.disparate_analysis(models, test_frame, protected_columns, reference, favorable_class = favorable_class)
  }