


test.rdocRM.golden <- function() {

prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
s <- as.h2o(runif(nrow(prostate.hex)))
prostate.hex <- prostate.hex[s <= 0.8,]
h2o.ls()
h2o.rm(ids = "Last.value.hex")
h2o.ls()
h2o.rm(
ids = "prostate.hex")
remove(prostate.hex)
h2o.ls()


}

doTest("R Doc RM", test.rdocRM.golden)

