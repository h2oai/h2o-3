setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.RdocGBM.golden <- function(H2Oserver) {
	

ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(H2Oserver, path = ausPath)
independent<- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")
dependent<- "runoffnew"
h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, loss= "gaussian")
h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 15, 
  max_depth = 5, min_rows = 2, loss = 0.01, loss= "multinomial")


testEnd()
}

doTest("R Doc GBM", test.RdocGBM.golden)

