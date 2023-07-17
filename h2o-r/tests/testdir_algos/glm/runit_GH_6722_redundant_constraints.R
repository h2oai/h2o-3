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
      
    # setup linear constraints
      Log.info("Set the linear constraints variables...")
      col_names <- c("C1.1", "C1.3", "constant", "C2.3", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "constant", 
                     "C1.4", "C2.1", "constant", "C2.1", "C18", "C2.2", "C11", "C13", "constant", "C15", "C16", "C17",
                     "constant", "C11", "C13", "constant")
      values <- c(0.5, 1.0, -3, 3, -4, 0.5, 0.1, -0.2, 2, -0.1, -0.4, 0.8, 0.1, 0.7, -1.1, 2, 0.5, -0.3, 0.5, -1.5, -0.3,
                  4, -0.2, -0.8, 1.6, 1.5, -4.5, -0.9)
      types <- c("lessthanequal", "lessthanequal", "lessthanqual", "lessthanequal", "lessthanqual", "lessthanequal",
                 "equal", "equal", "lessthanequal", "lessthanequal", "lessthanqual", "lessthanequal", "equal", "equal",
                 "equal", "equal", "equal", "equal", "equal", "equal", "equal", "lessthanequal", "lessthanequal", 
                 "lessthanqual", "lessthanequal", "equal", "equal", "equal")
      constraints_numbers <- c(0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 7, 7, 8, 8, 8)

      con <- data.frame( names = col_names ,
                         values = values,
                         types = types,
                         constraint_numbers = constraints_numbers)

    x=c(1:20)
    model <- h2o.glm(x = x, y=21, training_frame=training_frame, max_iterations=1, linear_constraints=con, solver="IRLSM")
  }, error = function(e) {
    print("***")
    print(e)
    expect_true(grepl("redundant and possibly conflicting linear constraints:", e))
  })
}

doTest("GLM Test: Detect redundant constraint specification", test_constraints_redundant)
