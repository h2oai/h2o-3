setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

running_inside_hexdata = file.exists("/mnt/0xcustomer-datasets/c27/data.csv")

if (!running_inside_hexdata) {
    # hdp2.2 cluster
    stop("0xdata internal test and data.")
}

data.hex <- h2o.uploadFile("/mnt/0xcustomer-datasets/c27/data.csv", header = F)

model <- h2o.glm(x=4:ncol(data.hex), y=3, training_frame=data.hex, family="binomial", standardize=T)
}

h2oTest.doTest("Test",rtest)
