setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions5 <- function() {

  df <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
  XY <- names(df)[c(1,2,3,4,6,8,9,13,17,18,19,31)]
  interactions <- XY[c(5,9)]
  m <- h2o.glm(x=XY[-length(XY)],y=XY[length(XY)],training_frame=df,interactions=interactions, lambda_search=TRUE)

  m <- h2o.glm(x=XY[-length(XY)],y=XY[length(XY)],training_frame=df,interactions=interactions, lambda=0, standardize=FALSE)
  dfExpanded <- h2o.cbind(.getExpanded(df[,c(interactions,"IsDepDelayed")], interactions=interactions,F,F,T),df[,XY])

  m2 <- h2o.glm(x=1:142,y=143, training_frame=dfExpanded, lambda=0, standardize=FALSE)



}

doTest("Testing model accessors for GLM", test.glm.interactions5)




  df <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
  XY <- names(df)[c(1,2,3,4,6,8,9,13,17,18,19,31)]

  interactions <- XY[c(5,9)]
  .getExpanded(df[,c(interactions,"IsDepDelayed")], interactions=interactions,F,F,T)  # interactions only










  df <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"))
  XY <- names(df)[c(1,2,3,4,6,8,9,13,17,18,19,31)]
  interactions <- XY[c(5,9)]
  m <- h2o.glm(x=XY[-length(XY)],y=XY[length(XY)],training_frame=df,interactions=interactions, lambda_search=TRUE)
