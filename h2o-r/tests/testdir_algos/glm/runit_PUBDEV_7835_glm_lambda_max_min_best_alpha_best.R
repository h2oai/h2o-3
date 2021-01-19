library(glmnet)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# I have added two parameters to GLMModelOutput: lambda_min and lambda_max.  These parameters will take on the following
# meaning when lambda_search is enabled:
# lambda_max is the first lambda value searched;
# lambda_min is the smallest lambda value that may be searched.  If early-stop is enabled, we may not reach the end to 
# build GLM with lambda_min.
# In addition, users will be able to directly access alpha_best, lambda_best, lambda_min and lambda_max from the new functions
# that I added.

test.glm_lambda_min_max_best_alpha_best <- function() {
    d <-  h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))
    m = h2o.glm(training_frame=d,x=3:9,y=2,family='binomial',lambda_search=TRUE)
    expect_true(m@model$lambda_best==h2o.getLambdaBest(m))
    expect_true(m@model$lambda_min==h2o.getLambdaMin(m))
    expect_true(m@model$lambda_max==h2o.getLambdaMax(m))
    expect_true(m@model$alpha_best==h2o.getAlphaBest(m))
}

doTest("GLM alpha_best, lambda_best, lambda_min and lambda_max in modelOutput.", test.glm_lambda_min_max_best_alpha_best)
