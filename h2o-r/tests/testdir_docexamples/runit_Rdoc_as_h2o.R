setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocash2o.golden <- function() {
	
#Example from as.factor R example

data(iris)
summary(iris)
iris.r <- iris
iris.h2o <- as.h2o(iris.r, destination_frame="iris.h2o")
class<- class(iris.h2o)

Log.info("Print class of iris.h2o")
Log.info(paste("iris.h2o  :" ,class))


testEnd()
}

doTest("R Doc as h2o", test.rdocash2o.golden)

