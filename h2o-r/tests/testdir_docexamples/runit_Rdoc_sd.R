


test.rdocsd.golden <- function() {

    prosPath <- locate("smalldata/extdata/prostate.csv")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    sd(prostate.hex$AGE)


}

doTest("R Doc SD", test.rdocsd.golden)

