


test.rdocls.golden <- function() {


prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
h2o.ls()


}

doTest("R Doc ls", test.rdocls.golden)
