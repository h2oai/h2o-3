library(glmnet)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm_reg_path <- function() {
    # first run with no validation, compare against itself and glmnet
    d <-  h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))
    m = h2o.glm(training_frame=d,x=3:9,y=2,family='binomial',alpha=1,lambda_search = TRUE, solver='COORDINATE_DESCENT')
    regpath = h2o.getGLMFullRegularizationPath(m)
    expect_true(is.null(regpath$explained_deviance_valid))
    coefs1 = coefficients(m@model)
    coefs2 = regpath$coefficients[length(regpath$lambdas),]
    expect_false(max(abs(coefs1[names(coefs2)] - coefs2)) > 1e-10)
    # run glmnet
    d2 = as.data.frame(d)
    x = as.matrix(d2[,3:9])
    y = as.matrix(d2[,2])
    m_net = glmnet(x=x,y=y,family='binomial')
    for(i in 1:length(regpath$lambdas)){
      coefs_net = m_net$beta[,i]
      coefs_h2o = regpath$coefficients[i,]
      diff = max(abs((coefs_h2o[names(coefs_net)] - coefs_net)/max(1,coefs_net)))
      expect_false(diff > 1e-3)
    }
    print("with validation")
    # now make sure we can run with validation
    splits = h2o.splitFrame(d)
    d2 = as.data.frame(splits[[1]])
    x = as.matrix(d2[,3:9])
    y = as.matrix(d2[,2])
    m_net = glmnet(x=x,y=y,family='binomial')
    m = h2o.glm(training_frame=splits[[1]],validation_frame=splits[[2]],x=3:9,y=2,family='binomial',alpha=1,lambda_search = TRUE, solver='COORDINATE_DESCENT')
    regpath = h2o.getGLMFullRegularizationPath(m)
    expect_false(is.null(regpath$explained_deviance_valid))
    n = min(length(m_net$lambda),dim(regpath$coefficients)[1])
    for(i in 1:n){
          coefs_net = m_net$beta[,i]
          coefs_h2o = regpath$coefficients[i,]
          diff = max(abs((coefs_h2o[names(coefs_net)] - coefs_net)/max(1,coefs_net)))
          expect_false(diff > 1e-3)
    }
}
doTest("GLM Regularization Path extraction", test.glm_reg_path)