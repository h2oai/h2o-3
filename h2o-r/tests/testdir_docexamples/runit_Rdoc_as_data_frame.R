setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocasdataframe.golden <- function(H2Oserver) {
	
# Example from as.data.frame R doc

prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(H2Oserver, path = prosPath)
prostate.data.frame<- as.data.frame(prostate.hex)
sum<- summary(prostate.data.frame)
head<- head(prostate.data.frame)


testEnd()
}

doTest("R Doc as.data.frame", test.rdocasdataframe.golden)

