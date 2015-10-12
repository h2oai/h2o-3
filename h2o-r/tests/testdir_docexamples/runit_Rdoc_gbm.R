setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.RdocGBM.golden <- function() {


ausPath <- locate("smalldata/extdata/australia.csv", package="h2o")
australia.hex <- h2o.uploadFile(path = ausPath)
independent<- c("premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")
dependent<- "runoffnew"

# gaussian
h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, distribution= "gaussian")

# multinomial (coerce response to factor. "AUTO" recognize this as a multinomial classification problem)
australia.hex$runoffnew <- as.factor(australia.hex$runoffnew)
h2o.gbm(y = dependent, x = independent, training_frame = australia.hex, ntrees = 15, max_depth = 5, min_rows = 2, learn_rate = 0.01, distribution= "multinomial")


}

doTest("R Doc GBM", test.RdocGBM.golden)

