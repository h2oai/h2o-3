setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Test PCA with dataset from PUBDEV-3502.  However, this is a very wide dataset and therefore
# cannot use pca method Randomized or GLRM at this point.  In fact, Randomized does not work
# with wide dataset at all and I am in the process of making GLRM work with wide dataset.  For
# the purpose of this test, we are only going to use Power.  You can try GramSVD as well.
test.pca.la1s <- function() {
  run_time_c = c()
  num_run = 2

  browser()
  dataR = h2o.importFile("bigdata/laptop/jira/la1s.wc.arff.txt.zip",sep=',',destination_frame = "data",header = T, parse=FALSE)
  data <- h2o.parseRaw(dataR, destination_frame = "bigParse",
                              parse_type = "CSV", header = T) # chunk_size = 124022500 size will make one chunk.
  data$CLASS_LABEL = NULL

  for (runIndex in 1:num_run) {
    # k=1939 is very large and this is going to take a long time.
    mm = h2o.prcomp(data,transform = "STANDARDIZE",k =1938,max_iterations = 300,pca_method = "Randomized")
    print("PCA run time with car.arff.txt data in ms is ")
    print(mm@model$run_time)
    run_time_c = c(run_time_c,mm@model$run_time)
    print("average run time in ms for data.arff.txt is: ")
    print(mean(run_time_c))
    print("maximum run time in ms for data.arff.txt is: ")
    print(max(run_time_c))
    print("minimum run time in ms for data.arff.txt is: ")
    print(min(run_time_c))

    h2o.rm(mm)
  }
}

doTest("PCA Test: USArrests Data", test.pca.la1s)
