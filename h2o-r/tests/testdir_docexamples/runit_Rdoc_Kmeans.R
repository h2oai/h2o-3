


test.rdocKmeans.golden <- function() {

prosPath <- locate("smalldata/extdata/prostate.csv")
prostate.hex <- h2o.uploadFile(path = prosPath)
h2o.kmeans(training_frame = prostate.hex, k = 10, x = c("AGE", "RACE", "VOL", "GLEASON"))


}

doTest("R Doc K Means", test.rdocKmeans.golden)
