setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.deeplearning_no_hidden <- function() {
	print("Reading in iris")
	iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
	print("Building Deep Learning model with no hidden units")
	hh <- h2o.deeplearning(x=c(1,2,3,4),y=5,hidden=numeric(),training_frame=iris.hex,export_weights_and_biases=TRUE)
    print(hh)
    w3 = h2o.weights(object = hh,1)
    expect_equal(dim(w3), c(3,4))
}

doTest("Deep Learning Test: Iris", check.deeplearning_no_hidden)
