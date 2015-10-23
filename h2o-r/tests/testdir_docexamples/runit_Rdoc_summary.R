



test.rdocsummary.golden <- function() {

prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
summary(prostate.hex)
summary(prostate.hex$GLEASON)
summary(prostate.hex[,4:6])


}

doTest("R Doc Summary", test.rdocsummary.golden)

