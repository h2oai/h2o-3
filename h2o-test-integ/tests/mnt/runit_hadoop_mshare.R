library(h2o)

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit-hadoop.R')

ipPort <- get_args(commandArgs(trailingOnly = TRUE))
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

running_inside_hexdata = file.exists("/mnt/0xcustomer-datasets/c27/data.csv")

if (!running_inside_hexdata) {
    # hdp2.2 cluster
    stop("0xdata internal test and data.")
}

data.hex <- h2o.uploadFile("/mnt/0xcustomer-datasets/c27/data.csv", header = F)

model <- h2o.glm(x=4:ncol(data.hex), y=3, training_frame=data.hex, family="binomial", standardize=T)
PASS_BANNER()
