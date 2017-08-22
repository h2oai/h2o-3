setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Test PCA with dataset from PUBDEV-3502.  However, this is a very wide dataset and therefore
# cannot use pca method Randomized or GLRM at this point.  In fact, Randomized does not work
# with wide dataset at all and I am in the process of making GLRM work with wide dataset.  For
# the purpose of this test, we are only going to use Power.  You can try GramSVD as well.
test.pca.la1s <- function() {
  run_time_c = c()
  num_run = 1

  browser()
  dataR = h2o.importFile(locate("bigdata/laptop/jira/la1s.wc.arff.txt.zip"),sep=',',destination_frame = "data",header = T, parse=FALSE)
  data <- h2o.parseRaw(dataR, destination_frame = "bigParse",
                              parse_type = "CSV", header = T) # chunk_size = 124022500 size will make one chunk.
  data$CLASS_LABEL = NULL

  for (runIndex in 1:num_run) {
    # k=1939 is very large and this is going to take a long time.
    mm = h2o.prcomp(data,transform = "STANDARDIZE",k =1938,max_iterations = 10,pca_method = "GramSVD")
    print("PCA (GramSVD) run time with car.arff.txt data in ms is ")
    print(mm@model$run_time)

    h2o.rm(mm)

    mm = h2o.prcomp(data,transform = "STANDARDIZE",k =1938,max_iterations = 10,pca_method = "Power")
    print("PCA (Power) run time with car.arff.txt data in ms is ")
    print(mm@model$run_time)

    h2o.rm(mm)

    mm = h2o.prcomp(data,transform = "STANDARDIZE",k =1938,max_iterations = 10,pca_method = "Randomized")
    print("PCA (Randomized) run time with car.arff.txt data in ms is ")
    print(mm@model$run_time)

    h2o.rm(mm)
  }
}

doTest("PCA Test: USArrests Data", test.pca.la1s)
