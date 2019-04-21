setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.multinomial.coeff.names <- function() {
  trainF <- h2o.importFile(locate("smalldata/iris/iris_train.csv"))
  my_model <- h2o.glm(x = 1:4,y = "species",training_frame = trainF,family = "multinomial",model_id = "my_model",seed = 1,lambda = 0)
  my_model

  cold <-my_model@model$coefficients_table
  cnew <- my_model@model$coefficients_table_multinomials_with_class_names
  
  # spot check values, complete check is already done in Python
  expect_equal(cold[[2]], cnew[[2]], tolerance=1e-10, info= "Return numerical values are different.")
}

doTest("Test GLM coefficient names", test.multinomial.coeff.names)

