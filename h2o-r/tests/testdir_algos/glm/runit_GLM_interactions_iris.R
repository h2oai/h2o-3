setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions <- function() {
  d <- data.frame(iris[,1:3],
                  # the interactions
                  Sepal.Length_Sepal.Width  = iris[,"Sepal.Length"] * iris[,"Sepal.Width" ],
                  Sepal.Length_Petal.Length = iris[,"Sepal.Length"] * iris[,"Petal.Length"],
                  Sepal.Width_Petal.Length  = iris[,"Sepal.Width" ] * iris[,"Petal.Length"],
                  # response
                  Petal.Width = iris[,"Petal.Width"])

  fr <- as.h2o(d)
  m_h2o <- h2o.glm(x=1:6,y=7,training_frame=fr,lambda=0)
  m_h2o_coefs <- m_h2o@model$coefficients_table
  m_R   <- lm(Petal.Width~.,data=d)
  m_R_coefs <- coef(m_R)
  for(i in 1:length(m_R_coefs)) {
    name <- names(m_R_coefs[i])
    if( name=="(Intercept)" ) { name <- "Intercept" }
    print(name)
    h2o_coef <- m_h2o_coefs[m_h2o_coefs$names==name,"coefficients"]
    R_coef <- as.vector(m_R_coefs)[i]
    print( paste0("H2O Coeff: ",  h2o_coef))
    print( paste0("R   Coeff: ",  R_coef))
    expect_true( abs( h2o_coef - R_coef) < 1e-10 )
  }

  train <- as.h2o(iris)

  # use all pairwise interactions of specified columns
  m_h2o_interaction <- h2o.glm(x=1:3,y=4,training_frame=train,
                               interactions=c("Sepal.Length", "Sepal.Width", "Petal.Length"),
                               lambda=0, standardize=FALSE)
  # enumerate all pairwise interactions explicitly
  m_h2o_interaction_explicit <- h2o.glm(x=1:3,y=4,training_frame=train,
                                        interaction_pairs=list(
                                          c("Sepal.Length", "Sepal.Width"),
                                          c("Sepal.Length", "Petal.Length"),
                                          c("Sepal.Width", "Petal.Length")
                                        ), lambda=0, standardize=FALSE)

  m_h2o_interaction_coefs <- m_h2o_interaction@model$coefficients_table
  m_h2o_interaction_coefs_expl <- m_h2o_interaction_explicit@model$coefficients_table
  for(i in 1:length(m_R_coefs)) {
    name <- names(m_R_coefs[i])
    if( name=="(Intercept)" ) { name <- "Intercept" }
    print(name)
    h2o_coef <- m_h2o_interaction_coefs[m_h2o_interaction_coefs$names==name,"coefficients"]
    h2o_coef_expl <- m_h2o_interaction_coefs_expl[m_h2o_interaction_coefs_expl$names==name,"coefficients"]
    R_coef <- as.vector(m_R_coefs)[i]
    print( paste0("R   Coeff: ",  R_coef))
    print( paste0("H2O Coeff: ",  h2o_coef))
    print( paste0("H2O CoExp: ",  h2o_coef_expl))
    expect_true( abs( h2o_coef - R_coef) < 1e-10 )
    expect_true( abs( h2o_coef - h2o_coef_expl) < 1e-10 )
  }
}

doTest("Testing model accessors for GLM", test.glm.interactions)
