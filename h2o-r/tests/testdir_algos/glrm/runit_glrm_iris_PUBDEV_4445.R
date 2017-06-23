setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glrm.iris <- function() {
  Log.info("Importing iris_wheader.csv data...") 
  irisH2O <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"), destination_frame = "irisH2O")
  irisTest <- h2o.uploadFile(locate("smalldata/iris/iris_wheader_bad_cnames.csv"))
  
  num_cols <- colnames(irisH2O)[1:4]   # sepal_len, sepal_wid, petal_len, and petal_wid
  fitH2O <- h2o.glrm(irisH2O, k = 2, loss = "Quadratic", gamma_x = 0.5, gamma_y = 0.5, transform = "STANDARDIZE",
    impute_original = FALSE)

  abc = predict(fitH2O, irisTest)
  expect_warning(predict(fitH2O, irisTest))
}

doTest("GLRM Test: Iris with Various Transformations", test.glrm.iris)
