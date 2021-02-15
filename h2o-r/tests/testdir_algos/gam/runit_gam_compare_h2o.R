setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(mgcv)

test.model.gam <- function() {
  browser()
  h2o_data <- h2o.importFile(locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
  h2o_data$C1 <- h2o.asfactor(h2o_data$C1)
  h2o_data$C2 <- h2o.asfactor(h2o_data$C2)
  h2o_data$C21 <- h2o.asfactor(h2o_data$C21)
  rData <- as.data.frame(h2o_data)
  browser()
  fit <- gam(C21~C1+C2+s(C11,k=10,bs="cr")+s(C12,k=10,bs="cr")+s(C13,k=10,bs="cr"), family = binomial("logit"),data=rData)
  summary(fit)
  data <- h2o.importFile(path = locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
  data$C1 <- h2o.asfactor(data$C1)
  data$C2 <- h2o.asfactor(data$C2)
  rData <- as.data.frame(data)
  
  fit <- gam(C21~s(C11,k=10,bs="cr"), family = gaussian("identity"),data=rData)
  summary(fit)
  
  summary(fit)
}

doTest("General Additive Model test", test.model.gam)