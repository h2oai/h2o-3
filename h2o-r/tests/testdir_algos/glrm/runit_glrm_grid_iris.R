test.glrm.iris <- function() {
  Log.info("Importing iris_wheader.csv data...") 
  irisH2O <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"), destination_frame = "irisH2O")
  print(summary(irisH2O))

  hyper_params <- list()
  hyper_params$transform <- c("NONE", "DEMEAN", "DESCALE", "STANDARDIZE")
  hyper_params$k <- sample(1:7, 3)
  size_of_hyper_space <- length(hyper_params$transform) * length(hyper_params$k)
  gx <- abs(runif(1)); gy <- abs(runif(1))
  Log.info(paste("H2O GLRM with gamma_x = ", gx, ", gamma_y = ", gy, ", hyper_params = '", hyper_params , "'", sep = ""))
  grid <- h2o.grid("glrm", training_frame = irisH2O, loss = "Quadratic", gamma_x = gx, gamma_y = gy, hyper_params = hyper_params)
  # Verify size of grid
  expect_equal(length(grid@model_ids), size_of_hyper_space)
  # Fetch models
  grid_models <- lapply(grid@model_ids, function(model_id) { model = h2o.getModel(model_id) })
  expect_equal(length(grid_models), size_of_hyper_space)

  expect_model_param(grid_models, "transform", hyper_params$transform)
  expect_model_param(grid_models, "k", hyper_params$k)

  # Verify expected quality of models
  lapply(grid_models, function(model) {
    Log.info(paste("Iterations:", model@model$iterations, "\tFinal Objective:", model@model$objective))
    checkGLRMPredErr(model, irisH2O, tolerance = 1e-5)
    h2o.rm(model@model$representation_name)   # Remove X matrix to free memory
  })
}

doTest("GLRM Test: Iris with Various Transformations", test.glrm.iris)
