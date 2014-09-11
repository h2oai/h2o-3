setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.RdocGBM.golden <- function(H2Oserver) {
	

ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(H2Oserver, path = ausPath)
independent<- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")
dependent<- "runoffnew"
h2o.gbm(y = dependent, x = independent, data = australia.hex, n.trees = 10, interaction.depth = 3, n.minobsinnode = 2, shrinkage = 0.2, distribution= "gaussian")
h2o.gbm(y = dependent, x = independent, data = australia.hex, n.trees = 15, 
  interaction.depth = 5, n.minobsinnode = 2, shrinkage = 0.01, distribution= "multinomial")


testEnd()
}

doTest("R Doc GBM", test.RdocGBM.golden)

