setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocextremes.golden <- function() {


ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.uploadFile(path = ausPath, destination_frame = "australia.hex")
min(australia.hex)
min(australia.hex[,1:4], australia.hex[,5:8], na.rm=FALSE)


testEnd()
}

doTest("R Doc Extremes", test.rdocextremes.golden)

