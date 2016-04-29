setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions2 <- function() {
  h2o.glm(x=1:4,y=5,training_frame=as.h2o(iris[,c(5,1,2,3,4)]), interactions=c(1,2), lambda=0, standardize=FALSE)

   lm(Petal.Width ~ Petal.Length*Sepal.Width, data=iris)
   h2o.glm(y="Petal.Width", x=c("Petal.Length","Sepal.Width"), interactions=c("Petal.Length","Sepal.Width"), training_frame=as.h2o(iris), lambda=0, standardize=FALSE)

   h2o.glm(x=1:4,y=5,training_frame=as.h2o(iris[,c(5,1,2,3,4)]), interactions=c(1,2), lambda=0, standardize=FALSE)
   lm(Petal.Width ~ Species*Sepal.Length + Sepal.Width  + Petal.Length, data=iris)



   h2o.glm(y="Petal.Width", x=c("Species","Sepal.Length","Sepal.Width", "Petal.Length"), interactions=c("Species", "Sepal.Length"), lambda=0, standardize=FALSE, training_frame=as.h2o(iris))
   lm(Petal.Width ~ Species*Sepal.Length + Sepal.Width + Petal.Length, data=iris)
}

doTest("Testing model accessors for GLM", test.glm.interactions2)
