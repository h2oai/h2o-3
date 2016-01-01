setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

iris.hex <- h2o.importFile("maprfs:/datasets/iris/iris.csv", header = T)

print(summary(iris.hex))

myX = 1:4
myY = 5

# GBM Model
iris.gbm <- h2o.gbm(myX, myY, training_frame = iris.hex, distribution = 'multinomial')
print(iris.gbm)

myZ = 1

# GLM Model
iris.glm <- h2o.glm(myX, myZ, training_frame = iris.hex, family = "gaussian")
print(iris.glm)


# DL Model
iris.dl  <- h2o.deeplearning(myX, myY, training_frame = iris.hex, epochs=1, hidden=c(50,50), loss = 'CrossEntropy')
print(iris.dl)

# DRF Model
# iris.drf <- h2o.randomforest(myX, myY, training_frame = iris.hex)
}

h2oTest.doTest("Test",rtest)
