setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.PCA.australia <- function(conn) {
  Log.info("Importing AustraliaCoast.csv data...\n")
  australia.data = read.csv(locate("smalldata/pca_test/AustraliaCoast.csv"), header = TRUE)
  australia.hex = h2o.importFile(conn, locate( "smalldata/pca_test/AustraliaCoast.csv",))
  australia.sum = summary(australia.hex)
  print(australia.sum)
  
  Log.info("H2O PCA on Australia coastline data:\n")
  australia.pca = h2o.prcomp(australia.hex, standardize = TRUE)

  Log.info("H2O PCA on Australia coastline data returning a maximum of 2 components:\n")
  australia.pca2 = h2o.prcomp(australia.hex, max_pc = 2, standardize = TRUE)
  
  expect_equal(ncol(australia.pca@model$rotation), 8)
  expect_equal(ncol(australia.pca2@model$rotation), 2)
  expect_equal(australia.pca@model$num_pc, australia.pca2@model$num_pc)
  
  testEnd()
}

doTest("PCA: Australia Data", test.PCA.australia)

