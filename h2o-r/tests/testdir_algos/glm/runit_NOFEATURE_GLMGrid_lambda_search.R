setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GLMGrid.lambda.search <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  prostate.hex = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  prostate.sum = summary(prostate.hex)
  print(prostate.sum)
  
  h2oTest.logInfo("H2O GLM (binomial) with parameters: alpha = c(0.25, 0.5), nlambda = 20, lambda_search = TRUE, nfolds: 2\n")
  # missing alpha=c(0.25, 0.5)
  prostate.bestlambda = h2o.glm(x = 3:9, y = 2, training_frame = prostate.hex, family = "binomial", nlambdas = 5, lambda_search = TRUE, nfolds = 2)
  model_idx = ifelse(runif(1) <= 0.5, 1, 2)
  model.bestlambda = prostate.bestlambda@model[[model_idx]]
  params.bestlambda = model.bestlambda@model$params
  
  h2oTest.logInfo(cat("All lambda values returned:\n", params.bestlambda$lambda_all))
  expect_true(length(params.bestlambda$lambda_all) <= 5)
  
  random_lambda = sample(params.bestlambda$lambda_all, 1)
  print("RANDOM LAMBDA")
  print(random_lambda)

  h2oTest.logInfo(cat("Retrieving model corresponding to alpha =", params.bestlambda$alpha, "and randomly chosen lambda", random_lambda, "\n"))
  random_model = h2o.getGLMLambdaModel(model.bestlambda, random_lambda)

  h2oTest.logInfo("EXPECTING THESE TO BE EQUAL")
  print(random_model@model$lambda)
  print(random_lambda)

  expect_equal(random_model@model$lambda, random_lambda)

  h2oTest.logInfo(cat("Retrieving model corresponding to alpha =", params.bestlambda$alpha, "and best lambda", params.bestlambda$lambda_best, "\n"))
  best_model = h2o.getGLMLambdaModel(model.bestlambda, params.bestlambda$lambda_best)
  expect_equal(best_model@model, model.bestlambda@model)
  
  h2oTest.logInfo("H2O GLM (binomial) with parameters: alpha = c(0.25, 0.5), nlambda = 20, lambda_search = TRUE, nfolds: 2\n")
  prostate.search = h2o.glm(x = 3:9, y = 2, data = prostate.hex, family = "binomial", alpha = c(0.25, 0.5), nlambdas = 5, lambda_search = TRUE, nfolds = 2)
  model.search = prostate.search@model[[model_idx]]
  models.best = model.search@models[[model.search@best_model]]
  params.best = models.best@model$params



  expect_equal(params.bestlambda$lambda_best, params.best$lambda_best)
  expect_true(length(params.best$lambda_all) <= 20)
  
}

h2oTest.doTest("GLM Grid Lambda Search Test: Prostate", test.GLMGrid.lambda.search)
