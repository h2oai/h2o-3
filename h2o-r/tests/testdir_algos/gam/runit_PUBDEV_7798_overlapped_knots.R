setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.gam.overlapped.knots <- function() {
    data <- data.frame(C1=sample(8, size=1000, replace = TRUE), target=sample(0:1, size=1000, replace = TRUE))
    data$target <- as.factor(data$target)
    
    splits <- h2o.splitFrame(data = data, ratios = 0.8)
    train <- splits[[1]]
    test <- splits[[2]]
    
    # Set the predictor and response columns
    predictors <- c("C1")
    response <- 'target'
    tryCatch({
      gam_model <- h2o.gam(x = predictors,
                           y = response,
                           training_frame = train,
                           family = 'binomial',
                           gam_columns = predictors,
                           scale = c(1),
                           num_knots = c(10))
    }, error=function(cond) {
        print("Test passed.")
    }, finally={
        print("Done.")
    })
    
}

doTest("General Additive Model test for overlapped knots", test.model.gam.overlapped.knots)

