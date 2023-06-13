setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#Export file with h2o.export_file and compare with R counterpart when re importing file to check for parity.


test.export.file <- function(path) {
  data <- h2o.uploadFile(locate(path))

  fname <- paste(paste0(sample(letters, 3, replace = TRUE), collapse = ""),
                 paste0(sample(0:9, 3, replace = TRUE), collapse = ""), "prostate.parquet", sep = "_")
  dname <- file.path(sandbox(), fname)

  Log.info("Exporting File...")
  h2o.exportFile(data, dname, format = "parquet", force=TRUE)

  files <- list.files(dname, full.names = TRUE)
  print(files)

  Log.info("Comparing file with R...")
  rfiles <- ifelse( length(files) > 1, list.files(dname, full.names = TRUE), dname)
  Log.info(sprintf("Results stored in files: %s", paste(rfiles, collapse = ", ")))

  imported <- h2o.importFolder(path = dname, pattern = "part-m-")

  if (length(files) == 1) {
    expect_equal(imported, data)
  } else {
    expect_equal(mean(imported), mean(data))
  }

}

 test.export.file.prostate <- function() test.export.file("smalldata/prostate/prostate.csv")
 test.export.file.titanic_expanded <- function() test.export.file("smalldata/titanic/titanic_expanded.csv")
 test.export.file.airquality_train1 <- function() test.export.file("smalldata/testng/airquality_train1.csv")
 test.export.file.autoclaims <- function() test.export.file("smalldata/gbm_test/autoclaims.csv")
 test.export.file.item_demand <- function() test.export.file("smalldata/demos/item_demand.csv")

 doTest("Testing Exporting Parquet Files (prostate)", test.export.file.prostate)
 doTest("Testing Exporting Parquet Files (titanic_expanded)", test.export.file.titanic_expanded)
 doTest("Testing Exporting Parquet Files (airquality_train1)", test.export.file.airquality_train1)
 doTest("Testing Exporting Parquet Files (autoclaims)", test.export.file.autoclaims)
 doTest("Testing Exporting Parquet Files (item_demand)",  test.export.file.item_demand)
