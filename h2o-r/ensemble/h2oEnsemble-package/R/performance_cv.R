h2o.ensemble_performance_cv <- function(object, training_frame=train, score_base_models=TRUE){
  out <- vector("list", length(object))
  for (i in 1:length(out)){
    out[[i]] <- h2o.ensemble_performance(object[[i]], newdata=training_frame[-object[[i]]$tt_ind,], score_base_models=score_base_models)
  }
  names(out) <- names(object)
  class(out) <- "h2o.ensemble_cv_performance"
  return(out)
}


### print function for class 'h2o.ensemble_cv_performance'
print.h2o.ensemble_cv_performance <- function(x, metric = c("AUTO", "logloss", "MSE", "AUC", "r2"), ...) {
  
  # We limit metrics to those common among all possible base algos
  metric <- match.arg(metric)
  if (metric == "AUTO") {
    if (class(x[[1]]$ensemble) == "H2OBinomialMetrics") {
      metric <- "AUC"
      family <- "binomial"
    } else {
      metric <- "MSE"
      family <- "gaussian"
    }
  }
  
  # Base learner test set AUC (for comparison)
  if (!is.null(x[[1]]$base)) {
    res <- data.frame(model=NA, learner=NA, metric=NA)
    
    for (i in 1:length(x)){
      model <- names(x)[i]
      learner <- names(x[[i]]$base)
      L <- length(learner)
      base_perf <- sapply(seq(L), function(l) x[[i]]$base[[l]]@metrics[[metric]])
      res2 <- data.frame(model = model, learner = learner, metric = base_perf)
      # Sort order for base learner metrics
      res <- rbind(res, res2)
    }
    names(res)[3] <- metric
    if (metric %in% c("AUC", "r2")) {
      # Higher AUC/R2, the better
      decreasing <- FALSE
    } else {
      decreasing <- TRUE
    }
    cat("\nBase learner performance, sorted by specified metric:\n")
    res <- na.omit(res[order(res[, c('model',metric)], decreasing = decreasing), ])
    print(res)
  }
  cat("\n")
  
  # Ensemble test set AUC
  metares <- data.frame(Repeat=NA, metric=NA)
  row <- 1
  reps <- sapply(stringr::str_split(names(x), "\\."),"[", 2)
  
  for (i in 1:length(x)){
    Repeat <- reps[i]
    perf <- x[[i]]$ensemble@metrics[[metric]]
    metares[row, 'Repeat'] <- Repeat
    metares[row, 'metric'] <- perf
    
    row <- row + 1
  }
  metares <- aggregate(metares$metric, list(metares$Repeat), mean)
  names(metares) <- c('repeat',paste0('mean ',metric))
  
  ensemble_perf <- mean(metares[,2], na.rm=T)
  
  cat("\nH2O Ensemble CV Performance on <newdata>:")
  cat("\n----------------")
  cat(paste0("\nFamily: ", family))
  cat("\n")
  cat("\nK-fold cross validation mean performance:\n")
  print(metares)
  cat(paste0("\nRepeated K-fold cross-validation mean ensemble performance (", metric, "): ", ensemble_perf))
  cat("\n\n")
}

