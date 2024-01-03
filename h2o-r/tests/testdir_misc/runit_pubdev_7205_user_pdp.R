setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

##
# PUBDEV-7205: fixed pdp bug caught by Megan Kurka.  Thanks.
##

testpdpUserSplits <- function() {
  iris.hex <- h2o.importFile(locate("smalldata/iris/iris.csv"), "iris.hex", col.names=c("Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width", "response"))
  temp <- as.data.frame(iris.hex[,4:5])
  for (ind in c(1:h2o.nrow(iris.hex))) {
    if (temp[ind, 2]=="Iris-virginica") {
      temp[ind,1] = 0
    } else {
      temp[ind,1] = 1
    }
  }
  iris.hex <- h2o.cbind(iris.hex, as.h2o(temp[,1]))
  iris.gbm <- h2o.gbm(model_id = "gbm_iris",
                      x = 1:4,
                      y = 6,
                      training_frame = iris.hex)
  pdps <- h2o.partialPlot(object=iris.gbm, newdata=iris.hex, cols=c("Sepal.Width", "Petal.Length", "Petal.Width"), 
  user_splits=list(c("Sepal.Width","0","1"), c("Petal.Length","1","2"),c("Petal.Width", "3","4","5")))
  petalW <- pdps[[3]]$Petal.Width
  expect_true(checkEqualsNumeric(petalW, c(3,4,5)))
  }

doTest("Test Partial Dependence Plots in H2O with User defined split points: ", testpdpUserSplits)

