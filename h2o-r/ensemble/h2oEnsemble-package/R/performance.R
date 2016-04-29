# Generate predictions on a test set and return a 2-item list:  list(ensemble, base)
# ensemble: H2OModelMetrics object for ensemble performance on `newdata`
#     base: a list of H2OModelMetrics objects for base learner performance on `newdata`

h2o.ensemble_performance <- function(object, newdata, score_base_models = TRUE) {
  
  if (class(object) != "h2o.ensemble") {
    stop("object must be of class, h2o.ensemble")
  }
  if (!grepl("H2O", class(object$metafit))) {
    stop("H2O performance metrics are not supported for SuperLearner-based metalearners.")
  }
  
  # Training_frame may be a key or an H2OFrame object
  if ((!inherits(newdata, "Frame") && !inherits(newdata, "H2OFrame")))
    tryCatch(newdata <- h2o.getFrame(newdata),
             error = function(err) {
               stop("argument \"newdata\" must be a valid H2OFrame or id")
             })
  
  if (object$family == "binomial") {
    newdata_levelone <- h2o.cbind(sapply(object$basefits, function(ll) h2o.predict(object = ll, newdata = newdata)[,3]))
  } else {
    newdata_levelone <- h2o.cbind(sapply(object$basefits, function(ll) h2o.predict(object = ll, newdata = newdata)[,1]))
  }
  names(newdata_levelone) <- names(object$basefits)
  newdata_levelone$y <- newdata[,object$y]  #cbind the response column to calculate metrics
  newdata_metrics <- h2o.performance(model = object$metafit, newdata = newdata_levelone)
  if (score_base_models) {
    newdata_base_metrics <- sapply(object$basefits, function(ll) h2o.performance(model = ll, newdata = newdata))
  } else {
    newdata_base_metrics <- NULL
  }

  out <- list(ensemble = newdata_metrics, base = newdata_base_metrics)
  class(out) <- "h2o.ensemble_performance"
  return(out)
}


print.h2o.ensemble_performance <- function(x, metric = c("AUTO", "logloss", "MSE", "AUC", "r2"), ...) {

  # We limit metrics to those common among all possible base algos
  metric <- match.arg(metric)
  if (metric == "AUTO") {
    if (class(x$ensemble) == "H2OBinomialMetrics") {
      metric <- "AUC"
      family <- "binomial"
    } else {
      metric <- "MSE"
      family <- "gaussian"
    }
  }

  # Base learner test set AUC (for comparison)
  if (!is.null(x$base)) {
    learner <- names(x$base)
    L <- length(learner)
    base_perf <- sapply(seq(L), function(l) x$base[[l]]@metrics[[metric]])
    res <- data.frame(learner = learner, base_perf)
    names(res)[2] <- metric
    # Sort order for base learner metrics
    if (metric %in% c("AUC", "r2")) {
      # Higher AUC/R2, the better
      decreasing <- FALSE
    } else {
      decreasing <- TRUE
    }
    cat("\nBase learner performance, sorted by specified metric:\n")
    res <- res[order(res[, metric], decreasing = decreasing), ]
    print(res)
  }
  cat("\n")
  
  # Ensemble test set AUC
  ensemble_perf <- x$ensemble@metrics[[metric]]
  
  cat("\nH2O Ensemble Performance on <newdata>:")
  cat("\n----------------")
  cat(paste0("\nFamily: ", family))
  cat("\n")
  cat(paste0("\nEnsemble performance (", metric, "): ", ensemble_perf))
  cat("\n\n")
}
