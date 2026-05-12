setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(rpart)

# Initialize H2O cluster
h2o.init()

# Define the test function
test_dt_multinomial <- function() {
  # Load the data
  data <- h2o.importFile(path = h2o:::.h2o.locate("smalldata/sdt/sdt_3EnumCols_10kRows_multinomial.csv"))
  response_col <- "response"
  data[, response_col] <- as.factor(data[, response_col])
  
  predictors <- c("C1", "C2", "C3")
  
  # Train the model
  dt <- h2o.decision_tree(max_depth = 3, x = predictors, y = response_col, training_frame = data)
  pred <- predict(dt, data)
  
  # Print model summary
  print(dt)
  expect_equal(nrow(unique(as.data.frame(pred$predict))), 3)
}

doTest("Decision tree: multinomial classification", test_dt_multinomial)
