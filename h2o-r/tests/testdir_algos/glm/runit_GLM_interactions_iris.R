setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.bin.accessors <- function() {
  d <- data.frame(iris[,1:3],
                  # the interactions
                  Sepal.Length_Sepal.Width  = iris[,"Sepal.Length"]* iris[,"Sepal.Width"],
                  Sepal.Length_Petal.Length = iris[,"Sepal.Length"]* iris[,"Petal.Length"],
                  Sepal.Width_Petal.Length  = iris[,"Sepal.Width"] * iris[,"Petal.Length"],
                  # response
                  Petal.Width = iris[,"Petal.Width"])

  fr <- as.h2o(d)
  m_h2o <- h2o.glm(x=1:6,y=7,training_frame=fr,lambda=0)
  m_R   <- lm(Petal.Width~.,data=d)
  m_h2o_interaction <- h2o.glm(x=1:3,y=4,training_frame=as.h2o(iris), interactions=c(1,2,3), lambda=0)
  coef_R <-   coef(m_R)
}

doTest("Testing model accessors for GLM", test.glm.bin.accessors)
