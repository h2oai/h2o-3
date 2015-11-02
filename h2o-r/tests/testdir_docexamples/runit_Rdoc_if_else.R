


test.rdoc_if_else.golden <- function() {

ausPath <- locate("smalldata/extdata/australia.csv")
australia.hex <- h2o.uploadFile(path = ausPath)
australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)


}

doTest("R Doc If Else", test.rdoc_if_else.golden)

