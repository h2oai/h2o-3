


test.rdocsum.golden <- function() {

ausPath <- locate("smalldata/extdata/australia.csv")
australia.hex <- h2o.uploadFile(path = ausPath, destination_frame = "australia.hex")
sum(australia.hex)
sum(australia.hex[,1:4], australia.hex[,5:8], na.rm=FALSE)


}

doTest("R Doc Sum", test.rdocsum.golden)

