setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.bin.accessors <- function() {

  h2o.glm(x=1:4,y=5,training_frame=as.h2o(iris[,c(5,1,2,3,4)]), interactions=c(1,2), lambda=0, standardize=FALSE)

  lm(Petal.Width ~ Petal.Length*Sepal.Width, data=iris)
  h2o.glm(y="Petal.Width", x=c("Petal.Length","Sepal.Width"), interactions=c("Petal.Length","Sepal.Width"), training_frame=as.h2o(iris), lambda=0, standardize=FALSE)

  h2o.glm(x=1:4,y=5,training_frame=as.h2o(iris[,c(5,1,2,3,4)]), interactions=c(1,2), lambda=0, standardize=FALSE)
  lm(Petal.Width ~ Species*Sepal.Length + Sepal.Width + Petal.Length, data=iris)



  h2o.glm(y="Petal.Width", x=c("Species","Sepal.Length","Sepal.Width", "Petal.Length"), interactions=c("Species", "Sepal.Length"), lambda=0, standardize=FALSE, training_frame=as.h2o(iris))
  lm(Petal.Width ~ Species*Sepal.Length + Sepal.Width + Petal.Length, data=iris)

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

  d2 <- iris[,-5]
  m2 <- h2o.glm(x=1:3,y=4,training_frame=as.h2o(iris[,-5]),interactions=c(1,2),lambda=0, standardize=FALSE)
}

doTest("Testing model accessors for GLM", test.glm.bin.accessors)
