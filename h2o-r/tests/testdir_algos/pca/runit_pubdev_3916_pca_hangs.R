setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Test PCA on car.arff.txt
test.pca.car <- function() {
  run_time_c = c()
  run_time_c2 = c()
  num_run = 10

  Log.info("Importing car.arff.txt data...\n")
  data=h2o.importFile(locate("smalldata/pca_test/car.arff.txt"))
  for (runIndex in 1:num_run) {
    mm = h2o.prcomp(data,transform = "STANDARDIZE",k=ncol(data))
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
  # check to make sure PCA is not taking too long to run
  expect_true(max(run_time_c) < 60000)
}

doTest("PCA Test: USArrests Data", test.pca.car)
