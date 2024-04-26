setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
###############################################################
#### Test redundant constraints are caught             #######
###############################################################

test_constraints_redundant <- function() {
  result = tryCatch({
    training_frame <<- h2o.importFile(locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    training_frame[,1] <- as.factor(training_frame[,1])
    training_frame[,2] <- as.factor(training_frame[,2])
    training_frame[,3] <- as.factor(training_frame[,3])
    training_frame[,4] <- as.factor(training_frame[,4])
    training_frame[,5] <- as.factor(training_frame[,5])
    training_frame[,6] <- as.factor(training_frame[,6])
    training_frame[,7] <- as.factor(training_frame[,7])
    training_frame[,8] <- as.factor(training_frame[,8])
    training_frame[,9] <- as.factor(training_frame[,9])
    training_frame[,10] <- as.factor(training_frame[,10])
    linearConstraints <<- h2o.importFile(locate("smalldata/glm_test/linearConstraint3.csv"))

    x=c(1:20)
    model <- h2o.glm(x = x, y=21, training_frame=training_frame, max_iterations=1, linear_constraints=linearConstraints, solver="IRLSM")
  }, error = function(e) {
    print("***")
    print(e)
    expect_true(grepl("redundant and possibly conflicting linear constraints:", e))
  })
}

doTest("GLM Test: Detect redundant constraint specification", test_constraints_redundant)
