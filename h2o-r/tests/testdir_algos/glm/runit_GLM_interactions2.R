setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.bin.accessors <- function() {
  d <- data.frame(iris[,c(5,1,2,3,4)])
  fr <- as.h2o(d)

  m_h2o_interaction <- h2o.glm(x=1:4,y=5,training_frame=fr, interactions=c(1,2), lambda=0, standardize=FALSE)
  m_h2o_interaction_coefs <- m_h2o_interaction@model$coefficients_table
    for(i in 1:length(m_R_coefs)) {
      name <- names(m_R_coefs[i])
      if( name=="(Intercept)" ) { name <- "Intercept" }
      print(name)
      h2o_coef <- m_h2o_interaction_coefs[m_h2o_interaction_coefs$names==name,"coefficients"]
      R_coef <- as.vector(m_R_coefs)[i]
      print( paste0("H2O Coeff: ",  h2o_coef))
      print( paste0("R   Coeff: ",  R_coef))
      expect_true( abs( h2o_coef - R_coef) < 1e-12 )
    }
}

doTest("Testing model accessors for GLM", test.glm.bin.accessors)
