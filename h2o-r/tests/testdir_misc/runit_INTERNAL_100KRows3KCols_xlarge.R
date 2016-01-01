setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

data.hex <- h2o.importFile("maprfs:/datasets/WU_100KRows3KCols.csv")

#print(summary(data.hex))

myY = "C1"
myX = setdiff(names(data.hex), myY) 

# GLM Model
data.glm <- h2o.glm(myX, myY, training_frame = data.hex, family = 'gaussian', solver = 'L_BFGS')
print(data.glm)

# GBM Model
data.gbm <- h2o.gbm(myX, myY, training_frame = data.hex, distribution = 'gaussian')
print(data.gbm)
}

h2oTest.doTest("Test",rtest)
