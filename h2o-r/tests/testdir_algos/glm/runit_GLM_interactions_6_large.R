setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions5 <- function() {

  df <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
  XY <- names(df)[c(1,2,3,4,6,8,9,13,17,18,19,31)]
  interactions <- XY[c(5,7,9)]
  m <- h2o.glm(x=XY[-length(XY)],y=XY[length(XY)],training_frame=df,interactions=interactions, lambda_search=TRUE,family="binomial")
  dfExpanded <- h2o.cbind(.getExpanded(df[,c(interactions,"IsDepDelayed")], interactions=interactions,T,F,T),df[,XY])
  m2 <- h2o.glm(x=1:443,y=444, training_frame=dfExpanded, lambda_search=TRUE, family="binomial")
  m1_coefs <- m@model$coefficients_table
  m2_coefs <- m2@model$coefficients_table

  # check that coeffs are the same up to 5 digits
  all(round(m2_coefs[m2_coefs[,2]>0,2],5) == round(m1_coefs[m1_coefs[,2]>0,2],5))
  all(round(m2_coefs[m2_coefs[,3]>0,3],5) == round(m1_coefs[m1_coefs[,3]>0,3],5))

}

doTest("Testing model accessors for GLM", test.glm.interactions5)