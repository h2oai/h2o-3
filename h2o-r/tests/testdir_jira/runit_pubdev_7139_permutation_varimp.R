setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.permutation.varimp <- function(){
    pros.train <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
    pros.train[,2] <- as.factor(pros.train[,2])
    pros.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train)

    # Default settings work
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train)
    expect_true(is.numeric(permutation_varimp[,3]))

    # Using specific metric works
    for (m in c("AUTO", "MSE", "RMSE", "AUC", "logloss")){
        permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, metric = m)
        expect_true(is.numeric(permutation_varimp[,3]))
    }

    # Using all data works
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, n_samples = -1)
    expect_true(is.numeric(permutation_varimp[,3]))

    # Warn about not being able to permute 1 row
    e <- tryCatch(h2o.permutation_importance(pros.gbm, pros.train, n_samples = 1), error = function(e) e)
    expect_true(is(e, "error"))

    # Using just two rows works
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, n_samples = 2)
    expect_true(is.numeric(permutation_varimp[,3]))

    # Using all features works
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, features = c())
    expect_true(is.numeric(permutation_varimp[,3]))

    # Using just one feature works
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, features = c("PSA"))
    expect_equal(nrow(permutation_varimp), 1)
    expect_true(is.numeric(permutation_varimp[,3]))

    # Using just two features works
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, features = c("PSA", "AGE"))
    expect_equal(nrow(permutation_varimp), 2)
    expect_true(is.numeric(permutation_varimp[,3]))

    # Repeated evaluation runs as expected
    permutation_varimp <- h2o.permutation_importance(pros.gbm, pros.train, n_repeats = 5)
    expect_equal(names(permutation_varimp), c("Variable", paste("Run", 1:5)))
    expect_true(is.numeric(permutation_varimp[,3]))
}


test.permutation.varimp_plot  <- function() {
   pros.train <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
   pros.train[,2] <- as.factor(pros.train[,2])
   pros.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train)
   f <- tempfile(fileext = ".pdf")
   # Barplot
   tryCatch({
     pdf(f)
     h2o.permutation_importance_plot(pros.gbm, pros.train)
     dev.off()
     expect_true(file.exists(f))
   }, finally={
     unlink(f)
   })
   # Boxplot
  tryCatch({
    pdf(f)
    h2o.permutation_importance_plot(pros.gbm, pros.train, n_repeats=5)
    dev.off()
    expect_true(file.exists(f))
  }, finally={
    unlink(f)
  })
}

doSuite("Testing Permutation Feature Importance", makeSuite(test.permutation.varimp, test.permutation.varimp_plot))
