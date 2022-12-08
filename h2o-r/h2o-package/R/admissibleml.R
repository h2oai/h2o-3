#' Calculate intersectional fairness metrics.
#'
#' @param model H2O Model
#' @param frame Frame used to calculate the metrics.
#' @param protected_columns List of categorical columns that contain sensitive information
#'                          such as race, gender, age etc.
#' @param reference List of values corresponding to a reference for each protected columns.
#'        If set to NULL, it will use the biggest group as the reference.
#' @param favorable_class Positive/favorable outcome class of the response.
#'
#' @return Dictionary of frames. One frame is the overview, other frames contain dependence
#'         of performance on threshold for each protected group.
#'
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#' data <- h2o.importFile(paste0("https://s3.amazonaws.com/h2o-public-test-data/smalldata/",
#'                               "admissibleml_test/taiwan_credit_card_uci.csv"))
#' x <- c('LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1',
#'        'BILL_AMT2', 'BILL_AMT3', 'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2',
#'        'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6')
#' y <- "default payment next month"
#' protected_columns <- c('SEX', 'EDUCATION')
#'
#' for (col in c(y, protected_columns))
#'   data[[col]] <- as.factor(data[[col]])
#'
#' splits <- h2o.splitFrame(data, 0.8)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' reference <- c(SEX = "1", EDUCATION = "2")  # university educated man
#' favorable_class <- "0" # no default next month
#'
#' gbm <- h2o.gbm(x, y, training_frame = train)
#'
#' h2o.calculate_fairness_metrics(gbm, test, protected_columns = protected_columns,
#'                                reference = reference, favorable_class = favorable_class)
#' }
#' @export
h2o.calculate_fairness_metrics <- function(model, frame, protected_columns, reference, favorable_class) {
  model_id <- if (is.character(model)) model else model@model_id
  if (is.null(h2o.keyof(frame)))
    head(frame, n = 1) # force evaluation of frame (in case it was manipulated before (e.g. subset))
  list_to_string <- function(entries) paste0("[\"", paste0(entries, collapse = "\", \"") ,"\"]")
  expr <- sprintf("(fairnessMetrics %s %s %s %s \"%s\")",
                  model_id ,
                  h2o.keyof(frame),
                  list_to_string(protected_columns),
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
#' @param protected_columns List of categorical columns that contain sensitive information such as race, gender, age etc.
#' @param reference List of values corresponding to a reference for each protected columns.
#'                  If set to NULL, it will use the biggest group as the reference.
#' @param favorable_class Positive/favorable outcome class of the response.
#' @param air_metric Metric used for Adverse Impact Ratio calculation. Defaults to ``selectedRatio``.
#' @param alpha The alpha level is the probability of rejecting the null hypothesis that the protected group
#'              and the reference came from the same population when the null hypothesis is true.
#' @return frame containing aggregations of intersectional fairness across the models
#'
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.init()
#' data <- h2o.importFile(paste0("https://s3.amazonaws.com/h2o-public-test-data/smalldata/",
#'                               "admissibleml_test/taiwan_credit_card_uci.csv"))
#' x <- c('LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1',
#'        'BILL_AMT2', 'BILL_AMT3', 'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2',
#'        'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6')
#' y <- "default payment next month"
#' protected_columns <- c('SEX', 'EDUCATION')
#'
#' for (col in c(y, protected_columns))
#'   data[[col]] <- as.factor(data[[col]])
#'
#' splits <- h2o.splitFrame(data, 0.8)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' reference <- c(SEX = "1", EDUCATION = "2")  # university educated man
#' favorable_class <- "0" # no default next month
#'
#' aml <- h2o.automl(x, y, training_frame = train, max_models = 3)
#'
#' h2o.disparate_analysis(aml, test, protected_columns = protected_columns,
#'                        reference = reference, favorable_class = favorable_class)
#' }
#' @export
h2o.disparate_analysis <-
  function(models,
           newdata,
           protected_columns,
           reference,
           favorable_class,
           air_metric = "selectedRatio",
           alpha = 0.05
  ) {
    models_info <- .process_models_or_automl(
        models,
        newdata,
        check_x_y_consistency = FALSE,
        require_multiple_models = TRUE
      )
    leaderboard <- .create_leaderboard(models_info, newdata, top_n = Inf)
    return(cbind(
     leaderboard,
      t(sapply(lapply(leaderboard$model_id, models_info$get_model), function(model) {
        capture.output({
          dm <-
            h2o.calculate_fairness_metrics(
              model = model,
              frame = newdata,
              protected_columns = protected_columns,
              reference = reference,
              favorable_class = favorable_class
            )$overview
        })
        selected_air_metric <- paste0("AIR_", air_metric)
        if (!selected_air_metric %in% names(dm)) {
          stop(paste0("Metric ", air_metric, " is not present in the result of h2o.calculate_fairness_metrics. ",
                      "Please specify one of ", paste(Filter(function (m) startsWith(m, "AIR_"),
                                                             names(dm)), collapse = ", "), "."))
        }
        return(
          c(
            num_of_features = length(models_info$x),
            var = var(dm[["accuracy"]], na.rm = TRUE),
            corrected_var = .get_corrected_variance(dm),
            air_min = min(dm[[selected_air_metric]], na.rm = TRUE),
            air_mean = mean(dm[[selected_air_metric]], na.rm = TRUE),
            air_median = stats::median(dm[[selected_air_metric]], na.rm = TRUE),
            air_max = max(dm[[selected_air_metric]], na.rm = TRUE),
            cair = stats::weighted.mean(dm[[selected_air_metric]], dm$relativeSize, na.rm = TRUE),
            significant_air_min = min(dm[[selected_air_metric]][dm[["p.value"]] < alpha], na.rm = TRUE),
            significant_air_mean = mean(dm[[selected_air_metric]][dm[["p.value"]] < alpha], na.rm = TRUE),
            significant_air_median = stats::median(dm[[selected_air_metric]][dm[["p.value"]] < alpha], na.rm = TRUE),
            significant_air_max = max(dm[[selected_air_metric]][dm[["p.value"]] < alpha], na.rm = TRUE),
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
    models_info <- .process_models_or_automl(obj, training_frame, check_x_y_consistency=FALSE)
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
#' @param feature_selection_metrics One or more columns from the infogram@admissible_score.
#' @param metric Metric supported by stats::dist which is used to sort the features.
#' @param air_metric Metric used for Adverse Impact Ratio calculation. Defaults to ``selectedRatio``.
#' @param alpha The alpha level is the probability of rejecting the null hypothesis that the protected group
#'              and the reference came from the same population when the null hypothesis is true.
#' @param ... Parameters that are passed to the model_fun.
#' @return frame containing aggregations of intersectional fairness across the models
#'
#' @examples
#'\dontrun{
#' library(h2o)
#' h2o.connect()
#' data <- h2o.importFile(paste0("https://s3.amazonaws.com/h2o-public-test-data/smalldata/",
#'                               "admissibleml_test/taiwan_credit_card_uci.csv"))
#' x <- c('LIMIT_BAL', 'AGE', 'PAY_0', 'PAY_2', 'PAY_3', 'PAY_4', 'PAY_5', 'PAY_6', 'BILL_AMT1',
#'        'BILL_AMT2', 'BILL_AMT3', 'BILL_AMT4', 'BILL_AMT5', 'BILL_AMT6', 'PAY_AMT1', 'PAY_AMT2',
#'        'PAY_AMT3', 'PAY_AMT4', 'PAY_AMT5', 'PAY_AMT6')
#' y <- "default payment next month"
#' protected_columns <- c('SEX', 'EDUCATION')
#'
#' for (col in c(y, protected_columns))
#'   data[[col]] <- as.factor(data[[col]])
#'
#' splits <- h2o.splitFrame(data, 0.8)
#' train <- splits[[1]]
#' test <- splits[[2]]
#' reference <- c(SEX = "1", EDUCATION = "2")  # university educated man
#' favorable_class <- "0" # no default next month
#'
#' ig <- h2o.infogram(x, y, train, protected_columns = protected_columns)
#' print(ig@admissible_score)
#' plot(ig)
#'
#' infogram_models <- h2o.infogram_train_subset_models(ig, h2o.gbm, train, test, y,
#'                                                     protected_columns, reference,
#'                                                     favorable_class)
#'
#' pf <- h2o.pareto_front(infogram_models, x_metric = "air_min",
#'                        y_metric = "AUC", optimum = "top right")
#' plot(pf)
#' pf@pareto_front
#' }
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
           feature_selection_metrics = c("safety_index"),
           metric = "euclidean",
           air_metric = "selectedRatio",
           alpha = 0.05,
           ...
  ) {
    score <- as.data.frame(ig@admissible_score)
    if (missing(feature_selection_metrics) && !feature_selection_metrics %in% names(score)) {
      feature_selection_metrics <- "admissible_index"
    }
    for (fs_metric in feature_selection_metrics) {
      if (! fs_metric %in% names(score))
        stop(paste0("Metric ", fs_metric, " is not found in ig@admissible_score!"))
    }

    origin <- rep_len(0, length(feature_selection_metrics))
    feature_score <- apply(score[, feature_selection_metrics, drop = FALSE], 1,
                           function(row) stats::dist(rbind(row, origin), method = metric))

    score <- score[order(feature_score, decreasing = TRUE), ]
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
    if (missing(protected_columns) || length(protected_columns) == 0)
      return(h2o.make_leaderboard(models, test_frame))
    return(h2o.disparate_analysis(models, test_frame, protected_columns, reference,
                                  favorable_class = favorable_class,
                                  air_metric = air_metric,
                                  alpha = alpha
    ))
  }