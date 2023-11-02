setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


ALGOS <- c("deeplearning", "drf", "gbm", "glm", "stackedensemble", "xgboost")


aml_models_regression_test <- function() {
    df <- h2o.importFile(locate("smalldata/titanic/titanic_expanded.csv"))
    df$name <- NULL
    dfs <- h2o.splitFrame(df)
    
    train <- dfs[[1]]
    test <- dfs[[2]]
    
    nColOriginal <- -1
    aml <- h2o.automl(y="fare", training_frame = train, max_models = 12)
    
    for (algo in ALGOS){
      print(algo)
      model <- h2o.get_best_model(aml, algo)
      if (is.null(model) && algo == "xgboost") next; # multinode
      contr <- h2o.predict_contributions(model, test, output_format = if (algo == "stackedensemble") "compact" else "original", background_frame = train)
      nColOriginal <- max(nColOriginal, ncol(contr))
      expect_true(all.equal(h2o.predict(model, test), h2o.sum(contr, axis=1, return_frame = TRUE)))
    }

    for (algo in ALGOS){
      print(algo)
      model <- h2o.get_best_model(aml, algo)
      if (is.null(model) && algo == "xgboost") next; # multinode
      contr <- h2o.predict_contributions(model, test[1:3,], output_format = "compact",  background_frame = train, output_per_reference = TRUE)
      expect_true(nColOriginal >= ncol(contr))
      eps <- 1e-4
      if (algo %in% c("xgboost"))
        eps <- 1e-3
    
      contr0 <- contr[contr$RowIdx == 0,]
      contr0 <- contr0[order(as.vector(contr0$BackgroundRowIdx)),]
    
      contr1 <- contr[contr$RowIdx == 1,]
      contr1 <- contr1[order(as.vector(contr1$BackgroundRowIdx)),]
    
      expect_true(all.equal(contr0$BiasTerm, contr1$BiasTerm))

      expect_true(mean(abs(as.vector(h2o.predict(model, train)) - as.vector(contr0$BiasTerm))) < eps)
      expect_true(max(abs(as.vector(h2o.predict(model, train)) - as.vector(contr0$BiasTerm))) < eps)
    }
}


aml_models_binomial_test <- function() {
    df <- h2o.importFile(locate("smalldata/titanic/titanic_expanded.csv"))
    df$name <- NULL
    dfs <- h2o.splitFrame(df)
    
    train <- dfs[[1]]
    test <- dfs[[2]]
    
    nColCompact <- -1

    aml <- h2o.automl(y="survived", training_frame = train, max_models = 12)
    
    for (algo in ALGOS){
      print(algo)
      model <- h2o.get_best_model(aml, algo)
      if (is.null(model) && algo == "xgboost") next; # multinode
      contr <- h2o.predict_contributions(model, test, output_format = "compact", background_frame = train, output_space = TRUE)
      nColCompact <- max(nColCompact, ncol(contr))
      expect_true(h2o.all(h2o.abs(h2o.predict(model, test)[, 3] - h2o.sum(contr, axis=1, return_frame = TRUE)) < 1e-3))
    }
    
    for (algo in ALGOS){
      print(algo)
      model <- h2o.get_best_model(aml, algo)
      if (is.null(model) && algo == "xgboost") next; # multinode
      eps <- 1e-4
      if (algo %in% c("xgboost"))
        eps <- 1e-3
    
      contr <- h2o.predict_contributions(model, test[1:3,], output_format = if (algo == "stackedensemble") "compact" else "original", background_frame = train, output_per_reference = TRUE)
      expect_true(nColCompact <= ncol(contr))
    
      contr0 <- contr[contr$RowIdx == 0,]
      contr0 <- contr0[order(as.vector(contr0$BackgroundRowIdx)),]
    
      contr1 <- contr[contr$RowIdx == 1,]
      contr1 <- contr1[order(as.vector(contr1$BackgroundRowIdx)),]
    
      expect_true(all.equal(contr0$BiasTerm, contr1$BiasTerm))
    
      link <- if (algo %in% c("gbm", "xgboost", "glm", "stackedensemble")) binomial()$linkinv else function(x) x
      
      expect_true(mean(abs(as.vector(h2o.predict(model, train)[,3]) - link(as.vector(contr0$BiasTerm)))) < eps)
      expect_true(max(abs(as.vector(h2o.predict(model, train)[,3]) - link(as.vector(contr0$BiasTerm)))) < eps)
    }
}


doSuite("SHAP Tests", makeSuite(
                    aml_models_regression_test,
                    aml_models_binomial_test
))
