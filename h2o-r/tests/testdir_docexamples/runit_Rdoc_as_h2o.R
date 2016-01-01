setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdocash2o.golden <- function() {
	
#Example from as.factor R example

data(iris)
summary(iris)
iris.r <- iris
iris.h2o <- as.h2o(iris.r, destination_frame="iris.h2o")
class<- class(iris.h2o)

h2oTest.logInfo("Print class of iris.h2o")
h2oTest.logInfo(paste("iris.h2o  :" ,class))



}

h2oTest.doTest("R Doc as h2o", test.rdocash2o.golden)

