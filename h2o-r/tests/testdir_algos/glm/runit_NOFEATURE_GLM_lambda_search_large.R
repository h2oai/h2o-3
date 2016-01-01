setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GLM.lambda.search <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  prostate.hex = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame= "prostate.hex")
  prostate.sum = summary(prostate.hex)
  print(prostate.sum)
  
  # GLM without lambda search, lambda is single user-provided value
  h2oTest.logInfo("H2O GLM (binomial) with parameters: lambda_search = TRUE, nfolds: 2\n")
  prostate.nosearch = h2o.glm(x = 3:9, y = 2, training_frame = prostate.hex, family = "binomial", nlambdas = 5, lambda_search = FALSE, nfolds = 2)
  params.nosearch = prostate.nosearch@model$params
  expect_error(h2o.getGLMLambdaModel(prostate.nosearch, 0.5))
  
  # GLM with lambda search, return only model corresponding to best lambda as determined by H2O
  h2oTest.logInfo("H2O GLM (binomial) with parameters: lambda_search: TRUE, nfolds: 2\n")
  prostate.bestlambda = h2o.glm(x = 3:9, y = 2, training_frame = prostate.hex, family = "binomial", nlambdas = 5, lambda_search = TRUE, nfolds = 2)
  params.bestlambda = prostate.bestlambda@model$params
  
  random_lambda = sample(params.bestlambda$lambda_all, 1)
  h2oTest.logInfo(cat("Retrieving model corresponding to randomly chosen lambda", random_lambda, "\n"))
  random_model = h2o.getGLMLambdaModel(prostate.bestlambda, random_lambda)
  expect_equal(random_model@model$lambda, random_lambda)
  
  h2oTest.logInfo(cat("Retrieving model corresponding to best lambda", params.bestlambda$lambda_best, "\n"))
  best_model = h2o.getGLMLambdaModel(prostate.bestlambda, params.bestlambda$lambda_best)
  expect_equal(best_model@model, prostate.bestlambda@model)
  
  # GLM with lambda search, return models corresponding to all lambda searched over
  h2oTest.logInfo("H2O GLM (binomial) with parameters: lambda_search: TRUE, nfolds: 2\n")
  prostate.search = h2o.glm(x = 3:9, y = 2, training_frame = prostate.hex, family = "binomial", nlambdas = 5, lambda_search = TRUE, nfolds = 2)
  models.best = prostate.search@models[[prostate.search@best_model]]
  models.bestlambda = models.best@model$params$lambda_best
  expect_equal(models.best@model$lambda, models.bestlambda)
  
  
}

h2oTest.doTest("GLM Lambda Search Test: Prostate", test.GLM.lambda.search)
