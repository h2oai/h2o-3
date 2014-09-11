setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.Rbasicfunctions_Apply.golden <- function(H2Oserver) {
	
#Import data: 
Log.info("Importing Iris data...") 
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(H2Oserver, path = irisPath, key = "iris.hex")
summary(apply(iris.hex, 1, sum))



  testEnd()
}

doTest("Test Basic Function Apply", test.Rbasicfunctions_Apply.golden)

