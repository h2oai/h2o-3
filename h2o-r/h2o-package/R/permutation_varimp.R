#' Calculate Permutation Feature Importance.
#'
#' When n_repeats == 1, the result is similar to the one from h2o.varimp(), i.e., it contains
#' the following columns "Relative Importance", "Scaled Importance", and "Percentage".
#'
#' When n_repeats > 1, the individual columns correspond to the permutation variable
#' importance values from individual runs which corresponds to the "Relative Importance" and also
#' to the distance between the original prediction error and prediction error using a frame with
#' a given feature permuted.
#'
#' @param object    A trained supervised H2O model.
#' @param newdata   Training frame of the model which is going to be permuted
#' @param metric    Metric to be used. One of "AUTO", "AUC", "MAE", "MSE", "RMSE", "logloss", "mean_per_class_error",
#'                  "PR_AUC".  Defaults to "AUTO".
#' @param n_samples Number of samples to be evaluated. Use -1 to use the whole dataset. Defaults to 10 000.
#' @param n_repeats Number of repeated evaluations. Defaults to 1.
#' @param features  Character vector of features to include in the permutation importance. Use NULL to include all.
#' @param seed      Seed for the random generator. Use -1 to pick a random seed. Defaults to -1.
#' @return          H2OTable with variable importance.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' h2o.permutation_importance(model, prostate)
#' }
#' @export
h2o.permutation_importance <- function(object,
                                       newdata,
                                       metric = c("AUTO", "AUC", "MAE", "MSE", "RMSE", "logloss", "mean_per_class_error", "PR_AUC"),
                                       n_samples = 10000,
                                       n_repeats = 1,
                                       features = NULL,
                                       seed = -1
) {
  if (missing(object)) stop("Parameter 'object' needs to be specified.")
  if (!is(object, "H2OModel")) stop("Parameter 'object' has to be an H2O model.")
  .validate.H2OFrame(newdata, required = TRUE)

  if (!object@parameters$y %in% names(newdata))
    stop("The frame 'newdata' must contain the response column!")

  metric <- match.arg(arg = if (missing(metric)) "mse" else tolower(metric),
                      choices = tolower(eval(formals()$metric)))

  if (n_samples < -1 || n_samples %in% c(0, 1)) {
    stop("Argument n_samples must be either -1 or greater than 2.")
  }

  if (n_samples > nrow(newdata)) {
    n_samples <- -1
  }

  if (n_repeats < 1) {
    stop("Argument n_repeats must be greater than 0!")
  }

  if (length(features) > 0) {
    not_in_frame <- Filter(function(f) ! f %in% names(newdata), features)
    if (length(not_in_frame)) {
      stop(paste("Features ", paste0(features, collapse = ", "), " are not present in the newdata frame!"))
    }
  }

  vi <- as.data.frame(.newExpr("PermutationVarImp",
                               object@model_id,
                               newdata,
                               paste0("'", metric, "'"),
                               n_samples,
                               n_repeats,
                               if (length(features) == 0) "[]" else paste0("[\"", paste0(features, collapse = "\", \""), "\"]"),
                               seed
  ), check.names = FALSE)
  oldClass(vi) <- c("H2OTable", "data.frame")
  attr(vi, "header") <- "Variable Importances"
  attr(vi, "description") <- ""
  attr(vi, "formats") <- c("%s", rep_len("%5f", ncol(vi) - 1))
  vi
}


#' Plot Permutation Variable Importances.
#'
#' This method plots either a bar plot or if n_repeats > 1 a box plot and returns the variable importance table.
#'
#' @param object    A trained supervised H2O model.
#' @param newdata   Training frame of the model which is going to be permuted
#' @param metric    Metric to be used. One of "AUTO", "AUC", "MAE", "MSE", "RMSE", "logloss", "mean_per_class_error",
#'                  "PR_AUC".  Defaults to "AUTO".
#' @param n_samples Number of samples to be evaluated. Use -1 to use the whole dataset. Defaults to 10 000.
#' @param n_repeats Number of repeated evaluations. Defaults to 1.
#' @param features  Character vector of features to include in the permutation importance. Use NULL to include all.
#' @param seed      Seed for the random generator. Use -1 to pick a random seed. Defaults to -1.
#' @param num_of_features The number of features shown in the plot (default is 10 or all if less than 10).
#' @return H2OTable with variable importance.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' h2o.permutation_importance_plot(model, prostate)
#' }
#' @export
h2o.permutation_importance_plot <- function(object, newdata, metric = c("AUTO", "AUC", "MAE", "MSE", "RMSE", "logloss", "mean_per_class_error", "PR_AUC"),
                                            n_samples = 10000,
                                            n_repeats = 1,
                                            features = NULL,
                                            seed = -1,
                                            num_of_features = NULL) {
  if (is.null(num_of_features)) {
    num_of_features <- 10
  } else if ((num_of_features != round(num_of_features)) || (num_of_features <= 0)) {
    stop("num_of_features must be an integer greater than 0")
  }
  vi <- as.data.frame(h2o.permutation_importance(object = object,
                                                 newdata = newdata,
                                                 metric = metric,
                                                 n_samples = n_samples,
                                                 n_repeats = n_repeats,
                                                 features = features,
                                                 seed = seed))

  # check the model type and then update the model title
  title <- paste("Permutation Variable Importance: ", switch(object@algorithm,
                                                             "deeplearning" = "Deep Learning",
                                                             "stackedensemble" = "Stacked Ensemble",
                                                             toupper(object@algorithm)),
                 if (tolower(metric[1]) == "auto") "" else paste0(" (", tolower(metric), ")"),
                 sep = "")
  # use the longest ylable to adjust margins so ylabels don't cut off long string labels
  ylabels <- as.character(as.list(vi$Variable))
  ymargin <- max(strwidth(ylabels, "inch") + 0.4, na.rm = TRUE)
  op <- par(mai = c(1.02, ymargin, 0.82, 0.42))
  on.exit(par(op))
  if (n_repeats > 1) {
    varimp_order <- order(apply(vi[, -1], 1, mean))
    vi <- tail(vi[varimp_order,], n = num_of_features)
    graphics::boxplot(t(vi[, -1]), names = vi$Variable, horizontal = TRUE, col = "white", yaxt = "n", main = title,
                      xlab = "Permutation Variable Importance", axes = FALSE)
    graphics::axis(1)
    graphics::axis(2, at = seq_along(vi$Variable), labels = vi$Variable, las = 2, lwd = 0)
  } else {
    # if num_of_features = 1, create only one bar (adjust size to look nice)
    if (num_of_features == 1) {
      graphics::barplot(rev(head(vi[["Scaled Importance"]], n = num_of_features)),
                        names.arg = rev(head(vi$Variable, n = num_of_features)),
                        width = 0.2,
                        space = 1,
                        horiz = TRUE, las = 2,
                        ylim = c(0, 2),
                        xlim = c(0, 1),
                        axes = TRUE,
                        col = '#1F77B4',
                        main = title)
    } else if (num_of_features > 1) {
      graphics::barplot(rev(head(vi[["Scaled Importance"]], n = num_of_features)),
                        names.arg = rev(head(vi$Variable, n = num_of_features)),
                        space = 1, las = 2,
                        horiz = TRUE,
                        col = '#1F77B4', # blue
                        main = title)
    }
  }
  invisible(vi)
}